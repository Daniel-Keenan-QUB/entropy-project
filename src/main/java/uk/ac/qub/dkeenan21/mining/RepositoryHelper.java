package uk.ac.qub.dkeenan21.mining;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.tinylog.Logger;

import java.io.File;

/**
 * Provides a helper method for generating a Git repository representation
 */
public class RepositoryHelper {
	/**
	 * Converts a repository path to its corresponding repository representation
	 * Note: the Git metadata (.git) directory must be at the root of the repository
	 *
	 * @param repositoryPath the repository path
	 * @return the repository representation
	 */
	public Repository convertToRepository(String repositoryPath) {
		final File repositoryDirectory = new File(repositoryPath);
		if (repositoryDirectory.exists()) {
			final RepositoryBuilder repositoryBuilder = new RepositoryBuilder();
			final File repositoryMetadataDirectory = new File(repositoryDirectory, ".git");
			try {
				return repositoryBuilder
						.setGitDir(repositoryMetadataDirectory)
						.readEnvironment()
						.setMustExist(true)
						.build();
			} catch (Exception exception) {
				Logger.error("An error occurred while generating the repository representation");
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
