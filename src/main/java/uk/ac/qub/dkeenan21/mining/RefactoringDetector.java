package uk.ac.qub.dkeenan21.mining;

import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects refactorings in the version history of a Git repository (Java projects only)
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
	 * Counts the refactorings of each type detected between two commits (inclusive)
	 *
	 * @param startCommitId the ID of the less recent commit
	 * @param endCommitId   the ID of the more recent commit
	 * @return a map containing key-value pairs of the form: refactoring type, total number of occurrences
	 */
	public Map<String, Integer> summariseRefactorings(String startCommitId, String endCommitId) {
		Iterable<Refactoring> refactorings = extractRefactorings(startCommitId, endCommitId);
		Map<String, Integer> refactoringsMap = new HashMap<>();
		for (Refactoring refactoring : refactorings) {
			if (refactoringsMap.containsKey(refactoring.getName())) {
				refactoringsMap.put(refactoring.getName(), refactoringsMap.get(refactoring.getName()) + 1);
			} else {
				refactoringsMap.put(refactoring.getName(), 1);
			}
		}
		Logger.debug("Summary of refactorings over all commits in series");
		for (Map.Entry<String, Integer> entry : refactoringsMap.entrySet()) {
			Logger.debug(entry.getKey() + " (" + entry.getValue() + " occurrences)");
		}
		int numberOfRefactorings = refactoringsMap.values().stream().reduce(0, Integer::sum);
		Logger.info("Total number of refactorings: " + numberOfRefactorings);

		return refactoringsMap;
	}

	/**
	 * Extracts the refactorings between two commits (inclusive)
	 *
	 * @param startCommitId the ID of the less recent commit
	 * @param endCommitId   the ID of the more recent commit
	 */
	private Iterable<Refactoring> extractRefactorings(String startCommitId, String endCommitId) {
		GitHistoryRefactoringMiner gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl();
		List<Refactoring> refactorings = new ArrayList<>();
		try {
			gitHistoryRefactoringMiner.detectAtCommit(repository, startCommitId, refactoringHandler(refactorings));
			gitHistoryRefactoringMiner.detectBetweenCommits(repository, startCommitId, endCommitId, refactoringHandler(refactorings));
		} catch (Exception exception) {
			Logger.error("An error occurred while extracting refactorings");
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
				Logger.debug("Refactorings in commit: " + commitId);
				for (Refactoring refactoring : refactoringsInCommit) {
					Logger.debug(refactoring.toString());
					refactorings.add(refactoring);
				}
			}
		};
	}
}
