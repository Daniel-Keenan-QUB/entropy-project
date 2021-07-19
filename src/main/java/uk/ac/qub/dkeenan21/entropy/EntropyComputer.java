package uk.ac.qub.dkeenan21.entropy;

import java.util.Map;

/**
 * Computes source code change entropy (or simply 'entropy'), a measure of the 'scattering' of changes
 * Entropy increases with the number of changed files and how evenly distributed line changes are across the files
 * Entropy is minimum (zero) when no more than one file has been changed
 * Entropy is maximum for a given number of changed files when each has been changed by an equal number of lines
 */
public class EntropyComputer {
	/**
	 * Computes the entropy of a change period
	 *
	 * @param changePeriodSummary a map containing an entry for each changed file in the change period
	 *                            entries are of the form [key = path, value = number of changed lines]
	 * @return the entropy value
	 */
	public double computeEntropy(Map<String, Integer> changePeriodSummary) {
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
}
