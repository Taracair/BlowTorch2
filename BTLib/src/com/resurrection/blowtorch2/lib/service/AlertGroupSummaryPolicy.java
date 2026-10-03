package com.resurrection.blowtorch2.lib.service;

/**
 * Whether the alert-group header belongs in the shade.
 * Android-free so the empty-group cancel can be tested without a Service.
 */
public final class AlertGroupSummaryPolicy {

	private AlertGroupSummaryPolicy() {
	}

	/** Post the summary only when at least one alert child is still posted. */
	public static boolean shouldPostSummary(final int alertChildCount) {
		return alertChildCount > 0;
	}

	/**
	 * Count notifications that belong in {@code groupKey}, skipping the summary
	 * id. Parallel {@code ids} / {@code groupKeys} come from active shade entries;
	 * a null group key means ungrouped.
	 */
	public static int countAlertChildren(final int[] ids, final String[] groupKeys,
			final int summaryId, final String groupKey) {
		return countAlertChildren(ids, groupKeys, summaryId, summaryId, groupKey);
	}

	/**
	 * Same as {@link #countAlertChildren(int[], String[], int, String)} but also
	 * skips a second summary id (chat bar header).
	 */
	public static int countAlertChildren(final int[] ids, final String[] groupKeys,
			final int summaryId, final int extraSummaryId, final String groupKey) {
		if (ids == null || groupKeys == null || groupKey == null) {
			return 0;
		}
		int n = Math.min(ids.length, groupKeys.length);
		int count = 0;
		for (int i = 0; i < n; i++) {
			if (ids[i] == summaryId || ids[i] == extraSummaryId) {
				continue;
			}
			if (groupKey.equals(groupKeys[i])) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Connection and alert-summary taps open the session when one exists; idle
	 * with no session may still open the launcher.
	 */
	public static boolean useSessionTap(final boolean sessionExists) {
		return sessionExists;
	}
}
