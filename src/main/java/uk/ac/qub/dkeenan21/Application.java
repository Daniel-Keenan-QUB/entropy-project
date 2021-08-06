package uk.ac.qub.dkeenan21;

import org.apache.commons.cli.*;
import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.driver.AnalysisDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.apache.log4j.LogManager.getRootLogger;

/**
 * Entrypoint for the application
 * Parses and validates command-line arguments, then delegates responsibility to other classes
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

		final String repositoryPathOptionValue = commandLine.getOptionValue("repository-path");
		final String periodLengthOptionValue = commandLine.getOptionValue("period-length");
		final String modeOptionValue = commandLine.getOptionValue("mode");

		final String repositoryPath = validateRepositoryPath(repositoryPathOptionValue);
		final int periodLength = parseAndValidatePeriodLength(periodLengthOptionValue);
		final int mode = parseAndValidateMode(modeOptionValue);

		final AnalysisDriver analysisDriver = new AnalysisDriver(repositoryPath);
		analysisDriver.analyse(periodLength, mode);
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
	 * @param options the supported options
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
	 * Validates the repository path
	 *
	 * @param repositoryPathOptionValue the repository path option value
	 * @return the repository path
	 */
	private static String validateRepositoryPath(String repositoryPathOptionValue) {
		final Path path = Paths.get(repositoryPathOptionValue);
		if (!Files.isDirectory(path)) {
			Logger.error("Repository path is not a path to a directory");
			System.exit(1);
		}
		return repositoryPathOptionValue;
	}

	/**
	 * Parses and validates the period length
	 *
	 * @param periodLengthOptionValue the period length option value
	 * @return the period length
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
	 * Parses and validates the mode
	 *
	 * @param modeOptionValue the mode option value
	 * @return the mode
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
}
