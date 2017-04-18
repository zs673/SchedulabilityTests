package cominedAnalysisFramework;

import java.util.ArrayList;

import entity.Resource;
import entity.SporadicTask;

public class CombinedAnalysis {

	private boolean use_deadline_insteadof_Ri = false;
	long count = 0;
	SporadicTask problemtask = null;
	int isTestPrint = 0;

	public long[][] calculateResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long np, boolean printDebug) {
		long[][] init_Ri = UtilsNoOverheads.initResponseTime(tasks);

		long[][] response_time = new long[tasks.size()][];
		boolean isEqual = false, missDeadline = false;
		count = 0;

		for (int i = 0; i < init_Ri.length; i++) {
			response_time[i] = new long[init_Ri[i].length];
		}

		UtilsNoOverheads.cloneList(init_Ri, response_time);

		/* a huge busy window to get a fixed Ri */
		while (!isEqual) {
			isEqual = true;
			long[][] response_time_plus = busyWindow(tasks, resources, response_time, UtilsNoOverheads.MrsP_PREEMPTION_AND_MIGRATION, np);

			for (int i = 0; i < response_time_plus.length; i++) {
				for (int j = 0; j < response_time_plus[i].length; j++) {
					if (response_time[i][j] != response_time_plus[i][j]) {
						if (count > 10000) {
							System.out.println("task T" + tasks.get(i).get(j).id + " : " + response_time_plus[i][j] + " " + response_time[i][j]);
							System.out.println("task T" + tasks.get(i).get(j).id + " : " + tasks.get(i).get(j).spin + " " + tasks.get(i).get(j).local
									+ " " + tasks.get(i).get(j).interference);
						}
						isEqual = false;
					}

					if (response_time_plus[i][j] > tasks.get(i).get(j).deadline)
						missDeadline = true;
				}
			}

			count++;
			UtilsNoOverheads.cloneList(response_time_plus, response_time);

			if (missDeadline)
				break;
		}

		if (printDebug) {
			if (missDeadline)
				System.out.println("Combined Analysis    after " + count + " tims of recursion, the tasks miss the deadline.");
			else
				System.out.println("Combined Analysis    after " + count + " tims of recursion, we got the response time.");
			UtilsNoOverheads.printResponseTime(response_time, tasks);
		}

		return response_time;
	}

	private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, double oneMig,
			long np) {
		long[][] response_time_plus = new long[tasks.size()][];

		for (int i = 0; i < response_time.length; i++) {
			response_time_plus[i] = new long[response_time[i].length];
		}

		for (int i = 0; i < tasks.size(); i++) {
			for (int j = 0; j < tasks.get(i).size(); j++) {
				SporadicTask task = tasks.get(i).get(j);

				task.implementation_overheads = 0;
				task.migration_overheads_plus = 0;
				task.implementation_overheads += UtilsNoOverheads.FULL_CONTEXT_SWTICH1;

				task.spin = resourceAccessingTime(task, tasks, resources, response_time, response_time[i][j], 0, oneMig, np, task);
				task.interference = highPriorityInterference(task, tasks, response_time[i][j], response_time, resources, oneMig, np);
				task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j], oneMig, np);

				long implementation_overheads = (long) Math.ceil(task.implementation_overheads);
				long migration_overheads = (long) Math.ceil(task.migration_overheads_plus);

				response_time_plus[i][j] = task.Ri = task.WCET + task.spin + task.interference + task.local + implementation_overheads
						+ migration_overheads;

				if (task.Ri > task.deadline)
					return response_time_plus;

			}
		}
		return response_time_plus;
	}

	/*
	 * Direct Spin delay
	 */
	private long resourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris,
			long time, long jitter, double oneMig, long np, SporadicTask calT) {
		long resourceTime = 0;
		resourceTime += MrsPresourceAccessingTime(task, tasks, resources, Ris, time, jitter, oneMig, np, calT);
		resourceTime += FIFONPResourceTime(task, tasks, resources, Ris, time);
		resourceTime += FIFOPResourceAccessTime(task, tasks, resources, time, Ris);
		return resourceTime;
	}

	/**
	 * FIFO-NP resource accessing time.
	 */
	private long FIFONPResourceTime(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
		long spin_delay = 0;
		for (int k = 0; k < t.resource_required_index.size(); k++) {
			Resource resource = resources.get(t.resource_required_index.get(k));
			if (resource.protocol != 2 && resource.protocol != 3) {
				long NoS = getNoSpinDelay(t, resource, tasks, Ris, Ri);
				spin_delay += NoS * resource.csl;
				t.implementation_overheads += NoS * (UtilsNoOverheads.FIFONP_LOCK + UtilsNoOverheads.FIFONP_UNLOCK);
				t.implementation_overheads += t.number_of_access_in_one_release.get(k)
						* (UtilsNoOverheads.FIFONP_LOCK + UtilsNoOverheads.FIFONP_UNLOCK);

				spin_delay += resource.csl * t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(resource.id - 1));
			}

		}
		return spin_delay;
	}

	/**
	 * FIFO-P resource accessing time.
	 */
	private long FIFOPResourceAccessTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long time,
			long[][] Ris) {
		long spin = 0;
		ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
		ArrayList<Resource> fifo_resources = new ArrayList<>();
		for (int i = 0; i < resources.size(); i++) {
			Resource res = resources.get(i);
			if (res.protocol == 2) {
				requestsLeftOnRemoteP.add(new ArrayList<Long>());
				fifo_resources.add(res);
				spin += getSpinDelayForOneResoruce(task, tasks, res, time, Ris, requestsLeftOnRemoteP.get(i));
			}
		}

		if (fifo_resources.size() > 0) {
			// preemptions
			long preemptions = 0;
			long request_by_preemptions = 0;
			for (int i = 0; i < tasks.get(task.partition).size(); i++) {
				if (tasks.get(task.partition).get(i).priority > task.priority) {
					preemptions += (int) Math.ceil((double) (time) / (double) tasks.get(task.partition).get(i).period);
				}
			}
			task.implementation_overheads += preemptions * (UtilsNoOverheads.FIFOP_DEQUEUE_IN_SCHEDULE + UtilsNoOverheads.FIFOP_RE_REQUEST);

			while (preemptions > 0) {

				long max_delay = 0;
				int max_delay_resource_index = -1;
				for (int i = 0; i < fifo_resources.size(); i++) {
					if (max_delay < fifo_resources.get(i).csl * requestsLeftOnRemoteP.get(i).size()) {
						max_delay = fifo_resources.get(i).csl * requestsLeftOnRemoteP.get(i).size();
						max_delay_resource_index = i;
					}
				}

				if (max_delay > 0) {
					spin += max_delay;
					for (int i = 0; i < requestsLeftOnRemoteP.get(max_delay_resource_index).size(); i++) {
						requestsLeftOnRemoteP.get(max_delay_resource_index).set(i, requestsLeftOnRemoteP.get(max_delay_resource_index).get(i) - 1);
						if (requestsLeftOnRemoteP.get(max_delay_resource_index).get(i) < 1) {
							requestsLeftOnRemoteP.get(max_delay_resource_index).remove(i);
							i--;
						}
					}
					preemptions--;
					request_by_preemptions++;
				} else
					break;
			}

			task.spin_delay_by_preemptions = request_by_preemptions;
		}

		return spin;
	}

	private long getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, long time, long[][] Ris,
			ArrayList<Long> requestsLeftOnRemoteP) {
		long spin = 0;
		long ncs = 0;

		for (int i = 0; i < tasks.get(task.partition).size(); i++) {
			SporadicTask hpTask = tasks.get(task.partition).get(i);
			if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
				ncs += (int) Math.ceil((double) (time + Ris[hpTask.partition][i]) / (double) hpTask.period)
						* hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
			}
		}

		if (task.resource_required_index.contains(resource.id - 1))
			ncs += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));

		if (ncs > 0) {
			for (int i = 0; i < tasks.size(); i++) {
				if (task.partition != i) {
					/* For each remote partition */
					long number_of_request_by_Remote_P = 0;
					for (int j = 0; j < tasks.get(i).size(); j++) {
						if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
							SporadicTask remote_task = tasks.get(i).get(j);
							int indexR = getIndexRInTask(remote_task, resource);
							int number_of_release = (int) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
							number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
						}
					}

					long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);
					spin += possible_spin_delay;
					if (number_of_request_by_Remote_P - ncs > 0)
						requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
				}
			}
		}

		task.implementation_overheads += (spin + ncs) * (UtilsNoOverheads.FIFOP_LOCK + UtilsNoOverheads.FIFOP_UNLOCK);
		return spin * resource.csl + ncs * resource.csl;
	}

	/**
	 * MrsP resource accessing time.
	 */
	private long MrsPresourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris,
			long time, long jitter, double oneMig, long np, SporadicTask calT) {
		long resource_accessing_time = 0;

		for (int i = 0; i < task.resource_required_index.size(); i++) {
			Resource resource = resources.get(task.resource_required_index.get(i));

			if (resource.protocol == 3) {
				int number_of_request_with_btb = (int) Math.ceil((double) (time + jitter) / (double) task.period)
						* task.number_of_access_in_one_release.get(i);

				for (int j = 1; j < number_of_request_with_btb + 1; j++) {
					long oneAccess = 0;
					oneAccess += MrsPresourceAccessingTimeInOne(task, resource, tasks, Ris, time, jitter, j, calT);

					if (oneMig != 0) {
						double mc = migrationCostForSpin(oneMig, np, task, j, resource, tasks, time, Ris, calT);
						long mc_long = (long) Math.floor(mc);
						calT.migration_overheads_plus += mc - mc_long;
						if (mc - mc_long < 0) {
							System.err.println("MrsP mig error");
							System.exit(-1);
						}
						oneAccess += mc_long;
					}

					resource_accessing_time += oneAccess;
				}
			}

		}

		return resource_accessing_time;
	}

	private long MrsPresourceAccessingTimeInOne(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris,
			long time, long jitter, int n, SporadicTask calTask) {
		int number_of_access = 0;

		for (int i = 0; i < tasks.size(); i++) {
			if (i != task.partition) {
				/* For each remote partition */
				int number_of_request_by_Remote_P = 0;
				for (int j = 0; j < tasks.get(i).size(); j++) {
					if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
						SporadicTask remote_task = tasks.get(i).get(j);
						int indexR = getIndexRInTask(remote_task, resource);
						int number_of_release = (int) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
						number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
					}
				}
				int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time);
				int possible_spin_delay = number_of_request_by_Remote_P - getNoRFromHP - n + 1 < 0 ? 0
						: number_of_request_by_Remote_P - getNoRFromHP - n + 1;
				number_of_access += Integer.min(possible_spin_delay, 1);
			}
		}

		// account for the request of the task itself
		number_of_access++;

		calTask.implementation_overheads += number_of_access * (UtilsNoOverheads.MrsP_LOCK + UtilsNoOverheads.MrsP_UNLOCK);

		return number_of_access * resource.csl;
	}

	/*
	 * Indirect Spin delay
	 */
	private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long time, long[][] Ris,
			ArrayList<Resource> resources, double oneMig, long np) {
		long interference = 0;
		int partition = t.partition;
		ArrayList<SporadicTask> tasks = allTasks.get(partition);

		for (int i = 0; i < tasks.size(); i++) {
			if (tasks.get(i).priority > t.priority) {
				SporadicTask hpTask = tasks.get(i);
				interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
				interference += MrsPresourceAccessingTime(hpTask, allTasks, resources, Ris, time, Ris[partition][i], oneMig, np, t);
				interference += getIndirectSpinDelay(hpTask, time, Ris[partition][i], Ris, allTasks, resources, t);
				t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period)
						* (UtilsNoOverheads.FULL_CONTEXT_SWTICH1 + UtilsNoOverheads.FULL_CONTEXT_SWTICH2);
			}
		}
		return interference;
	}

	/**
	 * FIFO-NP indirect spin delay.
	 */
	private long getIndirectSpinDelay(SporadicTask hpTask, long Ri, long Rihp, long[][] Ris, ArrayList<ArrayList<SporadicTask>> allTasks,
			ArrayList<Resource> resources, SporadicTask calTask) {
		long BTBhit = 0;

		for (int i = 0; i < hpTask.resource_required_index.size(); i++) {
			/* for each resource that a high priority task request */
			Resource resource = resources.get(hpTask.resource_required_index.get(i));

			if (resource.protocol != 2 && resource.protocol != 3) {
				int number_of_higher_request = getNoRFromHP(resource, hpTask, allTasks.get(hpTask.partition), Ris[hpTask.partition], Ri);
				int number_of_request_with_btb = (int) Math
						.ceil((double) (Ri + (use_deadline_insteadof_Ri ? hpTask.deadline : Rihp)) / (double) hpTask.period)
						* hpTask.number_of_access_in_one_release.get(i);

				BTBhit += number_of_request_with_btb * resource.csl;
				calTask.implementation_overheads += number_of_request_with_btb * (UtilsNoOverheads.FIFONP_LOCK + UtilsNoOverheads.FIFONP_UNLOCK);

				for (int j = 0; j < resource.partitions.size(); j++) {
					if (resource.partitions.get(j) != hpTask.partition) {
						int remote_partition = resource.partitions.get(j);
						int number_of_remote_request = getNoRRemote(resource, allTasks.get(remote_partition), Ris[remote_partition], Ri);

						int possible_spin_delay = number_of_remote_request - number_of_higher_request < 0 ? 0
								: number_of_remote_request - number_of_higher_request;

						int spin_delay_with_btb = Integer.min(possible_spin_delay, number_of_request_with_btb);

						BTBhit += spin_delay_with_btb * resource.csl;
						calTask.implementation_overheads += spin_delay_with_btb * (UtilsNoOverheads.FIFONP_LOCK + UtilsNoOverheads.FIFONP_UNLOCK);
					}
				}
			}

		}
		return BTBhit;
	}

	/*
	 * Arrival Blocking
	 */
	private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
			double oneMig, long np) {
		long localblocking = 0;

		localblocking += FIFONPlocalBlocking(t, tasks, resources, Ris, time);
		localblocking += FIFOPlocalBlocking(t, tasks, resources, Ris, time);

		long MrsPlocalblocking = MrsPlocalBlocking(t, tasks, resources, Ris, time, oneMig, np);
		long npsection = (isTaskIncurNPSection(t, tasks.get(t.partition), resources) ? np : 0);

		localblocking += Long.max(MrsPlocalblocking, npsection);

		return localblocking;
	}

	private long FIFONPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
		ArrayList<Resource> LocalBlockingResources = FIFONPgetLocalBlockingResources(t, resources);
		ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

		for (int i = 0; i < LocalBlockingResources.size(); i++) {
			Resource res = LocalBlockingResources.get(i);
			long local_blocking = res.csl;
			t.implementation_overheads += UtilsNoOverheads.FIFONP_LOCK + UtilsNoOverheads.FIFONP_UNLOCK;

			if (res.isGlobal) {
				for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
					int partition = res.partitions.get(parition_index);
					int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri);
					int norT = t.resource_required_index.contains(res.id - 1)
							? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1)) : 0;
					int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], Ri);

					if (partition != t.partition && (norHP + norT) < norR) {
						local_blocking += res.csl;
						t.implementation_overheads += UtilsNoOverheads.FIFONP_LOCK + UtilsNoOverheads.FIFONP_UNLOCK;
					}
				}
			}
			local_blocking_each_resource.add(local_blocking);
		}

		if (local_blocking_each_resource.size() > 1)
			local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

		return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
	}

	private ArrayList<Resource> FIFONPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources) {
		ArrayList<Resource> localBlockingResources = new ArrayList<>();
		int partition = task.partition;

		for (int i = 0; i < resources.size(); i++) {
			Resource resource = resources.get(i);
			if (resource.protocol != 2 && resource.protocol != 3) {
				// local resources that have a higher ceiling
				if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
						&& resource.ceiling.get(resource.partitions.indexOf(partition)) >= task.priority) {
					for (int j = 0; j < resource.requested_tasks.size(); j++) {
						SporadicTask LP_task = resource.requested_tasks.get(j);
						if (LP_task.partition == partition && LP_task.priority < task.priority) {
							localBlockingResources.add(resource);
							break;
						}
					}
				}
				// global resources that are accessed from the partition
				if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
					for (int j = 0; j < resource.requested_tasks.size(); j++) {
						SporadicTask LP_task = resource.requested_tasks.get(j);
						if (LP_task.partition == partition && LP_task.priority < task.priority) {
							localBlockingResources.add(resource);
							break;
						}
					}
				}
			}

		}

		return localBlockingResources;
	}

	private long FIFOPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
		ArrayList<Resource> LocalBlockingResources = FIFOPgetLocalBlockingResources(t, resources);
		ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

		for (int i = 0; i < LocalBlockingResources.size(); i++) {
			Resource res = LocalBlockingResources.get(i);
			long local_blocking = res.csl;
			local_blocking_each_resource.add(local_blocking);
		}

		if (local_blocking_each_resource.size() > 1)
			local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

		if (local_blocking_each_resource.size() > 0)
			t.implementation_overheads += UtilsNoOverheads.FIFOP_LOCK + UtilsNoOverheads.FIFOP_UNLOCK;

		return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
	}

	private ArrayList<Resource> FIFOPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources) {
		ArrayList<Resource> localBlockingResources = new ArrayList<>();
		int partition = task.partition;

		for (int i = 0; i < resources.size(); i++) {
			Resource resource = resources.get(i);
			if (resource.protocol == 2) {
				// local resources that have a higher ceiling
				if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
						&& resource.ceiling.get(resource.partitions.indexOf(partition)) >= task.priority) {
					for (int j = 0; j < resource.requested_tasks.size(); j++) {
						SporadicTask LP_task = resource.requested_tasks.get(j);
						if (LP_task.partition == partition && LP_task.priority < task.priority) {
							localBlockingResources.add(resource);
							break;
						}
					}
				}
				// global resources that are accessed from the partition
				if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
					for (int j = 0; j < resource.requested_tasks.size(); j++) {
						SporadicTask LP_task = resource.requested_tasks.get(j);
						if (LP_task.partition == partition && LP_task.priority < task.priority) {
							localBlockingResources.add(resource);
							break;
						}
					}
				}
			}

		}

		return localBlockingResources;
	}

	private long MrsPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
			double oneMig, long np) {
		ArrayList<Resource> LocalBlockingResources = MrsPgetLocalBlockingResources(t, resources);
		ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

		for (int i = 0; i < LocalBlockingResources.size(); i++) {
			ArrayList<Integer> migration_targets = new ArrayList<>();

			Resource res = LocalBlockingResources.get(i);
			long local_blocking = res.csl;
			t.implementation_overheads += UtilsNoOverheads.MrsP_LOCK + UtilsNoOverheads.MrsP_UNLOCK;

			migration_targets.add(t.partition);

			if (res.isGlobal) {
				for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
					int partition = res.partitions.get(parition_index);
					int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], time);
					int norT = t.resource_required_index.contains(res.id - 1)
							? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1)) : 0;
					int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], time);

					if (partition != t.partition && (norHP + norT) < norR) {
						local_blocking += res.csl;
						t.implementation_overheads += UtilsNoOverheads.MrsP_LOCK + UtilsNoOverheads.MrsP_UNLOCK;
						migration_targets.add(partition);
					}
				}

				if (oneMig != 0) {
					double mc = migrationCostForArrival(oneMig, np, migration_targets, res, tasks, t);

					long mc_long = (long) Math.floor(mc);
					t.migration_overheads_plus += mc - mc_long;
					if (mc - mc_long < 0) {
						System.err.println("MrsP mig error");
						System.exit(-1);
					}
					local_blocking += mc_long;
				}
			}

			local_blocking_each_resource.add(local_blocking);
		}

		if (local_blocking_each_resource.size() > 1)
			local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

		return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;

	}

	private double migrationCostForArrival(double oneMig, long np, ArrayList<Integer> migration_targets, Resource resource,
			ArrayList<ArrayList<SporadicTask>> tasks, SporadicTask calT) {
		return migrationCost(oneMig, np, migration_targets, resource, tasks, calT);
	}

	private ArrayList<Resource> MrsPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources) {
		ArrayList<Resource> localBlockingResources = new ArrayList<>();
		int partition = task.partition;

		for (int i = 0; i < resources.size(); i++) {
			Resource resource = resources.get(i);

			if (resource.protocol == 3 && resource.partitions.contains(partition)
					&& resource.ceiling.get(resource.partitions.indexOf(partition)) >= task.priority) {
				for (int j = 0; j < resource.requested_tasks.size(); j++) {
					SporadicTask LP_task = resource.requested_tasks.get(j);
					if (LP_task.partition == partition && LP_task.priority < task.priority) {
						localBlockingResources.add(resource);
						break;
					}
				}
			}
		}

		return localBlockingResources;
	}

	private double migrationCostForSpin(double oneMig, long np, SporadicTask task, int request_number, Resource resource,
			ArrayList<ArrayList<SporadicTask>> tasks, long time, long[][] Ris, SporadicTask calT) {

		ArrayList<Integer> migration_targets = new ArrayList<>();

		// identify the migration targets
		migration_targets.add(task.partition);
		for (int i = 0; i < tasks.size(); i++) {
			if (i != task.partition) {
				int number_requests_left = 0;
				number_requests_left = getNoRRemote(resource, tasks.get(i), Ris[i], time)
						- getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time) - request_number + 1;

				if (number_requests_left > 0)
					migration_targets.add(i);
			}
		}

		return migrationCost(oneMig, np, migration_targets, resource, tasks, calT);
	}

	private double migrationCost(double oneMig, long np, ArrayList<Integer> migration_targets, Resource resource,
			ArrayList<ArrayList<SporadicTask>> tasks, SporadicTask calT) {
		double migrationCost = 0;
		ArrayList<Integer> migration_targets_with_P = new ArrayList<>();

		// identify the migration targets with preemptors
		for (int i = 0; i < migration_targets.size(); i++) {
			int partition = migration_targets.get(i);
			if (tasks.get(partition).get(0).priority > resource.ceiling.get(resource.partitions.indexOf(partition)))
				migration_targets_with_P.add(migration_targets.get(i));
		}

		// check
		if (!migration_targets.containsAll(migration_targets_with_P)) {
			System.out.println("migration targets error!");
			System.exit(0);
		}

		// now we compute the migration cost for each request
		for (int i = 0; i < migration_targets.size(); i++) {
			double migration_cost_for_one_access = 0;
			int partition = migration_targets.get(i); // the request issued
														// from.

			// calculating migration cost
			// 1. If there is no preemptors on the task's partition OR there is
			// no
			// other migration targets
			if (!migration_targets_with_P.contains(partition) || (migration_targets.size() == 1 && migration_targets.get(0) == partition))
				migration_cost_for_one_access = 0;

			// 2. If there is preemptors on the task's partition AND there are
			// no
			// preemptors on other migration targets
			else if (migration_targets_with_P.size() == 1 && migration_targets_with_P.get(0) == partition && migration_targets.size() > 1)
				migration_cost_for_one_access = 2 * oneMig;

			// 3. If there exist multiple migration targets with preemptors.
			// With NP
			// section applied.
			else {
				double migCostWithHP = migrationCostBusyWindow(migration_targets_with_P, oneMig, resource, tasks, calT);
				if (np > 0) {
					double migCostWithNP = (long) (1 + Math.ceil((double) resource.csl / (double) np)) * oneMig;

					migration_cost_for_one_access = Math.min(migCostWithHP, migCostWithNP);
				} else {
					migration_cost_for_one_access = migCostWithHP;
				}
			}

			migrationCost += migration_cost_for_one_access;
		}

		return migrationCost;
	}

	public double migrationCostBusyWindow(ArrayList<Integer> migration_targets_with_P, double oneMig, Resource resource,
			ArrayList<ArrayList<SporadicTask>> tasks, SporadicTask calT) {
		double migCost = 0;

		double newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, resource.csl + migCost, resource, tasks);

		while (migCost != newMigCost) {
			migCost = newMigCost;
			newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, resource.csl + migCost, resource, tasks);

			if (newMigCost > calT.deadline) {
				return newMigCost;
			}
		}

		return migCost;
	}

	public double migrationCostOneCal(ArrayList<Integer> migration_targets_with_P, double oneMig, double duration, Resource resource,
			ArrayList<ArrayList<SporadicTask>> tasks) {
		double migCost = 0;

		for (int i = 0; i < migration_targets_with_P.size(); i++) {
			int partition_with_p = migration_targets_with_P.get(i);
			int ceiling_index = resource.partitions.indexOf(partition_with_p);

			for (int j = 0; j < tasks.get(partition_with_p).size(); j++) {
				SporadicTask hpTask = tasks.get(partition_with_p).get(j);

				if (hpTask.priority > resource.ceiling.get(ceiling_index))
					migCost += Math.ceil((double) (duration) / (double) hpTask.period) * oneMig;
			}
		}

		return migCost + oneMig;
	}

	private boolean isTaskIncurNPSection(SporadicTask task, ArrayList<SporadicTask> tasksOnItsParititon, ArrayList<Resource> resources) {
		int partition = task.partition;
		int priority = task.priority;
		int minCeiling = 1000;

		for (int i = 0; i < resources.size(); i++) {
			Resource resource = resources.get(i);
			if (resource.partitions.contains(partition) && minCeiling > resource.ceiling.get(resource.partitions.indexOf(partition))) {
				minCeiling = resource.ceiling.get(resource.partitions.indexOf(partition));
			}
		}

		if (priority > minCeiling)
			return true;
		else
			return false;
	}

	/*
	 * gives the number of requests from remote partitions for a resource that
	 * is required by the given task.
	 */
	private int getNoSpinDelay(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris, long Ri) {
		int number_of_spin_dealy = 0;

		for (int i = 0; i < tasks.size(); i++) {
			if (i != task.partition) {
				/* For each remote partition */
				int number_of_request_by_Remote_P = 0;
				for (int j = 0; j < tasks.get(i).size(); j++) {
					if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
						SporadicTask remote_task = tasks.get(i).get(j);
						int indexR = getIndexRInTask(remote_task, resource);
						int number_of_release = (int) Math
								.ceil((double) (Ri + (use_deadline_insteadof_Ri ? remote_task.deadline : Ris[i][j])) / (double) remote_task.period);
						number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
					}
				}
				int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], Ri);
				int possible_spin_delay = number_of_request_by_Remote_P - getNoRFromHP < 0 ? 0 : number_of_request_by_Remote_P - getNoRFromHP;

				int NoRFromT = task.number_of_access_in_one_release.get(getIndexRInTask(task, resource));
				number_of_spin_dealy += Integer.min(possible_spin_delay, NoRFromT);
			}
		}
		return number_of_spin_dealy;
	}

	/*
	 * gives that number of requests from HP local tasks for a resource that is
	 * required by the given task.
	 */
	private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, long[] Ris, long Ri) {
		int number_of_request_by_HP = 0;
		int priority = task.priority;

		for (int i = 0; i < tasks.size(); i++) {
			if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
				SporadicTask hpTask = tasks.get(i);
				int indexR = getIndexRInTask(hpTask, resource);
				number_of_request_by_HP += Math.ceil((double) (Ri + (use_deadline_insteadof_Ri ? hpTask.deadline : Ris[i])) / (double) hpTask.period)
						* hpTask.number_of_access_in_one_release.get(indexR);
			}
		}
		return number_of_request_by_HP;
	}

	private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, long[] Ris, long Ri) {
		int number_of_request_by_Remote_P = 0;

		for (int i = 0; i < tasks.size(); i++) {
			if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
				SporadicTask remote_task = tasks.get(i);
				int indexR = getIndexRInTask(remote_task, resource);
				number_of_request_by_Remote_P += Math
						.ceil((double) (Ri + (use_deadline_insteadof_Ri ? remote_task.deadline : Ris[i])) / (double) remote_task.period)
						* remote_task.number_of_access_in_one_release.get(indexR);
			}
		}
		return number_of_request_by_Remote_P;
	}

	/*
	 * Return the index of a given resource in stored in a task.
	 */
	private int getIndexRInTask(SporadicTask task, Resource resource) {
		int indexR = -1;
		if (task.resource_required_index.contains(resource.id - 1)) {
			for (int j = 0; j < task.resource_required_index.size(); j++) {
				if (resource.id - 1 == task.resource_required_index.get(j)) {
					indexR = j;
					break;
				}
			}
		}
		return indexR;
	}

	public boolean isResponseTimeEqual(long[][] oldRi, long[][] newRi, ArrayList<ArrayList<SporadicTask>> tasks) {
		boolean is_equal = true;

		for (int i = 0; i < oldRi.length; i++) {
			for (int j = 0; j < oldRi[i].length; j++) {
				if (oldRi[i][j] != newRi[i][j]) {
					is_equal = false;
					System.out.println("not equal: " + oldRi[i][j] + " vs " + newRi[i][j]);
					System.out.println("T" + tasks.get(i).get(j).id + " old: S = " + tasks.get(i).get(j).spin + ", I = "
							+ tasks.get(i).get(j).interference + ", Local =" + tasks.get(i).get(j).local);
					problemtask = tasks.get(i).get(j);
				}
			}
		}
		System.out.println();
		return is_equal;
	}

}
