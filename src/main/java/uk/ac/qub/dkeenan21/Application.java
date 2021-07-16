package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;
import uk.ac.qub.dkeenan21.driver.AnalysisDriver;

import java.util.Set;

import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String repositoryPath = "../refactoring-toy-example";
	private static final String[] fileTypes = new String[]{};

	public static void main(String[] args) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		new AnalysisDriver(repositoryPath, Set.of(fileTypes)).analyse(1);
	}
}
