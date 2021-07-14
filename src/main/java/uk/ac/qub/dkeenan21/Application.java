package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;
import uk.ac.qub.dkeenan21.mining.RefactoringDetector;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String repositoryPath = "../entropy-tester";
	private static final String startCommitId = "0efd9f02740e5d05cc7b6bea1fe9b2105839b6a0";
	private static final String endCommitId = "0efd9f02740e5d05cc7b6bea1fe9b2105839b6a0";
	private static final String[] fileTypes = new String[]{"txt"};

	public static void main(String[] args) throws Exception {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library

		final Set<String> fileTypesSet = new HashSet<>(Set.of(fileTypes));
		final ChangeDetector changeDetector = new ChangeDetector(repositoryPath);
		Date startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2021-06-24 04:00:00");
		Date endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2021-06-24 04:10:00");
		final Map<String, Integer> changeSetSummary = changeDetector.summariseChangePeriod(startTime, endTime, fileTypesSet);
		final int numberOfFilesInSystem = changeDetector.countFilesInSystem(startTime, endTime, fileTypesSet);
		final EntropyComputer entropyComputer = new EntropyComputer();

		final double absoluteEntropy = entropyComputer.computeAbsoluteEntropy(changeSetSummary);
		Logger.info("Absolute entropy = " + String.format("%.2f", absoluteEntropy));

//		final double normalisedEntropy = entropyComputer.computeNormalisedEntropy(changeSetSummary, numberOfFilesInSystem);
//		Logger.info("Normalised entropy = " + String.format("%.2f", normalisedEntropy));

//		RefactoringDetector refactoringDetector = new RefactoringDetector(repositoryPath);
//		Map<String,Integer> refactoringsMap = refactoringDetector.countRefactoringsOfEachType(startCommitId, endCommitId);

//		Date startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2021-06-24 04:00:00");
//		Date endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2021-06-24 04:03:59");
	}
}
