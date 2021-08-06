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
 * Orchestrates the analysis of the version history of a Git repository
 * Records the entropy and number of each type of refactoring occurring in each change period
 * Writes the results to a CSV file
 */
public class AnalysisDriver {
	private final ChangeDetector changeDetector;
	private final RefactoringDetector refactoringDetector;

	private static final String[] fileTypesToInclude = new String[]{".java"};

	private static final String[] filePathPatternsToExclude = new String[]{
			"test/", "tests/", "tester/", "testers/", "androidTest/",
			"Test.java", "Tests.java", "Tester.java", "Testers.java"
	};

	private static final String[] refactoringTypesToInclude = {
			"Extract Superclass", "Extract Subclass", "Extract Class", "Extract Interface", "Extract Method",
			"Inline Method", "Merge Method", "Move Method", "Extract And Move Method", "Move And Inline Method",
			"Move And Rename Method", "Pull Up Method", "Push Down Method", "Extract Attribute", "Merge Attribute",
			"Split Attribute", "Move Attribute", "Replace Attribute", "Move And Rename Attribute", "Pull Up Attribute",
			"Push Down Attribute", "Introduce Polymorphism",
	};

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
	 * @param changePeriodSize the number of commits defining one change period
	 */
	public void analyse(int changePeriodSize, int mode) {
		if (changePeriodSize < 1) {
			Logger.error("Change period size must be at least 1");
			System.exit(1);
		}

		final List<RevCommit> commits = changeDetector.extractNonMergeCommits();
		final int numberOfCommits = commits.size();
		final EntropyComputer entropyComputer = new EntropyComputer();
		final List<Map<String, Integer>> periodSummaries = new ArrayList<>();
		final List<Map<String, Map<String, Integer>>> refactoringPeriodSummaries = new ArrayList<>();

		for (int i = 0; i < numberOfCommits; i++) {
			final String startCommitId = commits.get(i).getName();
			if (numberOfCommits - i >= changePeriodSize) {
				i += changePeriodSize - 1;
			} else {
				i = numberOfCommits - 1;
			}
			final String endCommitId = commits.get(i).getName();
			final Map<String, Integer> changesSummary = changeDetector.summariseChanges(startCommitId, endCommitId,
					fileTypesToInclude, filePathPatternsToExclude);
			periodSummaries.add(changesSummary);
			final double entropy = entropyComputer.computeEntropyOfChangeSet(changesSummary);
			final String entropyString = String.format("%.4f", entropy);
			Logger.info("Entropy = " + entropyString);
			final Map<String, Map<String, Integer>> refactoringsSummary = refactoringDetector.summariseRefactorings(
					startCommitId, endCommitId, refactoringTypesToInclude);
			refactoringPeriodSummaries.add(refactoringsSummary);
		}

		// ------------- start of low-level analysis ------------- //

		final String fileLevelResultsFilePath = "results.csv";
//		final String typeOfValuesToRecord = "entropy of each period";
		final String typeOfValuesToRecord = "percentage change in entropy between adjacent periods";
		final boolean includeOnlyPeriodsInWhichFileWasChanged = true;
		final boolean markPeriodsInWhichFileWasRefactored = true;

		final PrintWriter lowLevelResultsWriter = generatePrintWriter(fileLevelResultsFilePath);

		// extract the path of every file which existed in the system at some point
		final Set<String> filePaths = new HashSet<>();
		for (Map<String, Integer> changePeriodSummary : periodSummaries) {
			final Set<String> filePathsInChangePeriod = changePeriodSummary.keySet();
			filePaths.addAll(filePathsInChangePeriod);
		}

		// for each file
		for (String filePath : filePaths) {
			int i = 0;
			final List<Double> periodValues = new ArrayList<>();
			// for each period
			for (Map<String, Integer> periodSummary : periodSummaries) {
				final Set<String> pathsOfFilesChangedDuringPeriod = periodSummary.keySet();
				if (markPeriodsInWhichFileWasRefactored && refactoringPeriodSummaries.get(i).containsKey(filePath)) {
					// file was refactored during this period, so record a special value of -1.0
					periodValues.add(-1.0);
				} else if (pathsOfFilesChangedDuringPeriod.contains(filePath)) {
					// file was changed during this period, so record its entropy for this period
					final double fileEntropy = entropyComputer.computeEntropyOfFileInChangeSet(periodSummary, filePath);
					periodValues.add(fileEntropy);
				} else if (!includeOnlyPeriodsInWhichFileWasChanged) {
					// file was not changed during this period, so record a special value of -2.0
					periodValues.add(-2.0);
				}
				i++;
			}

			// periodValues now contains an element for each period in which the file was changed (in time order)
			// — if the file was not refactored in a period, the value recorded is its entropy for that period
			// — if the file was refactored in a period, the value recorded is -1.0

			final StringBuilder resultsLine = new StringBuilder(filePath);

			if (typeOfValuesToRecord.equals("entropy of each period")) {
				for (Double value : periodValues) {
					if (value == -2.0) {
						resultsLine.append(", ");
					} else if (value == -1.0) {
						resultsLine.append(", R");
					} else {
						resultsLine.append(", ").append(String.format("%.4f", value));
					}
				}
				continue; // finished 'entropy of each period' processing for this file
			}

			// -------- if reached this point, we are calculating the percentage change between periods --------

			// includeOnlyPeriodsInWhichFileWasChanged MUST be true
			final boolean calculatePercentageChangeBetweenAdjacentNonRefactoringPeriods = true;
			final boolean calculateAveragePercentageChangeBeforeAndAfterRefactoringPeriods = false;

			if (calculatePercentageChangeBetweenAdjacentNonRefactoringPeriods) {
				boolean previousPeriodWasNonRefactoringPeriod = false;
				double previousPeriodValue = 0.0;
				for (Double value : periodValues) {
					if (!previousPeriodWasNonRefactoringPeriod || value == -1.0) {
						// previous period was, or this period is, a refactoring period, so must skip forward
						// update 'previous period' info before moving onto next period value
						previousPeriodWasNonRefactoringPeriod = (value != -1.0);
						previousPeriodValue = value;
						continue;
					}
					// previous period was, and this period is, a non-refactoring period
					// now calculate and record the percentage change between previous period and this period
					final double percentageChangeInEntropy = 100.0 * ((value - previousPeriodValue) / previousPeriodValue);
					resultsLine.append(", ").append(String.format("%.4f", percentageChangeInEntropy));

					// update 'previous period' info before moving onto next period value
					previousPeriodWasNonRefactoringPeriod = (value != -1.0);
					previousPeriodValue = value;
				}
			} else if (calculateAveragePercentageChangeBeforeAndAfterRefactoringPeriods) {
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

				// calculate the percentage change between each pair of multi-period average entropy values
				if (periodAverages.size() > 1) {
					final Iterator<Double> iterator = periodAverages.listIterator();
					double before = iterator.next();
					double after;
					while (iterator.hasNext()) {
						after = iterator.next();
						final double relativeChangeInEntropy = 100.0 * ((after - before) / before);
						resultsLine.append(", ").append(String.format("%.4f", relativeChangeInEntropy));
						before = after;
					}
				}
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
