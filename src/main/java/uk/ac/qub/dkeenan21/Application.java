package uk.ac.qub.dkeenan21;

import org.apache.commons.cli.*;
import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;
import org.yaml.snakeyaml.Yaml;
import uk.ac.qub.dkeenan21.driver.AnalysisDriver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.log4j.LogManager.getRootLogger;

/**
 * Parses and validates command-line arguments, loads filters from config file, and delegates responsibility
 */
public class Application {
	/**
	 * Entrypoint for the application
	 *
	 * @param arguments the command-line arguments passed to the application
	 */
	public static void main(String[] arguments) {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library

		final Options options = generateOptions();
		final CommandLine commandLine = generateCommandLine(options, arguments);

		// parse and validate command-line arguments
		final String repositoryPath = commandLine.getOptionValue("repository-path");
		validateRepositoryPath(repositoryPath);
		final int periodLength = parseAndValidatePeriodLength(commandLine.getOptionValue("period-length"));
		final int mode = parseAndValidateMode(commandLine.getOptionValue("mode"));

		// load filters from config file and extract the individual filter groups
		final Map<String, List<String>> filters = loadFiltersFromConfigFile();
		final String[] fileTypesToInclude = extractFilterGroup(filters, "file_types_to_include");
		final String[] filePathPatternsToExclude = extractFilterGroup(filters, "file_path_patterns_to_exclude");
		final String[] refactoringTypesToInclude = extractFilterGroup(filters, "refactoring_types_to_include");

		// construct and delegate responsibility to an AnalysisDriver
		final AnalysisDriver analysisDriver = new AnalysisDriver(repositoryPath, periodLength, mode,
				fileTypesToInclude, filePathPatternsToExclude, refactoringTypesToInclude, "results.csv");
		analysisDriver.analyse();
	}

	/**
	 * Generates the command-line options
	 *
	 * @return the command-line options
	 */
	private static Options generateOptions() {
		final Options options = new Options();
		options.addOption(generateRepositoryPathOption());
		options.addOption(generatePeriodLengthOption());
		options.addOption(generateModeOption());
		return options;
	}

	/**
	 * Generates the 'repository-path' command-line option
	 *
	 * @return the 'repository-path' command-line option
	 */
	private static Option generateRepositoryPathOption() {
		return Option.builder("rp").longOpt("repository-path").argName("PATH")
				.desc("analyse the Git repository at PATH").hasArg().required().build();
	}

	/**
	 * Generates the 'period-length' command-line option
	 *
	 * @return the 'period-length' command-line option
	 */
	private static Option generatePeriodLengthOption() {
		return Option.builder("pl").longOpt("period-length").argName("LENGTH")
				.desc("define a period as LENGTH commits").hasArg().required().build();
	}

	/**
	 * Generates the 'mode' command-line option
	 *
	 * @return the 'mode' command-line option
	 */
	private static Option generateModeOption() {
		return Option.builder("m").longOpt("mode").argName("NUMBER")
				.desc("execute in mode NUMBER").hasArg().required().build();
	}

	/**
	 * Generates a command line representation
	 *
	 * @param options   the supported options
	 * @param arguments the arguments used
	 * @return the command line representation
	 */
	private static CommandLine generateCommandLine(Options options, String[] arguments) {
		CommandLine commandLine = null;
		try {
			commandLine = new DefaultParser().parse(options, arguments);
		} catch (ParseException parseException) {
			// invalid command-line options, so print usage message and exit
			final HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.printHelp("repository-analyser", options);
			System.exit(1);
		}
		return commandLine;
	}

	/**
	 * Validates that the repository path is a path to an existing directory
	 * Logs an error message and terminates the application if it fails validation
	 *
	 * @param repositoryPath the repository path
	 */
	private static void validateRepositoryPath(String repositoryPath) {
		final Path path = Paths.get(repositoryPath);
		if (!Files.isDirectory(path)) {
			Logger.error("Repository path is not a path to an existing directory");
			System.exit(1);
		}
	}

	/**
	 * Parses the period length in its command-line option value (string) form
	 * Validates that it is a positive integer
	 * Returns the period length as an integer if it passes validation
	 * Logs an error message and terminates the application if it fails validation
	 *
	 * @param periodLengthOptionValue the period length in its command-line option value (string) form
	 * @return the period length as an integer
	 */
	private static int parseAndValidatePeriodLength(String periodLengthOptionValue) {
		int periodLength = 0;
		try {
			periodLength = Integer.parseInt(periodLengthOptionValue);
		} catch (NumberFormatException numberFormatException) {
			Logger.error("Period length must be a positive integer");
			System.exit(1);
		}
		if (periodLength < 1) {
			Logger.error("Period length must be a positive integer");
			System.exit(1);
		}
		return periodLength;
	}

	/**
	 * Parses the mode in its command-line option value (string) form
	 * Validates that it is an integer between 1 and 7
	 * Returns the mode as an integer if it passes validation
	 * Logs an error message and terminates the application if it fails validation
	 *
	 * @param modeOptionValue the mode in its command-line option value (string) form
	 * @return the mode as an integer
	 */
	private static int parseAndValidateMode(String modeOptionValue) {
		int mode = 0;
		try {
			mode = Integer.parseInt(modeOptionValue);
		} catch (NumberFormatException numberFormatException) {
			Logger.error("Mode must be an integer between 1 and 7");
			System.exit(1);
		}
		if (!(mode >= 1 && mode <= 7)) {
			Logger.error("Mode must be an integer between 1 and 7");
			System.exit(1);
		}
		return mode;
	}

	/**
	 * Loads the filters from the relevant config file
	 * Returns an empty map if no content is found in the config file
	 *
	 * @return a map containing entries of the form: [filter group name -> list of filter strings]
	 */
	private static Map<String, List<String>> loadFiltersFromConfigFile() {
		try (InputStream inputStream = new FileInputStream("src/main/resources/filters-config.yaml")) {
			final Yaml yaml = new Yaml();
			final Map<String, List<String>> filters = yaml.load(inputStream);
			return filters != null ? filters : new HashMap<>();
		} catch (FileNotFoundException fileNotFoundException) {
			Logger.error("Filters config file was not found");
			System.exit(1);
			return null;
		} catch (IOException ioException) {
			Logger.error("Failed to read filters from config file");
			System.exit(1);
			return null;
		}
	}

	/**
	 * Extracts a filter group from a map of filters
	 * Returns an empty array if the filter group is not found in the map
	 *
	 * @param filters         a map containing entries of the form: [filter group name -> list of filter strings]
	 * @param filterGroupName the name of the filter group
	 * @return the filter group
	 */
	private static String[] extractFilterGroup(Map<String, List<String>> filters, String filterGroupName) {
		final List<String> filterGroup = filters.get(filterGroupName);
		if (filterGroup != null) {
			return filterGroup.toArray(new String[0]);
		} else {
			return new String[0];
		}
	}
}
