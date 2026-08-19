package com.resurrection.blowtorch2.lib.service;

import java.util.LinkedList;
import java.util.List;

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
	 * Colour to restore when {@link #open} is closed. Taken from the line that
	 * left the colour running, not from the continuation's server snapshot —
	 * that snapshot is the trigger colour once the window has absorbed the
	 * unfinished dump.
	 */
	private java.util.List<Integer> restoreOps;

	/**
	 * Put the server's colour back at the end of every line a trigger left its
	 * colour running on. Lines are walked oldest first, the order they were
	 * received in and the order {@code dumpToBytes} writes them.
	 *
	 * @param tree the buffer about to be dumped into the stream.
	 */
	public void closeAtLineEnds(final TextTree tree) {
		LinkedList<Line> lines = tree.getLines();
		// The buffer runs newest first.
		for (int i = lines.size() - 1; i >= 0; i--) {
			Line line = lines.get(i);
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
			if (close(tree, line)) {
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
		List<Integer> restore = restoreOps != null
				? restoreOps : line.getServerColorAtEnd();
		// Lines built by paths that do not parse colour carry no snapshot; the
		// stream's current colour is the best that is known there.
		TextTree.Color color = restore != null
				? tree.makeRestoreColor(restore) : tree.getBleedColor();
		// Before the newline: every reader of this buffer asks whether the last
		// unit is a NewLine to decide whether the line is finished.
		data.add(data.size() - 1, color);
		return true;
	}
}
