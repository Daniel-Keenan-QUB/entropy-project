package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.driver.AnalysisDriver;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String repositoryPath = "../entropy-tester";
	private static final String startCommitId = "839008fb17bf24722ac459d608b5e105e307d4e1";
	private static final String endCommitId = "0efd9f02740e5d05cc7b6bea1fe9b2105839b6a0";
	private static final String startTimeString = "2021-06-24 04:37:00";
	private static final String endTimeString = "2021-06-24 04:38:00";
	private static final String[] fileTypes = new String[]{"java", "txt"};
	private static Date startTime;
	private static Date endTime;

	public static void main(String[] args) throws Exception {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTimeString);
		endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTimeString);

		new AnalysisDriver(repositoryPath, Set.of(fileTypes)).analyse(1);

//		computeAbsoluteEntropyUsingCommits();
//		computeNormalisedEntropyUsingCommits();
//		computeAbsoluteEntropyUsingTimes();
//		computeNormalisedEntropyUsingTimes();
	}

	private static void computeAbsoluteEntropyUsingCommits() {
		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChanges(startCommitId, endCommitId, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();
		final double absoluteEntropy = entropyComputer.computeAbsoluteEntropy(changeSetSummary);
		Logger.info("Absolute entropy = " + String.format("%.2f", absoluteEntropy));
	}

	private static void computeAbsoluteEntropyUsingTimes() {
		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChanges(startTime, endTime, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();
		final double absoluteEntropy = entropyComputer.computeAbsoluteEntropy(changeSetSummary);
		Logger.info("Absolute entropy = " + String.format("%.2f", absoluteEntropy));
	}

	private static void computeNormalisedEntropyUsingCommits() {
		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChanges(startCommitId, endCommitId, fileTypesSet);
		final int numberOfFilesInSystem = changeDetector.countFilesInSystem(startCommitId, endCommitId, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();
		final double normalisedEntropy = entropyComputer.computeNormalisedEntropy(changeSetSummary, numberOfFilesInSystem);
		Logger.info("Normalised entropy = " + String.format("%.2f", normalisedEntropy));
	}

	private static void computeNormalisedEntropyUsingTimes() {
		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChanges(startTime, endTime, fileTypesSet);
		final int numberOfFilesInSystem = changeDetector.countFilesInSystem(startTime, endTime, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();
		final double normalisedEntropy = entropyComputer.computeNormalisedEntropy(changeSetSummary, numberOfFilesInSystem);
		Logger.info("Normalised entropy = " + String.format("%.2f", normalisedEntropy));
	}
}
