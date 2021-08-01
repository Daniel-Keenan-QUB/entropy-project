package uk.ac.qub.dkeenan21.entropy;

import org.tinylog.Logger;

import java.util.Map;

/**
 * Computes source code change entropy, a measure of the scattering of changes
 */
public class EntropyComputer {
	/**
	 * Computes the entropy of a change period
	 *
	 * @param changePeriodSummary a map containing an entry for each changed file in the change period
	 *                            entries are of the form [key = path, value = number of changed lines]
	 * @return the entropy value
	 */
	public double computeEntropyOfPeriod(Map<String, Integer> changePeriodSummary) {
		final int numberOfChangedLinesInChangePeriod = changePeriodSummary.values().stream().reduce(0, Integer::sum);
		double entropy = 0.0;
		for (Map.Entry<String, Integer> entry : changePeriodSummary.entrySet()) {
			final int numberOfChangedLinesInChangedFile = entry.getValue();
			if (numberOfChangedLinesInChangedFile <= 0) {
				continue; // '0 changed lines' entries (e.g., binaries) must be ignored, otherwise result will be undefined
			}
			final double changedLineRatio = (double) numberOfChangedLinesInChangedFile / numberOfChangedLinesInChangePeriod;
			// calculate log2(changedLineRatio) indirectly using the log change of base formula
			entropy -= changedLineRatio * Math.log10(changedLineRatio) / Math.log10(2);
		}
		return entropy;
	}

	/**
	 * Computes the entropy of a file in a change period
	 *
	 * @param changePeriodSummary a map containing an entry for each changed file in the change period
	 *                            entries are of the form [key = path, value = number of changed lines]
	 * @param filePath            the path of the file for which to compute entropy
	 * @return the entropy value
	 */
	public double computeEntropyOfFileWithinPeriod(Map<String, Integer> changePeriodSummary, String filePath) {
		if (!changePeriodSummary.containsKey(filePath)) {
			Logger.error("File '" + filePath + "' not found in change period");
			System.exit(1);
			return -1.0;
		}

		final double entropyOfChangePeriod = computeEntropyOfPeriod(changePeriodSummary);
		final int numberOfChangedLinesInChangedFile = changePeriodSummary.get(filePath);
		final int numberOfChangedLinesInChangePeriod = changePeriodSummary.values().stream().reduce(0, Integer::sum);

		return entropyOfChangePeriod * ((double) numberOfChangedLinesInChangedFile / (double) numberOfChangedLinesInChangePeriod);
	}
}
