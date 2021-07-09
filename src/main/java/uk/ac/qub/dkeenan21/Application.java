package uk.ac.qub.dkeenan21;

import org.apache.log4j.varia.NullAppender;
import org.tinylog.Logger;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.log4j.LogManager.getRootLogger;

public class Application {
	private static final String REPOSITORY_PATH = "../entropy-tester";
	private static final String BRANCH_NAME = "master";
	private static final String START_COMMIT_ID = "0efd9f02740e5d05cc7b6bea1fe9b2105839b6a0";
	private static final String END_COMMIT_ID = "72cc100347d10b2c1429876fa3785f5d274d49f1";
	private static final String START_TIME = "2021-06-24 04:00:00";
	private static final String END_TIME = "2021-06-24 04:03:59";
	private static final String[] FILE_TYPES = new String[] {"java", "txt"};
	private static final boolean NORMALISE = false;

	public static void main(String[] args) throws ParseException {
		getRootLogger().addAppender(new NullAppender()); // disable log4j output from JGit library
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startTime = format.parse(START_TIME);
		Date endTime = format.parse(END_TIME);
		List<String> fileTypes = asList(FILE_TYPES);
		EntropyComputer entropyComputer = new EntropyComputer(REPOSITORY_PATH, BRANCH_NAME);

		double entropy = entropyComputer.computeEntropy(START_COMMIT_ID, END_COMMIT_ID, fileTypes, NORMALISE);
//		double entropy = entropyComputer.computeEntropy(startTime, endTime, fileTypes, NORMALISE);

		DecimalFormat decimalFormat = new DecimalFormat();
		decimalFormat.setMinimumFractionDigits(2);
		decimalFormat.setMaximumFractionDigits(2);
		Logger.info("Entropy = " + decimalFormat.format(entropy));
	}
}
