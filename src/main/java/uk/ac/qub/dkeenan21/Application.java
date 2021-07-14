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
	private static final String repositoryPath = "../csc4006-project";
	private static final String startCommitId = "6406270b17fedb4bc51142ece009d62d1ebd14d7";
	private static final String endCommitId = "154487df3ea588060c9d8361bfafff29371e3e3d";
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
