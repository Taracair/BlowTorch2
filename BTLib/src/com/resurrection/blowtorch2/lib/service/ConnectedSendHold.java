package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lines {@code SendToServer} produced while {@code OnConnected} was running.
 * {@link #take()} returns them in call order and leaves the hold empty.
 */
final class ConnectedSendHold {

	private final ArrayList<String> lines = new ArrayList<String>();

	void add(final String line) {
		if (line != null) {
			lines.add(line);
		}
	}

	void clear() {
		lines.clear();
	}

	List<String> take() {
		if (lines.isEmpty()) {
			return Collections.emptyList();
		}
		ArrayList<String> out = new ArrayList<String>(lines);
		lines.clear();
		return out;
	}
}
