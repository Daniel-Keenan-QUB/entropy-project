package uk.ac.qub.dkeenan21.driver;

import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;
import uk.ac.qub.dkeenan21.mining.RefactoringDetector;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Analyses the version history of a repository
 * Records the entropy and number of each type of refactoring occurring in each change period
 * Writes the results to a CSV file
 */
public class AnalysisDriver {
	private final ChangeDetector changeDetector;
	private final RefactoringDetector refactoringDetector;

	/**
	 * Constructor which accepts a path to a repository
	 *
	 * @param repositoryPath a path to the repository
	 */
	public AnalysisDriver(String repositoryPath) {
		changeDetector = new ChangeDetector(repositoryPath);
		refactoringDetector = new RefactoringDetector(repositoryPath);
	}

	/**
	 * Computes entropy and detects refactorings for each change period in the version history of the repository
	 * The final (most recent) change period may have fewer commits than the defined change period size
	 * Results are written to CSV files 'results-high-level.csv' and 'results-low-level.csv'
	 *
	 * @param changePeriodSize  the number of commits defining one change period
	 */
	public void analyse(int changePeriodSize) {
		if (changePeriodSize < 1) {
			Logger.error("Change period size must be at least 1");
			System.exit(1);
		}

		final List<RevCommit> commits = changeDetector.extractNonMergeCommits();
		final int numberOfCommits = commits.size();
		final EntropyComputer entropyComputer = new EntropyComputer();
		final List<Map<String, Integer>> changePeriodSummaries = new ArrayList<>();
		final List<Map<String, Map<String, Integer>>> refactoringPeriodSummaries = new ArrayList<>();

		for (int i = 0; i < numberOfCommits; i++) {
			final String startCommitId = commits.get(i).getName();
			if (numberOfCommits - i >= changePeriodSize) {
				i += changePeriodSize - 1;
			} else {
				i = numberOfCommits - 1;
			}
			final String endCommitId = commits.get(i).getName();
			final Map<String, Integer> changesSummary = changeDetector.summariseChanges(startCommitId, endCommitId);
			changePeriodSummaries.add(changesSummary);
			final double entropy = entropyComputer.computeEntropyOfPeriod(changesSummary);
			final String entropyString = String.format("%.4f", entropy);
			Logger.info("Entropy = " + entropyString);
			final Map<String, Map<String, Integer>> refactoringsSummary = refactoringDetector.summariseRefactorings(
					startCommitId, endCommitId);
			refactoringPeriodSummaries.add(refactoringsSummary);
		}

		// start of low-level analysis

		final PrintWriter lowLevelResultsWriter = generatePrintWriter("results-low-level.csv");
		lowLevelResultsWriter.println(",");

		// extract the path of every file which existed in the system at some point
		final Set<String> filePaths = new HashSet<>();
		for (Map<String, Integer> changePeriodSummary : changePeriodSummaries) {
			final Set<String> filePathsInChangePeriod = changePeriodSummary.keySet();
			filePaths.addAll(filePathsInChangePeriod);
		}

		// for each file
		for (String filePath : filePaths) {
			final StringBuilder resultsLine = new StringBuilder(filePath);
			int i = 0;
			final List<Double> periodValues = new ArrayList<>();
			// for each period
			for (Map<String, Integer> changePeriodSummary : changePeriodSummaries) {
				final Set<String> filePathsInChangePeriod = changePeriodSummary.keySet();
				if (refactoringPeriodSummaries.get(i).containsKey(filePath)) {
					// file was refactored during this period, so record a special value of -1.0
					periodValues.add(-1.0);
				} else if (filePathsInChangePeriod.contains(filePath)) {
					// file was not refactored but was changed during this period, so record its entropy for this period
					final double fileEntropy = entropyComputer.computeEntropyOfFileWithinPeriod(changePeriodSummary, filePath);
					periodValues.add(fileEntropy);
				}
				i++;
			}

			// periodValues now contains an element for each period in which the file was changed (in time order)
			// — if the file was not refactored in a period, the value recorded is its entropy for that period
			// — if the file was refactored in a period, the value recorded is -1.0

			// calculate the average file entropy over the periods in which the file was changed, up to the period
			// before the first period in which the file was refactored, then repeat this calculation starting from
			// after the refactoring period, continuously repeating until there are no periods remaining
			double runningEntropyTotal = 0.0;
			int runningPeriodCount = 0;
			final List<Double> periodAverages = new ArrayList<>();
			for (Double value : periodValues) {
				if (value == -1.0) {
					if (runningPeriodCount > 0) {
						periodAverages.add(runningEntropyTotal / runningPeriodCount);
						runningEntropyTotal = 0.0;
						runningPeriodCount = 0;
					}
				} else {
					runningEntropyTotal += value;
					runningPeriodCount++;
				}
			}
			if (runningPeriodCount > 0) {
				periodAverages.add(runningEntropyTotal / runningPeriodCount);
			}

			// now, each element in periodAverages represents the file's average entropy over all periods up to the next
			// period in which the file was refactored (or the end of the project history if there are no more such periods)

			for (Double periodAverage : periodAverages) {
				resultsLine.append(", ").append(String.format("%.4f", periodAverage));
			}

			lowLevelResultsWriter.println(resultsLine);
		}
		lowLevelResultsWriter.close();
	}

	/**
	 * Creates and configures a print writer for a given file
	 *
	 * @param filePath a path to the file to be written to
	 * @return the generated print writer
	 */
	private PrintWriter generatePrintWriter(String filePath) {
		try {
			final FileOutputStream fileOutputStream = new FileOutputStream(filePath);
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
			return new PrintWriter(outputStreamWriter, true);
		} catch (Exception exception) {
			Logger.error("An error occurred while generating a print writer");
			System.exit(1);
			return null;
		}
	}
}
