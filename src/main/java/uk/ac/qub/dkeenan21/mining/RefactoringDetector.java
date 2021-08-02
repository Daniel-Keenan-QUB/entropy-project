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
 * Detects Java code refactorings in the version history of a Git repository
 */
public class RefactoringDetector {
	private final Repository repository;

	// refactoring types recognised by the RefactoringMiner library, enumerated here for filtering purposes
	private static final String[] refactoringTypeWhiteList = {
			// classes and interfaces
			"Extract Superclass", "Extract Subclass", "Extract Class", "Extract Interface",

			// methods
			"Extract Method", "Inline Method", "Merge Method", "Move Method",
			"Extract And Move Method", "Move And Inline Method", "Move And Rename Method",
			"Pull Up Method", "Push Down Method",

			// attributes
			"Extract Attribute", "Merge Attribute", "Split Attribute", "Move Attribute", "Replace Attribute",
			"Move And Rename Attribute", "Pull Up Attribute", "Push Down Attribute",

			// miscellaneous
			"Introduce Polymorphism",

			// IGNORE: folder/package/class
			// "Move Source Folder", "Change Package", "Move Class", "Move And Rename Class", "Rename Class"
			// "Convert Anonymous Class to Type",

			// IGNORE: attribute
			//  "Change Attribute Type",
			// "Rename Attribute",

			// IGNORE: method
			// "Rename Method", "Change Return Type"

			// IGNORE: parameter
			// "Add Parameter", "Remove Parameter", "Merge Parameter", "Split Parameter", "Change Parameter Type",
			// "Reorder Parameter", "Rename Parameter",

			// IGNORE: variable
			// "Extract Variable", "Inline Variable", "Merge Variable", "Split Variable", "Change Variable Type",
			// "Parameterize Variable", "Replace Variable With Attribute", "Rename Variable"

			// IGNORE: annotation
			// "Add Class Annotation", "Add Attribute Annotation", "Add Method Annotation", "Add Parameter Annotation",
			// "Add Variable Annotation", "Modify Class Annotation", "Modify Attribute Annotation",
			// "Modify Method Annotation", "Modify Parameter Annotation", "Modify Variable Annotation",
			// "Remove Class Annotation", "Remove Attribute Annotation",  "Remove Method Annotation",
			// "Remove Parameter Annotation", "Remove Variable Annotation"

			// IGNORE: thrown exception
			// "Add Thrown Exception Type", "Remove Thrown Exception Type",
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
	 * entries are of the form: [file path -> [refactoring type -> number of occurrences]]
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
