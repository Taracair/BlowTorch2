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
 *
 * <p>A button carries <em>two</em> stored positions, one per orientation:
 * {@code floatX}/{@code floatY} for portrait and {@code floatXLand}/
 * {@code floatYLand} for landscape. The activity handles orientation itself
 * ({@code configChanges="orientation"}), so nothing re-lays buttons out on a
 * turn; with a single stored pair a button dragged in portrait followed the
 * portrait coordinates into landscape. {@link #floatX}/{@link #floatY} are the
 * pair for the orientation this snapshot was built for, so every caller keeps
 * reading one position and does not have to know which one it is.
 *
 * <p>A missing landscape pair reads as {@link FloatingLayerGeometry#UNPLACED},
 * which is the same "never dragged" state a new floating button starts in — so
 * an existing profile keeps its portrait layout and gets its landscape one
 * seeded from the grid.
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
	/** Per-button U/D/L/R / Hold badges; independent of {@link #showGestureLabel}. */
	public final boolean showGestureHints;
	/** Wrap the label inside the tile. Default false: one-line drawText. */
	public final boolean wrapLabel;
	public final String floatMode;
	/** Position for {@link #landscape} — one of the two stored pairs below. */
	public final int floatX;
	public final int floatY;
	/** True when {@link #floatX}/{@link #floatY} resolve to the landscape pair. */
	public final boolean landscape;
	private final int floatXPortrait;
	private final int floatYPortrait;
	private final int floatXLandscape;
	private final int floatYLandscape;
	public final boolean floatRound;
	public final boolean floatFrame;
	/**
	 * Player-chosen stroke from Others → Border. Drawn on the floating copy in
	 * the floater's shape ({@link #floatRound}). Distinct from
	 * {@link #floatFrame}, which keeps the auto-contrast outline when Border is
	 * off.
	 */
	public final boolean border;
	public final int borderColor;
	/**
	 * Corner radius for square floaters ({@code !floatRound}), in px — same as
	 * Lua {@code buttonRoundness} so the floating copy matches the grid tile.
	 * Ignored when {@link #floatRound} is true (full oval). {@code < 0} means
	 * the payload omitted it (use factory 6dp); explicit {@code 0} is sharp.
	 */
	public final float cornerRadiusPx;
	/**
	 * Grid centre for {@link #landscape} — where the button sits on the button
	 * grid, and in portrait the position the floating copy is placed from.
	 */
	public final float gridX;
	public final float gridY;
	private final float gridXPortrait;
	private final float gridYPortrait;
	/**
	 * Landscape grid pair, or {@link Float#NaN} when the player has not moved
	 * this button in landscape — landscape then shows the portrait layout, the
	 * state every profile written before this starts in.
	 */
	private final float gridXLandscape;
	private final float gridYLandscape;
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
		this(o, false);
	}

	public FloatingButtonModel(JSONObject o, boolean forLandscape) throws JSONException {
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
		showGestureHints = o.optBoolean("showGestureHints", true);
		wrapLabel = o.optBoolean("wrapLabel", false);
		String mode = o.optString("floatMode", MODE_ALWAYS);
		floatMode = MODE_KEYBOARD.equals(mode) ? MODE_KEYBOARD : MODE_ALWAYS;
		floatXPortrait = o.optInt("floatX", FloatingLayerGeometry.UNPLACED);
		floatYPortrait = o.optInt("floatY", FloatingLayerGeometry.UNPLACED);
		floatXLandscape = o.optInt("floatXLand", FloatingLayerGeometry.UNPLACED);
		floatYLandscape = o.optInt("floatYLand", FloatingLayerGeometry.UNPLACED);
		landscape = forLandscape;
		floatX = forLandscape ? floatXLandscape : floatXPortrait;
		floatY = forLandscape ? floatYLandscape : floatYPortrait;
		floatRound = o.optBoolean("floatRound", false);
		floatFrame = o.optBoolean("floatFrame", false);
		border = o.optBoolean("border", false);
		borderColor = (int) o.optLong("borderColor", 0xE0FFFFFFL);
		cornerRadiusPx = (float) o.optDouble("cornerRadiusPx", -1);
		hasGridOrigin = o.has("gridX") && o.has("gridY");
		gridXPortrait = (float) o.optDouble("gridX", 0);
		gridYPortrait = (float) o.optDouble("gridY", 0);
		gridXLandscape = (float) o.optDouble("gridXLand", Double.NaN);
		gridYLandscape = (float) o.optDouble("gridYLand", Double.NaN);
		gridX = resolveGridX(forLandscape);
		gridY = resolveGridY(forLandscape);
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

	/**
	 * @param newFloatX new position, or {@link Integer#MIN_VALUE} to keep the
	 *                  existing one (used when only the orientation changes)
	 */
	private FloatingButtonModel(FloatingButtonModel src, int newFloatX, int newFloatY,
			boolean forLandscape) {
		this(src, newFloatX, newFloatY, forLandscape, Float.NaN, Float.NaN);
	}

	/**
	 * @param newGridX new grid centre, or {@link Float#NaN} to keep the existing one
	 */
	private FloatingButtonModel(FloatingButtonModel src, int newFloatX, int newFloatY,
			boolean forLandscape, float newGridX, float newGridY) {
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
		showGestureHints = src.showGestureHints;
		wrapLabel = src.wrapLabel;
		floatMode = src.floatMode;
		landscape = forLandscape;
		// A drag writes only the pair for the orientation it happened in; the
		// other one keeps whatever the player set there.
		boolean keep = newFloatX == KEEP;
		floatXPortrait = (!forLandscape && !keep) ? newFloatX : src.floatXPortrait;
		floatYPortrait = (!forLandscape && !keep) ? newFloatY : src.floatYPortrait;
		floatXLandscape = (forLandscape && !keep) ? newFloatX : src.floatXLandscape;
		floatYLandscape = (forLandscape && !keep) ? newFloatY : src.floatYLandscape;
		floatX = forLandscape ? floatXLandscape : floatXPortrait;
		floatY = forLandscape ? floatYLandscape : floatYPortrait;
		floatRound = src.floatRound;
		floatFrame = src.floatFrame;
		border = src.border;
		borderColor = src.borderColor;
		cornerRadiusPx = src.cornerRadiusPx;
		hasGridOrigin = src.hasGridOrigin;
		if (forLandscape && !Float.isNaN(newGridX)) {
			gridXLandscape = newGridX;
			gridYLandscape = newGridY;
			gridXPortrait = src.gridXPortrait;
			gridYPortrait = src.gridYPortrait;
		} else if (!forLandscape && !Float.isNaN(newGridX)) {
			gridXPortrait = newGridX;
			gridYPortrait = newGridY;
			gridXLandscape = src.gridXLandscape;
			gridYLandscape = src.gridYLandscape;
		} else {
			gridXPortrait = src.gridXPortrait;
			gridYPortrait = src.gridYPortrait;
			gridXLandscape = src.gridXLandscape;
			gridYLandscape = src.gridYLandscape;
		}
		gridX = resolveGridX(forLandscape);
		gridY = resolveGridY(forLandscape);
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
		return new FloatingButtonModel(this, newFloatX, newFloatY, landscape);
	}

	/**
	 * Same button with a new grid centre. A drag updates the grid in Lua but
	 * {@link #attachOverlayFor} reads the grid from this cache, so without this
	 * the next keyboard-mode re-attach snaps back to the pre-drag tile.
	 */
	FloatingButtonModel withGridPosition(final float newGridX, final float newGridY) {
		if (newGridX == gridX && newGridY == gridY) {
			return this;
		}
		return new FloatingButtonModel(this, KEEP, KEEP, landscape, newGridX, newGridY);
	}

	private float resolveGridX(final boolean forLandscape) {
		return forLandscape && !Float.isNaN(gridXLandscape) ? gridXLandscape : gridXPortrait;
	}

	private float resolveGridY(final boolean forLandscape) {
		return forLandscape && !Float.isNaN(gridYLandscape) ? gridYLandscape : gridYPortrait;
	}

	/** True when the grid has a position of its own for this orientation. */
	public boolean hasOwnLandscapeGrid() {
		return !Float.isNaN(gridXLandscape) && !Float.isNaN(gridYLandscape);
	}

	/** Sentinel for "keep the stored position" in the copy constructor. */
	private static final int KEEP = Integer.MIN_VALUE;

	/**
	 * The same button read for the other orientation. Both stored pairs travel
	 * on every snapshot, so a turn does not need a fresh push from Lua.
	 */
	public FloatingButtonModel forOrientation(final boolean forLandscape) {
		if (forLandscape == landscape) {
			return this;
		}
		return new FloatingButtonModel(this, KEEP, KEEP, forLandscape);
	}

	/** Stored landscape X, whatever orientation this snapshot resolves to. */
	public int getFloatXLandscape() {
		return floatXLandscape;
	}

	/** Stored landscape Y, whatever orientation this snapshot resolves to. */
	public int getFloatYLandscape() {
		return floatYLandscape;
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
