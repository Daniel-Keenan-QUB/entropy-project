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

	// refactoring types recognised by the RefactoringMiner library — enumerated here for results-writing purposes
	private static final String[] refactoringTypes = {"Extract Method", "Rename Class", "Move Attribute",
			"Move And Rename Attribute", "Replace Attribute", "Rename Method", "Inline Method", "Move Method",
			"Move And Rename Method", "Pull Up Method", "Move Class", "Move And Rename Class",
			"Move Source Folder", "Pull Up Attribute", "Push Down Attribute", "Push Down Method",
			"Extract Interface", "Extract Superclass", "Extract Subclass", "Extract Class", "Merge Method",
			"Extract And Move Method", "Move And Inline Method", "Convert Anonymous Class to Type",
			"Introduce Polymorphism", "Change Package", "Extract Variable", "Extract Attribute",
			"Inline Variable", "Rename Variable", "Rename Parameter", "Rename Attribute", "Merge Variable",
			"Merge Parameter", "Merge Attribute", "Split Variable", "Split Parameter", "Split Attribute",
			"Replace Variable With Attribute", "Parameterize Variable", "Change Return Type",
			"Change Variable Type", "Change Parameter Type", "Change Attribute Type", "Add Method Annotation",
			"Remove Method Annotation", "Modify Method Annotation", "Add Attribute Annotation",
			"Remove Attribute Annotation", "Modify Attribute Annotation", "Add Class Annotation",
			"Remove Class Annotation", "Modify Class Annotation", "Add Parameter Annotation",
			"Remove Parameter Annotation", "Modify Parameter Annotation", "Add Parameter", "Remove Parameter",
			"Reorder Parameter", "Add Variable Annotation", "Remove Variable Annotation",
			"Modify Variable Annotation", "Add Thrown Exception Type", "Remove Thrown Exception Type",
			"Change Thrown Exception Type", "Change Method Access Modifier"
	};

	/**
	 * Constructor which accepts a path to a repository and a file type whitelist
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
	 * Results are written to CSV file 'results.csv'
	 *
	 * @param changePeriodSize  the number of commits defining one change period
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 */
	public void analyse(int changePeriodSize, Set<String> fileTypeWhitelist) {
		if (changePeriodSize < 1) {
			Logger.error("Change period size must be at least 1");
			System.exit(1);
		}
		final List<RevCommit> nonMergeCommits = changeDetector.extractNonMergeCommits();
		final int numberOfNonMergeCommits = nonMergeCommits.size();
		final EntropyComputer entropyComputer = new EntropyComputer();
		final String resultsFileName = "results.csv";
		try {
			final FileOutputStream fileOutputStream = new FileOutputStream(resultsFileName);
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
			final PrintWriter printWriter = new PrintWriter(outputStreamWriter, true);
			writeHeaderLineToResultsFile(printWriter);
			for (int i = 0; i < numberOfNonMergeCommits; i++) {
				final String startCommitId = nonMergeCommits.get(i).getName();
				if (numberOfNonMergeCommits - i >= changePeriodSize) {
					i += changePeriodSize - 1;
				} else {
					i = numberOfNonMergeCommits - 1;
				}
				final String endCommitId = nonMergeCommits.get(i).getName();
				final Map<String, Integer> changesSummary = changeDetector.summariseChanges(startCommitId, endCommitId,
						fileTypeWhitelist);
				final double entropy = entropyComputer.computeEntropy(changesSummary);
				final String entropyString = String.format("%.4f", entropy);
				Logger.info("Entropy = " + entropyString);
				final Map<String, Integer> refactoringsSummary = refactoringDetector.summariseRefactorings(startCommitId,
						endCommitId);
				writeResultsLineToResultsFile(printWriter, startCommitId, endCommitId, entropyString, refactoringsSummary);
			}
			printWriter.close();
		} catch (Exception exception) {
			Logger.error("An error occurred while writing to the results file");
			System.exit(1);
		}
	}

	/**
	 * Writes a header line to the results file
	 *
	 * @param printWriter the writer for the results file
	 */
	private void writeHeaderLineToResultsFile(PrintWriter printWriter) {
		final String headerLine = "Start Commit,End Commit,Entropy," + String.join(",", refactoringTypes);
		printWriter.println(headerLine);
	}

	/**
	 * Writes a results line (for a change period) to the results file
	 *
	 * @param printWriter         the writer for the results file
	 * @param startCommitId       the ID of the first commit in the change period
	 * @param endCommitId         the ID of the last commit in the change period
	 * @param entropyString       the (rounded) entropy value for the change period, represented as a string
	 * @param refactoringsSummary a map containing an entry for each refactoring type occurring in the change period
	 *                            entries are of the form: [key = refactoring type, value = number of occurrences]
	 */
	private void writeResultsLineToResultsFile(PrintWriter printWriter, String startCommitId, String endCommitId,
											   String entropyString, Map<String, Integer> refactoringsSummary) {
		final StringBuilder resultsLine = new StringBuilder(startCommitId + "," + endCommitId + "," + entropyString);
		for (String refactoringType : refactoringTypes) {
			final String columnValueToAdd = refactoringsSummary.get(refactoringType) != null ?
					String.valueOf(refactoringsSummary.get(refactoringType)) : "";
			resultsLine.append(",").append(columnValueToAdd);
		}
		printWriter.println(resultsLine);
	}
}
