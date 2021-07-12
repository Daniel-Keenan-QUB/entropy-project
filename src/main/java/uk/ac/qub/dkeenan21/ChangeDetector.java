package uk.ac.qub.dkeenan21;

import org.apache.commons.io.FilenameUtils;
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
	 * Constructor which accepts a repository path
	 *
	 * @param repositoryPath a path to the repository
	 */
	public ChangeDetector(String repositoryPath) {
		this.repository = new RepositoryHelper().convertToRepository(repositoryPath);
	}

	/**
	 * Generates a map representing a summary of a single-commit change set
	 *
	 * @param commitId the ID of the single commit in the change set
	 * @param fileTypesToInclude the filename extensions (with no preceding dot) of the only file types to be included
	 * @return a map containing an entry for each changed file in the change set
	 *         entries are of the form [key = file name, value = number of changed lines]
	 */
	public Map<String,Integer> summariseChangeSet(String commitId, Collection<String> fileTypesToInclude) {
		return summariseChangeSet(commitId, commitId, fileTypesToInclude);
	}

	/**
	 * Generates a map representing a summary of a multi-commit change set
	 *
	 * @param startCommitId the ID of the earliest commit in the change set
	 * @param endCommitId the ID of the latest commit in the change set
	 * @param fileTypesToInclude the filename extensions (with no preceding dot) of the only file types to be included
	 * @return a map containing an entry for each changed file in the change set
	 *         entries are of the form [key = file name, value = number of changed lines]
	 */
	public Map<String,Integer> summariseChangeSet(String startCommitId, String endCommitId, Collection<String> fileTypesToInclude) {
		try {
			final Iterable<RevCommit> commits = extractNonMergeCommits(startCommitId, endCommitId);
			final Map<String,Integer> changeSetSummary = new TreeMap<>();
			for (RevCommit commit : commits) {
				Logger.debug("Listing changes in commit " + commit.getName());
				final Iterable<DiffEntry> fileChanges = extractFileChanges(commit);
				for (DiffEntry fileChange : fileChanges) {
					final String changedFileName = fileChange.getOldPath().equals("/dev/null") ? fileChange.getNewPath() : fileChange.getOldPath();
					final String changedFileNameExtension = FilenameUtils.getExtension(changedFileName);
					if (!fileTypesToInclude.contains(changedFileNameExtension)) {
						continue;
					}
					final int numberOfChangedLinesInChangedFile = countChangedLines(fileChange);
					Logger.debug("– " + changedFileName + " (" + numberOfChangedLinesInChangedFile + " lines)");
					if (changeSetSummary.containsKey(changedFileName)) {
						changeSetSummary.put(changedFileName, changeSetSummary.get(changedFileName) + numberOfChangedLinesInChangedFile);
					} else {
						changeSetSummary.put(changedFileName, numberOfChangedLinesInChangedFile);
					}
				}
			}
			return changeSetSummary;
		} catch (Exception exception) {
			Logger.error("An error occurred while summarising the change set");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Extracts the non-merge commits from a change set
	 *
	 * @param startCommitId the ID of the earliest commit in the change set
	 * @param endCommitId the ID of the latest commit in the change set
	 * @return the extracted non-merge commits
	 */
	private Iterable<RevCommit> extractNonMergeCommits(String startCommitId, String endCommitId) {
		try {
			final RevCommit startCommit = repository.parseCommit(ObjectId.fromString(startCommitId));
			final RevCommit endCommit = repository.parseCommit(ObjectId.fromString(endCommitId));
			final Iterable<RevCommit> commits = new Git(repository).log().addRange(startCommit, endCommit).call();
			final List<RevCommit> nonMergeCommits = StreamSupport.stream(commits.spliterator(), false)
					.filter(commit -> commit.getParentCount() < 2)
					.collect(Collectors.toList());
			nonMergeCommits.add(startCommit);
			Collections.reverse(nonMergeCommits);
			return nonMergeCommits;
		} catch (Exception exception) {
			Logger.error("An error occurred while extracting non-merge commits");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Extracts the file changes from a commit
	 *
	 * @param commit the commit
	 * @return the file changes
	 */
	private Iterable<DiffEntry> extractFileChanges(RevCommit commit) {
		try (DiffFormatter diffFormatter = diffFormatter()) {
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
		try (DiffFormatter diffFormatter = diffFormatter()) {
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
	 * Counts the lines in the system at a commit
	 *
	 * @param commitId the ID of the commit
	 * @param fileTypesToInclude the filename extensions (with no preceding dot) of the only file types to be included
	 * @return the number of lines in the system
	 */
	public int countLinesInSystem(String commitId, Collection<String> fileTypesToInclude) {
		try (DiffFormatter diffFormatter = diffFormatter()) {
			final RevCommit commit = repository.parseCommit(ObjectId.fromString(commitId));
			final ObjectReader objectReader = repository.newObjectReader();
			final AbstractTreeIterator commitTreeIterator = new CanonicalTreeParser(null, objectReader, commit.getTree());
			final AbstractTreeIterator emptyTreeIterator = new EmptyTreeIterator();
			final Iterable<DiffEntry> fileChanges = diffFormatter.scan(emptyTreeIterator, commitTreeIterator);
			int numberOfLinesInSystem = 0;
			for (DiffEntry fileChange : fileChanges) {
				final String changedFileName = fileChange.getNewPath();
				final String changedFileNameExtension = FilenameUtils.getExtension(changedFileName);
				if (!fileTypesToInclude.contains(changedFileNameExtension)) {
					continue;
				}
				// comparing with empty tree means every line in the system will be considered a 'changed line'
				numberOfLinesInSystem += countChangedLines(fileChange);
			}
			return numberOfLinesInSystem;
		} catch (Exception exception) {
			Logger.error("An error occurred while counting the lines in the system");
			exception.printStackTrace();
			System.exit(1);
			return -1;
		}
	}

	/**
	 * Creates and configures a diff formatter
	 *
	 * @return the diff formatter
	 */
	private DiffFormatter diffFormatter() {
		DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
		diffFormatter.setRepository(repository);
		diffFormatter.setDetectRenames(true);
		return diffFormatter;
	}
}
