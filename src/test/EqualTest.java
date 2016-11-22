package test;

import java.util.ArrayList;

import analysis.FIFONPLinearJava;
import analysis.FIFONonPreemptiveLinearC;
import entity.Resource;
import entity.SporadicTask;
import generatorTools.SystemGenerator;

public class EqualTest {

	public static int TOTAL_NUMBER_OF_SYSTEMS = 9999999;
	public static int TOTAL_PARTITIONS = 5;
	public static int MIN_PERIOD = 1;
	public static int MAX_PERIOD = 1000;
	public static int NUMBER_OF_MAX_TASKS_ON_EACH_PARTITION = 5;
	public static int NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE = 5;
	public static double RESOURCE_SHARING_FACTOR = .5;

	public static void main(String[] args) {

		FIFONonPreemptiveLinearC new_mrsp = new FIFONonPreemptiveLinearC();
		FIFONPLinearJava fnp = new FIFONPLinearJava();
		long[][] r1,r2;

		SystemGenerator generator = new SystemGenerator(MIN_PERIOD, MAX_PERIOD, 0.1 * (double) NUMBER_OF_MAX_TASKS_ON_EACH_PARTITION,
				TOTAL_PARTITIONS, NUMBER_OF_MAX_TASKS_ON_EACH_PARTITION, true, Analysiser.CS_LENGTH_RANGE.MEDIUM_CS_LEN,
				Analysiser.RESOURCES_RANGE.PARTITIONS, RESOURCE_SHARING_FACTOR, NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE);

		for (int i = 0; i < TOTAL_NUMBER_OF_SYSTEMS; i++) {
			ArrayList<ArrayList<SporadicTask>> tasks = generator.generateTasks();
			ArrayList<Resource> resources = generator.generateResources();
			generator.generateResourceUsage(tasks, resources);

			r1 = new_mrsp.NewMrsPRTATest(tasks, resources, false,false);
			System.out.println(i);
			r2 = fnp.NewMrsPRTATest(tasks, resources,false);
			System.out.println(i+"*");
			boolean isEqual = isEqual(r1, r2);
			
			if(!isEqual){
				System.out.println("not equal");
				generator.testifyGeneratedTasksetAndResource(tasks, resources);
				r1 = new_mrsp.NewMrsPRTATest(tasks, resources, false,true);
				r2 = fnp.NewMrsPRTATest(tasks, resources,true);
				System.exit(0);
			}
			
			
		}

	}
	
	public static boolean isEqual(long[][] r1, long[][] r2){
		for(int i=0;i<r1.length;i++){
			for(int j=0;j<r1[i].length;j++){
				if(r1[i][j] != r2[i][j]){
					System.out.println("not equal at:  i="  + i + "  j="+j + "   r1: " + r1[i][j] + "   r2:" + r2[i][j]);
					
					return false;
				}
			}
		}
		return true;
	}

}