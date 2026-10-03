package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Letters from successive shakes, kept for {@link #WINDOW_MILLIS}. A wanted
 * {@code pat:} id completes when the buffer ends with that sequence.
 */
public final class ShakeSequence {

	public static final long WINDOW_MILLIS = 2500L;

	private final StringBuilder have = new StringBuilder();
	private long lastAt;

	public synchronized void reset() {
		have.setLength(0);
		lastAt = 0L;
	}

	public synchronized List<String> push(final String letter, final long now,
			final Collection<String> wanted) {
		if (letter == null || letter.length() != 1) {
			return Collections.emptyList();
		}
		if (lastAt > 0L && now - lastAt > WINDOW_MILLIS) {
			have.setLength(0);
		}
		lastAt = now;
		have.append(letter);
		if (have.length() > 8) {
			have.delete(0, have.length() - 8);
		}
		String text = have.toString();
		ArrayList<String> hit = new ArrayList<String>();
		if (wanted != null) {
			for (String id : wanted) {
				if (id == null || !id.startsWith("pat:")) {
					continue;
				}
				String seq = id.substring(4);
				if (seq.length() >= 2 && text.endsWith(seq)) {
					hit.add(id);
				}
			}
		}
		if (!hit.isEmpty()) {
			have.setLength(0);
			lastAt = 0L;
		}
		return hit;
	}
}
