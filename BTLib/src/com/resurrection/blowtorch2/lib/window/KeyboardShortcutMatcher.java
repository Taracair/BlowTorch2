package com.resurrection.blowtorch2.lib.window;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a hardware key is a shortcut. The window posts
 * {@link #HOLD_DELAY_MS} and {@link #REPEAT_MS}; this class does not wait.
 */
public final class KeyboardShortcutMatcher {

	public static final int ACTION_DOWN = 0;
	public static final int ACTION_UP = 1;
	public static final int HOOK_PRE_IME = 1;
	public static final int HOOK_ACTIVITY = 2;

	/** Platform KeyEvent.FLAG_FALLBACK. The measured twin was 0x408. */
	public static final int FLAG_FALLBACK = 0x400;

	/** buttonwindow.lua HOLD_DELAY_MS. */
	public static final int HOLD_DELAY_MS = 450;
	public static final int REPEAT_MS = 200;

	/** Injected keys use device id -1 and scan 0. FLAG_SOFT_KEYBOARD was 0 on those too. */
	public static final int VIRTUAL_DEVICE_ID = -1;

	private static final int KEY_ALT_LEFT = 57;
	private static final int KEY_ALT_RIGHT = 58;
	private static final int KEY_SHIFT_LEFT = 59;
	private static final int KEY_SHIFT_RIGHT = 60;
	private static final int KEY_CTRL_LEFT = 113;
	private static final int KEY_CTRL_RIGHT = 114;
	private static final int KEY_META_LEFT = 117;
	private static final int KEY_META_RIGHT = 118;

	private KeyboardShortcuts shortcuts = KeyboardShortcuts.empty();
	private boolean typingForced;
	private boolean walkingSuspended;
	private boolean dialogOpen;
	private boolean hardwareKeyboard;
	private boolean latchOn;

	private String lastWalk;
	private final Map<String, String> reverses = new HashMap<String, String>();

	private int timerToken;
	private KeyboardShortcuts.Row active;
	private boolean holdPending;
	private boolean holdFired;
	private boolean repeating;
	private boolean suppressRelease;

	private boolean pressDown;
	private boolean pressConsumed;
	private int pressDevice;
	private int pressScan;
	private int pressKey;

	private boolean scanMarked;
	private int markedDevice;
	private int markedScan;

	private boolean haveLast;
	private boolean paired;
	private int lastDevice;
	private int lastScan;
	private int lastKey;
	private int lastAction;
	private int lastRepeat;
	private int lastHook;
	private boolean lastConsumed;

	public void setShortcuts(final KeyboardShortcuts next) {
		boolean wasOn = shortcuts.isOn();
		shortcuts = next == null ? KeyboardShortcuts.empty() : next;
		if (wasOn) {
			stopTimers();
		}
	}

	public void setTypingForced(final boolean forced) {
		if (forced && !typingForced) {
			stopTimers();
		}
		typingForced = forced;
	}

	public void setWalkingSuspended(final boolean suspended) {
		if (suspended && !walkingSuspended) {
			stopTimers();
		}
		walkingSuspended = suspended;
	}

	public void setDialogOpen(final boolean open) {
		if (open && !dialogOpen) {
			stopTimers();
		}
		dialogOpen = open;
	}

	public void setHardwareKeyboard(final boolean present) {
		hardwareKeyboard = present;
	}

	public void setLatch(final boolean on) {
		if (!on && latchOn) {
			stopTimers();
		}
		latchOn = on;
	}

	public boolean isLatchOn() {
		return latchOn;
	}

	public boolean isTypingForced() {
		return typingForced;
	}

	/** Walk while the latch is on and the master is on. Typing while a hardware keyboard is present and the master is off. */
	public String mark() {
		if (shortcuts.isOn() && latchOn) {
			return "Walk";
		}
		if (!shortcuts.isOn() && hardwareKeyboard) {
			return "Typing";
		}
		return null;
	}

	/** A single-word rose walk. A command with a space is not a step. */
	public void noteRoseWalk(final String command) {
		if (command == null) {
			return;
		}
		String trimmed = command.trim();
		if (trimmed.length() == 0 || trimmed.indexOf(' ') >= 0) {
			return;
		}
		lastWalk = trimmed;
	}

	/** Look-layer sends do not count as a step. */
	public void noteLook(final String command) {
	}

	public void setReverse(final String command, final String reverse) {
		if (command == null) {
			return;
		}
		String key = command.trim();
		if (key.length() == 0) {
			return;
		}
		if (reverse == null || reverse.trim().length() == 0) {
			reverses.remove(key);
			return;
		}
		reverses.put(key, reverse.trim());
	}

	public Outcome onKey(final Key key) {
		if (key == null) {
			return Outcome.pass();
		}
		if (isDuplicate(key)) {
			paired = true;
			return lastConsumed ? Outcome.taken() : Outcome.pass();
		}
		if (isFallback(key)) {
			if (marked(key)) {
				if (key.action == ACTION_UP) {
					clearScan();
				}
				return finish(key, Outcome.taken());
			}
			return finish(key, Outcome.pass());
		}
		if (key.action == ACTION_UP && pressDown && pressConsumed && samePress(key)) {
			return finish(key, release());
		}
		if (key.action == ACTION_UP && modifierBit(key.keyCode) != 0
				&& active != null && (active.modifiers & modifierBit(key.keyCode)) != 0
				&& !(pressDown && samePress(key))) {
			stopTimers();
			return finish(key, Outcome.pass());
		}
		if (!hardware(key) || blocked()) {
			if (key.action == ACTION_DOWN && marked(key)) {
				clearScan();
			}
			return finish(key, Outcome.pass());
		}
		if (key.action == ACTION_DOWN && key.repeatCount > 0 && pressDown && pressConsumed
				&& samePress(key)) {
			return finish(key, Outcome.taken());
		}
		if (key.action != ACTION_DOWN || key.repeatCount > 0) {
			return finish(key, Outcome.pass());
		}
		KeyboardShortcuts.Row row = shortcuts.isOn()
				? shortcuts.find(key.keyCode, key.metaState) : null;
		if (row == null) {
			if (marked(key)) {
				clearScan();
			}
			return finish(key, Outcome.pass());
		}
		return finish(key, startPress(key, row));
	}

	public Outcome onHoldFired(final int token) {
		if (token != timerToken || !holdPending || holdFired) {
			return Outcome.pass();
		}
		holdPending = false;
		holdFired = true;
		KeyboardShortcuts.Row row = active;
		if (row == null || row.hold.length() == 0) {
			return Outcome.pass();
		}
		return Outcome.send(row.hold);
	}

	public Outcome onRepeatFired(final int token) {
		if (token != timerToken || !repeating || active == null) {
			return Outcome.pass();
		}
		return fire(active, 0, REPEAT_MS, timerToken);
	}

	private Outcome startPress(final Key key, final KeyboardShortcuts.Row row) {
		pressDown = true;
		pressConsumed = true;
		pressDevice = key.deviceId;
		pressScan = key.scanCode;
		pressKey = key.keyCode;
		suppressRelease = false;
		markScan(key);
		active = row;
		timerToken++;
		if (row.repeat) {
			holdPending = false;
			holdFired = false;
			repeating = true;
			return fire(row, 0, REPEAT_MS, timerToken);
		}
		if (row.hold.length() > 0) {
			holdPending = true;
			holdFired = false;
			repeating = false;
			return Outcome.schedule(HOLD_DELAY_MS, 0, timerToken);
		}
		holdPending = false;
		holdFired = true;
		repeating = false;
		return fire(row, 0, 0, 0);
	}

	private Outcome release() {
		KeyboardShortcuts.Row row = active;
		boolean sendTap = holdPending && !holdFired && !suppressRelease;
		pressDown = false;
		pressConsumed = false;
		timerToken++;
		holdPending = false;
		repeating = false;
		suppressRelease = false;
		active = null;
		if (!sendTap || row == null) {
			return Outcome.taken();
		}
		return fire(row, 0, 0, 0);
	}

	private Outcome fire(final KeyboardShortcuts.Row row, final int holdMs, final int repeatMs,
			final int token) {
		if (KeyboardShortcuts.ACTION_INPUT.equals(row.action)) {
			return Outcome.input(holdMs, repeatMs, token);
		}
		if (KeyboardShortcuts.ACTION_STEP.equals(row.action)) {
			return Outcome.send(resolveStep(), holdMs, repeatMs, token);
		}
		return Outcome.send(row.tap, holdMs, repeatMs, token);
	}

	private String resolveStep() {
		if (lastWalk == null) {
			return null;
		}
		return reverses.get(lastWalk);
	}

	private void stopTimers() {
		timerToken++;
		holdPending = false;
		repeating = false;
		if (pressDown && pressConsumed) {
			suppressRelease = true;
		}
	}

	private boolean blocked() {
		return dialogOpen || typingForced || !shortcuts.isOn();
	}

	private static boolean hardware(final Key key) {
		return key.deviceId != VIRTUAL_DEVICE_ID && key.scanCode != 0;
	}

	private static boolean isFallback(final Key key) {
		return (key.flags & FLAG_FALLBACK) != 0 && key.scanCode != 0
				&& key.deviceId != VIRTUAL_DEVICE_ID;
	}

	private void markScan(final Key key) {
		if (key.scanCode == 0 || key.deviceId == VIRTUAL_DEVICE_ID) {
			return;
		}
		scanMarked = true;
		markedDevice = key.deviceId;
		markedScan = key.scanCode;
	}

	private boolean marked(final Key key) {
		return scanMarked && key.deviceId == markedDevice && key.scanCode == markedScan;
	}

	private void clearScan() {
		scanMarked = false;
	}

	private boolean samePress(final Key key) {
		return key.deviceId == pressDevice && key.scanCode == pressScan && key.keyCode == pressKey;
	}

	private boolean isDuplicate(final Key key) {
		return haveLast && !paired
				&& key.deviceId == lastDevice && key.scanCode == lastScan
				&& key.keyCode == lastKey && key.action == lastAction
				&& key.repeatCount == lastRepeat && key.hook != lastHook;
	}

	private Outcome finish(final Key key, final Outcome outcome) {
		lastDevice = key.deviceId;
		lastScan = key.scanCode;
		lastKey = key.keyCode;
		lastAction = key.action;
		lastRepeat = key.repeatCount;
		lastHook = key.hook;
		lastConsumed = outcome.consumed;
		paired = false;
		haveLast = true;
		return outcome;
	}

	private static int modifierBit(final int keyCode) {
		if (keyCode == KEY_ALT_LEFT || keyCode == KEY_ALT_RIGHT) {
			return KeyboardShortcuts.MOD_ALT;
		}
		if (keyCode == KEY_CTRL_LEFT || keyCode == KEY_CTRL_RIGHT) {
			return KeyboardShortcuts.MOD_CTRL;
		}
		if (keyCode == KEY_SHIFT_LEFT || keyCode == KEY_SHIFT_RIGHT) {
			return KeyboardShortcuts.MOD_SHIFT;
		}
		if (keyCode == KEY_META_LEFT || keyCode == KEY_META_RIGHT) {
			return KeyboardShortcuts.MOD_META;
		}
		return 0;
	}

	public static final class Key {
		public final int action;
		public final int keyCode;
		public final int metaState;
		public final int plainUnicode;
		public final int metaUnicode;
		public final int scanCode;
		public final int flags;
		public final int repeatCount;
		public final int deviceId;
		public final int hook;

		public Key(final int action, final int keyCode, final int metaState,
				final int plainUnicode, final int metaUnicode, final int scanCode,
				final int flags, final int repeatCount, final int deviceId, final int hook) {
			this.action = action;
			this.keyCode = keyCode;
			this.metaState = metaState;
			this.plainUnicode = plainUnicode;
			this.metaUnicode = metaUnicode;
			this.scanCode = scanCode;
			this.flags = flags;
			this.repeatCount = repeatCount;
			this.deviceId = deviceId;
			this.hook = hook;
		}
	}

	public static final class Outcome {
		public final boolean consumed;
		public final String send;
		public final boolean sendInput;
		public final int scheduleHoldMs;
		public final int scheduleRepeatMs;
		public final int timerToken;

		private Outcome(final boolean consumed, final String send, final boolean sendInput,
				final int scheduleHoldMs, final int scheduleRepeatMs, final int timerToken) {
			this.consumed = consumed;
			this.send = send;
			this.sendInput = sendInput;
			this.scheduleHoldMs = scheduleHoldMs;
			this.scheduleRepeatMs = scheduleRepeatMs;
			this.timerToken = timerToken;
		}

		static Outcome pass() {
			return new Outcome(false, null, false, 0, 0, 0);
		}

		static Outcome taken() {
			return new Outcome(true, null, false, 0, 0, 0);
		}

		static Outcome schedule(final int holdMs, final int repeatMs, final int token) {
			return new Outcome(true, null, false, holdMs, repeatMs, token);
		}

		static Outcome send(final String line) {
			return send(line, 0, 0, 0);
		}

		static Outcome send(final String line, final int holdMs, final int repeatMs, final int token) {
			if (line == null || line.length() == 0) {
				return new Outcome(true, null, false, holdMs, repeatMs, token);
			}
			return new Outcome(true, line, false, holdMs, repeatMs, token);
		}

		static Outcome input(final int holdMs, final int repeatMs, final int token) {
			return new Outcome(true, null, true, holdMs, repeatMs, token);
		}
	}
}
