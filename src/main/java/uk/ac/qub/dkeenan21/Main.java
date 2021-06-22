package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;

import static org.apache.log4j.LogManager.getRootLogger;

public class Main {
	private static final String REPOSITORY_PATH = "../refactoring-toy-example";
	private static final String BRANCH_NAME = "master";
	private static final String START_COMMIT_ID = "40950c317bd52ea5ce4cf0d19707fe426b66649c";
	private static final String END_COMMIT_ID = "70b71b7fd3c5973511904c468e464d4910597928";

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		EntropyComputer entropyComputer = new EntropyComputer(REPOSITORY_PATH, BRANCH_NAME);
		double entropy = entropyComputer.computeEntropy(START_COMMIT_ID, END_COMMIT_ID);
		System.out.println("-----------------------------------------------------------");
		System.out.println("Entropy = " + entropy);
	}
}
