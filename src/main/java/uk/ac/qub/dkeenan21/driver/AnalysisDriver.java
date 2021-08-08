package uk.ac.qub.dkeenan21.driver;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;
import uk.ac.qub.dkeenan21.mining.RefactoringDetector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Orchestrates the analysis of the version history of a Git repository and writes the results to a CSV file
 */
public class AnalysisDriver {
	private final ChangeDetector changeDetector;
	private final RefactoringDetector refactoringDetector;
	private final int periodLength;
	private final int mode;
	private final String[] fileTypesToInclude;
	private final String[] filePathPatternsToExclude;
	private final String[] refactoringTypesToInclude;
	private final String resultsCsvFilePath;

	/**
	 * Constructor which accepts all parameters necessary to orchestrate analysis of the repository
	 *
	 * @param repositoryPath            a path to the repository
	 * @param periodLength              the number of commits defining one period
	 * @param mode                      the mode in which to execute
	 * @param fileTypesToInclude        the extensions of the only file types to consider
	 * @param filePathPatternsToExclude the patterns of paths of files to exclude from consideration
	 * @param refactoringTypesToInclude the only refactoring types (as named by RefactoringMiner) to consider
	 * @param resultsCsvFilePath        the path of the CSV file to which results will be written
	 */
	public AnalysisDriver(String repositoryPath, int periodLength, int mode, String[] fileTypesToInclude,
						  String[] filePathPatternsToExclude, String[] refactoringTypesToInclude, String resultsCsvFilePath) {
		changeDetector = new ChangeDetector(repositoryPath);
		refactoringDetector = new RefactoringDetector(repositoryPath);
		this.periodLength = periodLength;
		this.mode = mode;
		this.fileTypesToInclude = fileTypesToInclude.clone();
		this.filePathPatternsToExclude = filePathPatternsToExclude.clone();
		this.refactoringTypesToInclude = refactoringTypesToInclude.clone();
		this.resultsCsvFilePath = resultsCsvFilePath;
	}

	/**
	 * Orchestrates analysis of the version history of the repository in the appropriate mode
	 * Writes results data to the results CSV file
	 */
	public void analyse() {
		final List<String> commitIds = changeDetector.extractNonMergeCommitIds();
		final List<Pair<String, String>> periodBoundaries = divideIntoPeriods(commitIds);
		final List<Map<String, Integer>> periodChangeSummaries = summariseChangesForEachPeriod(periodBoundaries);

		final PrintWriter resultsWriter = generateWriterForResultsCsvFile();

		if (mode == 1) {
			// mode 1: entropy for each period
			final List<Double> entropyForEachPeriod = computeEntropyForEachPeriod(periodChangeSummaries);
			writeEntropyForEachPeriod(resultsWriter, entropyForEachPeriod);
		} else {
			final List<Map<String, Map<String, Integer>>> periodRefactoringSummaries = summariseRefactoringsForEachPeriod(
					periodBoundaries);
			final Set<String> filePaths = extractPathOfEachFileWhichExistedDuringAnyPeriod(periodChangeSummaries);

			final boolean markPeriodsInWhichFileWasRefactored = (mode == 3 || mode >= 5 && mode <= 7);
			final boolean markPeriodsInWhichFileWasNotChanged = (mode >= 4 && mode <= 7);

			final Map<String, List<Double>> fileEntropyForEachPeriodForEachFile = computeFileEntropyForEachPeriodForEachFile(
					periodChangeSummaries, periodRefactoringSummaries, markPeriodsInWhichFileWasRefactored,
					markPeriodsInWhichFileWasNotChanged, filePaths);

			if (mode >= 2 && mode <= 5) {
				// mode 2: file entropy for each period
				// mode 3: file entropy for each period and mark periods in which file was refactored
				// mode 4: file entropy for each period in which file was changed
				// mode 5: file entropy for each period in which file was changed and mark periods in which file was refactored
				writeFileEntropyForEachPeriodForEachFile(resultsWriter, fileEntropyForEachPeriodForEachFile);
			} else if (mode == 6) {
				// mode 6: percentage change in file entropy between periods in which the file was changed but not refactored
				for (Map.Entry<String, List<Double>> entry : fileEntropyForEachPeriodForEachFile.entrySet()) {
					final StringBuilder resultsLine = new StringBuilder(entry.getKey());
					boolean previousPeriodWasNonRefactoringPeriod = false;
					double previousPeriodValue = 0.0;
					final List<Double> fileEntropyForEachPeriod = entry.getValue();
					final List<Double> fileEntropyForEachPeriodInWhichFileWasChanged = new ArrayList<>();

					// remove all periods in which file was not changed so that we are always comparing pairs of 'change' periods
					for (Double fileEntropyForPeriod : fileEntropyForEachPeriod) {
						if (fileEntropyForPeriod != -2.0) {
							fileEntropyForEachPeriodInWhichFileWasChanged.add(fileEntropyForPeriod);
						}
					}

					for (Double fileEntropyForPeriod : fileEntropyForEachPeriodInWhichFileWasChanged) {
						if (!previousPeriodWasNonRefactoringPeriod || fileEntropyForPeriod == -1.0) {
							// previous period was, or this period is, a refactoring period, so must skip forward
							// update 'previous period' info before moving onto next period value
							previousPeriodWasNonRefactoringPeriod = (fileEntropyForPeriod != -1.0);
							previousPeriodValue = fileEntropyForPeriod;
							continue;
						}
						// previous period was, and this period is, a non-refactoring period
						// now calculate and record the percentage change between previous period and this period
						final double percentageChangeInEntropy = 100.0 * ((fileEntropyForPeriod - previousPeriodValue) /
								previousPeriodValue);
						resultsLine.append(", ").append(String.format("%.4f", percentageChangeInEntropy));

						// update 'previous period' info before moving onto next period value
						previousPeriodValue = fileEntropyForPeriod;
					}
					resultsWriter.println(resultsLine);
				}
			} else if (mode == 7) {
				// mode 7: percentage change in mean file entropy before and after refactoring periods
				for (Map.Entry<String, List<Double>> entry : fileEntropyForEachPeriodForEachFile.entrySet()) {
					final List<Double> fileEntropyForEachPeriod = entry.getValue();
					final List<Double> fileEntropyForEachPeriodInWhichFileWasChanged = new ArrayList<>();

					// remove all periods in which file was not changed so that we are always comparing pairs of 'change' periods
					for (Double fileEntropyForPeriod : fileEntropyForEachPeriod) {
						if (fileEntropyForPeriod != -2.0) {
							fileEntropyForEachPeriodInWhichFileWasChanged.add(fileEntropyForPeriod);
						}
					}

					// calculate the average file entropy over the periods in which the file was changed, up to the period
					// before the first period in which the file was refactored, then repeat this calculation starting from
					// after the refactoring period, continuously repeating until there are no periods remaining
					double runningEntropyTotal = 0.0;
					int runningPeriodCount = 0;
					final List<Double> periodAverages = new ArrayList<>();
					for (Double fileEntropyForPeriod : fileEntropyForEachPeriodInWhichFileWasChanged) {
						if (fileEntropyForPeriod == -1.0) {
							if (runningPeriodCount > 0) {
								periodAverages.add(runningEntropyTotal / runningPeriodCount);
								runningEntropyTotal = 0.0;
								runningPeriodCount = 0;
							}
						} else {
							runningEntropyTotal += fileEntropyForPeriod;
							runningPeriodCount++;
						}
					}
					if (runningPeriodCount > 0) {
						periodAverages.add(runningEntropyTotal / runningPeriodCount);
					}

					// now, each element in periodAverages represents the file's average entropy over all periods up to the next
					// period in which the file was refactored (or the end of the project history if there are no more such periods)

					// calculate the percentage change between each pair of average entropy values
					final StringBuilder resultsLine = new StringBuilder(entry.getKey());
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
					resultsWriter.println(resultsLine);
				}
			}
		}
		resultsWriter.close();
	}

	/**
	 * Divides a sequence of commits into periods defined by a start commit ID and end commit ID
	 *
	 * @param commitIds the sequence of commits (in chronological order)
	 * @return a list containing the start commit ID and end commit ID of each period
	 */
	private List<Pair<String, String>> divideIntoPeriods(List<String> commitIds) {
		final List<Pair<String, String>> periodBoundaries = new ArrayList<>();
		final int numberOfCommits = commitIds.size();
		for (int i = 0; i < numberOfCommits; i++) {
			final String startCommitId = commitIds.get(i);
			if (numberOfCommits - i >= periodLength) {
				i += periodLength - 1;
			} else {
				i = numberOfCommits - 1;
			}
			final String endCommitId = commitIds.get(i);
			periodBoundaries.add(new ImmutablePair<>(startCommitId, endCommitId));
		}
		return periodBoundaries;
	}

	/**
	 * Summarises the changes occurring in each period in a sequence of commits
	 * For each period, generates a map recording the number of line changes applied to each file changed during it
	 *
	 * @param boundariesForEachPeriod a list containing the start commit ID and end commit ID of each period
	 * @return a list containing a map summarising the changes occurring in each period
	 * map entries are of the form: [file path -> number of lines changed in file]
	 */
	private List<Map<String, Integer>> summariseChangesForEachPeriod(List<Pair<String, String>> boundariesForEachPeriod) {
		final List<Map<String, Integer>> periodChangeSummaries = new ArrayList<>();
		for (Pair<String, String> boundariesForPeriod : boundariesForEachPeriod) {
			final String startCommitId = boundariesForPeriod.getLeft();
			final String endCommitId = boundariesForPeriod.getRight();
			final Map<String, Integer> periodChangeSummary = changeDetector.summariseChanges(startCommitId, endCommitId,
					fileTypesToInclude, filePathPatternsToExclude);
			periodChangeSummaries.add(periodChangeSummary);
		}
		return periodChangeSummaries;
	}

	/**
	 * Summarises the refactorings occurring in each period in a sequence of commits
	 * For each period, generates a map recording the number of each refactoring type applied to each file refactored during it
	 *
	 * @param boundariesForEachPeriod a list containing the start commit ID and end commit ID of each period
	 * @return a list containing a map summarising the refactorings occurring in each period
	 * map entries are of the form: [file path -> [refactoring type -> number of times applied to file]]
	 */
	private List<Map<String, Map<String, Integer>>> summariseRefactoringsForEachPeriod(List<Pair<String, String>>
																							   boundariesForEachPeriod) {
		final List<Map<String, Map<String, Integer>>> periodRefactoringSummaries = new ArrayList<>();
		for (Pair<String, String> boundariesForPeriod : boundariesForEachPeriod) {
			final String startCommitId = boundariesForPeriod.getLeft();
			final String endCommitId = boundariesForPeriod.getRight();
			final Map<String, Map<String, Integer>> periodRefactoringSummary = refactoringDetector.summariseRefactorings(
					startCommitId, endCommitId, refactoringTypesToInclude);
			periodRefactoringSummaries.add(periodRefactoringSummary);
		}
		return periodRefactoringSummaries;
	}

	/**
	 * Computes the entropy for each period
	 *
	 * @param periodChangeSummaries a list containing a map summarising the changes occurring in each period
	 *                              map entries are of the form: [file path -> number of lines changed in file]
	 * @return a list containing the entropy for each period
	 */
	private List<Double> computeEntropyForEachPeriod(List<Map<String, Integer>> periodChangeSummaries) {
		final List<Double> entropyForEachPeriod = new ArrayList<>();
		final EntropyComputer entropyComputer = new EntropyComputer();
		for (Map<String, Integer> periodChangeSummary : periodChangeSummaries) {
			final double entropy = entropyComputer.computeEntropyOfChangeSet(periodChangeSummary);
			Logger.info("Entropy = " + String.format("%.4f", entropy));
			entropyForEachPeriod.add(entropy);
		}
		return entropyForEachPeriod;
	}

	/**
	 * Generates a writer for writing to the results CSV file
	 *
	 * @return the writer
	 */
	private PrintWriter generateWriterForResultsCsvFile() {
		try {
			final FileOutputStream fileOutputStream = new FileOutputStream(resultsCsvFilePath);
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
			return new PrintWriter(outputStreamWriter, true);
		} catch (Exception exception) {
			Logger.error("An error occurred while generating a writer for the results CSV file");
			System.exit(1);
			return null;
		}
	}

	/**
	 * Writes the entropy for each period to the results CSV file
	 *
	 * @param resultsWriter        a writer that will be used to write to the results CSV file
	 * @param entropyForEachPeriod a list containing the entropy for each period
	 */
	private void writeEntropyForEachPeriod(PrintWriter resultsWriter, List<Double> entropyForEachPeriod) {
		for (Double entropyForPeriod : entropyForEachPeriod) {
			resultsWriter.println(entropyForPeriod);
		}
	}

	/**
	 * Extracts the path of each file which existed during any period
	 * Each unique file path is recorded only once
	 *
	 * @param periodChangeSummaries a list containing a map summarising the changes occurring in each period
	 *                              map entries are of the form: [file path -> number of lines changed in file]
	 * @return a set containing the path of each file which existed during any period
	 */
	private Set<String> extractPathOfEachFileWhichExistedDuringAnyPeriod(List<Map<String, Integer>> periodChangeSummaries) {
		final Set<String> filePaths = new HashSet<>();
		for (Map<String, Integer> periodChangeSummary : periodChangeSummaries) {
			final Set<String> filePathsInPeriod = periodChangeSummary.keySet();
			filePaths.addAll(filePathsInPeriod);
		}
		return filePaths;
	}

	/**
	 * Computes the entropy of a file for each period
	 * Marks periods in which the file was refactored with the special value of -1.0 if the relevant flag is set
	 * Marks periods in which the file was not changed with the special value of -2.0 if the relevant flag is set
	 *
	 * @param periodChangeSummaries               a list containing a map summarising the changes occurring in each period
	 *                                            map entries are of the form: [file path -> number of lines changed in file]
	 * @param periodRefactoringSummaries          a list containing a map summarising the refactorings occurring in each period
	 *                                            map entries are of the form:
	 *                                            [file path -> [refactoring type -> number of times applied to file]]
	 * @param markPeriodsInWhichFileWasRefactored whether to mark periods in which the file was refactored
	 * @param markPeriodsInWhichFileWasNotChanged whether to mark periods in which the file was not changed
	 * @param filePath                            the path of the file
	 * @return a list containing the entropy of the file for each period
	 */
	private List<Double> computeFileEntropyForEachPeriod(List<Map<String, Integer>> periodChangeSummaries,
														 List<Map<String, Map<String, Integer>>> periodRefactoringSummaries,
														 boolean markPeriodsInWhichFileWasRefactored,
														 boolean markPeriodsInWhichFileWasNotChanged,
														 String filePath) {
		final List<Double> fileEntropyForEachPeriod = new ArrayList<>();
		final EntropyComputer entropyComputer = new EntropyComputer();
		// for each period
		for (int i = 0; i < periodChangeSummaries.size(); i++) {
			final Map<String, Integer> periodChangeSummary = periodChangeSummaries.get(i);
			final Map<String, Map<String, Integer>> periodRefactoringSummary = periodRefactoringSummaries.get(i);
			final Set<String> pathsOfFilesChangedDuringPeriod = periodChangeSummary.keySet();

			if (!pathsOfFilesChangedDuringPeriod.contains(filePath) && markPeriodsInWhichFileWasNotChanged) {
				// file was not changed during this period and relevant flag set, so record a special value of -2.0
				fileEntropyForEachPeriod.add(-2.0);
			} else {
				if (markPeriodsInWhichFileWasRefactored && periodRefactoringSummary.containsKey(filePath)) {
					// file was refactored during this period and relevant flag is set, so record a special value of -1.0
					fileEntropyForEachPeriod.add(-1.0);
				} else {
					// file was changed during this period, so record its entropy for this period
					final double fileEntropy = entropyComputer.computeEntropyOfFileInChangeSet(periodChangeSummary, filePath);
					fileEntropyForEachPeriod.add(fileEntropy);
				}
			}
		}
		return fileEntropyForEachPeriod;
	}

	/**
	 * Computes the entropy of each file for each period
	 * Marks periods in which the file was refactored with the special value of -1.0 if the relevant flag is set
	 * Marks periods in which the file was not changed with the special value of -2.0 if the relevant flag is set
	 *
	 * @param periodChangeSummaries               a list containing a map summarising the changes occurring in each period
	 *                                            map entries are of the form: [file path -> number of lines changed in file]
	 * @param periodRefactoringSummaries          a list containing a map summarising the refactorings occurring in each period
	 *                                            map entries are of the form:
	 *                                            [file path -> [refactoring type -> number of times applied to file]]
	 * @param markPeriodsInWhichFileWasRefactored whether to mark periods in which the file was refactored
	 * @param markPeriodsInWhichFileWasNotChanged whether to mark periods in which the file was not changed
	 * @param filePaths                           a set containing the path of each file which existed during any period
	 * @return a map containing entries of the form: [file path -> list containing the entropy of the file for each period]
	 */
	private Map<String, List<Double>> computeFileEntropyForEachPeriodForEachFile(List<Map<String, Integer>> periodChangeSummaries,
																				 List<Map<String, Map<String, Integer>>>
																						 periodRefactoringSummaries,
																				 boolean markPeriodsInWhichFileWasRefactored,
																				 boolean markPeriodsInWhichFileWasNotChanged,
																				 Set<String> filePaths) {
		final Map<String, List<Double>> fileEntropyForEachPeriodForEachFile = new HashMap<>();
		for (String filePath : filePaths) {
			final List<Double> fileEntropyForEachPeriod = computeFileEntropyForEachPeriod(periodChangeSummaries,
					periodRefactoringSummaries, markPeriodsInWhichFileWasRefactored, markPeriodsInWhichFileWasNotChanged,
					filePath);
			fileEntropyForEachPeriodForEachFile.put(filePath, fileEntropyForEachPeriod);
		}
		return fileEntropyForEachPeriodForEachFile;
	}

	/**
	 * Writes the entropy of each file for each period to the results CSV file
	 *
	 * @param resultsWriter                       a writer that will be used to write to the results CSV file
	 * @param fileEntropyForEachPeriodForEachFile a map containing entries of the form:
	 *                                            [file path -> list containing the entropy of the file for each period]
	 */
	private void writeFileEntropyForEachPeriodForEachFile(PrintWriter resultsWriter,
														  final Map<String, List<Double>> fileEntropyForEachPeriodForEachFile) {
		for (Map.Entry<String, List<Double>> entry : fileEntropyForEachPeriodForEachFile.entrySet()) {
			final StringBuilder resultsLine = new StringBuilder(entry.getKey());
			for (Double value : entry.getValue()) {
				if (value == -1.0) {
					// special value -1.0 means record as a period in which file was refactored
					resultsLine.append(", R");
				} else if (value != -2.0) {
					if (value == 0.0) {
						resultsLine.append(", "); // leave cell blank for readability
					} else {
						resultsLine.append(", ").append(String.format("%.4f", value));
					}
				}
				// special value -2.0 means file was not changed during period and should have the period ignored
			}
			resultsWriter.println(resultsLine);
		}
	}
}
