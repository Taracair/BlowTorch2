package com.resurrection.blowtorch2.lib.mapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Per-connection mapper session: active map, recording/follow flags, undo,
 * movement/GMCP sync, search/path, and UI bridge notifications.
 * Does not send commands to the MUD (callers / {@code MapCommand} do that).
 */
public class MapperController {

	public static final int UNDO_LIMIT = 20;
	public static final String DEFAULT_LEVEL_NAME = "0";
	public static final String DEFAULT_MAP_NAME = "default";
	/** Default CSV for the mapper overlay toolbar. */
	public static final String DEFAULT_TOOLBAR =
			"record,follow,level-,level+,find,undo,center,close";
	/** Default title regex for `.map capture` / Capture dialog (group 1 optional). */
	public static final String DEFAULT_CAPTURE_TITLE_REGEX = "^([A-Z].*)$";
	/** Default exits regex for `.map capture` / Capture dialog (group 1 optional). */
	public static final String DEFAULT_CAPTURE_EXITS_REGEX = "(?i)exits?:\\s*(.*)";

	/** Follow-only: jump by room num; no create rooms/exits; title if unlocked. */
	public static final String GMCP_POLICY_FOLLOW = "follow";
	/** Create/grow + update titles when unlocked (default). Conflicts may prompt. */
	public static final String GMCP_POLICY_SYNC = "sync";
	/** Like sync but always overwrite unlocked titles (no title conflict prompt). */
	public static final String GMCP_POLICY_STRICT = "strict";

	/** Listeners notified when the map model or session flags change. */
	public interface Listener {
		void onMapperChanged();
	}

	/**
	 * UI refresh listener used by {@link MapperOverlayController}.
	 * Same as {@link Listener}; kept as a distinct name for the overlay API.
	 */
	public interface MapperUiListener {
		void onMapperChanged();
	}

	/** Result of a capture-regex preview against buffer text. */
	public static class CapturePreview {
		public String matchedLines;
		public String title;
		public String exits;
	}

	/** Queued GMCP layout conflict for the overlay AlertDialog. */
	public static class PendingGmcpConflict {
		public String tileId;
		public String kind; // "title" | "position"
		public String message;
		public String proposedTitle;
		public Integer proposedX;
		public Integer proposedY;
		public String proposedLevelId;
	}

	/** Summary row for the level browser UI / {@link #levelBrowserLines()}. */
	public static class LevelInfo {
		public String id;
		public String name;
		public int index;
		public int tileCount;
		/** e.g. {@code via Hallway (down)} or empty for root floors. */
		public String anchorSummary;
	}

	private final Connection mConnection;
	private final List<Listener> mListeners = new CopyOnWriteArrayList<Listener>();
	private final List<MapperUiListener> mUiListeners = new CopyOnWriteArrayList<MapperUiListener>();
	private final LinkedList<String> mUndoStack = new LinkedList<String>();

	private MudMap mMap;
	private boolean mRecording;
	private boolean mFollowPlayer = true;
	private boolean mEnabled = true;
	private boolean mUseGmcp = true;
	/** Match/create tiles by GMCP room num/id/vnum when present. */
	private boolean mGmcpUseNum = true;
	/**
	 * Place/move tiles using absolute GMCP x/y. Off (default) = grow by exits /
	 * near the previous room — better when coords skip cells (Eden).
	 */
	private boolean mGmcpUseCoords = false;
	/** Create neighbor stubs for exits listed in Room.Info. */
	private boolean mGmcpCreateExits = true;
	/**
	 * When false, GMCP only follows existing rooms (by num) and updates the title —
	 * does not create tiles or exits. Independent of Record/Draw.
	 * Derived from {@link #mGmcpPolicy} ({@code follow} ⇒ off).
	 */
	private boolean mGmcpGrow = true;
	/**
	 * GMCP Room sync policy: {@link #GMCP_POLICY_FOLLOW}, {@link #GMCP_POLICY_SYNC}
	 * (default), or {@link #GMCP_POLICY_STRICT}.
	 */
	private String mGmcpPolicy = GMCP_POLICY_SYNC;
	/** Queued GMCP layout conflict awaiting Apply / Keep mine in the UI. */
	private PendingGmcpConflict mPendingGmcpConflict;
	/** Session: apply all further GMCP title/position conflicts without prompting. */
	private boolean mGmcpConflictApplyAll;
	/** Session: keep mine for all further GMCP title/position conflicts. */
	private boolean mGmcpConflictKeepAll;
	/** Per-tile session choices: {@code "apply"} or {@code "keep"}. */
	private final Map<String, String> mGmcpConflictTileChoice =
			new HashMap<String, String>();
	private boolean mAutoReverse = true;
	/**
	 * When true, special exits ({@code out}/{@code enter}/…) always create a new
	 * nearby tile (classic one-way / unknown return). When false (default), if
	 * exactly one other tile already exits into Here, link the special there
	 * (e.g. freezer {@code out} → hallway after hallway {@code west} → freezer).
	 */
	private boolean mAcceptOneWaySpecials = false;
	private boolean mPreferFloat = true;
	/** Session-only: false = Browse (navigate/view), true = Edit (create/draw/link/delete). */
	private boolean mEditMode = false;
	private boolean mPathAutoSend;
	/**
	 * When true, {@code .map} status lines are echoed into the game window.
	 * Off keeps the scrollback clean while using the overlay (sticky status instead).
	 */
	private boolean mEchoWindow = true;
	private int mOpacity = 85;
	private String mToolbarActions = DEFAULT_TOOLBAR;
	private String mCaptureTitleRegex = DEFAULT_CAPTURE_TITLE_REGEX;
	private String mCaptureExitsRegex = DEFAULT_CAPTURE_EXITS_REGEX;
	private String mLevelUpCommands = MapDirections.DEFAULT_LEVEL_UP_COMMANDS;
	private String mLevelDownCommands = MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS;
	private Map<String, Integer> mLevelDeltas = MapDirections.parseLevelCommandLists(
			MapDirections.DEFAULT_LEVEL_UP_COMMANDS,
			MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS);
	/** Planar + special (+ level overlay) command → map effect. */
	private String mMoveEffectsString = MapDirections.defaultMoveEffectsString();
	private Map<String, MapMoveEffect> mMoveEffects =
			MapDirections.defaultMoveEffects();
	private String mSelectedTileId;
	/** Last `.map capture preview` result for `.map capture apply`. */
	private CapturePreview mLastCapturePreview;

	private MapperUiBridge mUiBridge;
	/** When true, ignore outbound commands (path send already recorded). */
	private boolean mSuppressRecord;
	/**
	 * The compass move the player last typed, kept only long enough for the GMCP
	 * room that answers it. Without this, placing a new room was direction-blind:
	 * it guessed from exit lists and fell back to a fixed scan order, which is
	 * how a room entered by w ended up drawn directly below its neighbour.
	 */
	/**
	 * The move, and the room it was typed in. The room has to be captured here
	 * and not read back later: with Follow on, the current tile is advanced along
	 * the exit as soon as the command is sent, so by the time the GMCP room
	 * arrives "where we came from" is already "where we are".
	 */
	private static final class RememberedMove {
		private final String command;
		private final String fromTileId;

		RememberedMove(final String command, final String fromTileId) {
			this.command = command;
			this.fromTileId = fromTileId;
		}
	}

	private RememberedMove mLastMove;
	private long mLastMoveAt;
	/**
	 * The GMCP map file whose coordinate space this map is drawn in -- learnt
	 * from the first room that names one. Rooms belonging to any other file are
	 * placed by their exits instead, since their numbers mean nothing here.
	 */
	private String mHomeMapSpace;
	/**
	 * A move older than this is not the one this room answers -- a portal, a
	 * follow walk or a teleport happened in between, and a stale direction places
	 * worse than no direction at all.
	 */
	private static final long MOVE_MEMORY_MS = 4000L;
	private final android.os.Handler mAutosaveHandler =
			new android.os.Handler(android.os.Looper.getMainLooper());
	private final Runnable mAutosaveRunnable = new Runnable() {
		@Override
		public void run() {
			try {
				save();
			} catch (Exception ignored) {
			}
		}
	};

	public MapperController(final Connection connection) {
		mConnection = connection;
		mRecording = false;
		rebuildMoveEffects();
		ensureBlankMap(DEFAULT_MAP_NAME);
		registerInstance();
	}

	/**
	 * UI-only stub constructor (no Connection). Prefer binding the real
	 * connection mapper via {@link #forDisplay} / {@code setMapperController}.
	 */
	public MapperController(final Object uiHostIgnored) {
		mConnection = null;
		mRecording = false;
		rebuildMoveEffects();
		ensureBlankMap(DEFAULT_MAP_NAME);
	}

	private static final java.util.concurrent.ConcurrentHashMap<String, java.lang.ref.WeakReference<MapperController>> BY_DISPLAY =
			new java.util.concurrent.ConcurrentHashMap<String, java.lang.ref.WeakReference<MapperController>>();

	private void registerInstance() {
		if (mConnection == null) {
			return;
		}
		String display = null;
		try {
			display = mConnection.getDisplay();
		} catch (Exception ignored) {
		}
		if (display == null) {
			return;
		}
		BY_DISPLAY.put(display, new java.lang.ref.WeakReference<MapperController>(this));
	}

	/** Look up the live controller for a connection display name (same process). */
	public static MapperController forDisplay(final String display) {
		if (display == null) {
			return null;
		}
		java.lang.ref.WeakReference<MapperController> ref = BY_DISPLAY.get(display);
		return ref != null ? ref.get() : null;
	}

	/** Notify UI listeners after an external mutation of the map model. */
	public void fireChanged() {
		notifyChanged();
	}

	public Connection getConnection() {
		return mConnection;
	}

	public MudMap getMap() {
		return mMap;
	}

	public boolean isRecording() {
		return mRecording;
	}

	public boolean isFollowPlayer() {
		return mFollowPlayer;
	}

	public boolean isEnabled() {
		return mEnabled;
	}

	public boolean isPreferFloat() {
		return mPreferFloat;
	}

	/** Browse (false) vs Edit (true). Session-only; defaults to Browse. */
	public boolean isEditMode() {
		return mEditMode;
	}

	/**
	 * Switch between Browse and Edit. Browse is navigate/view only; Edit allows
	 * recording, creating nests, drawing, linking, and deleting levels/tiles.
	 * Leaving Edit stops recording.
	 */
	public void setEditMode(final boolean edit) {
		if (mEditMode == edit) {
			return;
		}
		mEditMode = edit;
		if (!edit) {
			mRecording = false;
		}
		notifyChanged();
	}

	/**
	 * @return error message when Browse forbids map changes; {@code null} if Edit.
	 */
	public String requireEditMode() {
		if (!mEditMode) {
			return "Mapper: Browse mode — switch to Edit to change the map.";
		}
		return null;
	}

	/** Toggle Browse ↔ Edit; returns the new edit-mode state. */
	public boolean toggleEditMode() {
		setEditMode(!mEditMode);
		return mEditMode;
	}

	public boolean isPathAutoSend() {
		return mPathAutoSend;
	}

	public int getOpacity() {
		return mOpacity;
	}

	public String getToolbarActions() {
		return mToolbarActions;
	}

	public String getCaptureTitleRegex() {
		return mCaptureTitleRegex != null ? mCaptureTitleRegex : DEFAULT_CAPTURE_TITLE_REGEX;
	}

	public String getCaptureExitsRegex() {
		return mCaptureExitsRegex != null ? mCaptureExitsRegex : DEFAULT_CAPTURE_EXITS_REGEX;
	}

	public void setCaptureTitleRegex(final String regex) {
		if (regex != null && regex.trim().length() > 0) {
			mCaptureTitleRegex = regex.trim();
		} else {
			mCaptureTitleRegex = DEFAULT_CAPTURE_TITLE_REGEX;
		}
	}

	public void setCaptureExitsRegex(final String regex) {
		if (regex != null && regex.trim().length() > 0) {
			mCaptureExitsRegex = regex.trim();
		} else {
			mCaptureExitsRegex = DEFAULT_CAPTURE_EXITS_REGEX;
		}
	}

	public void setUiBridge(final MapperUiBridge bridge) {
		mUiBridge = bridge;
	}

	public MapperUiBridge getUiBridge() {
		return mUiBridge;
	}

	public void addListener(final Listener listener) {
		if (listener != null && !mListeners.contains(listener)) {
			mListeners.add(listener);
		}
	}

	public void removeListener(final Listener listener) {
		mListeners.remove(listener);
	}

	public void addUiListener(final MapperUiListener listener) {
		if (listener != null && !mUiListeners.contains(listener)) {
			mUiListeners.add(listener);
		}
	}

	public void removeUiListener(final MapperUiListener listener) {
		mUiListeners.remove(listener);
	}

	public float getOpacityAlpha() {
		return mOpacity / 100f;
	}

	public String getToolbarActionsCsv() {
		return mToolbarActions != null ? mToolbarActions : DEFAULT_TOOLBAR;
	}

	public void setSelectedTileId(final String tileId) {
		mSelectedTileId = tileId;
		notifyChanged();
	}

	public String getSelectedTileId() {
		return mSelectedTileId;
	}

	public MapTile currentOrSelectedTile() {
		if (mSelectedTileId != null && mMap != null) {
			MapTile t = mMap.findTile(mSelectedTileId);
			if (t != null) {
				return t;
			}
		}
		return currentTile();
	}

	public void setCurrentTileId(final String tileId) {
		if (mMap == null || tileId == null) {
			return;
		}
		MapTile t = mMap.findTile(tileId);
		if (t == null) {
			return;
		}
		mMap.setCurrentTileId(tileId);
		mMap.setCurrentLevelId(t.getLevelId());
		notifyChanged();
	}

	/** Mark tile as the player's current position ("I am here"). */
	public String setHere(final String tileId) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		String id = tileId;
		if (TextUtils.isEmpty(id)) {
			MapTile sel = currentOrSelectedTile();
			if (sel == null) {
				return "Mapper: no tile (select one or pass id).";
			}
			id = sel.getId();
		}
		MapTile t = mMap.findTile(id);
		if (t == null) {
			return "Mapper: unknown tile.";
		}
		setCurrentTileId(t.getId());
		return "Mapper: here = " + (t.getTitle() != null && t.getTitle().length() > 0
				? t.getTitle() : shortId(t.getId())) + ".";
	}

	/**
	 * Manually place a tile on the current level. Occupied cells are rejected.
	 * When x/y are null, places near current (or at 0,0).
	 */
	public String placeTile(final Integer x, final Integer y, final String title) {
		return placeTile(x, y, title, false);
	}

	public String placeTile(final Integer x, final Integer y, final String title,
			final boolean makeCurrent) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		ensureDefaultLevel();
		String levelId = mMap.getCurrentLevelId();
		int gx;
		int gy;
		if (x != null && y != null) {
			gx = x.intValue();
			gy = y.intValue();
		} else {
			MapTile cur = currentTile();
			if (cur != null) {
				int[] free = findFreeNear(levelId, cur.getGridX(), cur.getGridY());
				gx = free[0];
				gy = free[1];
			} else {
				gx = 0;
				gy = 0;
			}
		}
		if (findTileAt(levelId, gx, gy) != null) {
			return "Mapper: cell (" + gx + "," + gy + ") occupied.";
		}
		pushUndo();
		MapTile tile = createTileAt(levelId, gx, gy);
		if (title != null && title.trim().length() > 0) {
			tile.setTitle(title.trim());
		}
		mSelectedTileId = tile.getId();
		if (makeCurrent || mMap.getCurrentTileId() == null) {
			mMap.setCurrentTileId(tile.getId());
		}
		notifyChanged();
		return "Mapper: placed tile at (" + gx + "," + gy + ") id="
				+ shortId(tile.getId()) + ".";
	}

	public String deleteTile(final String tileId) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		String id = tileId;
		if (TextUtils.isEmpty(id)) {
			MapTile sel = currentOrSelectedTile();
			if (sel == null) {
				return "Mapper: no tile to delete.";
			}
			id = sel.getId();
		}
		MapTile target = mMap.findTile(id);
		if (target == null) {
			return "Mapper: unknown tile.";
		}
		pushUndo();
		List<MapTile> tiles = mMap.getTiles();
		for (MapTile t : tiles) {
			if (t == null || t.getExits() == null) {
				continue;
			}
			List<MapExit> exits = t.getExits();
			for (int i = exits.size() - 1; i >= 0; i--) {
				MapExit e = exits.get(i);
				if (e != null && id.equals(e.getToId())) {
					exits.remove(i);
				}
			}
		}
		tiles.remove(target);
		if (id.equals(mMap.getCurrentTileId())) {
			mMap.setCurrentTileId(tiles.isEmpty() ? null : tiles.get(0).getId());
		}
		if (id.equals(mSelectedTileId)) {
			mSelectedTileId = mMap.getCurrentTileId();
		}
		refreshConflicts();
		notifyChanged();
		return "Mapper: deleted tile " + shortId(id) + ".";
	}

	/**
	 * Delete an entire level by id or name. Removes all tiles on that floor
	 * (with the same exit cleanup as {@link #deleteTile}), clears anchors on
	 * other levels that pointed at deleted tiles, and switches current level
	 * if needed. Refuses when only one level remains.
	 */
	public String deleteLevel(final String levelIdOrName) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(levelIdOrName)) {
			return "Mapper: usage .map level delete <id|name>";
		}
		String key = levelIdOrName.trim();
		MapLevel level = mMap.findLevel(key);
		if (level == null) {
			level = findLevelByName(key);
		}
		if (level == null) {
			return "Mapper: unknown level.";
		}
		if (mMap.getLevels().size() <= 1) {
			return "Mapper: cannot delete the only level.";
		}

		pushUndo();
		final String lid = level.getId();
		final String levelName = level.getName() != null ? level.getName() : lid;

		Set<String> deletedIds = new HashSet<String>();
		List<MapTile> tiles = mMap.getTiles();
		for (MapTile t : tiles) {
			if (t != null && lid.equals(t.getLevelId()) && t.getId() != null) {
				deletedIds.add(t.getId());
			}
		}

		// Remove exits on surviving tiles that pointed at deleted tiles.
		for (MapTile t : tiles) {
			if (t == null || t.getExits() == null || deletedIds.contains(t.getId())) {
				continue;
			}
			List<MapExit> exits = t.getExits();
			for (int i = exits.size() - 1; i >= 0; i--) {
				MapExit e = exits.get(i);
				if (e != null && e.getToId() != null && deletedIds.contains(e.getToId())) {
					exits.remove(i);
				}
			}
		}

		int removed = 0;
		for (int i = tiles.size() - 1; i >= 0; i--) {
			MapTile t = tiles.get(i);
			if (t != null && deletedIds.contains(t.getId())) {
				tiles.remove(i);
				removed++;
			}
		}

		mMap.getLevels().remove(level);

		if (lid.equals(mMap.getCurrentLevelId())) {
			List<MapLevel> remaining = sortedLevels();
			MapLevel next = remaining.isEmpty() ? null : remaining.get(0);
			mMap.setCurrentLevelId(next != null ? next.getId() : null);
		}

		String curTileId = mMap.getCurrentTileId();
		if (curTileId != null && deletedIds.contains(curTileId)) {
			MapTile first = firstTileOnLevel(mMap.getCurrentLevelId());
			mMap.setCurrentTileId(first != null ? first.getId() : null);
		}
		if (mSelectedTileId != null && deletedIds.contains(mSelectedTileId)) {
			mSelectedTileId = mMap.getCurrentTileId();
		}

		for (MapLevel other : mMap.getLevels()) {
			if (other != null && other.getAnchorTileId() != null
					&& deletedIds.contains(other.getAnchorTileId())) {
				other.setAnchorTileId(null);
				other.setAnchorDir(null);
			}
		}

		refreshConflicts();
		notifyChanged();
		return "Mapper: deleted level \"" + levelName + "\" (" + removed
				+ " tile" + (removed == 1 ? "" : "s") + ").";
	}

	/**
	 * From {@code fromTileId} (or current), create a destination in direction
	 * {@code cmd} if needed and link both ways when auto-reverse is on.
	 */
	public String addNeighbor(final String fromTileId, final String cmd) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(cmd)) {
			return "Mapper: usage .map neighbor <cmd>";
		}
		MapTile from = fromTileId != null ? mMap.findTile(fromTileId) : currentOrSelectedTile();
		if (from == null) {
			return "Mapper: no source tile.";
		}
		String norm = normalize(cmd);
		String stored = MapDirections.storeCommand(cmd, norm);
		int[] delta = gridDeltaFor(norm);
		boolean special = (delta == null);
		pushUndo();
		MapExit existing = findExit(from, norm);
		MapTile to;
		if (existing != null && existing.getToId() != null) {
			to = mMap.findTile(existing.getToId());
			if (to == null) {
				to = createDestination(from, norm, delta, special);
				existing.setToId(to.getId());
				existing.setCommand(stored);
			}
		} else {
			to = createDestination(from, norm, delta, special);
			String rev = MapDirections.suggestReverse(stored, directionMap());
			from.addExit(new MapExit(from.getId(), to.getId(), stored, special, rev));
			if (mAutoReverse && rev != null) {
				ensureReverse(from, to, stored);
			}
		}
		mSelectedTileId = to.getId();
		refreshConflicts();
		notifyChanged();
		return "Mapper: neighbor " + stored + " → " + shortId(to.getId())
				+ " at (" + to.getGridX() + "," + to.getGridY() + ").";
	}

	/**
	 * Add a nest/portal exit from a tile onto another floor of this map.
	 *
	 * @param existingLevelId link to this floor when non-null
	 * @param newIndependentName when creating a new floor with no ↑/↓ height
	 *        (enter/out zone). Ignored when {@code existingLevelId} is set.
	 *        When both null, create a new floor in the ↑/↓ direction of {@code cmd}.
	 */
	public String addLevelNeighbor(final String fromTileId, final String cmd,
			final String existingLevelId, final String newIndependentName) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(cmd)) {
			return "Mapper: usage .map neighbor <cmd>";
		}
		MapTile from = fromTileId != null ? mMap.findTile(fromTileId)
				: currentOrSelectedTile();
		if (from == null) {
			return "Mapper: no source tile.";
		}
		String norm = normalize(cmd);
		String stored = MapDirections.storeCommand(cmd, norm);
		boolean toExisting = existingLevelId != null && existingLevelId.length() > 0;
		boolean independent = !toExisting && newIndependentName != null
				&& newIndependentName.trim().length() > 0;
		Integer lvlDelta = levelDeltaFor(norm);
		if (!toExisting && !independent && lvlDelta == null) {
			return "Mapper: \"" + stored
					+ "\" is not a level move — pick up/down from Moves, "
					+ "or create an independent floor.";
		}
		pushUndo();
		MapLevel targetLevel;
		if (toExisting) {
			targetLevel = mMap.findLevel(existingLevelId);
			if (targetLevel == null) {
				return "Mapper: unknown target level.";
			}
			if (from.getLevelId() != null
					&& from.getLevelId().equals(targetLevel.getId())) {
				return "Mapper: already on that floor.";
			}
		} else if (independent) {
			targetLevel = createIndependentLevel(from, newIndependentName.trim(),
					stored);
		} else {
			targetLevel = createLevelBeside(from, lvlDelta.intValue(), stored);
		}
		MapTile to = findTileAt(targetLevel.getId(), from.getGridX(), from.getGridY());
		if (to == null) {
			to = createTileAt(targetLevel.getId(), from.getGridX(), from.getGridY());
		}
		MapExit existing = findExit(from, norm);
		if (existing != null) {
			existing.setToId(to.getId());
			existing.setCommand(stored);
			existing.setSpecial(true);
		} else {
			String rev = MapDirections.suggestReverse(stored, directionMap());
			from.addExit(new MapExit(from.getId(), to.getId(), stored, true, rev));
			if (mAutoReverse && rev != null) {
				ensureReverse(from, to, stored);
			}
		}
		if (targetLevel.getAnchorTileId() == null) {
			targetLevel.setAnchorTileId(from.getId());
			targetLevel.setAnchorDir(stored);
		}
		mSelectedTileId = to.getId();
		refreshConflicts();
		notifyChanged();
		String lname = targetLevel.getName() != null ? targetLevel.getName()
				: targetLevel.getId();
		return "Mapper: " + stored + " → floor \"" + lname + "\" ("
				+ shortId(to.getId()) + ").";
	}

	/** @deprecated use {@link #addLevelNeighbor(String, String, String, String)} */
	public String addLevelNeighbor(final String fromTileId, final String cmd,
			final String existingLevelId) {
		return addLevelNeighbor(fromTileId, cmd, existingLevelId, null);
	}

	/** New floor with no ↑/↓ index relation — next free high index + custom name. */
	private MapLevel createIndependentLevel(final MapTile from, final String name,
			final String enterCmd) {
		List<MapLevel> sorted = sortedLevels();
		int max = -1;
		for (MapLevel l : sorted) {
			if (l != null && l.getIndex() > max) {
				max = l.getIndex();
			}
		}
		String levelName = name;
		if (findLevelByName(levelName) != null) {
			levelName = name + " " + (max + 2);
		}
		return addLevel(levelName, max + 1, from.getId(), enterCmd);
	}

	/** Always allocate a new floor above/below {@code from}. */
	private MapLevel createLevelBeside(final MapTile from, final int levelDelta,
			final String enterCmd) {
		List<MapLevel> sorted = sortedLevels();
		int baseIndex = 0;
		for (int i = 0; i < sorted.size(); i++) {
			if (sorted.get(i).getId().equals(from.getLevelId())) {
				baseIndex = sorted.get(i).getIndex();
				break;
			}
		}
		int step = levelDelta >= 0 ? 1 : -1;
		int newIndex = baseIndex + step;
		java.util.HashSet<Integer> used = new java.util.HashSet<Integer>();
		for (MapLevel l : sorted) {
			if (l != null) {
				used.add(Integer.valueOf(l.getIndex()));
			}
		}
		while (used.contains(Integer.valueOf(newIndex))) {
			newIndex += step;
		}
		String name = "L" + newIndex;
		return addLevel(name, newIndex, from.getId(), enterCmd);
	}

	/**
	 * Add a portal exit on {@code fromTileId} that loads another saved map when
	 * walked (Follow / Record). Destination tile is the source itself; travel is
	 * via {@link MapExit#getTargetMap()}.
	 */
	public String linkMapPortal(final String fromTileId, final String cmd,
			final String mapName) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(cmd) || TextUtils.isEmpty(mapName)) {
			return "Mapper: usage .map portal <cmd> map <name> [from <tileId>]";
		}
		MapTile from = fromTileId != null ? mMap.findTile(fromTileId)
				: currentOrSelectedTile();
		if (from == null) {
			return "Mapper: no source tile.";
		}
		String norm = normalize(cmd);
		String stored = MapDirections.storeCommand(cmd, norm);
		String target = mapName.trim();
		pushUndo();
		MapExit existing = findExit(from, norm);
		if (existing != null) {
			existing.setTargetMap(target);
			existing.setToId(from.getId());
			existing.setSpecial(true);
			existing.setCommand(stored);
		} else {
			MapExit exit = new MapExit(from.getId(), from.getId(), stored, true, null);
			exit.setTargetMap(target);
			from.addExit(exit);
		}
		notifyChanged();
		return "Mapper: portal " + stored + " → map:" + target + ".";
	}

	public String moveTileOnGrid(final String tileId, final int x, final int y) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		MapTile tile = tileId != null ? mMap.findTile(tileId) : currentOrSelectedTile();
		if (tile == null) {
			return "Mapper: no tile.";
		}
		MapTile occupied = findTileAt(tile.getLevelId(), x, y);
		if (occupied != null && !occupied.getId().equals(tile.getId())) {
			return "Mapper: cell (" + x + "," + y + ") occupied.";
		}
		pushUndo();
		tile.setGridX(x);
		tile.setGridY(y);
		notifyChanged();
		return "Mapper: moved tile to (" + x + "," + y + ").";
	}

	public void toggleRecording() {
		setRecording(!mRecording);
	}

	public void toggleFollow() {
		setFollow(!mFollowPlayer);
	}

	public List<String> findPathToTile(final String toTileId) {
		if (mMap == null || toTileId == null || mMap.getCurrentTileId() == null) {
			return Collections.emptyList();
		}
		return MapPathfinder.findCommands(mMap, mMap.getCurrentTileId(), toTileId);
	}

	/**
	 * Rebuild the current level's positions from its exits.
	 *
	 * Placement only decides where a room goes at the moment it is first seen,
	 * with whatever was on the map then. A map grown before the direction was
	 * known -- or grown in an awkward order -- keeps those decisions forever, and
	 * the only cure was deleting it and walking the whole area again. This walks
	 * the rooms breadth-first from where the player is and puts each neighbour on
	 * the cell its own command asks for, which is the same rule placement now
	 * follows, applied to every room at once instead of one at a time.
	 *
	 * Pinned tiles do not move and are laid down first, so hand-placed rooms
	 * survive and everything else arranges itself around them. Rooms the walk
	 * cannot reach keep their positions unless something has taken the cell.
	 * Nothing but coordinates changes, and it is a single undo.
	 */
	public String relayoutFromExits() {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		final String levelId = mMap.getCurrentLevelId();
		List<MapTile> level = new ArrayList<MapTile>();
		for (MapTile t : mMap.getTiles()) {
			if (t != null && (levelId == null ? t.getLevelId() == null
					: levelId.equals(t.getLevelId()))) {
				level.add(t);
			}
		}
		if (level.size() < 2) {
			return "Mapper: nothing to lay out on this level.";
		}

		Map<String, int[]> placed = new LinkedHashMap<String, int[]>();
		Set<String> taken = new HashSet<String>();
		// Pinned first: they are fixed points the rest has to work around.
		for (MapTile t : level) {
			if (t.isLockPosition()) {
				placed.put(t.getId(), new int[] { t.getGridX(), t.getGridY() });
				taken.add(cellKey(t.getGridX(), t.getGridY()));
			}
		}

		MapTile seed = currentTile();
		if (seed == null || !level.contains(seed)) {
			seed = level.get(0);
		}
		if (!placed.containsKey(seed.getId())) {
			int[] anchor = freeCellNear(taken, seed.getGridX(), seed.getGridY(), null);
			placed.put(seed.getId(), anchor);
			taken.add(cellKey(anchor[0], anchor[1]));
		}

		LinkedList<MapTile> queue = new LinkedList<MapTile>();
		queue.add(seed);
		Set<String> visited = new HashSet<String>();
		visited.add(seed.getId());
		while (!queue.isEmpty()) {
			MapTile tile = queue.removeFirst();
			int[] here = placed.get(tile.getId());
			if (here == null || tile.getExits() == null) {
				continue;
			}
			for (MapExit e : tile.getExits()) {
				if (e == null || e.getToId() == null) {
					continue;
				}
				MapTile dest = findTileById(e.getToId());
				if (dest == null || !level.contains(dest)
						|| dest.getId().equals(tile.getId())) {
					continue;
				}
				if (!placed.containsKey(dest.getId())) {
					int[] delta = gridDeltaFor(normalize(e.getCommand()));
					int[] cell;
					if (delta == null) {
						// enter, climb, a portal: no direction to honour, so put it
						// beside its neighbour and let the label explain.
						cell = freeCellNear(taken, here[0], here[1], null);
					} else {
						int wantX = here[0] + delta[0];
						int wantY = here[1] + delta[1];
						cell = taken.contains(cellKey(wantX, wantY))
								? freeCellNear(taken, here[0], here[1], delta)
								: new int[] { wantX, wantY };
					}
					placed.put(dest.getId(), cell);
					taken.add(cellKey(cell[0], cell[1]));
				}
				if (visited.add(dest.getId())) {
					queue.add(dest);
				}
			}
		}

		// Anything the walk never reached: leave it be unless its cell is gone.
		for (MapTile t : level) {
			if (placed.containsKey(t.getId())) {
				continue;
			}
			int[] cell = taken.contains(cellKey(t.getGridX(), t.getGridY()))
					? freeCellNear(taken, t.getGridX(), t.getGridY(), null)
					: new int[] { t.getGridX(), t.getGridY() };
			placed.put(t.getId(), cell);
			taken.add(cellKey(cell[0], cell[1]));
		}

		int moved = 0;
		for (MapTile t : level) {
			int[] cell = placed.get(t.getId());
			if (cell != null && (t.getGridX() != cell[0] || t.getGridY() != cell[1])) {
				moved++;
			}
		}
		if (moved == 0) {
			return "Mapper: layout already follows the exits.";
		}
		pushUndo();
		for (MapTile t : level) {
			int[] cell = placed.get(t.getId());
			if (cell != null) {
				t.setGridX(cell[0]);
				t.setGridY(cell[1]);
			}
		}
		notifyChanged();
		return "Mapper: laid out " + level.size() + " room(s) from their exits, "
				+ moved + " moved. Undo restores the old positions.";
	}

	private static String cellKey(final int x, final int y) {
		return x + "," + y;
	}

	/** Nearest cell not in {@code taken}, preferring {@code travel} when given. */
	private static int[] freeCellNear(final Set<String> taken, final int ox,
			final int oy, final int[] travel) {
		if (!taken.contains(cellKey(ox, oy))) {
			return new int[] { ox, oy };
		}
		for (int r = 1; r < 60; r++) {
			for (int[] off : ringOffsetsByTravel(r, travel)) {
				int x = ox + off[0];
				int y = oy + off[1];
				if (!taken.contains(cellKey(x, y))) {
					return new int[] { x, y };
				}
			}
		}
		return new int[] { ox + 100, oy };
	}

	/**
	 * Mark every exit on the map as unchecked again.
	 *
	 * A map made before exits were being checked reads as though all of it had
	 * been walked, which is the compatible reading but hides exactly what the
	 * check was added to show. This puts the whole map back to claims so walking
	 * it proves them one at a time -- everything else about it, titles, notes,
	 * layout and pins, is untouched, which is what makes it better than deleting
	 * the map and starting again.
	 */
	public String recheckAllExits() {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		int count = 0;
		for (MapTile t : mMap.getTiles()) {
			if (t == null || t.getExits() == null) {
				continue;
			}
			for (MapExit e : t.getExits()) {
				if (e != null && e.isVerified()) {
					count++;
				}
			}
		}
		if (count == 0) {
			return "Mapper: nothing is marked as walked already.";
		}
		pushUndo();
		for (MapTile t : mMap.getTiles()) {
			if (t == null || t.getExits() == null) {
				continue;
			}
			for (MapExit e : t.getExits()) {
				if (e != null) {
					e.setVerified(false);
				}
			}
		}
		notifyChanged();
		return "Mapper: " + count + " exit(s) back to unchecked. Walk them to confirm; "
				+ "undo puts the marks back.";
	}

	public String moveTileToLevel(final String tileId, final String levelId) {
		if (mMap == null || tileId == null || levelId == null) {
			return "Mapper: missing tile/level.";
		}
		MapTile tile = mMap.findTile(tileId);
		MapLevel level = mMap.findLevel(levelId);
		if (tile == null || level == null) {
			return "Mapper: unknown tile or level.";
		}
		pushUndo();
		tile.setLevelId(levelId);
		if (tileId.equals(mMap.getCurrentTileId())) {
			mMap.setCurrentLevelId(levelId);
		}
		refreshConflicts();
		notifyChanged();
		return "Mapper: moved tile to level \"" + level.getName() + "\".";
	}

	public String getTitleBarText() {
		MudMap map = mMap;
		String mapName = map != null && map.getName() != null ? map.getName() : "map";
		String levelName = "?";
		if (map != null && map.getCurrentLevelId() != null) {
			MapLevel l = map.findLevel(map.getCurrentLevelId());
			if (l != null && l.getName() != null) {
				levelName = l.getName();
			}
		}
		MapTile cur = currentTile();
		String room = cur != null && cur.getTitle() != null ? cur.getTitle() : "";
		StringBuilder sb = new StringBuilder();
		sb.append(mapName).append(" / ").append(levelName);
		if (room.length() > 0) {
			sb.append(" — ").append(room);
		}
		if (mRecording) {
			sb.append(" [REC]");
		}
		return sb.toString();
	}

	public List<MapTile> tilesOnCurrentLevel() {
		List<MapTile> out = new ArrayList<MapTile>();
		if (mMap == null) {
			return out;
		}
		String levelId = mMap.getCurrentLevelId();
		for (MapTile t : mMap.getTiles()) {
			if (t == null) {
				continue;
			}
			String lid = t.getLevelId() != null ? t.getLevelId() : "";
			String want = levelId != null ? levelId : "";
			if (lid.equals(want)) {
				out.add(t);
			}
		}
		return out;
	}

	public void updateTile(final MapTile tile) {
		if (tile == null || mMap == null) {
			return;
		}
		pushUndo();
		MapTile existing = mMap.findTile(tile.getId());
		if (existing != null && existing != tile) {
			existing.setTitle(tile.getTitle());
			existing.setNotes(tile.getNotes());
			existing.setLevelId(tile.getLevelId());
			existing.setGridX(tile.getGridX());
			existing.setGridY(tile.getGridY());
			existing.setLockTitle(tile.isLockTitle());
			existing.setLockPosition(tile.isLockPosition());
			existing.setExits(tile.getExits());
		}
		refreshConflicts();
		notifyChanged();
	}

	public CapturePreview previewCapture(final String titleRegex, final String exitsRegex,
			final String bufferText, final int maxLines) {
		CapturePreview preview = new CapturePreview();
		String text = bufferText != null ? bufferText : "";
		String[] lines = text.split("\n", -1);
		int start = Math.max(0, lines.length - Math.max(1, maxLines));
		StringBuilder matched = new StringBuilder();
		for (int i = start; i < lines.length; i++) {
			if (matched.length() > 0) {
				matched.append('\n');
			}
			matched.append(lines[i]);
		}
		preview.matchedLines = matched.toString();
		preview.title = matchGroup1(titleRegex, preview.matchedLines);
		preview.exits = matchGroup1(exitsRegex, preview.matchedLines);
		return preview;
	}

	public void applyCapture(final CapturePreview preview) {
		if (preview == null) {
			return;
		}
		MapTile tile = currentOrSelectedTile();
		if (tile == null) {
			ensureDefaultLevel();
			tile = createTileAt(mMap.getCurrentLevelId(), 0, 0);
			mMap.setCurrentTileId(tile.getId());
		}
		pushUndo();
		if (preview.title != null && preview.title.length() > 0) {
			tile.setTitle(preview.title);
		}
		if (preview.exits != null && preview.exits.length() > 0) {
			String notes = tile.getNotes() != null ? tile.getNotes() : "";
			String exitLine = "Exits: " + preview.exits;
			if (notes.length() == 0) {
				tile.setNotes(exitLine);
			} else if (!notes.contains(exitLine)) {
				tile.setNotes(notes + "\n" + exitLine);
			}
		}
		notifyChanged();
	}

	private static String matchGroup1(final String regex, final String text) {
		if (regex == null || regex.trim().length() == 0 || text == null) {
			return null;
		}
		try {
			java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex.trim(),
					java.util.regex.Pattern.MULTILINE);
			java.util.regex.Matcher m = p.matcher(text);
			if (m.find()) {
				if (m.groupCount() >= 1 && m.group(1) != null) {
					return m.group(1).trim();
				}
				return m.group().trim();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/** Reload option values from the connection settings plugin. */
	public void applySettingsFromConnection() {
		mEnabled = boolOpt("mapper_enabled", true);
		if (!mSettingsApplied) {
			mRecording = boolOpt("mapper_recording_default", false);
			mSettingsApplied = true;
		}
		mFollowPlayer = boolOpt("mapper_follow", true);
		mPreferFloat = boolOpt("mapper_float", true);
		mPathAutoSend = boolOpt("mapper_path_auto_send", false);
		mEchoWindow = boolOpt("mapper_echo_window", true);
		mUseGmcp = boolOpt("mapper_use_gmcp", true);
		mGmcpUseNum = boolOpt("mapper_gmcp_use_num", true);
		mGmcpUseCoords = boolOpt("mapper_gmcp_use_coords", false);
		mGmcpCreateExits = boolOpt("mapper_gmcp_create_exits", true);
		String policyOpt = stringOpt("mapper_gmcp_policy", null);
		if (policyOpt != null && policyOpt.trim().length() > 0) {
			applyGmcpPolicyInternal(normalizeGmcpPolicy(policyOpt), false);
		} else {
			mGmcpGrow = boolOpt("mapper_gmcp_grow", true);
			mGmcpPolicy = mGmcpGrow ? GMCP_POLICY_SYNC : GMCP_POLICY_FOLLOW;
		}
		mAutoReverse = boolOpt("mapper_auto_reverse_link", true);
		mAcceptOneWaySpecials = boolOpt("mapper_accept_one_way_specials", false);
		mOpacity = clampOpacity(intOpt("mapper_opacity", 85));
		String toolbar = stringOpt("mapper_toolbar_actions", null);
		if (toolbar != null && toolbar.trim().length() > 0) {
			mToolbarActions = toolbar.trim();
		}
		String titleRx = stringOpt("mapper_capture_title_regex", null);
		if (titleRx != null && titleRx.trim().length() > 0) {
			mCaptureTitleRegex = titleRx.trim();
		} else {
			mCaptureTitleRegex = DEFAULT_CAPTURE_TITLE_REGEX;
		}
		String exitsRx = stringOpt("mapper_capture_exits_regex", null);
		if (exitsRx != null && exitsRx.trim().length() > 0) {
			mCaptureExitsRegex = exitsRx.trim();
		} else {
			mCaptureExitsRegex = DEFAULT_CAPTURE_EXITS_REGEX;
		}
		String upCsv = stringOpt("mapper_level_up_commands", null);
		if (upCsv != null) {
			mLevelUpCommands = upCsv.trim();
		} else {
			mLevelUpCommands = MapDirections.DEFAULT_LEVEL_UP_COMMANDS;
		}
		String downCsv = stringOpt("mapper_level_down_commands", null);
		if (downCsv != null) {
			mLevelDownCommands = downCsv.trim();
		} else {
			mLevelDownCommands = MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS;
		}
		String moves = stringOpt("mapper_move_effects", null);
		if (moves != null && moves.trim().length() > 0) {
			mMoveEffectsString = moves.trim();
		} else {
			mMoveEffectsString = MapDirections.defaultMoveEffectsString();
		}
		rebuildMoveEffects();
		notifyChanged();
	}

	private void rebuildLevelDeltas() {
		mLevelDeltas = MapDirections.parseLevelCommandLists(
				mLevelUpCommands, mLevelDownCommands);
	}

	private void rebuildMoveEffects() {
		LinkedHashMap<String, MapMoveEffect> base =
				MapDirections.parseMoveEffects(mMoveEffectsString);
		if (base.isEmpty()) {
			base = MapDirections.defaultMoveEffects();
		}
		MapDirections.applyLevelCommands(base, mLevelUpCommands,
				mLevelDownCommands);
		mMoveEffects = base;
		rebuildLevelDeltas();
	}

	/** Compact planar+special table (levels live in Level-Up/Down CSV). */
	public String getMoveEffectsString() {
		return mMoveEffectsString;
	}

	/**
	 * Combined editable table (planar + special + level) for the Moves dialog.
	 */
	public String getCombinedMoveEffectsDisplay() {
		return MapDirections.serializeMoveEffects(mMoveEffects, false);
	}

	/** Effective move-effect lookup table (includes level CSV overlay). */
	public Map<String, MapMoveEffect> getMoveEffects() {
		return mMoveEffects;
	}

	/**
	 * Replace planar+special table from Options / {@code .map moves replace}.
	 * Does not clear level CSVs. Does not re-write Options (caller may be
	 * the Options listener).
	 */
	public void setMoveEffectsString(final String table) {
		if (table == null || table.trim().length() == 0) {
			mMoveEffectsString = MapDirections.defaultMoveEffectsString();
		} else {
			mMoveEffectsString = table.trim();
		}
		rebuildMoveEffects();
		notifyChanged();
	}

	/**
	 * Apply a combined dialog/CLI table: splits LEVEL rows into Level CSVs
	 * and the rest into {@code mapper_move_effects}.
	 */
	public String applyCombinedMoveEffects(final String combined) {
		LinkedHashMap<String, MapMoveEffect> parsed =
				MapDirections.parseMoveEffects(combined);
		if (parsed.isEmpty() && combined != null
				&& combined.trim().length() > 0) {
			return "Mapper: no valid move lines (need cmd=grid:dx:dy | level:N | special).";
		}
		if (parsed.isEmpty()) {
			mMoveEffectsString = MapDirections.defaultMoveEffectsString();
			mLevelUpCommands = MapDirections.DEFAULT_LEVEL_UP_COMMANDS;
			mLevelDownCommands = MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS;
		} else {
			String[] split = MapDirections.splitCombinedEffects(parsed);
			mMoveEffectsString = split[0] != null && split[0].length() > 0
					? split[0] : MapDirections.defaultMoveEffectsString();
			mLevelUpCommands = split[1] != null ? split[1] : "";
			mLevelDownCommands = split[2] != null ? split[2] : "";
		}
		rebuildMoveEffects();
		persistMapperOption("mapper_move_effects", mMoveEffectsString);
		persistMapperOption("mapper_level_up_commands", mLevelUpCommands);
		persistMapperOption("mapper_level_down_commands", mLevelDownCommands);
		notifyChanged();
		return "Mapper: moves updated (" + mMoveEffects.size() + " entries).";
	}

	public String resetMoveEffects() {
		mMoveEffectsString = MapDirections.defaultMoveEffectsString();
		mLevelUpCommands = MapDirections.DEFAULT_LEVEL_UP_COMMANDS;
		mLevelDownCommands = MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS;
		rebuildMoveEffects();
		persistMapperOption("mapper_move_effects", mMoveEffectsString);
		persistMapperOption("mapper_level_up_commands", mLevelUpCommands);
		persistMapperOption("mapper_level_down_commands", mLevelDownCommands);
		notifyChanged();
		return "Mapper: moves reset to defaults.";
	}

	public String setOneMoveEffect(final String cmd, final MapMoveEffect effect) {
		if (cmd == null || cmd.trim().length() == 0 || effect == null) {
			return "Mapper: usage .map moves set <cmd> grid <dx> <dy>|level <n>|special";
		}
		String key = cmd.trim().toLowerCase(Locale.US);
		LinkedHashMap<String, MapMoveEffect> copy =
				new LinkedHashMap<String, MapMoveEffect>(mMoveEffects);
		copy.put(key, effect);
		return applyCombinedMoveEffects(
				MapDirections.serializeMoveEffects(copy, false));
	}

	public String unsetOneMoveEffect(final String cmd) {
		if (cmd == null || cmd.trim().length() == 0) {
			return "Mapper: usage .map moves unset <cmd>";
		}
		String key = cmd.trim().toLowerCase(Locale.US);
		LinkedHashMap<String, MapMoveEffect> copy =
				new LinkedHashMap<String, MapMoveEffect>(mMoveEffects);
		if (copy.remove(key) == null) {
			return "Mapper: no move effect for \"" + key + "\".";
		}
		return applyCombinedMoveEffects(
				MapDirections.serializeMoveEffects(copy, false));
	}

	private void persistMapperOption(final String key, final String value) {
		if (mConnection == null || key == null) {
			return;
		}
		try {
			if (mConnection.getSettings() != null) {
				String cur = mConnection.getSettings().getOptionValue(key);
				String next = value != null ? value : "";
				if (cur != null && cur.equals(next)) {
					return;
				}
				mConnection.getSettings().setOption(key, next);
			}
		} catch (Exception ignored) {
		} catch (Error e) {
			// Never let settings re-entry take down :stellar (e.g. StackOverflow).
			android.util.Log.e("BlowTorch", "persistMapperOption failed: " + key, e);
		}
	}

	/** CSV of commands that create a higher floor while recording. */
	public String getLevelUpCommands() {
		return mLevelUpCommands;
	}

	/** CSV of commands that create a lower floor while recording. */
	public String getLevelDownCommands() {
		return mLevelDownCommands;
	}

	public void setLevelUpCommands(final String csv) {
		mLevelUpCommands = csv != null ? csv.trim()
				: MapDirections.DEFAULT_LEVEL_UP_COMMANDS;
		rebuildMoveEffects();
		notifyChanged();
	}

	public void setLevelDownCommands(final String csv) {
		mLevelDownCommands = csv != null ? csv.trim()
				: MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS;
		rebuildMoveEffects();
		notifyChanged();
	}

	/**
	 * Level delta for recording / nest classification using Options lists.
	 * Empty Options lists disable auto level creation.
	 */
	public Integer levelDeltaFor(final String token) {
		MapMoveEffect e = MapDirections.effectFor(token, mMoveEffects);
		if (e != null && e.kind == MapMoveEffect.Kind.LEVEL) {
			return Integer.valueOf(e.levelDelta);
		}
		return MapDirections.levelDelta(token, mLevelDeltas);
	}

	/** Grid offset from the editable move table (null = not planar). */
	public int[] gridDeltaFor(final String token) {
		MapMoveEffect e = MapDirections.effectFor(token, mMoveEffects);
		if (e != null) {
			if (e.kind == MapMoveEffect.Kind.GRID) {
				return new int[] { e.dx, e.dy };
			}
			return null;
		}
		return MapDirections.gridDelta(token);
	}

	private boolean mSettingsApplied;

	public void setEnabled(final boolean enabled) {
		mEnabled = enabled;
		notifyChanged();
	}

	public void setRecording(final boolean recording) {
		if (recording && !mEditMode) {
			return;
		}
		mRecording = recording;
		notifyChanged();
	}

	/**
	 * Enable/disable recording. Returns a status line (including Browse denial).
	 */
	public String setRecordingStatus(final boolean recording) {
		if (recording && !mEditMode) {
			return "Mapper: Browse mode — switch to Edit to record.";
		}
		mRecording = recording;
		notifyChanged();
		return "Mapper recording: " + (mRecording ? "on" : "off");
	}

	public void setFollow(final boolean follow) {
		mFollowPlayer = follow;
		notifyChanged();
	}

	public void setPreferFloat(final boolean preferFloat) {
		mPreferFloat = preferFloat;
		notifyChanged();
	}

	public void setPathAutoSend(final boolean autoSend) {
		mPathAutoSend = autoSend;
	}

	public boolean isEchoWindow() {
		return mEchoWindow;
	}

	public void setEchoWindow(final boolean echo) {
		mEchoWindow = echo;
		persistMapperOption("mapper_echo_window", Boolean.toString(mEchoWindow));
		notifyChanged();
	}

	public boolean toggleEchoWindow() {
		setEchoWindow(!mEchoWindow);
		return mEchoWindow;
	}

	public void setUseGmcp(final boolean use) {
		mUseGmcp = use;
		persistMapperOption("mapper_use_gmcp", Boolean.toString(mUseGmcp));
		notifyChanged();
	}

	public void setGmcpUseNum(final boolean use) {
		mGmcpUseNum = use;
	}

	public void setGmcpUseCoords(final boolean use) {
		mGmcpUseCoords = use;
	}

	public void setGmcpCreateExits(final boolean create) {
		mGmcpCreateExits = create;
	}

	/**
	 * Sets grow flag and maps to policy for backward compat:
	 * grow off → {@link #GMCP_POLICY_FOLLOW}; grow on → {@link #GMCP_POLICY_SYNC}
	 * when currently follow (keeps sync/strict otherwise).
	 */
	public void setGmcpGrow(final boolean grow) {
		mGmcpGrow = grow;
		if (!grow) {
			mGmcpPolicy = GMCP_POLICY_FOLLOW;
		} else if (GMCP_POLICY_FOLLOW.equals(mGmcpPolicy)) {
			mGmcpPolicy = GMCP_POLICY_SYNC;
		}
		persistMapperOption("mapper_gmcp_grow", Boolean.toString(mGmcpGrow));
		persistMapperOption("mapper_gmcp_policy", mGmcpPolicy);
		notifyChanged();
	}

	public void setGmcpPolicy(final String policy) {
		applyGmcpPolicyInternal(normalizeGmcpPolicy(policy), true);
		notifyChanged();
	}

	private void applyGmcpPolicyInternal(final String policy, final boolean persist) {
		mGmcpPolicy = normalizeGmcpPolicy(policy);
		mGmcpGrow = !GMCP_POLICY_FOLLOW.equals(mGmcpPolicy);
		if (persist) {
			persistMapperOption("mapper_gmcp_policy", mGmcpPolicy);
			persistMapperOption("mapper_gmcp_grow", Boolean.toString(mGmcpGrow));
		}
	}

	public static String normalizeGmcpPolicy(final String policy) {
		if (policy == null) {
			return GMCP_POLICY_SYNC;
		}
		String p = policy.trim().toLowerCase(Locale.US);
		if (GMCP_POLICY_FOLLOW.equals(p) || "off".equals(p) || "nogrow".equals(p)) {
			return GMCP_POLICY_FOLLOW;
		}
		if (GMCP_POLICY_STRICT.equals(p)) {
			return GMCP_POLICY_STRICT;
		}
		return GMCP_POLICY_SYNC;
	}

	public boolean isUseGmcp() {
		return mUseGmcp;
	}

	public boolean isGmcpGrow() {
		return mGmcpGrow;
	}

	public String getGmcpPolicy() {
		return mGmcpPolicy;
	}

	public boolean isGmcpUseNum() {
		return mGmcpUseNum;
	}

	public boolean isGmcpUseCoords() {
		return mGmcpUseCoords;
	}

	public boolean isGmcpCreateExits() {
		return mGmcpCreateExits;
	}

	public boolean toggleUseGmcp() {
		mUseGmcp = !mUseGmcp;
		persistMapperOption("mapper_use_gmcp", Boolean.toString(mUseGmcp));
		notifyChanged();
		return mUseGmcp;
	}

	public boolean toggleGmcpGrow() {
		setGmcpGrow(!mGmcpGrow);
		return mGmcpGrow;
	}

	public PendingGmcpConflict getPendingGmcpConflict() {
		return mPendingGmcpConflict;
	}

	/** Apply the queued GMCP conflict change to the map. */
	public String applyPendingGmcpConflict(final boolean allSession) {
		PendingGmcpConflict pending = mPendingGmcpConflict;
		if (pending == null || mMap == null) {
			return "Mapper: no pending GMCP conflict.";
		}
		MapTile tile = mMap.findTile(pending.tileId);
		if (tile == null) {
			mPendingGmcpConflict = null;
			return "Mapper: conflict tile gone.";
		}
		if (allSession) {
			mGmcpConflictApplyAll = true;
		}
		if (pending.tileId != null) {
			mGmcpConflictTileChoice.put(pending.tileId, "apply");
		}
		pushUndo();
		if ("position".equals(pending.kind)) {
			if (pending.proposedX != null) {
				tile.setGridX(pending.proposedX.intValue());
			}
			if (pending.proposedY != null) {
				tile.setGridY(pending.proposedY.intValue());
			}
			if (pending.proposedLevelId != null) {
				tile.setLevelId(pending.proposedLevelId);
			}
		} else if ("title".equals(pending.kind)) {
			if (pending.proposedTitle != null && !tile.isLockTitle()) {
				tile.setTitle(pending.proposedTitle);
			}
		}
		mPendingGmcpConflict = null;
		refreshConflicts();
		notifyChanged();
		return "Mapper: applied GMCP " + pending.kind
				+ (allSession ? " (apply all this session)" : "") + ".";
	}

	/** Keep the user's layout; dismiss the queued conflict. */
	public String keepPendingGmcpConflict(final boolean allSession) {
		PendingGmcpConflict pending = mPendingGmcpConflict;
		if (pending == null) {
			return "Mapper: no pending GMCP conflict.";
		}
		if (allSession) {
			mGmcpConflictKeepAll = true;
		}
		if (pending.tileId != null) {
			mGmcpConflictTileChoice.put(pending.tileId, "keep");
		}
		mPendingGmcpConflict = null;
		notifyChanged();
		return "Mapper: kept mine for GMCP " + pending.kind
				+ (allSession ? " (keep all this session)" : "") + ".";
	}

	public void setAutoReverse(final boolean auto) {
		mAutoReverse = auto;
	}

	/**
	 * When true, special exits always spawn a new nearby tile. When false
	 * (default), try linking to the unique inbound neighbor first.
	 */
	public boolean isAcceptOneWaySpecials() {
		return mAcceptOneWaySpecials;
	}

	public void setAcceptOneWaySpecials(final boolean accept) {
		mAcceptOneWaySpecials = accept;
		notifyChanged();
	}

	public boolean toggleAcceptOneWaySpecials() {
		mAcceptOneWaySpecials = !mAcceptOneWaySpecials;
		notifyChanged();
		return mAcceptOneWaySpecials;
	}

	public void setOpacity(final int opacity) {
		int next = clampOpacity(opacity);
		// Avoid re-entry: persist → SettingsGroup.setOption → updateSetting →
		// setOpacity must not recurse (StackOverflow when entering a connection).
		if (next == mOpacity) {
			return;
		}
		mOpacity = next;
		persistMapperOption("mapper_opacity", Integer.toString(mOpacity));
	}

	/** Settings-driven apply only — never writes back into settings. */
	public void applyOpacityFromSettings(final int opacity) {
		mOpacity = clampOpacity(opacity);
	}

	public List<String> listMaps() {
		Context ctx = context();
		if (ctx == null) {
			return Collections.emptyList();
		}
		return MapStore.listMaps(ctx);
	}

	/**
	 * Load a named map (or create empty if missing).
	 *
	 * @return status message for the window
	 */
	public String openMap(final String name) {
		String mapName = TextUtils.isEmpty(name) ? DEFAULT_MAP_NAME : name.trim();
		Context ctx = context();
		if (ctx == null) {
			ensureBlankMap(mapName);
			return "Mapper: no context; using in-memory map \"" + mapName + "\".";
		}
		try {
			MudMap loaded = MapStore.load(ctx, mapName);
			if (loaded == null) {
				ensureBlankMap(mapName);
				save();
				return "Mapper: created map \"" + mapName + "\".";
			}
			mMap = loaded;
			ensureDefaultLevel();
			clearUndo();
			notifyChanged();
			return "Mapper: loaded \"" + mapName + "\" ("
					+ mMap.getTiles().size() + " tiles).";
		} catch (Exception e) {
			ensureBlankMap(mapName);
			return "Mapper: failed to load \"" + mapName + "\": " + e.getMessage();
		}
	}

	/**
	 * Create a fresh empty map with the given name (replaces session map).
	 * Refuses if a map file with that name already exists.
	 */
	public String newMap(final String name) {
		String mapName = TextUtils.isEmpty(name) ? DEFAULT_MAP_NAME : name.trim();
		Context ctx = context();
		if (ctx != null && MapStore.exists(ctx, mapName)) {
			return "Mapper: map \"" + MapStore.safeName(mapName)
					+ "\" already exists. Open it from Maps, or choose another name.";
		}
		ensureBlankMap(mapName);
		if (mConnection != null && mConnection.getHost() != null) {
			mMap.setHostHint(mConnection.getHost());
		}
		clearUndo();
		String saveMsg = save();
		notifyChanged();
		return "Mapper: new map \"" + mapName + "\". " + saveMsg;
	}

	/**
	 * Delete a saved map file. If it is the active map, switches to another
	 * saved map or a blank default.
	 */
	public String deleteMap(final String name) {
		if (TextUtils.isEmpty(name)) {
			return "Mapper: delete needs a map name.";
		}
		Context ctx = context();
		if (ctx == null) {
			return "Mapper: cannot delete (no context).";
		}
		String mapName = name.trim();
		String safe = MapStore.safeName(mapName);
		if (safe.length() == 0) {
			return "Mapper: invalid map name.";
		}
		if (!MapStore.exists(ctx, mapName)) {
			return "Mapper: no map \"" + safe + "\" on disk.";
		}
		boolean wasCurrent = mMap != null && safe.equals(MapStore.safeName(mMap.getName()));
		if (!MapStore.delete(ctx, mapName)) {
			return "Mapper: failed to delete \"" + safe + "\".";
		}
		if (wasCurrent) {
			List<String> left = MapStore.listMaps(ctx);
			if (!left.isEmpty()) {
				openMap(left.get(0));
				return "Mapper: deleted \"" + safe + "\". Switched to \""
						+ left.get(0) + "\".";
			}
			ensureBlankMap(DEFAULT_MAP_NAME);
			clearUndo();
			save();
			notifyChanged();
			return "Mapper: deleted \"" + safe + "\". Started blank \""
					+ DEFAULT_MAP_NAME + "\".";
		}
		notifyChanged();
		return "Mapper: deleted \"" + safe + "\".";
	}

	/** Persist the active map now. */
	public String save() {
		if (mMap == null) {
			return "Mapper: nothing to save.";
		}
		Context ctx = context();
		if (ctx == null) {
			return "Mapper: cannot save (no context).";
		}
		try {
			MapStore.save(ctx, mMap);
			return "Mapper: saved \"" + mMap.getName() + "\".";
		} catch (Exception e) {
			return "Mapper: save failed: " + e.getMessage();
		}
	}

	/**
	 * Import a map from a file path or an existing maps-dir name.
	 * Paths containing {@code /} or ending in {@code .json} are loaded as files
	 * (absolute, or relative to the BlowTorch root). Other args are map names
	 * under {@code /BlowTorch/maps/}. After load, the map becomes active and a
	 * copy is saved into the maps directory.
	 */
	public String importMap(final String pathOrName) {
		if (TextUtils.isEmpty(pathOrName)) {
			return "Mapper: usage .map import <path|name>";
		}
		String arg = pathOrName.trim();
		boolean looksLikePath = arg.indexOf('/') >= 0
				|| arg.toLowerCase(Locale.US).endsWith(".json");
		Context ctx = context();
		if (ctx == null) {
			return "Mapper: cannot import (no context).";
		}
		MudMap loaded;
		String fallbackName = arg;
		try {
			if (looksLikePath) {
				File file = MapStore.resolveExternalPath(ctx, arg);
				if (file == null || !file.isFile()) {
					return "Mapper: import file not found: " + arg;
				}
				loaded = MapStore.loadFromFile(file);
				if (loaded == null) {
					return "Mapper: import failed (unreadable): " + arg;
				}
				String base = file.getName();
				if (base.toLowerCase(Locale.US).endsWith(".json")) {
					base = base.substring(0, base.length() - 5);
				}
				fallbackName = base;
			} else {
				loaded = MapStore.load(ctx, arg);
				if (loaded == null) {
					return "Mapper: map \"" + arg + "\" not found.";
				}
				fallbackName = arg;
			}
		} catch (Exception e) {
			return "Mapper: import failed: " + e.getMessage();
		}
		String mapName = loaded.getName();
		if (TextUtils.isEmpty(mapName)) {
			mapName = fallbackName;
			loaded.setName(mapName);
		}
		mMap = loaded;
		ensureDefaultLevel();
		clearUndo();
		try {
			MapStore.save(ctx, mMap);
		} catch (Exception e) {
			notifyChanged();
			return "Mapper: imported \"" + mapName + "\" (" + mMap.getTiles().size()
					+ " tiles) but save failed: " + e.getMessage();
		}
		notifyChanged();
		return "Mapper: imported \"" + mapName + "\" ("
				+ mMap.getTiles().size() + " tiles).";
	}

	/**
	 * Export the active map. With no path, saves under {@code /BlowTorch/maps/}
	 * (same as {@link #save()}). With a path, writes JSON to that absolute path
	 * (or path relative to the BlowTorch root), creating parent dirs if needed.
	 */
	public String exportMap(final String path) {
		if (TextUtils.isEmpty(path)) {
			return save();
		}
		if (mMap == null) {
			return "Mapper: nothing to export.";
		}
		Context ctx = context();
		if (ctx == null) {
			return "Mapper: cannot export (no context).";
		}
		try {
			File file = MapStore.resolveExternalPath(ctx, path.trim());
			if (file == null) {
				return "Mapper: invalid export path.";
			}
			MapStore.saveToFile(file, mMap);
			return "Mapper: exported to \"" + file.getAbsolutePath() + "\".";
		} catch (Exception e) {
			return "Mapper: export failed: " + e.getMessage();
		}
	}

	/**
	 * Handle an outbound player command for the mapper.
	 * <ul>
	 *   <li>Edit + Record: grow the graph ({@link #recordMove}).</li>
	 *   <li>Otherwise, if Follow is on: advance Here along an existing exit
	 *       only (no new tiles) so the camera can track movement.</li>
	 * </ul>
	 */
	public void onPlayerCommand(final String cmd) {
		if (!mEnabled || cmd == null) {
			return;
		}
		String raw = cmd.trim();
		if (raw.length() == 0) {
			return;
		}
		// Skip special/dot commands
		if (raw.startsWith(".")) {
			return;
		}
		// Remembered before anything else can return early. Recording mode used to
		// jump straight to recordMove and out, so in the one mode where the player
		// is deliberately building a map, the room that arrived next had no idea
		// which way they had gone.
		String[] moves = raw.split("[\\r\\n;]+");
		for (int i = moves.length - 1; i >= 0; i--) {
			String piece = moves[i].trim();
			if (piece.length() == 0) {
				continue;
			}
			String norm = normalize(piece);
			// Up and down count. They have no grid delta, so testing for one threw
			// them away -- which is why going down into a cellar was never
			// recognised as the move that got you there.
			if (gridDeltaFor(norm) != null || levelDeltaFor(norm) != null) {
				// The last move in the line is the one that lands us. Take the
				// room we are standing in now, before Follow moves us on.
				MapTile here = currentTile();
				mLastMove = new RememberedMove(norm, here != null ? here.getId() : null);
				mLastMoveAt = System.currentTimeMillis();
				break;
			}
		}
		if (mRecording && mEditMode && !mSuppressRecord) {
			String[] parts = raw.split("[\\r\\n;]+");
			for (String part : parts) {
				String piece = part.trim();
				if (piece.length() == 0) {
					continue;
				}
				recordMove(piece);
			}
			return;
		}
		// Follow still runs while suppress is on (path auto-walk must update Here).
		if (mFollowPlayer) {
			boolean moved = false;
			String[] parts = raw.split("[\\r\\n;]+");
			for (String part : parts) {
				String piece = part.trim();
				if (piece.length() == 0) {
					continue;
				}
				if (advanceAlongExistingExit(piece)) {
					moved = true;
				}
			}
			if (moved) {
				notifyChanged();
			}
		}
	}

	/**
	 * Move Here along a known exit for {@code command} without creating tiles.
	 *
	 * @return true if current position changed
	 */
	private boolean advanceAlongExistingExit(final String command) {
		if (mMap == null) {
			return false;
		}
		MapTile from = currentTile();
		if (from == null) {
			return false;
		}
		String norm = normalize(command);
		if (norm.length() == 0) {
			return false;
		}
		MapExit existing = findExit(from, norm);
		if (existing == null) {
			return false;
		}
		String portal = existing.getTargetMap();
		if (portal != null && portal.trim().length() > 0) {
			openMap(portal.trim());
			return true;
		}
		if (existing.getToId() == null) {
			return false;
		}
		MapTile to = mMap.findTile(existing.getToId());
		if (to == null) {
			return false;
		}
		if (to.getId().equals(from.getId())) {
			return false;
		}
		mMap.setCurrentTileId(to.getId());
		mMap.setCurrentLevelId(to.getLevelId());
		return true;
	}

	/**
	 * Sync from GMCP Room.* payload when mapper_use_gmcp is on.
	 * <p>
	 * Independent of Record/Draw. Policy {@link #GMCP_POLICY_FOLLOW} only jumps to
	 * an existing room (by num) and may update its unlocked title — no new
	 * tiles/exits. Locked title/position are never overwritten. Title/position
	 * conflicts may queue a pending prompt for the UI.
	 */
	public void onGmcpRoom(final String name, final String roomNum,
			final Integer x, final Integer y, final Integer z,
			final List<String> exits, final Map<String, String> exitDestNums) {
		if (!mEnabled || !mUseGmcp || mMap == null) {
			return;
		}
		ensureDefaultLevel();
		boolean changed = false;
		String levelId = mMap.getCurrentLevelId();
		if (z != null && mGmcpGrow) {
			String levelName = Integer.toString(z.intValue());
			MapLevel level = findLevelByName(levelName);
			if (level == null) {
				pushUndo();
				level = addLevel(levelName);
				changed = true;
			}
			levelId = level.getId();
			if (!levelId.equals(mMap.getCurrentLevelId())) {
				mMap.setCurrentLevelId(levelId);
				changed = true;
			}
		}
		if (levelId == null) {
			levelId = mMap.getCurrentLevelId();
		}

		MapTile current = currentTile();
		MapTile tile = null;
		// Read once for the whole arrival: it decides where a new room is put and
		// then, further down, which exit this walk has proved.
		final RememberedMove moved = consumeLastMove();
		final String movedBy = moved != null ? moved.command : null;
		// Where the move was typed, which is not necessarily where we are: Follow
		// has already stepped us along the exit by now.
		final MapTile movedFrom = moved != null && moved.fromTileId != null
				? findTileById(moved.fromTileId) : current;
		String num = (mGmcpUseNum && roomNum != null && roomNum.length() > 0)
				? roomNum.trim() : null;

		if (num != null) {
			tile = findTileByExternalId(num);
		}
		if (tile == null && mGmcpUseCoords && x != null && y != null) {
			tile = findTileAt(levelId, x.intValue(), y.intValue());
		}
		if (tile == null && num == null) {
			// No identity at all: no room number from the server, or the player
			// turned matching by number off. Without this, nothing can ever match
			// an arrival to a tile that already exists, so grow mode treats every
			// revisit as a discovery and pacing between two rooms fills the map.
			tile = findRevisitedTile(movedFrom, movedBy, levelId, name);
		}

		if (tile == null) {
			if (!mGmcpGrow) {
				// Follow-only: stay put when the room is unknown.
				if (name != null && name.length() > 0 && current != null
						&& !current.isLockTitle()
						&& (current.getTitle() == null || !name.equals(current.getTitle()))) {
					if (maybeApplyOrQueueTitle(current, name)) {
						changed = true;
					}
				}
				if (changed) {
					notifyChanged();
				} else if (mFollowPlayer) {
					notifyChanged();
				}
				return;
			}
			if (mGmcpUseCoords && x != null && y != null
					&& canPlaceAtAbsolute(current, x.intValue(), y.intValue())) {
				pushUndo();
				tile = createTileAt(levelId, x.intValue(), y.intValue());
				changed = true;
			} else if (num != null && current != null
					&& (current.getExternalId() == null
							|| current.getExternalId().length() == 0)) {
				tile = current;
			} else if (current != null) {
				pushUndo();
				int[] delta = gridDeltaFor(movedBy);
				int[] slot = null;
				// The direction the player actually walked beats anything inferred
				// from exit lists. Only when it is unknown or the cell is taken do
				// we fall back to guessing.
				if (delta != null) {
					int nx = current.getGridX() + delta[0];
					int ny = current.getGridY() + delta[1];
					if (findTileAt(levelId, nx, ny) == null) {
						slot = new int[] { nx, ny };
					}
				}
				if (slot == null) {
					slot = preferAdjacentSlot(current, levelId, num, exitDestNums, movedBy);
				}
				if (slot == null) {
					slot = findFreeNear(levelId, current.getGridX(), current.getGridY(), delta);
				}
				tile = createTileAt(levelId, slot[0], slot[1]);
				changed = true;
			} else {
				pushUndo();
				int gx = (mGmcpUseCoords && x != null) ? x.intValue() : 0;
				int gy = (mGmcpUseCoords && y != null) ? y.intValue() : 0;
				tile = createTileAt(levelId, gx, gy);
				changed = true;
			}
		}

		if (tile == null) {
			return;
		}

		// The walk that got us here is the only proof this exit works. Everything
		// else on the map is a claim: the game listed it, or the mapper assumed
		// the way back. On worlds that do not fold neatly those claims are wrong
		// in both directions at once, which is what the map kept insisting on.
		if (movedBy != null && movedFrom != null
				&& !movedFrom.getId().equals(tile.getId())) {
			MapExit walked = findExit(movedFrom, movedBy);
			if (walked == null) {
				if (!changed) {
					pushUndo();
				}
				boolean special = gridDeltaFor(movedBy) == null;
				MapExit proved = new MapExit(movedFrom.getId(), tile.getId(),
						MapDirections.storeCommand(movedBy, movedBy), special, null);
				proved.setVerified(true);
				movedFrom.addExit(proved);
				changed = true;
			} else if (tile.getId().equals(walked.getToId())) {
				if (!walked.isVerified()) {
					if (!changed) {
						pushUndo();
					}
					walked.setVerified(true);
					// It has been walked, so it is no longer anybody's guess.
					walked.setGuessed(false);
					changed = true;
				}
			}
			// An exit for this command pointing somewhere else is left exactly as
			// it is, and stays unverified. Silently re-aiming it is how a wrong
			// link becomes permanent; the conflict detector can raise it instead.
		}

		if (num != null && (tile.getExternalId() == null || !num.equals(tile.getExternalId()))) {
			if (!changed) {
				pushUndo();
			}
			tile.setExternalId(num);
			changed = true;
		}

		if (mGmcpUseCoords && x != null && y != null && mGmcpGrow
				&& !tile.isLockPosition()) {
			int gx = x.intValue();
			int gy = y.intValue();
			boolean levelDiff = levelId != null && !levelId.equals(tile.getLevelId());
			if (tile.getGridX() != gx || tile.getGridY() != gy || levelDiff) {
				boolean stretch = wouldStretchLinks(tile, gx, gy, levelId);
				MapTile occupied = findTileAt(levelId, gx, gy);
				boolean free = occupied == null || occupied.getId().equals(tile.getId());
				if (free && !stretch) {
					if (!changed) {
						pushUndo();
					}
					tile.setGridX(gx);
					tile.setGridY(gy);
					if (levelId != null) {
						tile.setLevelId(levelId);
					}
					changed = true;
				} else if (free && stretch) {
					if (maybeApplyOrQueuePosition(tile, gx, gy, levelId)) {
						changed = true;
					}
				}
			}
		}

		if (name != null && name.length() > 0 && !tile.isLockTitle()
				&& (tile.getTitle() == null || !name.equals(tile.getTitle()))) {
			if (maybeApplyOrQueueTitle(tile, name)) {
				changed = true;
			}
		}

		if (current != null && !tile.getId().equals(current.getId())) {
			addGmcpMismatch(current, tile, name);
		}
		if (!tile.getId().equals(mMap.getCurrentTileId())) {
			mMap.setCurrentTileId(tile.getId());
			changed = true;
		}
		if (tile.getLevelId() != null
				&& !tile.getLevelId().equals(mMap.getCurrentLevelId())) {
			mMap.setCurrentLevelId(tile.getLevelId());
			changed = true;
		}

		if (mGmcpGrow && mGmcpCreateExits && exits != null && exits.size() > 0) {
			if (syncExitsFromGmcp(tile, exits, exitDestNums)) {
				changed = true;
			}
		}

		if (changed) {
			refreshConflicts();
			notifyChanged();
		} else if (mFollowPlayer || mPendingGmcpConflict != null) {
			notifyChanged();
		}
	}

	/**
	 * @return true if title was applied now
	 */
	private boolean maybeApplyOrQueueTitle(final MapTile tile, final String name) {
		if (tile == null || name == null || name.length() == 0 || tile.isLockTitle()) {
			return false;
		}
		boolean empty = tile.getTitle() == null || tile.getTitle().trim().length() == 0;
		String choice = conflictChoiceFor(tile.getId());
		if ("keep".equals(choice)) {
			return false;
		}
		boolean applyNow = empty
				|| GMCP_POLICY_STRICT.equals(mGmcpPolicy)
				|| "apply".equals(choice)
				|| mGmcpConflictApplyAll;
		if (applyNow) {
			pushUndo();
			tile.setTitle(name);
			return true;
		}
		// sync/follow with existing different title → prompt unless already pending
		queueGmcpConflict(tile, "title",
				"GMCP title differs: \"" + safeShort(tile.getTitle()) + "\" → \""
						+ safeShort(name) + "\"",
				name, null, null, null);
		return false;
	}

	/**
	 * @return true if position was applied now
	 */
	private boolean maybeApplyOrQueuePosition(final MapTile tile, final int gx,
			final int gy, final String levelId) {
		if (tile == null || tile.isLockPosition()) {
			return false;
		}
		String choice = conflictChoiceFor(tile.getId());
		if ("keep".equals(choice) || mGmcpConflictKeepAll) {
			return false;
		}
		if ("apply".equals(choice) || mGmcpConflictApplyAll) {
			pushUndo();
			tile.setGridX(gx);
			tile.setGridY(gy);
			if (levelId != null) {
				tile.setLevelId(levelId);
			}
			return true;
		}
		queueGmcpConflict(tile, "position",
				"GMCP wants to move room (may stretch exits) to (" + gx + "," + gy + ")",
				null, Integer.valueOf(gx), Integer.valueOf(gy), levelId);
		return false;
	}

	private String conflictChoiceFor(final String tileId) {
		if (tileId == null) {
			return null;
		}
		if (mGmcpConflictKeepAll) {
			return "keep";
		}
		if (mGmcpConflictApplyAll) {
			return "apply";
		}
		return mGmcpConflictTileChoice.get(tileId);
	}

	private void queueGmcpConflict(final MapTile tile, final String kind,
			final String message, final String proposedTitle,
			final Integer proposedX, final Integer proposedY,
			final String proposedLevelId) {
		if (tile == null || mPendingGmcpConflict != null) {
			return;
		}
		PendingGmcpConflict c = new PendingGmcpConflict();
		c.tileId = tile.getId();
		c.kind = kind;
		c.message = message;
		c.proposedTitle = proposedTitle;
		c.proposedX = proposedX;
		c.proposedY = proposedY;
		c.proposedLevelId = proposedLevelId;
		mPendingGmcpConflict = c;
		MapConflict recorded = new MapConflict(MapConflict.Type.GMCP_LAYOUT,
				message, java.util.Collections.singletonList(tile.getId()));
		mMap.getConflicts().add(recorded);
	}

	private static String safeShort(final String s) {
		if (s == null) {
			return "";
		}
		return s.length() > 40 ? s.substring(0, 40) + "…" : s;
	}

	/**
	 * Absolute coords are usable when there is no previous room, or the jump is
	 * at most one cell (true grid MUDs). Larger jumps (Eden world coords) grow
	 * topologically instead — avoids long W/E arrows across the screen.
	 */
	private boolean canPlaceAtAbsolute(final MapTile from, final int gx, final int gy) {
		if (from == null) {
			return true;
		}
		return chebyshev(from.getGridX(), from.getGridY(), gx, gy) <= 1;
	}

	private static int chebyshev(final int x0, final int y0, final int x1, final int y1) {
		return Math.max(Math.abs(x0 - x1), Math.abs(y0 - y1));
	}

	/** True if moving {@code tile} to (gx,gy) would leave a compass link spanning &gt; 1 cell. */
	private boolean wouldStretchLinks(final MapTile tile, final int gx, final int gy,
			final String levelId) {
		if (tile == null || mMap == null) {
			return false;
		}
		for (MapExit e : tile.getExits()) {
			if (e == null || e.getToId() == null) {
				continue;
			}
			MapTile dest = findTileById(e.getToId());
			if (dest == null) {
				continue;
			}
			if (levelId != null && dest.getLevelId() != null
					&& !levelId.equals(dest.getLevelId())) {
				continue;
			}
			if (chebyshev(gx, gy, dest.getGridX(), dest.getGridY()) > 1) {
				return true;
			}
		}
		for (MapTile other : mMap.getTiles()) {
			if (other == null || other.getId().equals(tile.getId())) {
				continue;
			}
			for (MapExit e : other.getExits()) {
				if (e != null && tile.getId().equals(e.getToId())) {
					if (chebyshev(other.getGridX(), other.getGridY(), gx, gy) > 1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * If the previous room already has an exit toward this room num, place on
	 * that compass neighbor cell; else null.
	 */
	private int[] preferAdjacentSlot(final MapTile from, final String levelId,
			final String destNum, final Map<String, String> exitDestNums,
			final String movedBy) {
		if (from == null) {
			return null;
		}
		// Exit on from already pointing at a stub without coords match — use delta.
		for (MapExit e : from.getExits()) {
			if (e == null) {
				continue;
			}
			MapTile dest = findTileById(e.getToId());
			if (dest != null && destNum != null && destNum.equals(dest.getExternalId())) {
				return new int[] { dest.getGridX(), dest.getGridY() };
			}
			// Only the stub for the exit we actually walked. This used to accept
			// any exit with a direction at all, so which cell won came down to
			// getExits() order -- a room entered by w could land on the cell that
			// belonged to the s stub, and the link drew straight down.
			if (movedBy == null || !movedBy.equals(normalize(e.getCommand()))) {
				continue;
			}
			int[] delta = gridDeltaFor(normalize(e.getCommand()));
			if (delta != null && destNum != null && dest != null
					&& (dest.getExternalId() == null || dest.getExternalId().length() == 0)) {
				// keep existing stub cell
				return new int[] { dest.getGridX(), dest.getGridY() };
			}
		}
		// Infer from new room exits listing the previous room: place on the
		// opposite compass cell (new.W→from ⇒ new is east of from).
		if (destNum != null && exitDestNums != null && from.getExternalId() != null) {
			for (Map.Entry<String, String> en : exitDestNums.entrySet()) {
				if (en.getValue() == null || !en.getValue().equals(from.getExternalId())) {
					continue;
				}
				String towardNew = MapDirections.suggestReverse(en.getKey(), directionMap());
				if (towardNew == null) {
					towardNew = en.getKey();
				}
				int[] delta = gridDeltaFor(normalize(towardNew));
				if (delta == null) {
					continue;
				}
				int nx = from.getGridX() + delta[0];
				int ny = from.getGridY() + delta[1];
				if (findTileAt(levelId, nx, ny) == null) {
					return new int[] { nx, ny };
				}
			}
		}
		return null;
	}

	/**
	 * Ensure each GMCP exit exists on {@code current}. Existing exits are kept
	 * (destinations never wiped). Missing exits get a destination via room num
	 * stub or {@link #createDestination}. Exits on the tile that are absent from
	 * GMCP are left alone.
	 *
	 * @return true if any exit or tile was added
	 */
	private boolean syncExitsFromGmcp(final MapTile current, final List<String> exits,
			final Map<String, String> exitDestNums) {
		List<String> missing = new ArrayList<String>();
		boolean changedStamp = false;
		// The game has just told us what this room offers, so anything the mapper
		// invented for it and the game does not list was a bad guess. Undo is
		// pushed first so it captures the state before the withdrawal.
		List<MapExit> badGuesses = guessedExitsContradictedBy(current, exits);
		if (!badGuesses.isEmpty()) {
			pushUndo();
			changedStamp = true;
			current.getExits().removeAll(badGuesses);
		}
		for (String ex : exits) {
			if (ex == null || ex.trim().length() == 0) {
				continue;
			}
			String norm = normalize(ex);
			if (norm.length() == 0) {
				continue;
			}
			if (findExit(current, norm) == null) {
				missing.add(ex);
			} else if (exitDestNums != null && mGmcpUseNum) {
				String destNum = exitDestNums.get(norm);
				if (destNum == null) {
					destNum = exitDestNums.get(ex);
				}
				if (destNum != null) {
					MapExit existing = findExit(current, norm);
					MapTile dest = existing != null ? findTileById(existing.getToId()) : null;
					if (dest != null && (dest.getExternalId() == null
							|| dest.getExternalId().length() == 0)) {
						if (!changedStamp) {
							pushUndo();
							changedStamp = true;
						}
						dest.setExternalId(destNum);
					}
				}
			}
		}
		boolean changed = changedStamp;
		if (missing.isEmpty()) {
			return changed;
		}
		if (!changedStamp) {
			pushUndo();
		}
		for (String ex : missing) {
			String norm = normalize(ex);
			if (norm.length() == 0 || findExit(current, norm) != null) {
				continue;
			}
			String stored = MapDirections.storeCommand(ex, norm);
			String destNum = null;
			if (exitDestNums != null) {
				destNum = exitDestNums.get(norm);
				if (destNum == null) {
					destNum = exitDestNums.get(ex);
				}
				if (destNum == null) {
					for (Map.Entry<String, String> e : exitDestNums.entrySet()) {
						if (e.getKey() != null && normalize(e.getKey()).equals(norm)) {
							destNum = e.getValue();
							break;
						}
					}
				}
			}
			MapTile to = null;
			if (destNum != null && mGmcpUseNum) {
				to = findTileByExternalId(destNum);
			}
			if (to == null) {
				int[] delta = gridDeltaFor(norm);
				boolean special = (delta == null);
				to = createDestination(current, norm, delta, special);
				if (destNum != null && mGmcpUseNum) {
					to.setExternalId(destNum);
				}
			}
			String rev = MapDirections.suggestReverse(stored, directionMap());
			boolean special = gridDeltaFor(norm) == null;
			current.addExit(new MapExit(current.getId(), to.getId(), stored, special, rev));
			if (mAutoReverse && rev != null) {
				ensureReverse(current, to, stored);
			}
			changed = true;
		}
		return changed;
	}

	/**
	 * Parse a GMCP body, tolerating a server that percent-encodes structure.
	 *
	 * <p>eden-test sends the exits object with its braces escaped —
	 * {@code "exits":%7B"S":1001019%7D} — which is not JSON, so the strict parse
	 * threw and the surrounding catch dropped the <em>whole</em> room: no name,
	 * no coordinates, no exits, no warning. GMCP room sync was simply dead on
	 * that world.
	 *
	 * <p>A well-formed payload is parsed by the first attempt and never reaches
	 * the repair, so nothing changes for servers that were already working. The
	 * repair only puts back the four structural characters; text is left alone,
	 * so a description containing a literal percent sign is unharmed.
	 *
	 * @param jsonBody Raw GMCP payload.
	 * @return The parsed object, or null if it is not recoverable.
	 */
	static JSONObject parseLoosely(final String jsonBody) {
		if (jsonBody == null) {
			return null;
		}
		try {
			return new JSONObject(jsonBody);
		} catch (JSONException strictFailed) {
			String repaired = jsonBody
					.replace("%7B", "{").replace("%7b", "{")
					.replace("%7D", "}").replace("%7d", "}")
					.replace("%5B", "[").replace("%5b", "[")
					.replace("%5D", "]").replace("%5d", "]");
			if (repaired.equals(jsonBody)) {
				return null;
			}
			try {
				JSONObject out = new JSONObject(repaired);
				// Worth a line in logcat: it means the server is sending something
				// it should not, and the next person to wonder why a world maps
				// oddly should be able to see it.
				android.util.Log.w("BlowTorch",
						"[MapperController.parseLoosely] GMCP payload had percent-encoded"
						+ " braces; parsed after repair");
				return out;
			} catch (JSONException stillBad) {
				return null;
			}
		}
	}

	/** Parse a GMCP Room.* JSON body and forward to {@link #onGmcpRoom}. */
	public void onGmcpRoomRaw(final String module, final String jsonBody) {
		if (!mEnabled || !mUseGmcp || jsonBody == null) {
			return;
		}
		try {
			JSONObject body = parseLoosely(jsonBody);
			if (body == null) {
				return;
			}
			JSONObject info = body;
			if (body.has("info") && body.opt("info") instanceof JSONObject) {
				info = body.getJSONObject("info");
			}
			String name = firstString(info, "name", "short", "title", "roomname");
			if (name == null) {
				name = firstString(body, "name", "short", "title");
			}
			String roomNum = firstRoomNum(info);
			if (roomNum == null) {
				roomNum = firstRoomNum(body);
			}
			Integer x = null;
			Integer y = null;
			Integer z = null;
			Integer[] xyz = parseRoomCoords(info);
			if (xyz == null) {
				xyz = parseRoomCoords(body);
			}
			if (xyz != null) {
				x = xyz[0];
				y = xyz[1];
				z = xyz[2];
			}
			// Rooms carry the map file their coordinates belong to. Areas with
			// their own file number their own space from scratch: a cave room
			// reached by going down reports z=0 in its map, which read against
			// the surface says the cellar's cellar is back up at ground level.
			// That contradiction is what kept raising conflicts, so coordinates
			// are only believed inside the space they were measured in.
			String space = firstString(info, "map");
			if (space == null) {
				space = firstString(body, "map");
			}
			if (space != null && space.length() > 0) {
				if (mHomeMapSpace == null) {
					mHomeMapSpace = space;
				} else if (!mHomeMapSpace.equals(space)) {
					x = null;
					y = null;
					z = null;
				}
			}
			List<String> exits = parseExits(info);
			if (exits.isEmpty()) {
				exits = parseExits(body);
			}
			Map<String, String> exitDests = parseExitDestinations(info);
			if (exitDests.isEmpty()) {
				exitDests = parseExitDestinations(body);
			}
			onGmcpRoom(name, roomNum, x, y, z, exits, exitDests);
		} catch (JSONException ignored) {
			// Loose parse: ignore malformed Room payloads
		}
	}

	/**
	 * Identity of last resort for a world that hands out no room numbers.
	 *
	 * <p>Only ever considers the one tile the walk points at: the destination
	 * already recorded for the exit taken, or failing that the neighbouring cell
	 * in the direction walked. Room names are not unique across a world and can
	 * change over time, so this deliberately never searches the whole map for a
	 * matching title — a wrong merge is far harder for a player to notice, and
	 * to undo, than a duplicate tile.
	 *
	 * @param from The tile the move was typed in, or null if unknown.
	 * @param movedBy The command walked, or null if the arrival was not a walk.
	 * @param levelId Level to look on for the neighbouring cell.
	 * @param name Room title reported by the server.
	 * @return The tile we have evidently walked back into, or null.
	 */
	private MapTile findRevisitedTile(final MapTile from, final String movedBy,
			final String levelId, final String name) {
		if (from == null || movedBy == null || name == null || name.length() == 0) {
			return null;
		}
		MapExit walked = findExit(from, movedBy);
		if (walked != null && walked.getToId() != null) {
			MapTile dest = findTileById(walked.getToId());
			if (isTileWeWalkedInto(dest, name)) {
				return dest;
			}
		}
		int[] delta = gridDeltaFor(movedBy);
		if (delta == null) {
			return null;
		}
		MapTile neighbour = findTileAt(levelId,
				from.getGridX() + delta[0], from.getGridY() + delta[1]);
		if (isTileWeWalkedInto(neighbour, name)) {
			return neighbour;
		}
		return null;
	}

	/**
	 * Whether a tile the walk points at is the room we have just arrived in.
	 *
	 * <p>True when the title matches, and also when the tile has no title at
	 * all: arriving from an exit list creates a bare stub for every listed
	 * direction, and the stub for the exit we then walked <em>is</em> this room.
	 * Not claiming it left the stub orphaned forever and put the real room in
	 * the next cell along, one stray tile per exit per move.
	 */
	private static boolean isTileWeWalkedInto(final MapTile tile, final String name) {
		if (tile == null) {
			return false;
		}
		String title = tile.getTitle();
		if (title == null || title.trim().length() == 0) {
			return true;
		}
		return title.equals(name);
	}

	/** GMCP room identity: {@code num}, {@code id}, {@code vnum}, {@code room}. */
	static String firstRoomNum(final JSONObject o) {
		if (o == null) {
			return null;
		}
		String[] keys = new String[] { "num", "id", "vnum", "room", "roomnum", "room_id" };
		for (String key : keys) {
			if (!o.has(key) || o.isNull(key)) {
				continue;
			}
			Object v = o.opt(key);
			if (v instanceof Number) {
				return Long.toString(((Number) v).longValue());
			}
			String s = String.valueOf(v).trim();
			if (s.length() > 0 && !"null".equals(s)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Extract x/y/z from common GMCP shapes:
	 * {@code coords}/{@code coord} object, top-level x/y/z, or string
	 * {@code "x,y"} / {@code "id,x,y,z"} / {@code "x,y,z"}.
	 *
	 * @return {@code [x, y, z]} with nulls for missing axes, or null if none
	 */
	static Integer[] parseRoomCoords(final JSONObject info) {
		if (info == null) {
			return null;
		}
		JSONObject c = null;
		if (info.has("coords") && info.opt("coords") instanceof JSONObject) {
			c = info.optJSONObject("coords");
		} else if (info.has("coord") && info.opt("coord") instanceof JSONObject) {
			c = info.optJSONObject("coord");
		} else if (info.has("coords") && info.opt("coords") instanceof String) {
			return parseCoordsString(info.optString("coords", null));
		} else if (info.has("coord") && info.opt("coord") instanceof String) {
			return parseCoordsString(info.optString("coord", null));
		}
		if (c != null) {
			Integer x = c.has("x") ? Integer.valueOf(c.optInt("x")) : null;
			Integer y = c.has("y") ? Integer.valueOf(c.optInt("y")) : null;
			Integer z = c.has("z") ? Integer.valueOf(c.optInt("z")) : null;
			if (x != null || y != null || z != null) {
				return new Integer[] { x, y, z };
			}
		}
		Integer x = info.has("x") ? Integer.valueOf(info.optInt("x")) : null;
		Integer y = info.has("y") ? Integer.valueOf(info.optInt("y")) : null;
		Integer z = info.has("z") ? Integer.valueOf(info.optInt("z")) : null;
		if (x != null || y != null || z != null) {
			return new Integer[] { x, y, z };
		}
		return null;
	}

	static Integer[] parseCoordsString(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return null;
		}
		String[] parts = s.split("[,;\\s]+");
		if (parts.length < 2) {
			return null;
		}
		try {
			if (parts.length >= 4) {
				// id,x,y,z
				return new Integer[] {
						Integer.valueOf(parts[1]),
						Integer.valueOf(parts[2]),
						Integer.valueOf(parts[3])
				};
			}
			if (parts.length == 3) {
				return new Integer[] {
						Integer.valueOf(parts[0]),
						Integer.valueOf(parts[1]),
						Integer.valueOf(parts[2])
				};
			}
			return new Integer[] {
					Integer.valueOf(parts[0]),
					Integer.valueOf(parts[1]),
					null
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Direction → destination room id from an exits object map
	 * ({@code {"n":123,"e":456}} or {@code {"north":"abc"}}). Empty if exits is
	 * an array/string without destinations.
	 */
	static Map<String, String> parseExitDestinations(final JSONObject o) {
		Map<String, String> out = new LinkedHashMap<String, String>();
		if (o == null) {
			return out;
		}
		Object ex = null;
		if (o.has("exits") && !o.isNull("exits")) {
			ex = o.opt("exits");
		} else if (o.has("exit") && !o.isNull("exit")) {
			ex = o.opt("exit");
		}
		if (!(ex instanceof JSONObject)) {
			return out;
		}
		JSONObject jo = (JSONObject) ex;
		if (jo.has("exits") && !jo.isNull("exits") && jo.opt("exits") instanceof JSONObject) {
			jo = jo.optJSONObject("exits");
		}
		if (jo == null) {
			return out;
		}
		Iterator<?> keys = jo.keys();
		while (keys.hasNext()) {
			Object k = keys.next();
			if (k == null) {
				continue;
			}
			String dir = String.valueOf(k).trim();
			if (dir.length() == 0 || "exits".equalsIgnoreCase(dir)) {
				continue;
			}
			Object dest = jo.opt(dir);
			if (dest == null || dest == JSONObject.NULL) {
				continue;
			}
			String destId;
			if (dest instanceof Number) {
				destId = Long.toString(((Number) dest).longValue());
			} else if (dest instanceof JSONObject) {
				destId = firstRoomNum((JSONObject) dest);
				if (destId == null) {
					continue;
				}
			} else {
				destId = String.valueOf(dest).trim();
				if (destId.length() == 0 || "null".equals(destId)) {
					continue;
				}
			}
			out.put(dir.toLowerCase(Locale.US), destId);
		}
		return out;
	}

	private MapTile findTileByExternalId(final String externalId) {
		if (mMap == null || externalId == null || externalId.length() == 0) {
			return null;
		}
		for (MapTile tile : mMap.getTiles()) {
			if (tile != null && externalId.equals(tile.getExternalId())) {
				return tile;
			}
		}
		return null;
	}

	private MapTile findTileById(final String id) {
		if (mMap == null || id == null) {
			return null;
		}
		for (MapTile tile : mMap.getTiles()) {
			if (tile != null && id.equals(tile.getId())) {
				return tile;
			}
		}
		return null;
	}

	public List<String> findPath(final String query) {
		if (mMap == null || TextUtils.isEmpty(query)) {
			return Collections.emptyList();
		}
		List<MapTile> hits = search(query);
		if (hits.isEmpty()) {
			return Collections.emptyList();
		}
		MapTile dest = hits.get(0);
		String from = mMap.getCurrentTileId();
		if (from == null) {
			return Collections.emptyList();
		}
		return MapPathfinder.findCommands(mMap, from, dest.getId());
	}

	public List<MapTile> search(final String query) {
		List<MapTile> out = new ArrayList<MapTile>();
		if (mMap == null || TextUtils.isEmpty(query)) {
			return out;
		}
		String q = query.trim().toLowerCase(Locale.US);
		for (MapTile tile : mMap.getTiles()) {
			if (tile == null) {
				continue;
			}
			String title = tile.getTitle() != null ? tile.getTitle().toLowerCase(Locale.US) : "";
			String notes = tile.getNotes() != null ? tile.getNotes().toLowerCase(Locale.US) : "";
			String id = tile.getId() != null ? tile.getId().toLowerCase(Locale.US) : "";
			if (title.contains(q) || notes.contains(q) || id.startsWith(q)) {
				out.add(tile);
			}
		}
		return out;
	}

	public String setTitle(final String text) {
		return setTitle(null, text);
	}

	/** Set title on {@code tileId}, or current tile when tileId is null/empty. */
	public String setTitle(final String tileId, final String text) {
		MapTile t = resolveEditTile(tileId);
		if (t == null) {
			return "Mapper: no tile.";
		}
		pushUndo();
		t.setTitle(text);
		notifyChanged();
		return "Mapper: title set on " + shortId(t.getId()) + ".";
	}

	public String setNotes(final String text) {
		return setNotes(null, text);
	}

	/** Set notes on {@code tileId}, or current tile when tileId is null/empty. */
	public String setNotes(final String tileId, final String text) {
		MapTile t = resolveEditTile(tileId);
		if (t == null) {
			return "Mapper: no tile.";
		}
		pushUndo();
		t.setNotes(text);
		notifyChanged();
		return "Mapper: notes set on " + shortId(t.getId()) + ".";
	}

	/**
	 * Lock/unlock GMCP title overwrite on {@code tileId}, or current when null/empty.
	 */
	public String setLockTitle(final String tileId, final boolean locked) {
		MapTile t = resolveEditTile(tileId);
		if (t == null) {
			return "Mapper: no tile.";
		}
		pushUndo();
		t.setLockTitle(locked);
		notifyChanged();
		return "Mapper: lock title " + (locked ? "on" : "off")
				+ " for " + shortId(t.getId()) + ".";
	}

	/**
	 * Lock/unlock GMCP position overwrite on {@code tileId}, or current when null/empty.
	 */
	public String setLockPosition(final String tileId, final boolean locked) {
		MapTile t = resolveEditTile(tileId);
		if (t == null) {
			return "Mapper: no tile.";
		}
		pushUndo();
		t.setLockPosition(locked);
		notifyChanged();
		return "Mapper: lock position " + (locked ? "on" : "off")
				+ " for " + shortId(t.getId()) + ".";
	}

	private MapTile resolveEditTile(final String tileId) {
		if (mMap == null) {
			return null;
		}
		if (tileId != null && tileId.trim().length() > 0) {
			return mMap.findTile(tileId.trim());
		}
		return currentTile();
	}

	public String link(final String cmd, final String toTileId) {
		return linkBetween(null, cmd, toTileId);
	}

	/**
	 * Link {@code fromTileId} (or current tile when null) to {@code toTileId}
	 * with movement command {@code cmd}.
	 */
	public String linkBetween(final String fromTileId, final String cmd,
			final String toTileId) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		MapTile from = fromTileId != null ? mMap.findTile(fromTileId) : currentTile();
		if (from == null) {
			return "Mapper: no source tile.";
		}
		if (TextUtils.isEmpty(cmd)) {
			return "Mapper: usage .map link <cmd> [from <id>] to <tileId>";
		}
		if (TextUtils.isEmpty(toTileId)) {
			return "Mapper: provide target tile id.";
		}
		MapTile to = mMap.findTile(toTileId);
		if (to == null) {
			return "Mapper: unknown tile id.";
		}
		String norm = normalize(cmd);
		String stored = MapDirections.storeCommand(cmd, norm);
		pushUndo();
		MapExit existing = findExit(from, norm);
		if (existing != null) {
			existing.setToId(to.getId());
			existing.setCommand(stored);
		} else {
			String rev = MapDirections.suggestReverse(stored, directionMap());
			boolean special = gridDeltaFor(norm) == null;
			from.addExit(new MapExit(from.getId(), to.getId(), stored, special, rev));
		}
		if (mAutoReverse) {
			ensureReverse(from, to, stored);
		}
		refreshConflicts();
		notifyChanged();
		return "Mapper: linked " + stored + " → " + shortId(to.getId()) + ".";
	}

	public String unlink(final String cmd) {
		return unlinkBetween(null, cmd);
	}

	public String unlinkBetween(final String fromTileId, final String cmd) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		MapTile from = fromTileId != null ? mMap.findTile(fromTileId) : currentTile();
		if (from == null) {
			return "Mapper: no source tile.";
		}
		String norm = normalize(cmd);
		pushUndo();
		List<MapExit> exits = from.getExits();
		boolean removed = false;
		for (int i = exits.size() - 1; i >= 0; i--) {
			MapExit e = exits.get(i);
			if (e != null && norm.equalsIgnoreCase(normalize(e.getCommand()))) {
				exits.remove(i);
				removed = true;
			}
		}
		refreshConflicts();
		notifyChanged();
		return removed ? "Mapper: unlinked " + cmd + "."
				: "Mapper: no exit \"" + cmd + "\" on tile.";
	}

	public String relink(final String cmd, final String toTileId) {
		return link(cmd, toTileId);
	}

	public String moveTileLevel(final String tileId, final String levelName) {
		MapTile tile = tileId != null ? mMap.findTile(tileId) : currentTile();
		if (tile == null) {
			return "Mapper: no tile.";
		}
		if (TextUtils.isEmpty(levelName)) {
			return "Mapper: level name required.";
		}
		pushUndo();
		MapLevel level = findLevelByName(levelName);
		if (level == null) {
			level = addLevel(levelName.trim());
		}
		tile.setLevelId(level.getId());
		if (tile.getId().equals(mMap.getCurrentTileId())) {
			mMap.setCurrentLevelId(level.getId());
		}
		refreshConflicts();
		notifyChanged();
		return "Mapper: moved tile to level \"" + level.getName() + "\".";
	}

	/**
	 * Rename a level by id or current name. {@code newName} must be unique
	 * among other levels (case-insensitive).
	 */
	public String renameLevel(final String idOrName, final String newName) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(idOrName)) {
			return "Mapper: usage .map level rename <id|name> <newName>";
		}
		if (TextUtils.isEmpty(newName)) {
			return "Mapper: new level name required.";
		}
		String key = idOrName.trim();
		MapLevel level = mMap.findLevel(key);
		if (level == null) {
			level = findLevelByName(key);
		}
		if (level == null) {
			return "Mapper: unknown level.";
		}
		String trimmed = newName.trim();
		if (trimmed.length() == 0) {
			return "Mapper: new level name required.";
		}
		MapLevel clash = findLevelByName(trimmed);
		if (clash != null && !clash.getId().equals(level.getId())) {
			return "Mapper: level \"" + trimmed + "\" already exists.";
		}
		pushUndo();
		String old = level.getName() != null ? level.getName() : key;
		level.setName(trimmed);
		notifyChanged();
		return "Mapper: renamed \"" + old + "\" → \"" + trimmed + "\".";
	}

	public String levelList() {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper levels:\n");
		List<MapLevel> sorted = sortedLevels();
		for (MapLevel level : sorted) {
			boolean cur = level.getId().equals(mMap.getCurrentLevelId());
			sb.append(cur ? " * " : "   ");
			sb.append(level.getIndex()).append(": ").append(level.getName());
			String anchor = formatAnchorSummary(level);
			if (anchor.length() > 0) {
				sb.append("  (").append(anchor).append(")");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	/** L−: nest follow/create in the {@code down} direction from Here. */
	public String levelPrev() {
		return shiftLevel("down");
	}

	/** L+: nest follow/create in the {@code up} direction from Here. */
	public String levelNext() {
		return shiftLevel("up");
	}

	public String levelSet(final String name) {
		if (TextUtils.isEmpty(name)) {
			return "Mapper: usage .map level set <name>";
		}
		MapLevel level = findLevelByName(name.trim());
		if (level == null) {
			pushUndo();
			level = addLevel(name.trim());
		}
		mMap.setCurrentLevelId(level.getId());
		MapTile cur = currentTile();
		if (cur != null && !level.getId().equals(cur.getLevelId())) {
			MapTile at = findTileAt(level.getId(), cur.getGridX(), cur.getGridY());
			if (at != null) {
				mMap.setCurrentTileId(at.getId());
			}
		}
		notifyChanged();
		return "Mapper: level \"" + level.getName() + "\".";
	}

	/**
	 * View a level without moving Here. Sets {@code currentLevelId} only;
	 * if Here is not on that floor it stays selected but will not paint until
	 * the player picks a tile on the viewed level.
	 */
	public String browseLevel(final String levelId) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(levelId)) {
			return "Mapper: level id required.";
		}
		MapLevel level = mMap.findLevel(levelId);
		if (level == null) {
			return "Mapper: unknown level.";
		}
		mMap.setCurrentLevelId(level.getId());
		notifyChanged();
		return "Mapper: viewing level \"" + level.getName() + "\".";
	}

	/** Formatted level browser lines for UI (name, tiles, anchor). */
	public List<String> levelBrowserLines() {
		List<String> lines = new ArrayList<String>();
		for (LevelInfo info : listLevelInfo()) {
			StringBuilder sb = new StringBuilder();
			sb.append(info.name != null ? info.name : "?");
			sb.append(" · ").append(info.tileCount).append(" tiles");
			if (info.anchorSummary != null && info.anchorSummary.length() > 0) {
				sb.append(" · ").append(info.anchorSummary);
			}
			lines.add(sb.toString());
		}
		return lines;
	}

	/** Structured level list for UI browsers. */
	public List<LevelInfo> listLevelInfo() {
		List<LevelInfo> out = new ArrayList<LevelInfo>();
		if (mMap == null) {
			return out;
		}
		for (MapLevel level : sortedLevels()) {
			if (level == null) {
				continue;
			}
			LevelInfo info = new LevelInfo();
			info.id = level.getId();
			info.name = level.getName();
			info.index = level.getIndex();
			info.tileCount = countTilesOnLevel(level.getId());
			info.anchorSummary = formatAnchorSummary(level);
			out.add(info);
		}
		return out;
	}

	/**
	 * Switch view to {@code levelId}. When {@code moveHereIfPossible}, also set
	 * Here to a tile on that level (prefer same grid as current Here, else first).
	 */
	public String goToLevel(final String levelId, final boolean moveHereIfPossible) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(levelId)) {
			return "Mapper: level id required.";
		}
		MapLevel level = mMap.findLevel(levelId);
		if (level == null) {
			return "Mapper: unknown level.";
		}
		mMap.setCurrentLevelId(level.getId());
		if (moveHereIfPossible) {
			MapTile cur = currentTile();
			MapTile dest = null;
			if (cur != null) {
				dest = findTileAt(level.getId(), cur.getGridX(), cur.getGridY());
			}
			if (dest == null) {
				dest = firstTileOnLevel(level.getId());
			}
			if (dest != null) {
				mMap.setCurrentTileId(dest.getId());
			}
		}
		notifyChanged();
		return "Mapper: level \"" + level.getName() + "\".";
	}

	/** Undo last graph mutation. @return true if a state was restored. */
	public boolean undo() {
		if (mUndoStack.isEmpty()) {
			return false;
		}
		String json = mUndoStack.removeLast();
		try {
			mMap = MapStore.fromJson(json);
			notifyChanged();
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	/** Human-readable undo for .map undo. */
	public String undoStatus() {
		return undo() ? "Mapper: undo OK (" + mUndoStack.size() + " left)."
				: "Mapper: nothing to undo.";
	}

	public int getUndoDepth() {
		return mUndoStack.size();
	}

	public List<MapConflict> listConflicts() {
		return conflictsFiltered(false);
	}

	/**
	 * Format conflicts for the window. When {@code includeResolved} is false,
	 * only open (unresolved) conflicts are listed.
	 */
	public String conflictList(final boolean includeResolved) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		List<MapConflict> list = conflictsFiltered(includeResolved);
		if (list.isEmpty()) {
			return includeResolved
					? "Mapper: no conflicts."
					: "Mapper: no open conflicts.";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper conflicts (").append(list.size());
		sb.append(includeResolved ? " total" : " open").append("):\n");
		int n = Math.min(list.size(), 50);
		for (int i = 0; i < n; i++) {
			MapConflict conf = list.get(i);
			sb.append("  ").append(i + 1).append(". [");
			sb.append(conf.getType() != null ? conf.getType().name() : "?");
			sb.append("] ").append(shortId(conf.getId()));
			if (conf.isResolved()) {
				sb.append(" (resolved)");
			}
			sb.append("  ");
			sb.append(conf.getMessage() != null ? conf.getMessage() : "");
			sb.append("\n");
		}
		if (list.size() > n) {
			sb.append("  …\n");
		}
		return sb.toString();
	}

	/** Mark one open conflict resolved (by 1-based index or id prefix). */
	public String resolveConflict(final String idOrIndex) {
		return setConflictResolved(idOrIndex, true);
	}

	/** Alias of {@link #resolveConflict} — marks the conflict resolved/ignored. */
	public String ignoreConflict(final String idOrIndex) {
		return setConflictResolved(idOrIndex, true);
	}

	/** Mark all open conflicts resolved. */
	public String resolveAllConflicts() {
		return setAllConflictsResolved();
	}

	/** Alias of {@link #resolveAllConflicts}. */
	public String ignoreAllConflicts() {
		return setAllConflictsResolved();
	}

	/** Remove resolved conflicts from the map (open ones kept). */
	public String purgeResolved() {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		List<MapConflict> conflicts = mMap.getConflicts();
		int removed = 0;
		Iterator<MapConflict> it = conflicts.iterator();
		while (it.hasNext()) {
			MapConflict c = it.next();
			if (c != null && c.isResolved()) {
				it.remove();
				removed++;
			}
		}
		if (removed > 0) {
			notifyChanged();
		}
		return "Mapper: purged " + removed + " resolved conflict(s).";
	}

	private String setConflictResolved(final String idOrIndex, final boolean resolved) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		if (TextUtils.isEmpty(idOrIndex)) {
			return "Mapper: usage .map conflict resolve|ignore <id|n>";
		}
		MapConflict conf = findConflict(idOrIndex.trim());
		if (conf == null) {
			return "Mapper: conflict not found: " + idOrIndex.trim();
		}
		if (conf.isResolved() == resolved) {
			return "Mapper: conflict " + shortId(conf.getId())
					+ (resolved ? " already resolved." : " already open.");
		}
		conf.setResolved(resolved);
		notifyChanged();
		return "Mapper: conflict " + shortId(conf.getId())
				+ (resolved ? " resolved." : " reopened.");
	}

	private String setAllConflictsResolved() {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		int n = 0;
		for (MapConflict c : mMap.getConflicts()) {
			if (c != null && !c.isResolved()) {
				c.setResolved(true);
				n++;
			}
		}
		if (n > 0) {
			notifyChanged();
		}
		return "Mapper: resolved " + n + " conflict(s).";
	}

	/**
	 * Find an open conflict by 1-based index in the open list, or any conflict
	 * by full id / id prefix (case-insensitive).
	 */
	private MapConflict findConflict(final String idOrIndex) {
		List<MapConflict> open = conflictsFiltered(false);
		try {
			int idx = Integer.parseInt(idOrIndex);
			if (idx >= 1 && idx <= open.size()) {
				return open.get(idx - 1);
			}
		} catch (NumberFormatException ignored) {
		}
		String want = idOrIndex.toLowerCase(Locale.US);
		MapConflict prefixHit = null;
		for (MapConflict c : mMap.getConflicts()) {
			if (c == null || c.getId() == null) {
				continue;
			}
			String id = c.getId().toLowerCase(Locale.US);
			if (id.equals(want)) {
				return c;
			}
			if (id.startsWith(want)) {
				if (prefixHit != null) {
					return null; // ambiguous prefix
				}
				prefixHit = c;
			}
		}
		return prefixHit;
	}

	private List<MapConflict> conflictsFiltered(final boolean includeResolved) {
		if (mMap == null) {
			return Collections.emptyList();
		}
		List<MapConflict> out = new ArrayList<MapConflict>();
		for (MapConflict c : mMap.getConflicts()) {
			if (c == null) {
				continue;
			}
			if (includeResolved || !c.isResolved()) {
				out.add(c);
			}
		}
		return out;
	}

	public String capturePreview(final int maxLines) {
		List<String> lines = lastBufferLines(maxLines > 0 ? maxLines : 20);
		if (lines.isEmpty()) {
			mLastCapturePreview = null;
			return "Mapper capture preview: (buffer empty — open Capture on the map"
					+ " toolbar for custom regex, or wait for more output)";
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				text.append('\n');
			}
			text.append(lines.get(i));
		}
		CapturePreview preview = previewCapture(getCaptureTitleRegex(),
				getCaptureExitsRegex(), text.toString(), lines.size());
		mLastCapturePreview = preview;
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper capture preview (last ").append(lines.size())
				.append(" lines; Options → Mapper capture regex):\n");
		sb.append("  title: ").append(preview.title != null ? preview.title : "(no match)")
				.append("\n");
		sb.append("  exits: ").append(preview.exits != null ? preview.exits : "(no match)")
				.append("\n");
		sb.append("Use .map capture apply to write matches onto the current tile.\n");
		sb.append("For one-off regex, add \"capture\" to Mapper toolbar CSV and use the dialog.");
		return sb.toString();
	}

	public String captureApply() {
		if (mLastCapturePreview == null) {
			capturePreview(20);
		}
		if (mLastCapturePreview == null) {
			return "Mapper capture apply: nothing to apply (empty buffer).";
		}
		boolean hasTitle = mLastCapturePreview.title != null
				&& mLastCapturePreview.title.length() > 0;
		boolean hasExits = mLastCapturePreview.exits != null
				&& mLastCapturePreview.exits.length() > 0;
		if (!hasTitle && !hasExits) {
			return "Mapper capture apply: no title/exits matched — try the Capture dialog"
					+ " (toolbar CSV token \"capture\") with custom regex.";
		}
		applyCapture(mLastCapturePreview);
		StringBuilder sb = new StringBuilder("Mapper capture apply: updated current tile");
		if (hasTitle) {
			sb.append(" title=\"").append(mLastCapturePreview.title).append("\"");
		}
		if (hasExits) {
			sb.append(" exits=\"").append(mLastCapturePreview.exits).append("\"");
		}
		sb.append(".");
		return sb.toString();
	}

	public MapTile currentTile() {
		if (mMap == null || mMap.getCurrentTileId() == null) {
			return null;
		}
		return mMap.findTile(mMap.getCurrentTileId());
	}

	public void openUi() {
		if (mUiBridge != null) {
			mUiBridge.openMapUi();
		}
	}

	public void closeUi() {
		if (mUiBridge != null) {
			mUiBridge.closeMapUi();
		}
	}

	public void toggleUi() {
		if (mUiBridge != null) {
			mUiBridge.toggleMapUi();
		}
	}

	public void setModeFullscreen(final boolean fullscreen) {
		mPreferFloat = !fullscreen;
		if (mUiBridge != null) {
			mUiBridge.setMapMode(fullscreen);
		}
		notifyChanged();
	}

	public void centerUi() {
		if (mUiBridge != null) {
			mUiBridge.centerOnPlayer();
		}
	}

	/**
	 * Zoom the open map UI. {@code action} is {@code in}, {@code out},
	 * {@code reset}, or a float scale factor (same as pinch focus at view center).
	 *
	 * @return status string for the window
	 */
	public String zoom(final String action) {
		if (action == null || action.trim().length() == 0) {
			return "Usage: .map zoom in|out|reset";
		}
		String a = action.trim();
		if (mUiBridge != null) {
			mUiBridge.zoomMap(a);
			return "Mapper: zoom " + a.toLowerCase(Locale.US) + ".";
		}
		// Cross-process: UI overlay lives in :stellar; push via IPC.
		if (mConnection != null) {
			try {
				mConnection.requestMapperUiArg(5, a);
				return "Mapper: zoom " + a.toLowerCase(Locale.US) + ".";
			} catch (Exception ignored) {
			}
		}
		return "Mapper: zoom (UI not open)";
	}

	/** Temporarily suppress recording (e.g. while auto-sending a path). */
	public void setSuppressRecord(final boolean suppress) {
		mSuppressRecord = suppress;
	}

	// --- internals ---

	private void recordMove(final String command) {
		ensureDefaultLevel();
		MapTile from = currentTile();
		if (from == null) {
			from = createTileAt(mMap.getCurrentLevelId(), 0, 0);
			mMap.setCurrentTileId(from.getId());
		}
		String norm = normalize(command);
		if (norm.length() == 0) {
			return;
		}
		String stored = MapDirections.storeCommand(command, norm);

		int[] delta = gridDeltaFor(norm);
		boolean special = (delta == null);

		pushUndo();

		MapExit existing = findExit(from, norm);
		MapTile to;
		if (existing != null && existing.getToId() != null) {
			to = mMap.findTile(existing.getToId());
			if (to == null) {
				to = createDestination(from, norm, delta, special);
				existing.setToId(to.getId());
			}
			// Re-walk (incl. return trip): prefer the player's actual wording
			// over a previously guessed reverse (e.g. "s" → "go south").
			adoptRecordedExitCommand(existing, from, to, stored, special);
		} else {
			MapTile smartReturn = null;
			if (special && levelDeltaFor(norm) == null && !mAcceptOneWaySpecials) {
				smartReturn = findUniqueInboundNeighbor(from);
			}
			if (smartReturn != null) {
				to = smartReturn;
			} else {
				to = createDestination(from, norm, delta, special);
			}
			MapExit byDest = findExitTo(from, to.getId());
			if (byDest != null) {
				// Same edge under a different spelling than the guessed reverse.
				adoptRecordedExitCommand(byDest, from, to, stored, special);
				removeOtherExitsTo(from, to.getId(), byDest);
			} else {
				String rev = MapDirections.suggestReverse(stored, directionMap());
				MapExit exit = new MapExit(from.getId(), to.getId(), stored, special, rev);
				from.addExit(exit);
				// Skip auto-reverse when we closed a special back to the room
				// that already leads here (reverse edge already exists).
				if (mAutoReverse && rev != null && smartReturn == null) {
					ensureReverse(from, to, stored);
				}
			}
		}

		mMap.setCurrentTileId(to.getId());
		mMap.setCurrentLevelId(to.getLevelId());
		refreshConflicts();
		notifyChanged();
	}

	/**
	 * Store the command the player just typed on this exit and sync the
	 * opposite edge's {@code reverseCommand} hint.
	 */
	private void adoptRecordedExitCommand(final MapExit exit, final MapTile from,
			final MapTile to, final String stored, final boolean special) {
		if (exit == null || stored == null) {
			return;
		}
		exit.setCommand(stored);
		exit.setSpecial(special);
		exit.setReverseCommand(MapDirections.suggestReverse(stored, directionMap()));
		if (to == null || from == null) {
			return;
		}
		MapExit back = findExitTo(to, from.getId());
		if (back != null) {
			back.setReverseCommand(stored);
		}
	}

	private MapExit findExitTo(final MapTile tile, final String toId) {
		if (tile == null || toId == null) {
			return null;
		}
		for (MapExit e : tile.getExits()) {
			if (e != null && toId.equals(e.getToId())) {
				return e;
			}
		}
		return null;
	}

	private void removeOtherExitsTo(final MapTile tile, final String toId,
			final MapExit keep) {
		if (tile == null || toId == null) {
			return;
		}
		List<MapExit> exits = tile.getExits();
		for (int i = exits.size() - 1; i >= 0; i--) {
			MapExit e = exits.get(i);
			if (e != keep && e != null && toId.equals(e.getToId())) {
				exits.remove(i);
			}
		}
	}

	/**
	 * If exactly one other tile has an exit into {@code here}, return it.
	 * Used to close special returns ({@code out}/{@code leave}) when recording.
	 */
	private MapTile findUniqueInboundNeighbor(final MapTile here) {
		if (mMap == null || here == null || here.getId() == null) {
			return null;
		}
		String hereId = here.getId();
		MapTile unique = null;
		for (MapTile tile : mMap.getTiles()) {
			if (tile == null || hereId.equals(tile.getId())) {
				continue;
			}
			if (findExitTo(tile, hereId) == null) {
				continue;
			}
			if (unique != null) {
				return null;
			}
			unique = tile;
		}
		return unique;
	}

	private MapTile createDestination(final MapTile from, final String norm,
			final int[] delta, final boolean special) {
		if (!special && delta != null) {
			int nx = from.getGridX() + delta[0];
			int ny = from.getGridY() + delta[1];
			MapTile at = findTileAt(from.getLevelId(), nx, ny);
			if (at != null) {
				return at;
			}
			return createTileAt(from.getLevelId(), nx, ny);
		}
		// Vertical / climb: auto level change (Options → Mapper level CSVs)
		Integer lvl = levelDeltaFor(norm);
		if (lvl != null) {
			return tileOnRelativeLevel(from, lvl.intValue(), norm);
		}
		// Other specials: same level, free cell near origin (no auto title —
		// leave blank until capture/GMCP/manual edit).
		int[] free = findFreeNear(from.getLevelId(), from.getGridX(), from.getGridY());
		return createTileAt(from.getLevelId(), free[0], free[1]);
	}

	private MapTile tileOnRelativeLevel(final MapTile from, final int levelDelta,
			final String norm) {
		List<MapLevel> sorted = sortedLevels();
		int idx = 0;
		for (int i = 0; i < sorted.size(); i++) {
			if (sorted.get(i).getId().equals(from.getLevelId())) {
				idx = i;
				break;
			}
		}
		int target = idx + levelDelta;
		MapLevel level;
		if (target < 0) {
			level = addLevel(Integer.toString(sorted.get(0).getIndex() - 1));
			// re-sort conceptually: put at front by index
		} else if (target >= sorted.size()) {
			level = addLevel(Integer.toString(sorted.get(sorted.size() - 1).getIndex() + 1));
		} else {
			level = sorted.get(target);
		}
		MapTile at = findTileAt(level.getId(), from.getGridX(), from.getGridY());
		if (at != null) {
			return at;
		}
		MapTile tile = createTileAt(level.getId(), from.getGridX(), from.getGridY());
		return tile;
	}

	private int[] findFreeNear(final String levelId, final int ox, final int oy) {
		return findFreeNear(levelId, ox, oy, null);
	}

	/**
	 * Nearest free cell to (ox,oy), preferring the way the player travelled.
	 *
	 * The ring scan alone runs -r..r on both axes, so it hands back the
	 * north-west corner of the ring first and works round from there, whichever
	 * way the player was going. That is how a walk east filled a column
	 * downwards. With {@code travel} known, ring candidates are ordered by how
	 * well they agree with it, so the room lands behind the player only when
	 * everything ahead is taken.
	 */
	private int[] findFreeNear(final String levelId, final int ox, final int oy,
			final int[] travel) {
		for (int r = 1; r < 30; r++) {
			for (int[] off : ringOffsetsByTravel(r, travel)) {
				int x = ox + off[0];
				int y = oy + off[1];
				if (findTileAt(levelId, x, y) == null) {
					return new int[] { x, y };
				}
			}
		}
		return new int[] { ox + 50, oy };
	}

	/**
	 * Cells on the ring at radius {@code r}, the ones most nearly ahead first.
	 *
	 * With {@code travel} null this is the plain scan: north-west corner, then
	 * round. That order is unrelated to where the player was going, which is how
	 * a walk east could put the new room north-west of the old one and fill a
	 * column downwards. Sorting by cosine against the travel vector puts the
	 * room behind the player only when everything ahead is taken. The sort is
	 * stable, so cells that agree equally well keep the plain order.
	 */
	static List<int[]> ringOffsetsByTravel(final int r, final int[] travel) {
		List<int[]> cells = new ArrayList<int[]>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				if (Math.abs(dx) != r && Math.abs(dy) != r) {
					continue;
				}
				cells.add(new int[] { dx, dy });
			}
		}
		if (travel == null || (travel[0] == 0 && travel[1] == 0)) {
			return cells;
		}
		final double tlen = Math.sqrt((double) travel[0] * travel[0]
				+ (double) travel[1] * travel[1]);
		Collections.sort(cells, new Comparator<int[]>() {
			@Override
			public int compare(int[] a, int[] b) {
				return Double.compare(agreement(b), agreement(a));
			}

			private double agreement(int[] c) {
				double len = Math.sqrt((double) c[0] * c[0] + (double) c[1] * c[1]);
				if (len < 0.0001) {
					return 0;
				}
				return (c[0] * travel[0] + c[1] * travel[1]) / (len * tlen);
			}
		});
		return cells;
	}

	private void ensureReverse(final MapTile from, final MapTile to, final String forwardCmd) {
		String rev = MapDirections.suggestReverse(forwardCmd, directionMap());
		if (rev == null || rev.length() == 0) {
			return;
		}
		String revNorm = normalize(rev);
		if (findExit(to, revNorm) != null || findExitTo(to, from.getId()) != null) {
			return;
		}
		boolean special = gridDeltaFor(revNorm) == null;
		String storedRev = MapDirections.storeCommand(rev, revNorm);
		MapExit back = new MapExit(to.getId(), from.getId(), storedRev, special, forwardCmd);
		// Walking s into a room does not prove n comes back out of it. Marked as
		// a guess so the room's own exit list can withdraw it.
		back.setGuessed(true);
		to.addExit(back);
	}

	/**
	 * Drop invented exits the room's own list does not have.
	 *
	 * The mapper adds a reverse exit whenever the player walks into a room,
	 * which is a fair guess and often right. When it is wrong the map grows a
	 * way through that does not exist -- Training Ground gained an n back to
	 * Beehives because s led there, while the room only ever offered E and W --
	 * and the invented exit then misleads the map, the path finder and the
	 * player equally.
	 *
	 * Only guesses are withdrawn, and only when the game has actually told us
	 * what the room offers. Exits the player walked, linked by hand or that the
	 * game listed are never touched.
	 */
	private List<MapExit> guessedExitsContradictedBy(final MapTile tile,
			final List<String> exits) {
		if (tile == null || exits == null || exits.isEmpty() || tile.getExits() == null) {
			return Collections.emptyList();
		}
		Set<String> listed = new HashSet<String>();
		for (String ex : exits) {
			if (ex == null) {
				continue;
			}
			String norm = normalize(ex);
			if (norm.length() > 0) {
				listed.add(norm);
			}
		}
		if (listed.isEmpty()) {
			return Collections.emptyList();
		}
		List<MapExit> withdraw = new ArrayList<MapExit>();
		for (MapExit e : tile.getExits()) {
			if (e == null || !e.isGuessed()) {
				continue;
			}
			// A portal is the player's doing whatever else is true of it.
			if (e.getTargetMap() != null && e.getTargetMap().trim().length() > 0) {
				continue;
			}
			String norm = normalize(e.getCommand());
			if (norm.length() > 0 && !listed.contains(norm)) {
				withdraw.add(e);
			}
		}
		return withdraw;
	}

	private MapTile createTileAt(final String levelId, final int x, final int y) {
		MapTile tile = new MapTile(UUID.randomUUID().toString(), levelId, x, y);
		mMap.getTiles().add(tile);
		return tile;
	}

	private MapTile findTileAt(final String levelId, final int x, final int y) {
		if (mMap == null) {
			return null;
		}
		for (MapTile tile : mMap.getTiles()) {
			if (tile == null) {
				continue;
			}
			String lid = tile.getLevelId() != null ? tile.getLevelId() : "";
			String want = levelId != null ? levelId : "";
			if (lid.equals(want) && tile.getGridX() == x && tile.getGridY() == y) {
				return tile;
			}
		}
		return null;
	}

	private MapExit findExit(final MapTile tile, final String command) {
		if (tile == null || command == null) {
			return null;
		}
		String want = normalize(command);
		for (MapExit e : tile.getExits()) {
			if (e != null && want.equalsIgnoreCase(normalize(e.getCommand()))) {
				return e;
			}
		}
		return null;
	}

	private String normalize(final String cmd) {
		return MapDirections.normalize(cmd, directionMap());
	}

	/**
	 * The move that this room arrival answers, if it is recent enough to be that
	 * move. Read once: a second room without a fresh command did not come from
	 * walking, so it must not reuse the direction.
	 */
	private RememberedMove consumeLastMove() {
		RememberedMove move = mLastMove;
		mLastMove = null;
		if (move == null) {
			return null;
		}
		if (System.currentTimeMillis() - mLastMoveAt > MOVE_MEMORY_MS) {
			return null;
		}
		return move;
	}

	private Map<String, DirectionData> directionMap() {
		if (mConnection == null) {
			return null;
		}
		try {
			HashMap<String, DirectionData> d = mConnection.getDirectionData();
			return d;
		} catch (Exception e) {
			return null;
		}
	}

	private void ensureBlankMap(final String name) {
		mMap = new MudMap();
		mMap.setName(name);
		MapLevel level = new MapLevel(null, DEFAULT_LEVEL_NAME, 0);
		mMap.getLevels().add(level);
		mMap.setCurrentLevelId(level.getId());
		mMap.setCurrentTileId(null);
		if (mConnection != null && mConnection.getHost() != null) {
			mMap.setHostHint(mConnection.getHost());
		}
	}

	private void ensureDefaultLevel() {
		if (mMap == null) {
			ensureBlankMap(DEFAULT_MAP_NAME);
			return;
		}
		if (mMap.getLevels().isEmpty()) {
			MapLevel level = new MapLevel(null, DEFAULT_LEVEL_NAME, 0);
			mMap.getLevels().add(level);
			mMap.setCurrentLevelId(level.getId());
		} else if (mMap.getCurrentLevelId() == null) {
			mMap.setCurrentLevelId(mMap.getLevels().get(0).getId());
		}
	}

	/**
	 * A level named with a number is a floor number, and its order is that
	 * number. This is what the GMCP z path creates, and handing out max+1
	 * regardless put "-1" second and therefore *above* "0": going down from a
	 * cellar then walked into the level above it, found the room standing at the
	 * same cell, and reused it -- so the cellar's own down led back up to the
	 * room the player had come from.
	 *
	 * Names that are not numbers keep the old behaviour, as does a number whose
	 * place is already taken.
	 */
	private MapLevel addLevel(final String name) {
		Integer numbered = numericLevelIndex(name);
		if (numbered != null && findLevelByIndex(numbered.intValue()) == null) {
			return addLevel(name, numbered.intValue(), null, null);
		}
		int max = -1;
		for (MapLevel l : mMap.getLevels()) {
			if (l.getIndex() > max) {
				max = l.getIndex();
			}
		}
		return addLevel(name, max + 1, null, null);
	}

	private static Integer numericLevelIndex(final String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		if (trimmed.length() == 0) {
			return null;
		}
		try {
			return Integer.valueOf(Integer.parseInt(trimmed));
		} catch (NumberFormatException notANumber) {
			return null;
		}
	}

	private MapLevel findLevelByIndex(final int index) {
		if (mMap == null) {
			return null;
		}
		for (MapLevel l : mMap.getLevels()) {
			if (l != null && l.getIndex() == index) {
				return l;
			}
		}
		return null;
	}

	/**
	 * Create a level at an explicit index, optionally anchored on a door tile.
	 */
	public MapLevel addLevel(final String name, final int index,
			final String anchorTileId, final String anchorDir) {
		ensureDefaultLevel();
		MapLevel level = new MapLevel(null, name, index, anchorTileId, anchorDir);
		mMap.getLevels().add(level);
		return level;
	}

	private MapLevel findLevelByName(final String name) {
		if (mMap == null || name == null) {
			return null;
		}
		for (MapLevel l : mMap.getLevels()) {
			if (l != null && name.equalsIgnoreCase(l.getName())) {
				return l;
			}
		}
		return null;
	}

	private List<MapLevel> sortedLevels() {
		List<MapLevel> list = new ArrayList<MapLevel>(mMap.getLevels());
		Collections.sort(list, new java.util.Comparator<MapLevel>() {
			@Override
			public int compare(MapLevel a, MapLevel b) {
				return Integer.compare(a.getIndex(), b.getIndex());
			}
		});
		return list;
	}

	/**
	 * Here-centric nest navigation for L− / L+ ({@code down} / {@code up}).
	 * <ol>
	 * <li>Follow an existing nest anchored on Here with dir D, or an exit whose
	 * destination is on another level in that direction.</li>
	 * <li>Else, if the current level is anchored and D is the opposite of how we
	 * entered, return to {@code anchorTileId}.</li>
	 * <li>Else (Edit mode only) create a new nest: level anchored on Here,
	 * landing tile, linked exits; move Here to the landing. In Browse mode,
	 * returns a status message instead of creating.</li>
	 * </ol>
	 */
	private String shiftLevel(final String dir) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		ensureDefaultLevel();
		final String D = dir != null ? dir.trim().toLowerCase(Locale.US) : "";
		if (!"up".equals(D) && !"down".equals(D)) {
			return "Mapper: nest direction must be up or down.";
		}

		MapTile here = currentTile();
		if (here == null) {
			if (!mEditMode) {
				return "Mapper: Browse mode — no nested floor that way. Switch to Edit to create, or open Levels.";
			}
			here = createTileAt(mMap.getCurrentLevelId(), 0, 0);
			mMap.setCurrentTileId(here.getId());
		}

		// 1a. Level already anchored on Here with matching entry dir.
		MapLevel nested = mMap.findLevelAnchoredOn(here.getId(), D);
		if (nested != null) {
			MapTile landing = firstTileOnLevel(nested.getId());
			if (landing == null) {
				pushUndo();
				landing = createTileAt(nested.getId(), here.getGridX(), here.getGridY());
			}
			mMap.setCurrentLevelId(nested.getId());
			mMap.setCurrentTileId(landing.getId());
			notifyChanged();
			return "Mapper: entered level \"" + nested.getName() + "\" via "
					+ shortTileLabel(here) + " (" + nestLabel(D) + ").";
		}

		// 1b. Follow an exit from Here onto another level in direction D.
		MapTile exitDest = findInterLevelExitDest(here, D);
		if (exitDest != null) {
			mMap.setCurrentLevelId(exitDest.getLevelId());
			mMap.setCurrentTileId(exitDest.getId());
			notifyChanged();
			MapLevel destLevel = mMap.findLevel(exitDest.getLevelId());
			String lname = destLevel != null && destLevel.getName() != null
					? destLevel.getName() : "?";
			return "Mapper: entered level \"" + lname + "\" via "
					+ shortTileLabel(here) + " (" + nestLabel(D) + ").";
		}

		// 2. Return via this level's anchor (entered via opposite of D).
		MapLevel currentLevel = mMap.findLevel(here.getLevelId());
		if (currentLevel != null && currentLevel.getAnchorTileId() != null) {
			String enteredVia = currentLevel.getAnchorDir();
			String returnDir = MapDirections.opposite(enteredVia);
			if (returnDir != null && dirEquals(D, returnDir)) {
				MapTile door = mMap.findTile(currentLevel.getAnchorTileId());
				if (door != null) {
					mMap.setCurrentLevelId(door.getLevelId());
					mMap.setCurrentTileId(door.getId());
					notifyChanged();
					return "Mapper: returned to \"" + shortTileLabel(door)
							+ "\" (" + nestLabel(D) + ").";
				}
			}
		}

		// 3. Create nest anchored on Here — Edit mode only.
		if (!mEditMode) {
			return "Mapper: Browse mode — no nested floor that way. Switch to Edit to create, or open Levels.";
		}

		pushUndo();
		int newIndex = adjacentNestIndex(here.getLevelId(), D);
		String name = suggestNestName(here, D, newIndex);
		MapLevel level = addLevel(name, newIndex, here.getId(), D);
		MapTile landing = createTileAt(level.getId(), here.getGridX(), here.getGridY());

		String stored = MapDirections.storeCommand(D, D);
		String rev = MapDirections.suggestReverse(stored, directionMap());
		if (rev == null || rev.length() == 0) {
			rev = MapDirections.opposite(D);
		}
		boolean special = gridDeltaFor(normalize(stored)) == null;
		here.addExit(new MapExit(here.getId(), landing.getId(), stored, special, rev));
		if (mAutoReverse && rev != null && rev.length() > 0) {
			String revNorm = normalize(rev);
			boolean revSpecial = gridDeltaFor(revNorm) == null;
			landing.addExit(new MapExit(landing.getId(), here.getId(), rev, revSpecial,
					stored));
		}

		mMap.setCurrentLevelId(level.getId());
		mMap.setCurrentTileId(landing.getId());
		refreshConflicts();
		notifyChanged();
		return "Mapper: entered level \"" + level.getName() + "\" via "
				+ shortTileLabel(here) + " (" + nestLabel(D) + ").";
	}

	/** L− / L+ label for status messages. */
	private static String nestLabel(final String dir) {
		if ("down".equalsIgnoreCase(dir)) {
			return "L-";
		}
		if ("up".equalsIgnoreCase(dir)) {
			return "L+";
		}
		return dir;
	}

	/**
	 * Destination of an exit from {@code here} that leaves this level in
	 * direction {@code dir} (normalized command, or dest level anchored on here).
	 */
	private MapTile findInterLevelExitDest(final MapTile here, final String dir) {
		if (here == null || mMap == null) {
			return null;
		}
		String hereLevel = here.getLevelId() != null ? here.getLevelId() : "";
		for (MapExit e : here.getExits()) {
			if (e == null || e.getToId() == null) {
				continue;
			}
			MapTile dest = mMap.findTile(e.getToId());
			if (dest == null) {
				continue;
			}
			String destLevel = dest.getLevelId() != null ? dest.getLevelId() : "";
			if (destLevel.equals(hereLevel)) {
				continue;
			}
			String norm = normalize(e.getCommand());
			boolean cmdMatches = dirEquals(dir, norm);
			MapLevel destLvl = mMap.findLevel(dest.getLevelId());
			boolean anchorMatches = destLvl != null
					&& here.getId().equals(destLvl.getAnchorTileId())
					&& dirEquals(dir, destLvl.getAnchorDir());
			if (cmdMatches || anchorMatches) {
				return dest;
			}
		}
		return null;
	}

	private static boolean dirEquals(final String a, final String b) {
		if (a == null || b == null) {
			return false;
		}
		String na = MapDirections.normalize(a, null);
		String nb = MapDirections.normalize(b, null);
		if (na.length() > 0 && na.equalsIgnoreCase(nb)) {
			return true;
		}
		// Long/short: up↔u, down↔d
		String la = MapDirections.toLongForm(na.length() > 0 ? na : a);
		String lb = MapDirections.toLongForm(nb.length() > 0 ? nb : b);
		return la != null && la.equalsIgnoreCase(lb);
	}

	private int adjacentNestIndex(final String fromLevelId, final String dir) {
		MapLevel from = mMap.findLevel(fromLevelId);
		int base = from != null ? from.getIndex() : 0;
		return "down".equalsIgnoreCase(dir) ? base - 1 : base + 1;
	}

	private String suggestNestName(final MapTile here, final String dir,
			final int newIndex) {
		String numeric = Integer.toString(newIndex);
		if (findLevelByName(numeric) == null) {
			return numeric;
		}
		String shortLabel = shortTileLabel(here);
		String descriptive = "down".equalsIgnoreCase(dir)
				? "under " + shortLabel
				: "above " + shortLabel;
		if (findLevelByName(descriptive) == null) {
			return descriptive;
		}
		return "L" + newIndex;
	}

	private String shortTileLabel(final MapTile tile) {
		if (tile == null) {
			return "?";
		}
		if (tile.getTitle() != null && tile.getTitle().trim().length() > 0) {
			String t = tile.getTitle().trim();
			return t.length() > 24 ? t.substring(0, 24) : t;
		}
		String id = tile.getId();
		if (id != null && id.length() > 8) {
			return id.substring(0, 8);
		}
		return id != null ? id : "?";
	}

	private String formatAnchorSummary(final MapLevel level) {
		if (level == null || level.getAnchorTileId() == null) {
			return "";
		}
		MapTile door = mMap != null ? mMap.findTile(level.getAnchorTileId()) : null;
		String via = door != null ? shortTileLabel(door) : level.getAnchorTileId();
		String dir = level.getAnchorDir() != null ? level.getAnchorDir() : "?";
		return "via " + via + " (" + dir + ")";
	}

	private int countTilesOnLevel(final String levelId) {
		int n = 0;
		if (mMap == null) {
			return 0;
		}
		String want = levelId != null ? levelId : "";
		for (MapTile t : mMap.getTiles()) {
			if (t == null) {
				continue;
			}
			String lid = t.getLevelId() != null ? t.getLevelId() : "";
			if (lid.equals(want)) {
				n++;
			}
		}
		return n;
	}

	private MapTile firstTileOnLevel(final String levelId) {
		if (mMap == null) {
			return null;
		}
		String want = levelId != null ? levelId : "";
		for (MapTile t : mMap.getTiles()) {
			if (t == null) {
				continue;
			}
			String lid = t.getLevelId() != null ? t.getLevelId() : "";
			if (lid.equals(want)) {
				return t;
			}
		}
		return null;
	}

	private void pushUndo() {
		if (mMap == null) {
			return;
		}
		try {
			String json = MapStore.toJson(mMap).toString();
			mUndoStack.addLast(json);
			while (mUndoStack.size() > UNDO_LIMIT) {
				mUndoStack.removeFirst();
			}
		} catch (JSONException ignored) {
		}
	}

	private void clearUndo() {
		mUndoStack.clear();
	}

	private void refreshConflicts() {
		if (mMap != null) {
			MapConflictDetector.refreshConflicts(mMap);
		}
	}

	private void addGmcpMismatch(final MapTile a, final MapTile b, final String name) {
		List<String> ids = Arrays.asList(
				a != null ? a.getId() : "",
				b != null ? b.getId() : "");
		MapConflict c = new MapConflict(MapConflict.Type.GMCP_MISMATCH,
				"GMCP position mismatch" + (name != null ? ": " + name : ""),
				ids);
		mMap.getConflicts().add(c);
	}

	private void notifyChanged() {
		for (Listener l : mListeners) {
			try {
				l.onMapperChanged();
			} catch (Exception ignored) {
			}
		}
		for (MapperUiListener l : mUiListeners) {
			try {
				l.onMapperChanged();
			} catch (Exception ignored) {
			}
		}
		if (mUiBridge != null) {
			try {
				mUiBridge.onMapModelChanged();
			} catch (Exception ignored) {
			}
		}
		// UI runs in a different process than Connection — push a refresh hint.
		if (mConnection != null) {
			try {
				mConnection.requestMapperUi(4);
			} catch (Exception ignored) {
			}
		}
		scheduleAutosave();
	}

	/** Debounced write to {@code /BlowTorch/maps/<name>.json}. */
	private void scheduleAutosave() {
		mAutosaveHandler.removeCallbacks(mAutosaveRunnable);
		mAutosaveHandler.postDelayed(mAutosaveRunnable, 2000);
	}

	private Context context() {
		if (mConnection == null) {
			return null;
		}
		try {
			return mConnection.getContext();
		} catch (Exception e) {
			return null;
		}
	}

	private List<String> lastBufferLines(final int max) {
		List<String> out = new ArrayList<String>();
		if (mConnection == null) {
			return out;
		}
		try {
			WindowToken[] windows = mConnection.getWindows();
			if (windows == null || windows.length == 0 || windows[0] == null) {
				return out;
			}
			TextTree buffer = windows[0].getBuffer();
			if (buffer == null) {
				return out;
			}
			LinkedList<Line> lines = buffer.getLines();
			if (lines == null || lines.isEmpty()) {
				return out;
			}
			int start = Math.max(0, lines.size() - max);
			for (int i = start; i < lines.size(); i++) {
				Line line = lines.get(i);
				out.add(TextTree.deColorLine(line).toString());
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	private static String shortId(final String id) {
		if (id == null) {
			return "?";
		}
		return id.length() > 8 ? id.substring(0, 8) : id;
	}

	private static String firstString(final JSONObject o, final String... keys) {
		if (o == null) {
			return null;
		}
		for (String key : keys) {
			if (o.has(key) && !o.isNull(key)) {
				String v = o.optString(key, null);
				if (v != null && v.length() > 0) {
					return v;
				}
			}
		}
		return null;
	}

	/**
	 * Extract exit command/direction names from a GMCP Room payload fragment.
	 * Supports common shapes:
	 * <ul>
	 *   <li>array of strings: {@code ["n","s","e"]}</li>
	 *   <li>array of objects with dir/direction/name/command/exit</li>
	 *   <li>object map direction→dest: {@code {"n":123,"e":456}} (keys used)</li>
	 *   <li>nested {@code exits} object/array inside the exits value</li>
	 *   <li>comma/whitespace-separated string: {@code "n, s, e"}</li>
	 * </ul>
	 * Also accepts singular key {@code exit}.
	 */
	static List<String> parseExits(final JSONObject o) {
		List<String> out = new ArrayList<String>();
		if (o == null) {
			return out;
		}
		Object ex = null;
		if (o.has("exits") && !o.isNull("exits")) {
			ex = o.opt("exits");
		} else if (o.has("exit") && !o.isNull("exit")) {
			ex = o.opt("exit");
		}
		if (ex == null) {
			return out;
		}
		appendParsedExits(out, ex);
		return out;
	}

	private static void appendParsedExits(final List<String> out, final Object ex) {
		if (ex instanceof JSONArray) {
			JSONArray arr = (JSONArray) ex;
			for (int i = 0; i < arr.length(); i++) {
				Object item = arr.opt(i);
				if (item == null || item == JSONObject.NULL) {
					continue;
				}
				if (item instanceof JSONObject) {
					String s = firstString((JSONObject) item,
							"dir", "direction", "name", "command", "exit");
					if (s != null) {
						addExitName(out, s);
					}
				} else {
					String s = String.valueOf(item).trim();
					if (s.length() > 0 && !"null".equals(s)) {
						addExitName(out, s);
					}
				}
			}
		} else if (ex instanceof JSONObject) {
			JSONObject jo = (JSONObject) ex;
			// Nested exits: { "exits": [...] } or { "exits": {...} }
			if (jo.has("exits") && !jo.isNull("exits")) {
				Object nested = jo.opt("exits");
				if (nested instanceof JSONArray || nested instanceof JSONObject
						|| nested instanceof String) {
					appendParsedExits(out, nested);
				}
			}
			JSONArray names = jo.names();
			if (names != null) {
				for (int i = 0; i < names.length(); i++) {
					String key = names.optString(i, null);
					if (key != null && key.length() > 0 && !"exits".equals(key)) {
						addExitName(out, key);
					}
				}
			}
		} else if (ex instanceof String) {
			String s = (String) ex;
			for (String part : s.split("[,;\\s]+")) {
				if (part.length() > 0) {
					addExitName(out, part);
				}
			}
		}
	}

	private static void addExitName(final List<String> out, final String name) {
		if (name == null) {
			return;
		}
		String t = name.trim();
		if (t.length() == 0) {
			return;
		}
		for (String existing : out) {
			if (t.equalsIgnoreCase(existing)) {
				return;
			}
		}
		out.add(t);
	}

	private boolean boolOpt(final String key, final boolean def) {
		try {
			BaseOption o = (BaseOption) mConnection.getSettings().findOptionByKey(key);
			if (o instanceof BooleanOption) {
				Object v = o.getValue();
				if (v instanceof Boolean) {
					return ((Boolean) v).booleanValue();
				}
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private int intOpt(final String key, final int def) {
		try {
			BaseOption o = (BaseOption) mConnection.getSettings().findOptionByKey(key);
			if (o instanceof IntegerOption) {
				Object v = o.getValue();
				if (v instanceof Integer) {
					return ((Integer) v).intValue();
				}
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private String stringOpt(final String key, final String def) {
		try {
			BaseOption o = (BaseOption) mConnection.getSettings().findOptionByKey(key);
			if (o instanceof StringOption) {
				Object v = o.getValue();
				if (v instanceof String) {
					return (String) v;
				}
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static int clampOpacity(final int v) {
		if (v < 40) {
			return 40;
		}
		if (v > 100) {
			return 100;
		}
		return v;
	}
}
