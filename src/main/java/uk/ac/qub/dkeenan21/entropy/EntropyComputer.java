package uk.ac.qub.dkeenan21.entropy;

import java.util.Map;

/**
 * Computes source code change entropy
 */
public class EntropyComputer {
	/**
	 * Computes the entropy of a change set
	 *
	 * @param changeSetSummary a map containing an entry for each file in the change set
	 *                         entries are of the form: [path -> number of changed lines]
	 * @return the entropy
	 */
	public double computeEntropyOfChangeSet(Map<String, Integer> changeSetSummary) {
		final int numberOfChangedLinesInChangeSet = changeSetSummary.values().stream().reduce(0, Integer::sum);
		double entropy = 0.0;
		for (Map.Entry<String, Integer> entry : changeSetSummary.entrySet()) {
			final int numberOfChangedLinesInFile = entry.getValue();
			if (numberOfChangedLinesInFile < 1) {
				// file has no changed lines, so exclude from computation
				continue;
			}
			final double proportionOfChangedLinesInFile = (double) numberOfChangedLinesInFile / numberOfChangedLinesInChangeSet;
			// calculate log[2](proportionOfChangedLinesInFile) indirectly using the log change of base formula
			entropy -= proportionOfChangedLinesInFile * Math.log10(proportionOfChangedLinesInFile) / Math.log10(2);
		}
		return entropy;
	}

	/**
	 * Computes the entropy of a file in a change set
	 *
	 * @param changeSetSummary a map containing an entry for each file in the change set
	 *                         entries are of the form: [path -> number of changed lines]
	 * @param filePath         the file path
	 * @return the entropy
	 */
	public double computeEntropyOfFileInChangeSet(Map<String, Integer> changeSetSummary, String filePath) {
		if (!changeSetSummary.containsKey(filePath)) {
			// file not part of change set, so its entropy is 0
			return 0.0;
		}
		final double entropyOfChangeSet = computeEntropyOfChangeSet(changeSetSummary);
		final int numberOfChangedLinesInChangeSet = changeSetSummary.values().stream().reduce(0, Integer::sum);
		final int numberOfChangedLinesInFile = changeSetSummary.get(filePath);
		final double proportionOfChangedLinesInFile = (double) numberOfChangedLinesInFile / numberOfChangedLinesInChangeSet;
		return entropyOfChangeSet * proportionOfChangedLinesInFile;
	}
}
