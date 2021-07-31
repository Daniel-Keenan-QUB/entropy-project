package uk.ac.qub.dkeenan21.mining;

import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.tinylog.Logger;

import java.util.*;

/**
 * Detects refactorings in the version history of a Git repository (supports Java projects only)
 */
public class RefactoringDetector {
	private final Repository repository;

	/**
	 * Constructor which accepts a path to a repository
	 *
	 * @param repositoryPath a path to the repository
	 */
	public RefactoringDetector(String repositoryPath) {
		this.repository = new RepositoryHelper().convertToRepository(repositoryPath);
	}

	/**
	 * Generates a map representing a summary of the refactorings in a change period
	 *
	 * @param startCommitId the ID of the first commit in the change period
	 * @param endCommitId   the ID of the last commit in the change period
	 * @return a map containing an entry for each refactoring type occurring in the change period
	 * entries are of the form: [key = refactoring type, value = number of occurrences]
	 */
	public Map<String, Integer> summariseRefactorings(String startCommitId, String endCommitId) {
		final Iterable<Refactoring> refactorings = extractRefactorings(startCommitId, endCommitId);
		final Map<String, Integer> changePeriodSummary = new HashMap<>();
		for (Refactoring refactoring : refactorings) {
			if (changePeriodSummary.containsKey(refactoring.getName())) {
				changePeriodSummary.put(refactoring.getName(), changePeriodSummary.get(refactoring.getName()) + 1);
			} else {
				changePeriodSummary.put(refactoring.getName(), 1);
			}
		}
		logChangePeriodSummary(changePeriodSummary);
		return changePeriodSummary;
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
	 * Logs summary information about the refactorings in a change period
	 *
	 * @param changePeriodSummary a map containing an entry for each refactoring type occurring in the change period
	 *                            entries are of the form: [key = refactoring type, value = number of occurrences]
	 */
	private void logChangePeriodSummary(Map<String, Integer> changePeriodSummary) {
		Logger.debug("Listing refactorings over all commits in change period");
		for (Map.Entry<String, Integer> entry : changePeriodSummary.entrySet()) {
			Logger.debug("— " + entry.getKey() + " (" + entry.getValue() + " occurrences)");
		}
		final int numberOfRefactorings = changePeriodSummary.values().stream().reduce(0, Integer::sum);
		final int numberOfFilesContainingRefactorings = changePeriodSummary.keySet().size();
		Logger.debug("Summary of refactorings in change period");
		Logger.debug("— Number of refactorings: " + numberOfRefactorings);
		Logger.debug("— Number of files containing refactorings: " + numberOfFilesContainingRefactorings);
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
					refactorings.add(refactoring);
				}
			}
		};
	}
}
