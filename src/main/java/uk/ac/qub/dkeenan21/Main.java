package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;

import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.log4j.LogManager.getRootLogger;

public class Main {
	private static final String REPOSITORY_PATH = "../entropy-tester";
	private static final String BRANCH_NAME = "master";
	private static final String START_COMMIT_ID = "1c0006c098d889af904bd4c5ac47b7e289b71400";
	private static final String END_COMMIT_ID = "72cc100347d10b2c1429876fa3785f5d274d49f1";
	private static final String[] FILE_TYPES = new String[] {"java", "txt"};
	private static final boolean NORMALISE = false;

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		EntropyComputer entropyComputer = new EntropyComputer(REPOSITORY_PATH, BRANCH_NAME);
		List<String> fileTypes = asList(FILE_TYPES);
		System.out.println("-----------------------------------------------------------");
		double entropy = entropyComputer.computeEntropy(START_COMMIT_ID, END_COMMIT_ID, fileTypes, NORMALISE);
		System.out.println("-----------------------------------------------------------");
		System.out.println("Entropy = " + entropy);
	}
}
