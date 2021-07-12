package uk.ac.qub.dkeenan21;

import org.tinylog.Logger;

import java.util.Map;

/**
 * Computes source code change entropy
 */
public class EntropyComputer {
	/**
	 * Computes the absolute entropy of a change set
	 *
	 * @param changeSetSummary a map containing an entry for each changed file in the change set
	 *                         entries are of the form [key = file name, value = number of changed lines]
	 * @return the entropy value
	 */
	public double computeAbsoluteEntropy(Map<String,Integer> changeSetSummary) {
		final int logBase = 2; // standard log base for entropy, yielding the unit of 'bits'
		final double entropy = computeEntropy(changeSetSummary, logBase);
		Logger.info("Absolute entropy = " + String.format("%.2f", entropy));
		return entropy;
	}

	/**
	 * Computes the normalised entropy of a change set
	 *
	 * @param changeSetSummary a map containing an entry for each changed file in the change set
	 *                         entries are of the form [key = file name, value = number of changed lines]
	 * @param numberOfLinesInSystem the number of lines in the system, used for normalisation
	 * @return the entropy value
	 */
	public double computeNormalisedEntropy(Map<String,Integer> changeSetSummary, int numberOfLinesInSystem) {
		final double entropy = computeEntropy(changeSetSummary, numberOfLinesInSystem);
		Logger.debug("– Number of lines in system = " + numberOfLinesInSystem);
		Logger.info("Normalised entropy = " + String.format("%.2f", entropy));
		return entropy;
	}

	/**
	 * Computes the entropy of a change set
	 *
	 * @param changeSetSummary a map containing an entry for each changed file in the change set
	 *                         entries are of the form [key = file name, value = number of changed lines]
	 * @param logBase the base used for the logarithm in the entropy formula
	 * @return the entropy value
	 */
	private double computeEntropy(Map<String,Integer> changeSetSummary, int logBase) {
		final int numberOfChangedLinesInChangeSet = changeSetSummary.values().stream().reduce(0, Integer::sum);
		double entropy = 0.0;
		Logger.debug("Listing changes over all commits in change set");
		for (Map.Entry<String,Integer> entry : changeSetSummary.entrySet()) {
			final String changedFileName = entry.getKey();
			final int numberOfChangedLinesInChangedFile = entry.getValue();
			Logger.debug("– " + changedFileName + " (" + numberOfChangedLinesInChangedFile + " lines)");
			if (numberOfChangedLinesInChangedFile <= 0) {
				continue; // '0 changed lines' entries must be ignored, otherwise result will be undefined
			}
			final double changedLineRatio = (double) numberOfChangedLinesInChangedFile / numberOfChangedLinesInChangeSet;
			entropy -= changedLineRatio * Math.log(changedLineRatio) / Math.log(logBase);
		}
		Logger.debug("Change set summary");
		Logger.debug("– Number of changed lines = " + numberOfChangedLinesInChangeSet);
		return entropy;
	}
}
