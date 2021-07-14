package uk.ac.qub.dkeenan21.mining;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.tinylog.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Detects changes in the version history of a Git repository
 */
public class ChangeDetector {
	private final Repository repository;

	/**
	 * Constructor which accepts a path to a repository
	 *
	 * @param repositoryPath a path to the repository
	 */
	public ChangeDetector(String repositoryPath) {
		this.repository = new RepositoryHelper().convertToRepository(repositoryPath);
	}

	/**
	 * Generates a map representing a summary of a change period
	 *
	 * @param startCommitId     the ID of the first commit in the change period
	 * @param endCommitId       the ID of the last commit in the change period
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 * @return a map containing an entry for each changed file in the change period
	 * entries are of the form [key = path, value = number of changed lines]
	 */
	public Map<String, Integer> summariseChangePeriod(String startCommitId, String endCommitId,
													  Set<String> fileTypeWhitelist) {
		try {
			final Iterable<RevCommit> commits = extractNonMergeCommits(startCommitId, endCommitId);
			final Map<String, Integer> changePeriodSummary = new TreeMap<>();
			for (RevCommit commit : commits) {
				Logger.debug("Listing changes in commit " + commit.getName());
				final Iterable<DiffEntry> fileChanges = extractFileChanges(commit, fileTypeWhitelist);
				for (DiffEntry fileChange : fileChanges) {
					// a value of '/dev/null' indicates file addition/deletion for an old/new path respectively
					final String changedFilePath = fileChange.getOldPath().equals("/dev/null") ? fileChange.getNewPath()
							: fileChange.getOldPath();
					final int numberOfChangedLinesInChangedFile = countChangedLines(fileChange);
					Logger.debug("– " + changedFilePath + " (" + numberOfChangedLinesInChangedFile + " lines)");
					if (changePeriodSummary.containsKey(changedFilePath)) {
						changePeriodSummary.put(changedFilePath, changePeriodSummary.get(changedFilePath)
								+ numberOfChangedLinesInChangedFile);
					} else {
						changePeriodSummary.put(changedFilePath, numberOfChangedLinesInChangedFile);
					}
				}
			}
			return changePeriodSummary;
		} catch (Exception exception) {
			Logger.error("An error occurred while summarising the change period");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Extracts the non-merge commits from a commit sequence
	 *
	 * @param startCommitId the ID of the first commit in the commit sequence
	 * @param endCommitId   the ID of the last commit in the commit sequence
	 * @return the extracted non-merge commits
	 */
	private Iterable<RevCommit> extractNonMergeCommits(String startCommitId, String endCommitId) {
		try {
			final RevCommit startCommit = repository.parseCommit(ObjectId.fromString(startCommitId));
			final RevCommit endCommit = repository.parseCommit(ObjectId.fromString(endCommitId));
			validateCommitOrder(startCommit, endCommit);
			final Iterable<RevCommit> commits = new Git(repository).log().addRange(startCommit, endCommit).call();
			final List<RevCommit> nonMergeCommits = StreamSupport.stream(commits.spliterator(), false)
					.filter(commit -> commit.getParentCount() < 2)
					.collect(Collectors.toList());
			// must add startCommit separately, as it is not included in the 'since..until' range of log command
			if (startCommit.getParentCount() < 2) {
				nonMergeCommits.add(startCommit);
			}
			Collections.reverse(nonMergeCommits);
			return nonMergeCommits;
		} catch (Exception exception) {
			Logger.error("An error occurred while extracting non-merge commits from a commit sequence");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Extracts the file changes from a commit
	 *
	 * @param commit            the commit
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 * @return the file changes
	 */
	private Iterable<DiffEntry> extractFileChanges(RevCommit commit, Set<String> fileTypeWhitelist) {
		try (final DiffFormatter diffFormatter = generateDiffFormatter()) {
			// extract file changes by comparing the commit tree with that of its parent
			final ObjectReader objectReader = repository.newObjectReader();
			final AbstractTreeIterator commitTreeIterator = new CanonicalTreeParser(null, objectReader, commit.getTree());
			final AbstractTreeIterator parentCommitTreeIterator;
			if (commit.getParentCount() > 0) {
				final RevWalk revWalk = new RevWalk(repository);
				final RevCommit parentCommit = revWalk.parseCommit(commit.getParent(0).getId());
				parentCommitTreeIterator = new CanonicalTreeParser(null, objectReader, parentCommit.getTree());
			} else {
				// this is the initial commit (no parent), so must compare with empty tree
				parentCommitTreeIterator = new EmptyTreeIterator();
			}
			diffFormatter.setPathFilter(generateFileTypeWhitelistTreeFilter(fileTypeWhitelist));
			return diffFormatter.scan(parentCommitTreeIterator, commitTreeIterator);
		} catch (Exception exception) {
			Logger.error("An error occurred while extracting the file changes from a commit");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Counts the changed lines in a file change
	 *
	 * @param fileChange the file change
	 * @return the number of changed lines in the file change
	 */
	private int countChangedLines(DiffEntry fileChange) {
		try (final DiffFormatter diffFormatter = generateDiffFormatter()) {
			final Iterable<Edit> changedRegionsOfFile = diffFormatter.toFileHeader(fileChange).toEditList();
			int numberOfChangedLines = 0;
			for (Edit changedRegionOfFile : changedRegionsOfFile) {
				if (changedRegionOfFile.getType() == Edit.Type.INSERT) {
					numberOfChangedLines += changedRegionOfFile.getLengthB();
				} else if (changedRegionOfFile.getType() == Edit.Type.DELETE) {
					numberOfChangedLines += changedRegionOfFile.getLengthA();
				} else if (changedRegionOfFile.getType() == Edit.Type.REPLACE) {
					numberOfChangedLines += changedRegionOfFile.getLengthA() + changedRegionOfFile.getLengthB();
				}
			}
			return numberOfChangedLines;
		} catch (Exception exception) {
			Logger.error("An error occurred while counting the changed lines in a file change");
			exception.printStackTrace();
			System.exit(1);
			return -1;
		}
	}

	/**
	 * Counts the files which existed in the system at any point in a commit sequence
	 * Each unique file path is counted at most once
	 *
	 * @param startCommitId     the ID of the first commit in the commit sequence
	 * @param endCommitId       the ID of the last commit in the commit sequence
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 * @return the number of files which existed in the system at any point in the commit sequence
	 */
	public int countFilesInSystem(String startCommitId, String endCommitId, Set<String> fileTypeWhitelist) {
		return enumerateFilesInSystem(startCommitId, endCommitId, fileTypeWhitelist).size();
	}

	/**
	 * Enumerates the files which existed in the system at any point in a commit sequence
	 * Each unique file path is included at most once
	 *
	 * @param startCommitId     the ID of the first commit in the commit sequence
	 * @param endCommitId       the ID of the last commit in the commit sequence
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 * @return the paths of the files which existed in the system at any point in the commit sequence
	 */
	private Set<String> enumerateFilesInSystem(String startCommitId, String endCommitId, Set<String> fileTypeWhitelist) {
		final Iterable<RevCommit> commits = extractNonMergeCommits(startCommitId, endCommitId);
		final Set<String> pathsOfFilesInSystem = new HashSet<>();
		for (RevCommit commit : commits) {
			final Set<String> pathsOfFilesInSystemAtCommit = enumerateFilesInSystem(commit, fileTypeWhitelist);
			pathsOfFilesInSystem.addAll(pathsOfFilesInSystemAtCommit);
		}
		return pathsOfFilesInSystem;
	}

	/**
	 * Enumerates the files in the system at a commit
	 *
	 * @param commit            the commit
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 * @return the paths of the files in the system at the commit
	 */
	private Set<String> enumerateFilesInSystem(RevCommit commit, Set<String> fileTypeWhitelist) {
		final Set<String> pathsOfFilesInSystem = new HashSet<>();
		try (final TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(commit.getTree());
			treeWalk.setRecursive(true);
			treeWalk.setFilter(generateFileTypeWhitelistTreeFilter(fileTypeWhitelist));
			while (treeWalk.next()) {
				pathsOfFilesInSystem.add(treeWalk.getPathString());
			}
		} catch (Exception exception) {
			Logger.error("An error occurred while enumerating the files in the system at a commit");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
		return pathsOfFilesInSystem;
	}

	/**
	 * Creates and configures a tree filter enforcing a whitelist of file types
	 *
	 * @param fileTypeWhitelist the extensions of the only file types to consider (empty set means consider all)
	 * @return a tree filter enforcing the whitelist of file types
	 */
	private TreeFilter generateFileTypeWhitelistTreeFilter(Set<String> fileTypeWhitelist) {
		final List<TreeFilter> treeFilters = new ArrayList<>();
		for (String fileType : fileTypeWhitelist) {
			final TreeFilter treeFilter = PathSuffixFilter.create(fileType);
			treeFilters.add(treeFilter);
		}
		if (treeFilters.size() == 0) {
			return TreeFilter.ALL;
		} else if (treeFilters.size() == 1) {
			return treeFilters.get(0);
		} else {
			return OrTreeFilter.create(treeFilters.toArray(new TreeFilter[0]));
		}
	}

	/**
	 * Creates and configures a diff formatter
	 *
	 * @return the diff formatter
	 */
	private DiffFormatter generateDiffFormatter() {
		final DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
		diffFormatter.setRepository(repository);
		return diffFormatter;
	}

	/**
	 * Validates the timestamp order of two commits
	 *
	 * @param startCommit the commit which should have an earlier timestamp
	 * @param endCommit the commit which should have a later timestamp
	 */
	private void validateCommitOrder(RevCommit startCommit, RevCommit endCommit) {
		if (startCommit.getCommitTime() > endCommit.getCommitTime()) {
			Logger.error("Start commit timestamp cannot be greater than end commit timestamo");
			System.exit(1);
		}
	}
}
