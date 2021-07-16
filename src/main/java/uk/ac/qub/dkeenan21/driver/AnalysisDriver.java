package uk.ac.qub.dkeenan21.driver;

import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;
import uk.ac.qub.dkeenan21.mining.RefactoringDetector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyses the version history of a repository for trends in entropy and refactorings
 */
public class AnalysisDriver {
	private final ChangeDetector changeDetector;
	private final RefactoringDetector refactoringDetector;

	/**
	 * Constructor which accepts a path to a repository and a file type whitelist
	 *
	 * @param repositoryPath    a path to the repository
	 */
	public AnalysisDriver(String repositoryPath) {
		changeDetector = new ChangeDetector(repositoryPath);
		refactoringDetector = new RefactoringDetector(repositoryPath);
	}

	/**
	 * Computes entropy and detects refactorings for each change period in the version history of the repository
	 * The final (most recent) change period may have fewer commits than the defined change period size
	 *
	 * @param changePeriodSize the number of commits defining one change period
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
			final double absoluteEntropy = entropyComputer.computeAbsoluteEntropy(changesSummary);
			Logger.info("Absolute entropy = " + String.format("%.2f", absoluteEntropy));
			final Map<String, Integer> refactoringsSummary = refactoringDetector.summariseRefactorings(startCommitId,
					endCommitId);
		}
	}
}
