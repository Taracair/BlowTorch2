package com.resurrection.blowtorch2.lib.service;

import java.util.LinkedList;

import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.window.TextTree.NewLine;
import com.resurrection.blowtorch2.lib.window.TextTree.Unit;

/**
 * Closes the colour a colour trigger left running.
 *
 * <p>A colour trigger paints by putting a colour code into the stream, and a
 * code runs until the next one. When the match reaches the end of the text that
 * has arrived, {@code ColorAction} deliberately leaves its colour open: the rest
 * of that line can still be in the next TCP packet, and it belongs to the match.
 * The line's end is where that stops being true.
 *
 * <p>The line is not always finished in the same dispatch that coloured it —
 * that is the whole point — and the working buffer is emptied after each one, so
 * "a colour is still open" is state of the connection rather than of a buffer.
 * One of these per connection.
 */
public final class TriggerColorState {

	/** A trigger colour is running and has not been closed yet. */
	private boolean open;
	/**
	 * Pre-match ops from the line that left the colour running. Kept for the
	 * bleed probe; {@link #close} writes a full reset, not this colour, or
	 * that CSI would run into the next uncoloured line.
	 */
	private java.util.List<Integer> restoreOps;

	/**
	 * Close the trigger colour at the end of every line it was left running
	 * on, so the next uncoloured line stays default grey. Lines are walked
	 * oldest first, the order they were received in and the order
	 * {@code dumpToBytes} writes them.
	 *
	 * @param tree the buffer about to be dumped into the stream.
	 */
	public void closeAtLineEnds(final TextTree tree) {
		LinkedList<Line> lines = tree.getLines();
		ColourBleedProbe probe = ColourBleedProbe.bound();
		// The buffer runs newest first.
		for (int i = lines.size() - 1; i >= 0; i--) {
			Line line = lines.get(i);
			boolean leftoverOpen = open;
			if (line.isTriggerColorOpen()) {
				open = true;
				java.util.List<Integer> fromLine = line.getTriggerColorRestore();
				if (fromLine != null) {
					restoreOps = fromLine;
				}
				line.setTriggerColorOpen(false);
			}
			if (!open) {
				continue;
			}
			boolean closed = close(tree, line);
			if (probe != null) {
				probe.recordClose(line, restoreOps, closed, leftoverOpen);
			}
			if (closed) {
				open = false;
				restoreOps = null;
			}
		}
	}

	/** Forget any open colour — a new connection starts a new stream. */
	public void reset() {
		open = false;
		restoreOps = null;
	}

	/**
	 * @return true when the line was finished and the colour has been closed on
	 *         it; false when the line is still open, and the colour with it.
	 */
	private boolean close(final TextTree tree, final Line line) {
		LinkedList<Unit> data = line.getData();
		if (data.isEmpty() || !(data.getLast() instanceof NewLine)) {
			return false;
		}
		// Close with a full reset, not the pre-match colour. Putting that
		// colour back is what left a non-grey CSI running into the next
		// uncoloured line. The continuation of an unfinished match has
		// already inherited the trigger paint; this only has to stop it.
		TextTree.Color color = tree.makeColor(java.util.Collections.singletonList(
				Integer.valueOf(0)));
		// Before the newline: every reader of this buffer asks whether the last
		// unit is a NewLine to decide whether the line is finished.
		data.add(data.size() - 1, color);
		return true;
	}
}
