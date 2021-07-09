package uk.ac.qub.dkeenan21;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.tinylog.Logger;

import java.io.File;
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
	 * Constructor which accepts a repository path
	 *
	 * @param repositoryPath the repository path
	 */
	public RefactoringDetector(String repositoryPath) {
		repository = convertToRepository(repositoryPath);
	}

	/**
	 * Counts the refactorings of each type detected between two commits (inclusive)
	 *
	 * @param startCommitId the ID of the less recent commit
	 * @param endCommitId the ID of the more recent commit
	 * @return a map containing key-value pairs of the form: refactoring type, total number of occurrences
	 */
	public Map<String,Integer> countRefactoringsOfEachType(String startCommitId, String endCommitId) {
		Iterable<Refactoring> refactorings = extractRefactorings(startCommitId, endCommitId);
		Map<String,Integer> refactoringsMap = new HashMap<>();
		for (Refactoring refactoring : refactorings) {
			if (refactoringsMap.containsKey(refactoring.getName())) {
				refactoringsMap.put(refactoring.getName(), refactoringsMap.get(refactoring.getName()) + 1);
			} else {
				refactoringsMap.put(refactoring.getName(), 1);
			}
		}

		Logger.debug("Summary of refactorings over all commits in series");
		for (Map.Entry<String,Integer> entry : refactoringsMap.entrySet()) {
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
	 * @param endCommitId the ID of the more recent commit
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

	/**
	 * Converts a repository path to a corresponding repository representation
	 * Note: the Git metadata (.git) directory must be at the root of the repository
	 *
	 * @param repositoryPath the repository path
	 * @return the repository representation
	 */
	private Repository convertToRepository(String repositoryPath) {
		File repositoryDirectory = new File(repositoryPath);
		if (repositoryDirectory.exists()) {
			RepositoryBuilder repositoryBuilder = new RepositoryBuilder();
			File repositoryMetadataDirectory = new File(repositoryDirectory, ".git");
			try {
				return repositoryBuilder
						.setGitDir(repositoryMetadataDirectory)
						.readEnvironment()
						.setMustExist(true)
						.build();
			} catch (Exception exception) {
				Logger.error("An error occurred while building the repository representation");
				exception.printStackTrace();
				System.exit(1);
				return null;
			}
		} else {
			Logger.error("Repository not found at: " + repositoryPath);
			System.exit(1);
			return null;
		}
	}
}
