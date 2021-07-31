package uk.ac.qub.dkeenan21;

import org.apache.commons.cli.*;
import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;
import uk.ac.qub.dkeenan21.driver.AnalysisDriver;

import java.util.Arrays;
import java.util.Set;

import static org.apache.log4j.LogManager.getRootLogger;

/**
 * Entrypoint for the program, responsible for parsing and validating the command-line arguments and then
 * delegating responsibility for more specific functionalities to the other classes
 */
public class Application {
	/**
	 * Entrypoint for the program
	 *
	 * @param args the command-line arguments passed to the program
	 */
	public static void main(String[] args) {
		final Options options = configureCommandLineOptions();
		try {
			final CommandLine commandLine = new DefaultParser().parse(options, args);
			final String repositoryPath = commandLine.getOptionValue("repository-path");

			int changePeriodSize = 0;
			try {
				changePeriodSize = Integer.parseInt(commandLine.getOptionValue("change-period-size"));
			} catch (NumberFormatException numberFormatException) {
				Logger.error("Change period size must be a positive integer");
				System.exit(1);
			}
			if (changePeriodSize < 1) {
				Logger.error("Change period size must be a positive integer");
				System.exit(1);
			}

			String[] fileTypeWhitelist = new String[0];
			if (commandLine.getOptionValue("file-type-whitelist") != null &&
					!commandLine.getOptionValue("file-type-whitelist").isBlank()) {
				fileTypeWhitelist = commandLine.getOptionValue("file-type-whitelist").trim().split(" ");
			}

			Logger.info("Beginning analysis with program arguments");
			Logger.info("— Repository path: " + repositoryPath);
			Logger.info("— Change period size: " + changePeriodSize);
			Logger.info("— File type whitelist: " + Arrays.toString(fileTypeWhitelist));

			getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
			final AnalysisDriver analysisDriver = new AnalysisDriver(repositoryPath);
			analysisDriver.analyse(changePeriodSize, Set.of(fileTypeWhitelist));
		} catch (ParseException parseException) {
			// incorrect use of command-line options, so print usage message and exit with non-zero status code
			final HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.printHelp("repository-analyser", options);
			System.exit(1);
		}
	}

	/**
	 * Configure the command-line options to be accepted by the application
	 *
	 * @return the command-line options
	 */
	private static Options configureCommandLineOptions() {
		final Option repositoryPathOption = Option.builder("rp")
				.longOpt("repository-path")
				.argName("PATH")
				.desc("analyse the Git repository at PATH")
				.hasArg()
				.required()
				.build();
		final Option changePeriodSizeOption = Option.builder("sz")
				.longOpt("change-period-size")
				.argName("SIZE")
				.desc("define a change period as SIZE commits")
				.hasArg()
				.required()
				.build();
		final Option fileTypeWhiteListOption = Option.builder("wl")
				.longOpt("file-type-whitelist")
				.argName("WHITELIST")
				.desc("only consider the file type extensions specified in space-separated list WHITELIST (must be " +
						"enclosed in quotemarks)")
				.hasArgs() // allows an unlimited number of arguments
				.build();
		final Options options = new Options();
		options.addOption(repositoryPathOption);
		options.addOption(changePeriodSizeOption);
		options.addOption(fileTypeWhiteListOption);
		return options;
	}
}
