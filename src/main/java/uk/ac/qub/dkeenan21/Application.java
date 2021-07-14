package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String repositoryPath = "../entropy-tester";
	private static final String startCommitId = "557d9eae1c86bddacd70b994f75829207eded0a4";
	private static final String endCommitId = "557d9eae1c86bddacd70b994f75829207eded0a4";
	private static final String[] fileTypes = new String[]{"java"};

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library

		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChangePeriod(startCommitId, endCommitId, fileTypesSet);
		final int numberOfFilesInSystem = changeDetector.countFilesInSystem(startCommitId, endCommitId, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();
		final double entropy = entropyComputer.computeAbsoluteEntropy(changeSetSummary);
//		final double entropy = entropyComputer.computeNormalisedEntropy(changeSetSummary, numberOfFilesInSystem);
	}
}
