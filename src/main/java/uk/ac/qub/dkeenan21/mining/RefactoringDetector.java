package uk.ac.qub.dkeenan21.mining;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.tinylog.Logger;

import java.util.*;

import static java.util.Arrays.asList;

/**
 * Detects refactorings in the version history of a Git repository (supports Java projects only)
 */
public class RefactoringDetector {
	private final Repository repository;

	// refactoring types recognised by the RefactoringMiner library, enumerated here for filtering purposes
	private static final String[] refactoringTypeWhiteList = {
		"Extract Method", "Rename Class", "Move Attribute", "Move And Rename Attribute",
		"Replace Attribute", "Rename Method", "Inline Method", "Move Method",
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
	 * Constructor which accepts a path to a repository
	 *
	 * @param repositoryPath a path to the repository
	 */
	public RefactoringDetector(String repositoryPath) {
		this.repository = new RepositoryHelper().convertToRepository(repositoryPath);
	}

	/**
	 * todo: update documentation here
	 * Generates a map representing a summary of the refactorings in a change period
	 *
	 * @param startCommitId the ID of the first commit in the change period
	 * @param endCommitId   the ID of the last commit in the change period
	 * @return a map containing an entry for each refactoring type occurring in the change period
	 * entries are of the form: [key = refactoring type, value = number of occurrences]
	 */
	public Map<String, Map<String, Integer>> summariseRefactorings(String startCommitId, String endCommitId) {
		// extract all refactorings applied during the period
		final Iterable<Refactoring> refactorings = extractRefactorings(startCommitId, endCommitId);

		// extract the file paths of the refactored files
		final Set<String> filePaths = new HashSet<>();
		for (Refactoring refactoring : refactorings) {
			final Set<String> filePathsInvolvedInRefactoring = extractInvolvedFilePaths(refactoring);
			filePaths.addAll(filePathsInvolvedInRefactoring);
		}

		// for each file
		final Map<String, Map<String, Integer>> overallSummary = new HashMap<>();
		for (String filePath: filePaths) {
			final Map<String, Integer> refactoringSummary = new HashMap<>();
			// for each refactoring
			for (Refactoring refactoring : refactorings) {
				// if the file is involved in this refactoring
				if (extractInvolvedFilePaths(refactoring).contains(filePath)) {
					// update the refactoring summary for this file
					if (refactoringSummary.containsKey(refactoring.getName())) {
						refactoringSummary.put(refactoring.getName(), refactoringSummary.get(refactoring.getName()) + 1);
					} else {
						refactoringSummary.put(refactoring.getName(), 1);
					}
				}
			}
			overallSummary.put(filePath, refactoringSummary);
		}

		return overallSummary;
	}

	/**
	 * Extracts the refactorings from a change period
	 *
	 * @param startCommitId the ID of the first commit in the change period
	 * @param endCommitId   the ID of the last commit in the change period
	 * @return the extracted refactorings
	 */
	private Iterable<Refactoring> extractRefactorings(String startCommitId, String endCommitId) {
		final GitHistoryRefactoringMiner gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl();
		final List<Refactoring> refactorings = new ArrayList<>();
		try {
			gitHistoryRefactoringMiner.detectAtCommit(repository, startCommitId, refactoringHandler(refactorings));
			gitHistoryRefactoringMiner.detectBetweenCommits(repository, startCommitId, endCommitId,
					refactoringHandler(refactorings));
		} catch (Exception exception) {
			Logger.error("An error occurred while extracting the refactorings from a change period");
			exception.printStackTrace();
			System.exit(1);
		}
		return refactorings;
	}

	/**
	 * Creates and configures a refactoring handler
	 *
	 * @param refactorings the list of refactorings to be handled by the refactoring handler
	 * @return the refactoring handler
	 */
	private RefactoringHandler refactoringHandler(List<Refactoring> refactorings) {
		return new RefactoringHandler() {
			@Override
			public void handle(String commitId, List<Refactoring> refactoringsInCommit) {
				Logger.debug("Listing refactorings in commit: " + commitId);
				for (Refactoring refactoring : refactoringsInCommit) {
					Logger.debug("– " + refactoring.toString());

					// filtering by refactoring type
					if (!asList(refactoringTypeWhiteList).contains(refactoring.getName())) {
						continue;
					}

					refactorings.add(refactoring);
				}
			}
		};
	}

	/**
	 * Extracts the file paths involved in the 'before' stage of a refactoring
	 *
	 * @param refactoring the refactoring
	 * @return the file paths
	 */
	private Set<String> extractInvolvedFilePaths(Refactoring refactoring) {
		final Set<ImmutablePair<String, String>> involvedElementsAndClasses = refactoring.getInvolvedClassesBeforeRefactoring();
		final Set<String> involvedFilePaths = new HashSet<>();
		for (ImmutablePair<String, String> involvedElementAndClass : involvedElementsAndClasses) {
			involvedFilePaths.add(involvedElementAndClass.getLeft());
		}
		return involvedFilePaths;
	}
}
