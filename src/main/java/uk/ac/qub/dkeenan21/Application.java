package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String REPOSITORY_PATH = "../refactoring-toy-example";
	private static final String BRANCH_NAME = "master";
	private static final String START_COMMIT_ID = "a5a7f852e45c7cadc8d1524bd4d14a1e39785aa5";
	private static final String END_COMMIT_ID = "d4bce13a443cf12da40a77c16c1e591f4f985b47";
	private static final String START_TIME = "2021-06-24 04:00:00";
	private static final String END_TIME = "2021-06-24 04:03:59";
	private static final String[] FILE_TYPES = new String[] {"java", "txt"};
	private static final boolean NORMALISE = false;

	public static void main(String[] args) throws Exception {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startTime = format.parse(START_TIME);
		Date endTime = format.parse(END_TIME);
		List<String> fileTypes = asList(FILE_TYPES);
		EntropyComputer entropyComputer = new EntropyComputer(REPOSITORY_PATH, BRANCH_NAME);
		RefactoringDetector refactoringDetector = new RefactoringDetector(REPOSITORY_PATH);

//		double entropy = entropyComputer.computeEntropy(START_COMMIT_ID, END_COMMIT_ID, fileTypes, NORMALISE);
//		double entropy = entropyComputer.computeEntropy(startTime, endTime, fileTypes, NORMALISE);

		Map<String,Integer> refactoringsMap = refactoringDetector.countRefactoringsOfEachType(START_COMMIT_ID, END_COMMIT_ID);
	}
}
