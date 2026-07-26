/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.window;

import java.util.HashMap;
import java.util.Map;

/** Commands bound to swipes and holds on the chrome around the game view.
 *
 * The chrome is the Edit, Send and overflow buttons. They keep their normal tap
 * behaviour; these are extra bindings on gestures that were doing nothing.
 *
 * The input bar is deliberately not among them: dragging there selects text and
 * moves the caret, and a swipe binding would be fighting the editing gestures.
 *
 * Stored as one setting rather than one per binding, because a plugin option each
 * would be unreadable. The format is
 * {@code target.gesture=command} joined by newlines; commands may not contain a
 * newline, which the editor enforces.
 *
 * Overflow deliberately has no hold binding: long-pressing it opens the button
 * editor, and that must stay.
 */
public final class ChromeGestures {

	/** Setting key inside the button_window plugin. */
	public static final String SETTING_KEY = "chrome_gestures";

	/** Plugin that owns the setting. */
	public static final String SETTING_PLUGIN = "button_window";

	/** Edit button target name. */
	public static final String TARGET_EDIT = "edit";

	/** Send button target name. */
	public static final String TARGET_SEND = "send";

	/** Overflow (three dots) target name. */
	public static final String TARGET_OVERFLOW = "overflow";

	/** Targets in the order the editor lists them. */
	public static final String[] TARGETS = {
		TARGET_EDIT, TARGET_SEND, TARGET_OVERFLOW,
	};

	/** Gestures every target supports. */
	public static final String[] SWIPES = { "up", "down", "left", "right" };

	/** Hold, which every target but the overflow supports. */
	public static final String GESTURE_HOLD = "hold";

	private final Map<String, String> bindings = new HashMap<String, String>();

	/** Bindings the button plugin last published, shared with the touch
	 * listeners. The plugin's Lua runs in this process, so it hands the value
	 * over directly rather than going back out through the service; the settings
	 * option this mirrors is only readable once the plugin has parsed it. */
	private static volatile ChromeGestures sCurrent = new ChromeGestures();

	/** Called from the button window Lua when the plugin's options load. */
	public static void publish(final String stored) {
		sCurrent = parse(stored);
	}

	/** The bindings in effect right now. */
	public static ChromeGestures current() {
		return sCurrent;
	}

	/** Whether this target can carry a hold binding. */
	public static boolean supportsHold(final String target) {
		return !TARGET_OVERFLOW.equals(target);
	}

	/** Human label for a target, for the editor and for help text. */
	public static String labelFor(final String target) {
		if (TARGET_EDIT.equals(target)) {
			return "Edit button";
		}
		if (TARGET_SEND.equals(target)) {
			return "Send button";
		}
		if (TARGET_OVERFLOW.equals(target)) {
			return "Overflow (⋮)";
		}
		return target;
	}

	/** Parse the stored form. Unknown or malformed entries are skipped rather
	 * than failing the whole set, so one bad line cannot cost every binding. */
	public static ChromeGestures parse(final String stored) {
		ChromeGestures out = new ChromeGestures();
		if (stored == null || stored.length() == 0) {
			return out;
		}
		String[] lines = stored.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.length() == 0) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0 || eq == line.length() - 1) {
				continue;
			}
			String key = line.substring(0, eq).trim();
			String command = line.substring(eq + 1).trim();
			if (command.length() == 0) {
				continue;
			}
			int dot = key.indexOf('.');
			if (dot <= 0 || dot == key.length() - 1) {
				continue;
			}
			out.put(key.substring(0, dot), key.substring(dot + 1), command);
		}
		return out;
	}

	/** Render back to the stored form. */
	public String format() {
		StringBuilder sb = new StringBuilder();
		for (int t = 0; t < TARGETS.length; t++) {
			String target = TARGETS[t];
			for (int g = 0; g < SWIPES.length; g++) {
				appendIfSet(sb, target, SWIPES[g]);
			}
			if (supportsHold(target)) {
				appendIfSet(sb, target, GESTURE_HOLD);
			}
		}
		return sb.toString();
	}

	private void appendIfSet(final StringBuilder sb, final String target, final String gesture) {
		String cmd = get(target, gesture);
		if (cmd == null) {
			return;
		}
		sb.append(target).append('.').append(gesture).append('=').append(cmd).append('\n');
	}

	/** Bind a command, or clear the binding when the command is empty. */
	public void put(final String target, final String gesture, final String command) {
		if (target == null || gesture == null) {
			return;
		}
		if (TARGET_OVERFLOW.equals(target) && GESTURE_HOLD.equals(gesture)) {
			// Long-press on the overflow opens the button editor and stays that way.
			return;
		}
		String key = target + "." + gesture;
		if (command == null || command.trim().length() == 0) {
			bindings.remove(key);
			return;
		}
		bindings.put(key, command.trim().replace("\n", " "));
	}

	/** The command bound to this gesture, or null. */
	public String get(final String target, final String gesture) {
		if (target == null || gesture == null) {
			return null;
		}
		return bindings.get(target + "." + gesture);
	}

	/** Whether this target has any binding at all, so listeners can be skipped. */
	public boolean hasAny(final String target) {
		for (int g = 0; g < SWIPES.length; g++) {
			if (get(target, SWIPES[g]) != null) {
				return true;
			}
		}
		return supportsHold(target) && get(target, GESTURE_HOLD) != null;
	}

	/** Whether anything is bound anywhere. */
	public boolean isEmpty() {
		return bindings.isEmpty();
	}
}
