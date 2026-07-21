package com.resurrection.blowtorch2.lib.mapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	private final Connection mConnection;
	private final List<Listener> mListeners = new CopyOnWriteArrayList<Listener>();
	private final List<MapperUiListener> mUiListeners = new CopyOnWriteArrayList<MapperUiListener>();
	private final LinkedList<String> mUndoStack = new LinkedList<String>();

	private MudMap mMap;
	private boolean mRecording;
	private boolean mFollowPlayer = true;
	private boolean mEnabled = true;
	private boolean mUseGmcp = true;
	private boolean mAutoReverse = true;
	private boolean mPreferFloat = true;
	private boolean mPathAutoSend;
	private int mOpacity = 85;
	private String mToolbarActions = DEFAULT_TOOLBAR;
	private String mCaptureTitleRegex = DEFAULT_CAPTURE_TITLE_REGEX;
	private String mCaptureExitsRegex = DEFAULT_CAPTURE_EXITS_REGEX;
	private String mSelectedTileId;
	/** Last `.map capture preview` result for `.map capture apply`. */
	private CapturePreview mLastCapturePreview;

	private MapperUiBridge mUiBridge;
	/** When true, ignore outbound commands (path send already recorded). */
	private boolean mSuppressRecord;
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
		int[] delta = MapDirections.gridDelta(norm);
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
		mUseGmcp = boolOpt("mapper_use_gmcp", true);
		mAutoReverse = boolOpt("mapper_auto_reverse_link", true);
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
		notifyChanged();
	}

	private boolean mSettingsApplied;

	public void setEnabled(final boolean enabled) {
		mEnabled = enabled;
		notifyChanged();
	}

	public void setRecording(final boolean recording) {
		mRecording = recording;
		notifyChanged();
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

	public void setUseGmcp(final boolean use) {
		mUseGmcp = use;
	}

	public void setAutoReverse(final boolean auto) {
		mAutoReverse = auto;
	}

	public void setOpacity(final int opacity) {
		mOpacity = clampOpacity(opacity);
		notifyChanged();
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
	 */
	public String newMap(final String name) {
		String mapName = TextUtils.isEmpty(name) ? DEFAULT_MAP_NAME : name.trim();
		ensureBlankMap(mapName);
		if (mConnection != null && mConnection.getHost() != null) {
			mMap.setHostHint(mConnection.getHost());
		}
		clearUndo();
		String saveMsg = save();
		notifyChanged();
		return "Mapper: new map \"" + mapName + "\". " + saveMsg;
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
	 * Record a player command if recording is on. Never sends to the server.
	 * Splits on CRLF / semicolon-separated lines when present.
	 */
	public void onPlayerCommand(final String cmd) {
		if (!mEnabled || !mRecording || mSuppressRecord || cmd == null) {
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
		String[] parts = raw.split("[\\r\\n;]+");
		for (String part : parts) {
			String piece = part.trim();
			if (piece.length() == 0) {
				continue;
			}
			recordMove(piece);
		}
	}

	/**
	 * Sync from GMCP Room.* payload when mapper_use_gmcp is on.
	 */
	public void onGmcpRoom(final String name, final Integer x, final Integer y,
			final Integer z, final List<String> exits) {
		if (!mEnabled || !mUseGmcp || mMap == null) {
			return;
		}
		ensureDefaultLevel();
		MapTile current = currentTile();
		boolean changed = false;

		if (name != null && name.length() > 0) {
			if (current == null) {
				current = createTileAt(mMap.getCurrentLevelId(), 0, 0);
				mMap.setCurrentTileId(current.getId());
				changed = true;
			}
			if (current.getTitle() == null || !name.equals(current.getTitle())) {
				pushUndo();
				current.setTitle(name);
				changed = true;
			}
		}

		if (x != null && y != null && current != null) {
			int gx = x.intValue();
			int gy = y.intValue();
			if (current.getGridX() != gx || current.getGridY() != gy) {
				MapTile at = findTileAt(current.getLevelId(), gx, gy);
				if (at != null && !at.getId().equals(current.getId())) {
					// GMCP says we are at a different tile on the grid
					pushUndo();
					mMap.setCurrentTileId(at.getId());
					if (name != null && name.length() > 0) {
						at.setTitle(name);
					}
					changed = true;
					addGmcpMismatch(current, at, name);
				} else if (at == null) {
					pushUndo();
					current.setGridX(gx);
					current.setGridY(gy);
					changed = true;
				}
			}
		}

		if (z != null) {
			String levelName = Integer.toString(z.intValue());
			MapLevel level = findLevelByName(levelName);
			if (level == null) {
				pushUndo();
				level = addLevel(levelName);
				changed = true;
			}
			if (current != null && !level.getId().equals(current.getLevelId())) {
				MapTile at = findTileAt(level.getId(),
						current.getGridX(), current.getGridY());
				if (at == null) {
					pushUndo();
					current.setLevelId(level.getId());
					mMap.setCurrentLevelId(level.getId());
					changed = true;
				} else {
					pushUndo();
					mMap.setCurrentTileId(at.getId());
					mMap.setCurrentLevelId(level.getId());
					changed = true;
				}
			} else if (!level.getId().equals(mMap.getCurrentLevelId())) {
				mMap.setCurrentLevelId(level.getId());
				changed = true;
			}
		}

		if (exits != null && current != null && exits.size() > 0) {
			if (syncExitsFromGmcp(current, exits)) {
				changed = true;
			}
		}

		if (changed) {
			refreshConflicts();
			notifyChanged();
		} else if (mFollowPlayer) {
			notifyChanged();
		}
	}

	/**
	 * Ensure each GMCP exit exists on {@code current}. Existing exits are kept
	 * (destinations never wiped). Missing exits get a destination via
	 * {@link #createDestination} (planar neighbor, relative level, or nearby
	 * free cell for specials) — same growth rules as {@link #recordMove}.
	 * Exits on the tile that are absent from GMCP are left alone.
	 *
	 * @return true if any exit or tile was added
	 */
	private boolean syncExitsFromGmcp(final MapTile current, final List<String> exits) {
		List<String> missing = new ArrayList<String>();
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
			}
		}
		if (missing.isEmpty()) {
			return false;
		}
		pushUndo();
		boolean changed = false;
		for (String ex : missing) {
			String norm = normalize(ex);
			if (norm.length() == 0 || findExit(current, norm) != null) {
				continue;
			}
			String stored = MapDirections.storeCommand(ex, norm);
			int[] delta = MapDirections.gridDelta(norm);
			boolean special = (delta == null);
			MapTile to = createDestination(current, norm, delta, special);
			String rev = MapDirections.suggestReverse(stored, directionMap());
			current.addExit(new MapExit(current.getId(), to.getId(), stored, special, rev));
			if (mAutoReverse && rev != null) {
				ensureReverse(current, to, stored);
			}
			changed = true;
		}
		return changed;
	}

	/** Parse a GMCP Room.* JSON body and forward to {@link #onGmcpRoom}. */
	public void onGmcpRoomRaw(final String module, final String jsonBody) {
		if (!mEnabled || !mUseGmcp || jsonBody == null) {
			return;
		}
		try {
			JSONObject body = new JSONObject(jsonBody);
			JSONObject info = body;
			if (body.has("info") && body.opt("info") instanceof JSONObject) {
				info = body.getJSONObject("info");
			}
			String name = firstString(info, "name", "short", "title", "roomname");
			if (name == null) {
				name = firstString(body, "name", "short", "title");
			}
			Integer x = null;
			Integer y = null;
			Integer z = null;
			if (info.has("coords") && info.opt("coords") instanceof JSONObject) {
				JSONObject c = info.getJSONObject("coords");
				if (c.has("x")) {
					x = Integer.valueOf(c.optInt("x"));
				}
				if (c.has("y")) {
					y = Integer.valueOf(c.optInt("y"));
				}
				if (c.has("z")) {
					z = Integer.valueOf(c.optInt("z"));
				}
			} else {
				if (info.has("x")) {
					x = Integer.valueOf(info.optInt("x"));
				}
				if (info.has("y")) {
					y = Integer.valueOf(info.optInt("y"));
				}
				if (info.has("z")) {
					z = Integer.valueOf(info.optInt("z"));
				}
			}
			List<String> exits = parseExits(info);
			if (exits.isEmpty()) {
				exits = parseExits(body);
			}
			onGmcpRoom(name, x, y, z, exits);
		} catch (JSONException ignored) {
			// Loose parse: ignore malformed Room payloads
		}
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
			boolean special = MapDirections.gridDelta(norm) == null;
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
			sb.append("\n");
		}
		return sb.toString();
	}

	public String levelPrev() {
		return shiftLevel(-1);
	}

	public String levelNext() {
		return shiftLevel(1);
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
		return undo() ? "Mapper: undo OK." : "Mapper: nothing to undo.";
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

		int[] delta = MapDirections.gridDelta(norm);
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
		} else {
			to = createDestination(from, norm, delta, special);
			String rev = MapDirections.suggestReverse(stored, directionMap());
			MapExit exit = new MapExit(from.getId(), to.getId(), stored, special, rev);
			from.addExit(exit);
			if (mAutoReverse && rev != null) {
				ensureReverse(from, to, stored);
			}
		}

		mMap.setCurrentTileId(to.getId());
		mMap.setCurrentLevelId(to.getLevelId());
		refreshConflicts();
		notifyChanged();
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
		// Vertical / climb: auto level change
		Integer lvl = MapDirections.levelDelta(norm);
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
		if (findTileAt(levelId, ox, oy) == null) {
			// Prefer not stacking on origin when origin is occupied by `from`
		}
		for (int r = 1; r < 30; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					if (Math.abs(dx) != r && Math.abs(dy) != r) {
						continue;
					}
					int x = ox + dx;
					int y = oy + dy;
					if (findTileAt(levelId, x, y) == null) {
						return new int[] { x, y };
					}
				}
			}
		}
		return new int[] { ox + 50, oy };
	}

	private void ensureReverse(final MapTile from, final MapTile to, final String forwardCmd) {
		String rev = MapDirections.suggestReverse(forwardCmd, directionMap());
		if (rev == null || rev.length() == 0) {
			return;
		}
		String revNorm = normalize(rev);
		if (findExit(to, revNorm) == null) {
			boolean special = MapDirections.gridDelta(revNorm) == null;
			to.addExit(new MapExit(to.getId(), from.getId(), revNorm, special, forwardCmd));
		}
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

	private MapLevel addLevel(final String name) {
		int max = -1;
		for (MapLevel l : mMap.getLevels()) {
			if (l.getIndex() > max) {
				max = l.getIndex();
			}
		}
		MapLevel level = new MapLevel(null, name, max + 1);
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
	 * Switch to the previous/next level. At either end of the list, create a new
	 * level so the player can organize floors manually (MUDs rarely map 1:1 to
	 * up/down — a west step can still be “upstairs”).
	 * <p>
	 * When a level is created, the current (Here) tile is moved onto it so the
	 * room you are standing on defines the new floor.
	 */
	private String shiftLevel(final int delta) {
		if (mMap == null) {
			return "Mapper: no map.";
		}
		ensureDefaultLevel();
		List<MapLevel> sorted = sortedLevels();
		if (sorted.isEmpty()) {
			return "Mapper: no levels.";
		}
		int idx = 0;
		for (int i = 0; i < sorted.size(); i++) {
			if (sorted.get(i).getId().equals(mMap.getCurrentLevelId())) {
				idx = i;
				break;
			}
		}
		int next = idx + delta;
		boolean created = false;
		MapLevel level;
		if (next < 0 || next >= sorted.size()) {
			pushUndo();
			int newIndex;
			if (next < 0) {
				newIndex = sorted.get(0).getIndex() - 1;
			} else {
				newIndex = sorted.get(sorted.size() - 1).getIndex() + 1;
			}
			String name = Integer.toString(newIndex);
			// Avoid colliding with an existing display name.
			if (findLevelByName(name) != null) {
				name = "L" + newIndex;
			}
			level = new MapLevel(null, name, newIndex);
			mMap.getLevels().add(level);
			created = true;
		} else {
			level = sorted.get(next);
		}

		mMap.setCurrentLevelId(level.getId());
		MapTile cur = currentTile();
		String movedNote = "";
		if (created && cur != null && !level.getId().equals(cur.getLevelId())) {
			// Here defines the new floor (player decided this room belongs here).
			MapTile occupied = findTileAt(level.getId(), cur.getGridX(), cur.getGridY());
			if (occupied == null || occupied.getId().equals(cur.getId())) {
				cur.setLevelId(level.getId());
				movedNote = " · Here moved here";
			}
		} else if (cur != null && !level.getId().equals(cur.getLevelId())) {
			MapTile at = findTileAt(level.getId(), cur.getGridX(), cur.getGridY());
			if (at != null) {
				mMap.setCurrentTileId(at.getId());
			}
		}
		notifyChanged();
		if (created) {
			return "Mapper: created level \"" + level.getName() + "\"" + movedNote
					+ ". Draw / move more tiles onto this floor as you like.";
		}
		return "Mapper: level \"" + level.getName() + "\".";
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
