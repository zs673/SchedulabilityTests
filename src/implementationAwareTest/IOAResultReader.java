package implementationAwareTest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class IOAResultReader {

	public static void main(String[] args) {

		schedreader();
	}

	public static void schedreader() {
		String result = "Work Load \n";
		for (int bigSet = 1; bigSet < 6; bigSet++) {

			result += "rsf: " + (0.2 + 0.1 * (bigSet - 1)) + "\n";

			for (int smallSet = 1; smallSet < 10; smallSet++) {
				String filepath = "result/" + "ioa 1" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n CS Length \n";

		for (int bigSet = 1; bigSet < 6; bigSet++) {
			result += "tasks per core: " + (3 + bigSet - 1) + "\n";

			for (int smallSet = 1; smallSet < 6; smallSet++) {
				String filepath = "result/" + "ioa 2" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n Resource Access \n";

		for (int bigSet = 1; bigSet < 4; bigSet++) {
			result += "tasks per core: " + (3 + (bigSet - 1) * 2) + "\n";

			for (int smallSet = 1; smallSet < 11; smallSet++) {
				String filepath = "result/" + "ioa 3" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}

				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}

		result += "\n \n Parallel \n";

		for (int bigSet = 1; bigSet < 4; bigSet++) {
			result += "rsf: " + (0.2 + 0.1 * (bigSet - 1)) + "\n";

			for (int smallSet = 1; smallSet <= 11; smallSet++) {
				String filepath = "result/" + "ioa 4" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}

				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		
		System.out.println(result);

		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(new File("result/all.txt"), false));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		writer.println(result);
		writer.close();
	}
}