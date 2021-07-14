package uk.ac.qub.dkeenan21;

import org.tinylog.Logger;

import java.util.Map;

/**
 * Computes source code change entropy
 */
public class EntropyComputer {
	/**
	 * Computes the absolute entropy of a change period
	 *
	 * @param changePeriodSummary a map containing an entry for each changed file in the change period
	 *                            entries are of the form [key = path, value = number of changed lines]
	 * @return the entropy value
	 */
	public double computeAbsoluteEntropy(Map<String, Integer> changePeriodSummary) {
		final int logBase = 2; // standard log base for entropy, yielding the unit of 'bits'
		final double entropy = computeEntropy(changePeriodSummary, logBase);
		Logger.info("Absolute entropy = " + String.format("%.2f", entropy));
		return entropy;
	}

	/**
	 * Computes the normalised entropy of a change period
	 * The absolute entropy value is divided by the determined potential maximum entropy for the change period
	 * Normalised entropy yields values strictly between 0 and 1
	 *
	 * @param changePeriodSummary           a map containing an entry for each changed file in the change period
	 *                                      entries are of the form [key = file path, value = number of changed lines]
	 * @param numberOfFilesForNormalisation the number of files which, if all were changed in equal measure, would
	 *                                      yield the maximum entropy for the change period — different criteria exist
	 *                                      for determining an appropriate number of files to use for normalisation
	 * @return the entropy value
	 */
	public double computeNormalisedEntropy(Map<String, Integer> changePeriodSummary, int numberOfFilesForNormalisation) {
		final double entropy = computeEntropy(changePeriodSummary, numberOfFilesForNormalisation);
		Logger.debug("– Number of files used for normalisation = " + numberOfFilesForNormalisation);
		Logger.info("Normalised entropy = " + String.format("%.2f", entropy));
		return entropy;
	}

	/**
	 * Computes the entropy of a change period
	 *
	 * @param changePeriodSummary a map containing an entry for each changed file in the change period
	 *                            entries are of the form [key = file path, value = number of changed lines]
	 * @param logBase             the base used for the logarithm in the entropy formula
	 * @return the entropy value
	 */
	private double computeEntropy(Map<String, Integer> changePeriodSummary, int logBase) {
		if (logBase < 2) {
			return 0.0;
		}
		final int numberOfChangedLinesInChangePeriod = changePeriodSummary.values().stream().reduce(0, Integer::sum);
		double entropy = 0.0;
		Logger.debug("Listing changes over all commits in change period");
		for (Map.Entry<String, Integer> entry : changePeriodSummary.entrySet()) {
			final String changedFilePath = entry.getKey();
			final int numberOfChangedLinesInChangedFile = entry.getValue();
			Logger.debug("– " + changedFilePath + " (" + numberOfChangedLinesInChangedFile + " lines)");
			if (numberOfChangedLinesInChangedFile <= 0) {
				continue; // '0 changed lines' entries must be ignored, otherwise result will be mathematically undefined
			}
			final double changedLineRatio = (double) numberOfChangedLinesInChangedFile / numberOfChangedLinesInChangePeriod;
			entropy -= changedLineRatio * Math.log(changedLineRatio) / Math.log(logBase);
		}
		Logger.debug("Change period summary");
		Logger.debug("– Number of changed lines = " + numberOfChangedLinesInChangePeriod);
		Logger.debug("– Number of changed files = " + changePeriodSummary.size());
		return entropy;
	}
}
