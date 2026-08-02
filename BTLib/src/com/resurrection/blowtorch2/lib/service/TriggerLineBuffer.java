package com.resurrection.blowtorch2.lib.service;

/**
 * Holds back trailing bytes that do not yet end in a newline so trigger matching
 * always sees complete logical lines.
 *
 * <p>{@link Connection#dispatch} feeds {@code mReader.available()} chunks into
 * both {@code TextTree} (which reassembles across reads) and the trigger matcher
 * (which historically did not). A chat colour trigger like
 * {@code \[chatnet\] (.+)} on a partial chunk matches a short span, then
 * {@code ColorAction} splices the pre-match "bleed" colour back in mid-sentence
 * — so the rest of the line paints cyan (or whatever the timestamp used) until
 * a real newline arrives. Holding the incomplete strip until the next
 * {@code \n} keeps TextTree's live display and makes the matcher agree with it.
 */
public final class TriggerLineBuffer {

	private TriggerLineBuffer() {
	}

	/** Complete lines to match now, plus the remainder to keep for later. */
	public static final class Slice {
		/** Zero or more lines, each ending with {@code \n}; may be empty. */
		public final String ready;
		/** Text after the last {@code \n}, or the whole input when none yet. */
		public final String holdover;

		Slice(final String ready, final String holdover) {
			this.ready = ready;
			this.holdover = holdover;
		}
	}

	/**
	 * @param previousHoldover incomplete strip from the previous chunk (may be
	 *        {@code null} or empty)
	 * @param strippedChunk    ANSI-stripped text from this network read (may be
	 *        {@code null})
	 */
	public static Slice take(final String previousHoldover, final String strippedChunk) {
		String left = previousHoldover == null ? "" : previousHoldover;
		String right = strippedChunk == null ? "" : strippedChunk;
		String combined = left.length() == 0 ? right
				: (right.length() == 0 ? left : left + right);
		int lastNl = combined.lastIndexOf('\n');
		if (lastNl < 0) {
			return new Slice("", combined);
		}
		return new Slice(
				combined.substring(0, lastNl + 1),
				combined.substring(lastNl + 1));
	}
}
