package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;

import static org.apache.log4j.LogManager.getRootLogger;

public class Main {
	private static final String REPOSITORY_PATH = "../entropy-tester";
	private static final String BRANCH_NAME = "master";
	private static final String START_COMMIT_ID = "7a0f50fb041062a32dcc03798d9e8a72415980b0";
	private static final String END_COMMIT_ID = "1c0006c098d889af904bd4c5ac47b7e289b71400";
	private static final boolean NORMALISE = false;

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		EntropyComputer entropyComputer = new EntropyComputer(REPOSITORY_PATH, BRANCH_NAME);
		System.out.println("-----------------------------------------------------------");
		double entropy = entropyComputer.computeEntropy(START_COMMIT_ID, END_COMMIT_ID, NORMALISE);
		System.out.println("-----------------------------------------------------------");
		System.out.println("Entropy = " + entropy);
	}
}
