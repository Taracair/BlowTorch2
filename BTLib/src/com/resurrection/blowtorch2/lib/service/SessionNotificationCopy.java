package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Copy for the one ongoing session notification when several worlds are open.
 * Android-free so the shade layout can be tested without a Service.
 */
public final class SessionNotificationCopy {

	/** Android shows at most three {@code addAction} buttons. */
	public static final int MAX_ACTIONS = 3;

	public static final class World {
		public final String display;
		public final String status;
		public final boolean current;

		public World(final String display, final String status, final boolean current) {
			this.display = display == null ? "" : display;
			this.status = status == null ? "" : status;
			this.current = current;
		}
	}

	public static final class Model {
		public final int count;
		public final String tapDisplay;
		public final String collapsed;
		public final List<String> inboxLines;
		public final List<String> actionDisplays;

		Model(final int count, final String tapDisplay, final String collapsed,
				final List<String> inboxLines, final List<String> actionDisplays) {
			this.count = count;
			this.tapDisplay = tapDisplay == null ? "" : tapDisplay;
			this.collapsed = collapsed == null ? "" : collapsed;
			this.inboxLines = Collections.unmodifiableList(inboxLines);
			this.actionDisplays = Collections.unmodifiableList(actionDisplays);
		}

		public boolean expand() {
			return count > 1;
		}
	}

	private SessionNotificationCopy() {
	}

	public static Model build(final List<World> worlds) {
		if (worlds == null || worlds.isEmpty()) {
			return new Model(0, "", "", new ArrayList<String>(), new ArrayList<String>());
		}
		ArrayList<World> ordered = new ArrayList<World>(worlds);
		Collections.sort(ordered, ORDER);
		String tap = ordered.get(0).display;
		if (ordered.size() == 1) {
			World only = ordered.get(0);
			return new Model(1, tap, only.status, new ArrayList<String>(),
					new ArrayList<String>());
		}
		StringBuilder collapsed = new StringBuilder();
		ArrayList<String> inbox = new ArrayList<String>();
		ArrayList<String> actions = new ArrayList<String>();
		for (int i = 0; i < ordered.size(); i++) {
			World w = ordered.get(i);
			if (w.display.length() == 0) {
				continue;
			}
			if (collapsed.length() > 0) {
				collapsed.append(" · ");
			}
			collapsed.append(w.display);
			if (w.current) {
				inbox.add("▸ " + w.display + " · " + w.status);
			} else {
				inbox.add(w.display + " · " + w.status);
			}
			if (actions.size() < MAX_ACTIONS) {
				actions.add(w.display);
			}
		}
		return new Model(ordered.size(), tap, collapsed.toString(), inbox, actions);
	}

	private static final Comparator<World> ORDER = new Comparator<World>() {
		@Override
		public int compare(final World a, final World b) {
			if (a.current != b.current) {
				return a.current ? -1 : 1;
			}
			return a.display.compareToIgnoreCase(b.display);
		}
	};
}
