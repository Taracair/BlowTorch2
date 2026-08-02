package com.resurrection.blowtorch2.lib.window;

import org.json.JSONException;
import org.json.JSONObject;

import com.resurrection.blowtorch2.lib.window.SuperButtonGestures.BoundSwipes;

/**
 * Snapshot of one floating button pushed from UI Lua ({@code buttonwindow})
 * into {@link FloatingButtonController}. Positions live on the button data
 * ({@code floatX}/{@code floatY}); identity is the 1-based Lua index in the
 * active set — not a renameable name (ORCHESTRATION trap: do not key state
 * on a name that can change).
 */
public final class FloatingButtonModel {

	public static final String MODE_ALWAYS = "always";
	public static final String MODE_KEYBOARD = "keyboard";

	/** 1-based index in the active Lua {@code buttons} table. */
	public final int index;
	public final String label;
	public final String command;
	public final String flipLabel;
	public final String flipCommand;
	public final String holdCommand;
	public final String switchTo;
	public final String swipeUpCommand;
	public final String swipeDownCommand;
	public final String swipeLeftCommand;
	public final String swipeRightCommand;
	public final String swipeUpLeftCommand;
	public final String swipeUpRightCommand;
	public final String swipeDownLeftCommand;
	public final String swipeDownRightCommand;
	public final boolean showGestureLabel;
	public final String floatMode;
	public final int floatX;
	public final int floatY;
	public final boolean floatRound;
	public final boolean floatFrame;
	/**
	 * Grid centre from Lua {@code data.x}/{@code data.y} — used only while
	 * {@code floatX}/{@code floatY} are still {@link FloatingLayerGeometry#UNPLACED}.
	 */
	public final float gridX;
	public final float gridY;
	/** Same {@code statusoffset} Lua adds in {@code BUTTON:updateRect}. */
	public final int statusOffsetPx;
	public final boolean hasGridOrigin;
	public final float widthDp;
	public final float heightDp;
	public final float labelSizeSp;
	public final int primaryColor;
	public final int labelColor;
	public final int selectedColor;
	public final int flipColor;
	public final int flipLabelColor;

	public FloatingButtonModel(JSONObject o) throws JSONException {
		index = o.getInt("index");
		label = o.optString("label", "");
		command = o.optString("command", "");
		flipLabel = o.optString("flipLabel", "");
		flipCommand = o.optString("flipCommand", "");
		holdCommand = o.optString("holdCommand", "");
		switchTo = o.optString("switchTo", "");
		swipeUpCommand = o.optString("swipeUpCommand", "");
		swipeDownCommand = o.optString("swipeDownCommand", "");
		swipeLeftCommand = o.optString("swipeLeftCommand", "");
		swipeRightCommand = o.optString("swipeRightCommand", "");
		swipeUpLeftCommand = o.optString("swipeUpLeftCommand", "");
		swipeUpRightCommand = o.optString("swipeUpRightCommand", "");
		swipeDownLeftCommand = o.optString("swipeDownLeftCommand", "");
		swipeDownRightCommand = o.optString("swipeDownRightCommand", "");
		showGestureLabel = o.optBoolean("showGestureLabel", true);
		String mode = o.optString("floatMode", MODE_ALWAYS);
		floatMode = MODE_KEYBOARD.equals(mode) ? MODE_KEYBOARD : MODE_ALWAYS;
		floatX = o.optInt("floatX", FloatingLayerGeometry.UNPLACED);
		floatY = o.optInt("floatY", FloatingLayerGeometry.UNPLACED);
		floatRound = o.optBoolean("floatRound", false);
		floatFrame = o.optBoolean("floatFrame", false);
		hasGridOrigin = o.has("gridX") && o.has("gridY");
		gridX = (float) o.optDouble("gridX", 0);
		gridY = (float) o.optDouble("gridY", 0);
		statusOffsetPx = o.optInt("statusOffset", 0);
		widthDp = (float) o.optDouble("width", 80);
		heightDp = (float) o.optDouble("height", 80);
		labelSizeSp = (float) o.optDouble("labelSize", 23);
		primaryColor = (int) o.optLong("primaryColor", 0x880000FFL);
		labelColor = (int) o.optLong("labelColor", 0xAAAAAAAA);
		selectedColor = (int) o.optLong("selectedColor", 0x8800FF00L);
		flipColor = (int) o.optLong("flipColor", 0x88FF0000L);
		flipLabelColor = (int) o.optLong("flipLabelColor", 0x880000FFL);
	}

	private FloatingButtonModel(FloatingButtonModel src, int newFloatX, int newFloatY) {
		index = src.index;
		label = src.label;
		command = src.command;
		flipLabel = src.flipLabel;
		flipCommand = src.flipCommand;
		holdCommand = src.holdCommand;
		switchTo = src.switchTo;
		swipeUpCommand = src.swipeUpCommand;
		swipeDownCommand = src.swipeDownCommand;
		swipeLeftCommand = src.swipeLeftCommand;
		swipeRightCommand = src.swipeRightCommand;
		swipeUpLeftCommand = src.swipeUpLeftCommand;
		swipeUpRightCommand = src.swipeUpRightCommand;
		swipeDownLeftCommand = src.swipeDownLeftCommand;
		swipeDownRightCommand = src.swipeDownRightCommand;
		showGestureLabel = src.showGestureLabel;
		floatMode = src.floatMode;
		floatX = newFloatX;
		floatY = newFloatY;
		floatRound = src.floatRound;
		floatFrame = src.floatFrame;
		hasGridOrigin = src.hasGridOrigin;
		gridX = src.gridX;
		gridY = src.gridY;
		statusOffsetPx = src.statusOffsetPx;
		widthDp = src.widthDp;
		heightDp = src.heightDp;
		labelSizeSp = src.labelSizeSp;
		primaryColor = src.primaryColor;
		labelColor = src.labelColor;
		selectedColor = src.selectedColor;
		flipColor = src.flipColor;
		flipLabelColor = src.flipLabelColor;
	}

	/**
	 * Same button with a new float position. Used when a drag drop updates the
	 * live overlay window but the cached Lua snapshot still has the old
	 * {@code floatX}/{@code floatY} — an IME rebuild from that cache would snap
	 * the button back.
	 */
	FloatingButtonModel withFloatPosition(final int newFloatX, final int newFloatY) {
		if (newFloatX == floatX && newFloatY == floatY) {
			return this;
		}
		return new FloatingButtonModel(this, newFloatX, newFloatY);
	}

	public boolean isKeyboardMode() {
		return MODE_KEYBOARD.equals(floatMode);
	}

	public BoundSwipes boundSwipes() {
		BoundSwipes b = new BoundSwipes();
		b.up = hasCmd(swipeUpCommand);
		b.down = hasCmd(swipeDownCommand);
		b.left = hasCmd(swipeLeftCommand);
		b.right = hasCmd(swipeRightCommand);
		b.upLeft = hasCmd(swipeUpLeftCommand);
		b.upRight = hasCmd(swipeUpRightCommand);
		b.downLeft = hasCmd(swipeDownLeftCommand);
		b.downRight = hasCmd(swipeDownRightCommand);
		return b;
	}

	public String commandForDirection(String direction) {
		if (direction == null) {
			return null;
		}
		if (SuperButtonGestures.DIR_UP.equals(direction)) {
			return emptyToNull(swipeUpCommand);
		}
		if (SuperButtonGestures.DIR_DOWN.equals(direction)) {
			return emptyToNull(swipeDownCommand);
		}
		if (SuperButtonGestures.DIR_LEFT.equals(direction)) {
			return emptyToNull(swipeLeftCommand);
		}
		if (SuperButtonGestures.DIR_RIGHT.equals(direction)) {
			return emptyToNull(swipeRightCommand);
		}
		if (SuperButtonGestures.DIR_UP_LEFT.equals(direction)) {
			return emptyToNull(swipeUpLeftCommand);
		}
		if (SuperButtonGestures.DIR_UP_RIGHT.equals(direction)) {
			return emptyToNull(swipeUpRightCommand);
		}
		if (SuperButtonGestures.DIR_DOWN_LEFT.equals(direction)) {
			return emptyToNull(swipeDownLeftCommand);
		}
		if (SuperButtonGestures.DIR_DOWN_RIGHT.equals(direction)) {
			return emptyToNull(swipeDownRightCommand);
		}
		return null;
	}

	private static boolean hasCmd(String cmd) {
		return cmd != null && cmd.length() > 0;
	}

	private static String emptyToNull(String cmd) {
		return hasCmd(cmd) ? cmd : null;
	}
}
