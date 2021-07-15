package uk.ac.qub.dkeenan21.driver;

import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.entropy.EntropyComputer;
import uk.ac.qub.dkeenan21.mining.ChangeDetector;
import uk.ac.qub.dkeenan21.mining.RefactoringDetector;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyses the version history of a repository for trends in entropy and refactorings
 */
public class AnalysisDriver {
	private final ChangeDetector changeDetector;
	private final RefactoringDetector refactoringDetector;
	private final EntropyComputer entropyComputer;
	private final Set<String> fileTypeWhitelist;

	/**
	 * Constructor which accepts a path to a repository and a file type whitelist
	 *
	 * @param repositoryPath    a path to the repository
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 */
	public AnalysisDriver(String repositoryPath, Set<String> fileTypeWhitelist) {
		changeDetector = new ChangeDetector(repositoryPath);
		refactoringDetector = new RefactoringDetector(repositoryPath);
		entropyComputer = new EntropyComputer();
		this.fileTypeWhitelist = fileTypeWhitelist;
	}

	/**
	 * Reports the entropy of each change period in the repository
	 * The final (most recent) change period may have fewer commits than the defined change period size
	 *
	 * @param changePeriodSize the number of commits in one change period
	 */
	public void analyse(int changePeriodSize) {
		final List<RevCommit> nonMergeCommits = changeDetector.extractNonMergeCommits();
		final int numberOfNonMergeCommits = nonMergeCommits.size();
		for (int i = 0; i < numberOfNonMergeCommits; i++) {
			final String startCommitId = nonMergeCommits.get(i).getName();
			if (numberOfNonMergeCommits - i >= changePeriodSize) {
				i += changePeriodSize - 1;
			} else {
				i = numberOfNonMergeCommits - 1;
			}
			final String endCommitId = nonMergeCommits.get(i).getName();
			final Map<String, Integer> changePeriodSummary = changeDetector.summariseChanges(startCommitId, endCommitId, fileTypeWhitelist);
			final double absoluteEntropy = entropyComputer.computeAbsoluteEntropy(changePeriodSummary);
			Logger.info("Absolute entropy = " + String.format("%.2f", absoluteEntropy));
			refactoringDetector.summariseRefactorings(startCommitId, endCommitId);
		}
	}
}
