package uk.ac.qub.dkeenan21;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

import static java.lang.Math.max;

/**
 * Computes source code change entropy on a Git repository branch
 */
public class EntropyComputer {
	private final Repository repository;
	private final String branchName;

	/**
	 * Constructor which accepts a repository path and branch name
	 *
	 * @param repositoryPath the repository path
	 * @param branchName the branch name
	 */
	public EntropyComputer(String repositoryPath, String branchName) {
		this.repository = convertToRepository(repositoryPath);
		this.branchName = branchName;
		checkOut(branchName);
	}

	/**
	 * Computes the entropy between two commits (inclusive)
	 *
	 * @param startCommitId the ID of the less recent commit
	 * @param endCommitId the ID of the more recent commit
	 * @param normalise whether entropy should be normalised according to the number of lines in the system
	 * @return the entropy value
	 */
	public double computeEntropy(String startCommitId, String endCommitId, boolean normalise) {
		int numberOfLinesInSystem = countLinesInSystem(endCommitId);
		int numberOfLinesInSystemChanged = 0;
		Iterable<RevCommit> commits = extractCommits(startCommitId, endCommitId);
		Map<String,Integer> changesMap = new HashMap<>();

		for (RevCommit commit : commits) {
			System.out.println("Commit: " + commit.getName());
			Iterable<DiffEntry> diffEntries = extractDiffEntries(commit);
			for (DiffEntry diffEntry : diffEntries) {
				String filename = diffEntry.getOldPath().equals("/dev/null") ? diffEntry.getNewPath() : diffEntry.getOldPath();
				int numberOfLinesInFileChanged = countLinesChanged(diffEntry);
				numberOfLinesInSystemChanged += numberOfLinesInFileChanged;
				System.out.println("File change: " + filename + " (" + numberOfLinesInFileChanged + " lines)");
				if (changesMap.containsKey(filename)) {
					changesMap.put(filename, changesMap.get(filename) + numberOfLinesInFileChanged);
				} else {
					changesMap.put(filename, numberOfLinesInFileChanged);
				}
			}
			System.out.println("-----------------------------------------------------------");
		}

		double entropy = 0.0;
		for (Map.Entry<String,Integer> entry : changesMap.entrySet()) {
			System.out.println("File change summary: " + entry.getKey() + " (" + entry.getValue() + " lines changed)");
			if (entry.getValue() <= 0) {
				// '0 lines changed' occurrences, caused by files such as JARs, must be ignored
				continue;
			}
			int logBase = normalise ? numberOfLinesInSystem : 2;
			double changedLineRatio = (double) entry.getValue() / numberOfLinesInSystemChanged;
			entropy -= changedLineRatio * Math.log(changedLineRatio) / Math.log(logBase);
		}
		System.out.println("-----------------------------------------------------------");
		System.out.println("Number of lines in system changed: " + numberOfLinesInSystemChanged);
		System.out.println("Number of lines in system: " + numberOfLinesInSystem);
		return entropy;
	}

	/**
	 * Converts a repository path to a corresponding repository representation
	 * Note: the metadata (.git) directory must be at the root of the repository
	 *
	 * @param repositoryPath the repository path
	 * @return the repository
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
				System.err.println("An error occurred");
				exception.printStackTrace();
				System.exit(1);
				return null;
			}
		} else {
			System.err.printf("Repository not found at: %s%n", repositoryPath);
			System.exit(1);
			return null;
		}
	}

	/**
	 * Check out a branch or commit in the repository
	 *
	 * @param target the branch name or commit ID
	 */
	private void checkOut(String target) {
		try {
			new Git(repository).checkout().setName(target).call();
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Counts the number of lines in the system as of a commit
	 *
	 * @param commitId the commit ID
	 * @return the number of lines in the system
	 */
	private int countLinesInSystem(String commitId) {
		try {
			checkOut(commitId);
			RevCommit commit = convertToCommit(commitId);
			TreeWalk treeWalk = new TreeWalk(repository);
			treeWalk.addTree(commit.getTree());
			treeWalk.setRecursive(false);
			int numberOfLinesOfCodeInSystem = 0;

			while (treeWalk.next()) {
				if (treeWalk.isSubtree()) {
					treeWalk.enterSubtree();
				} else {
					String repositoryDirectory = repository.getDirectory() + "/../";
					numberOfLinesOfCodeInSystem += countLinesInFile(repositoryDirectory + treeWalk.getPathString());
				}
			}
			return numberOfLinesOfCodeInSystem;
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
			return -1;
		} finally {
			checkOut(branchName);
		}
	}

	/**
	 * Converts a commit ID to a corresponding commit object
	 *
	 * @param commitId the commit ID
	 * @return the corresponding commit object
	 */
	private RevCommit convertToCommit(String commitId) {
		try {
			ObjectId commitIdObject = ObjectId.fromString(commitId);
			RevWalk revWalk = new RevWalk(repository);
			return revWalk.parseCommit(commitIdObject);
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Counts the lines in a file
	 *
	 * @param filePath the file path
	 * @return the number of lines in the file
	 */
	private int countLinesInFile(String filePath) {
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
			int numberOfLinesInFile = 0;
			while (bufferedReader.readLine() != null) {
				numberOfLinesInFile++;
			}
			bufferedReader.close();
			return numberOfLinesInFile;
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
			return -1;
		}
	}

	/**
	 * Extracts the non-merge commits from a branch between two commits (inclusive)
	 *
	 * @param startCommitId the ID of the less recent commit
	 * @param endCommitId the ID of the more recent commit
	 * @return the extracted commits
	 */
	private Iterable<RevCommit> extractCommits(String startCommitId, String endCommitId) {
		RevCommit startCommit = convertToCommit(startCommitId);
		RevCommit endCommit = convertToCommit(endCommitId);
		Date startCommitTime = new Date((long) startCommit.getCommitTime() * 1000);
		Date endCommitTime = new Date((long) endCommit.getCommitTime() * 1000);
		return extractCommits(startCommitTime, endCommitTime);
	}

	/**
	 * Extracts the non-merge commits from a branch between two instants in time (inclusive)
	 *
	 * @param startTime the less recent instant in time
	 * @param endTime the more recent instant in time
	 * @return the extracted commits
	 */
	private Iterable<RevCommit> extractCommits(Date startTime, Date endTime) {
		try {
			ObjectId branchObjectId = repository.findRef(branchName).getObjectId();
			RevFilter timeRangeFilter = CommitTimeRevFilter.between(startTime, endTime);
			Iterable<RevCommit> commits = new Git(repository).log().add(branchObjectId).setRevFilter(timeRangeFilter).call();
			List<RevCommit> commitsExcludingMergeCommits = new ArrayList<>();
			for (RevCommit commit : commits) {
				if (commit.getParentCount() < 2) {
					commitsExcludingMergeCommits.add(commit);
				}
			}
			return commitsExcludingMergeCommits;
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	/**
	 * Extracts the diff entries (changes to files) from a commit
	 *
	 * @param commit the commit
	 * @return the diff entries
	 */
	private Iterable<DiffEntry> extractDiffEntries(RevCommit commit) {
		try {
			// extract the list of diff entries by comparing the commit tree with that of its parent
			ObjectReader objectReader = repository.newObjectReader();
			AbstractTreeIterator commitTreeIterator = new CanonicalTreeParser(null, objectReader, commit.getTree());
			AbstractTreeIterator parentCommitTreeIterator;
			if (commit.getParentCount() > 0) {
				RevWalk revWalk = new RevWalk(repository);
				RevCommit parentCommit = revWalk.parseCommit(commit.getParent(0).getId());
				parentCommitTreeIterator = new CanonicalTreeParser(null, objectReader, parentCommit.getTree());
			} else {
				// this is the initial commit (no parent), so must compare with empty tree
				parentCommitTreeIterator = new EmptyTreeIterator();
			}
			return diffFormatter().scan(parentCommitTreeIterator, commitTreeIterator);
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
			return null;
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

	/**
	 * Counts the number of lines changed in a diff entry (change to file)
	 *
	 * @param diffEntry the diff entry
	 * @return the number of lines changed in the diff entry
	 */
	private int countLinesChanged(DiffEntry diffEntry) {
		try {
			Iterable<Edit> edits = diffFormatter().toFileHeader(diffEntry).toEditList();
			int numberOfLinesChanged = 0;
			// for each edit (changed region of a file)
			for (Edit edit : edits) {
				if (edit.getType() == Edit.Type.INSERT) {
					numberOfLinesChanged += edit.getLengthB();
				} else if (edit.getType() == Edit.Type.REPLACE) {
					numberOfLinesChanged += max(edit.getLengthA(), edit.getLengthB());
				} else if (edit.getType() == Edit.Type.DELETE) {
					numberOfLinesChanged += edit.getLengthA();
				}
			}
			return numberOfLinesChanged;
		} catch (Exception exception) {
			System.err.println("An error occurred");
			exception.printStackTrace();
			System.exit(1);
			return -1;
		}
	}
}
