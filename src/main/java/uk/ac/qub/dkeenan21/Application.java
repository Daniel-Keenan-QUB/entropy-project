package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;

import java.util.Map;

import static java.util.Arrays.asList;
import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String repositoryPath = "../entropy-tester";
	private static final String startCommitId = "839008fb17bf24722ac459d608b5e105e307d4e1";
	private static final String endCommitId = "839008fb17bf24722ac459d608b5e105e307d4e1";
	private static final String[] fileTypes = new String[] {"java", "txt"};

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library

		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String,Integer> changeSetSummary = changeDetector.summariseChangeSet(startCommitId, endCommitId, asList(fileTypes));
		final int numberOfLinesInSystem = changeDetector.countLinesInSystem(endCommitId, asList(fileTypes));
		final EntropyComputer entropyComputer = new EntropyComputer();
		final double entropy = entropyComputer.computeAbsoluteEntropy(changeSetSummary);
//		final double entropy = entropyComputer.computeNormalisedEntropy(changeSetSummary, numberOfLinesInSystem);
	}
}
