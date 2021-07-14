package uk.ac.qub.dkeenan21.entropy;

import org.tinylog.Logger;

import java.util.Map;

/**
 * Computes source code change entropy (or simply 'entropy'), a measure of the 'scattering' of changes
 * Entropy increases with the number of changed files and how evenly distributed line changes are across the files
 * Entropy is minimum (zero) when no more than one file has been changed
 * Entropy is maximum for a given number of changed files when each has been changed by an equal number of lines
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
		final int numberOfChangedLinesInChangePeriod = changePeriodSummary.values().stream().reduce(0, Integer::sum);
		double absoluteEntropy = 0.0;
		Logger.debug("Listing changes over all commits in change period");
		for (Map.Entry<String, Integer> entry : changePeriodSummary.entrySet()) {
			final String changedFilePath = entry.getKey();
			final int numberOfChangedLinesInChangedFile = entry.getValue();
			Logger.debug("– " + changedFilePath + " (" + numberOfChangedLinesInChangedFile + " lines)");
			if (numberOfChangedLinesInChangedFile <= 0) {
				continue; // '0 changed lines' entries must be ignored, otherwise result will be undefined
			}
			final double changedLineRatio = (double) numberOfChangedLinesInChangedFile / numberOfChangedLinesInChangePeriod;
			// calculate log2(changedLineRatio) indirectly using the log change of base formula
			absoluteEntropy -= changedLineRatio * Math.log10(changedLineRatio) / Math.log10(2);
		}
		Logger.debug("Change period summary");
		Logger.debug("– Number of changed lines = " + numberOfChangedLinesInChangePeriod);
		Logger.debug("– Number of changed files = " + changePeriodSummary.size());
		return absoluteEntropy;
	}

	/**
	 * Computes the normalised entropy of a change period
	 * The absolute entropy value is divided by the maximum potential absolute entropy for the change period
	 * Normalised entropy yields values strictly between 0 and 1
	 *
	 * @param changePeriodSummary       a map containing an entry for each changed file in the change period
	 *                                  entries are of the form [key = path, value = number of changed lines]
	 * @param fileCountForNormalisation the number of files which, if all were changed in equal measure, would
	 *                                  yield the maximum entropy for the change period — different criteria exist
	 *                                  for determining an appropriate number of files to use for normalisation
	 * @return the entropy value
	 */
	public double computeNormalisedEntropy(Map<String, Integer> changePeriodSummary, int fileCountForNormalisation) {
		final int numberOfChangedFiles = changePeriodSummary.size();
		if (fileCountForNormalisation < numberOfChangedFiles) {
			Logger.error("File count for entropy normalisation (" + fileCountForNormalisation + ") cannot be less than" +
					" number of changed files (" + numberOfChangedFiles + ")");
			System.exit(1);
			return -1.0;
		}
		final double absoluteEntropy = computeAbsoluteEntropy(changePeriodSummary);
		// calculate log2(fileCountForNormalisation) indirectly using the log change of base formula
		final double maxPotentialAbsoluteEntropyForFileCount = Math.log10(fileCountForNormalisation) / Math.log10(2);
		Logger.debug("– Number of files used for normalisation = " + fileCountForNormalisation);
		if (maxPotentialAbsoluteEntropyForFileCount <= 0) {
			return 0.0; // special case — must return early to prevent undefined result
		}
		return absoluteEntropy / maxPotentialAbsoluteEntropyForFileCount;
	}
}
