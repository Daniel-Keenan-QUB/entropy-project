package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String repositoryPath = "../entropy-tester";
	private static final String startCommitId = "474af99c4dd47c64e6387214dc0b5f34894599fd";
	private static final String endCommitId = "72cc100347d10b2c1429876fa3785f5d274d49f1";
	private static final String[] fileTypes = new String[]{"java"};

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library

		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChangePeriod(startCommitId, endCommitId, fileTypesSet);
		final int numberOfFilesInSystem = changeDetector.countFilesInSystem(startCommitId, endCommitId, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();

//		final double absoluteEntropy = entropyComputer.computeAbsoluteEntropy(changeSetSummary);
//		Logger.info("Absolute entropy = " + String.format("%.2f", absoluteEntropy));

		final double normalisedEntropy = entropyComputer.computeNormalisedEntropy(changeSetSummary, numberOfFilesInSystem);
		Logger.info("Normalised entropy = " + String.format("%.2f", normalisedEntropy));
	}
}
