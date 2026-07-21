package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
	private String mSelectedTileId;

	private MapperUiBridge mUiBridge;
	/** When true, ignore outbound commands (path send already recorded). */
	private boolean mSuppressRecord;

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
			// Soft sync: ensure exit stubs exist (unlinked special if no target yet)
			for (String ex : exits) {
				if (ex == null || ex.trim().length() == 0) {
					continue;
				}
				String norm = normalize(ex);
				if (findExit(current, norm) == null) {
					// Do not invent destinations from GMCP alone in v1
				}
			}
		}

		if (changed) {
			refreshConflicts();
			notifyChanged();
		} else if (mFollowPlayer) {
			notifyChanged();
		}
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
		MapTile t = currentTile();
		if (t == null) {
			return "Mapper: no current tile.";
		}
		pushUndo();
		t.setTitle(text);
		notifyChanged();
		return "Mapper: title set.";
	}

	public String setNotes(final String text) {
		MapTile t = currentTile();
		if (t == null) {
			return "Mapper: no current tile.";
		}
		pushUndo();
		t.setNotes(text);
		notifyChanged();
		return "Mapper: notes set.";
	}

	public String link(final String cmd, final String toTileId) {
		MapTile from = currentTile();
		if (from == null) {
			return "Mapper: no current tile.";
		}
		if (TextUtils.isEmpty(cmd)) {
			return "Mapper: usage .map link <cmd> [to <tileId>]";
		}
		if (TextUtils.isEmpty(toTileId)) {
			return "Mapper: provide target tile id (.map link <cmd> to <id>).";
		}
		MapTile to = mMap.findTile(toTileId);
		if (to == null) {
			return "Mapper: unknown tile id.";
		}
		String norm = normalize(cmd);
		pushUndo();
		MapExit existing = findExit(from, norm);
		if (existing != null) {
			existing.setToId(to.getId());
		} else {
			String rev = MapDirections.suggestReverse(norm, directionMap());
			boolean special = isCardinalGrid(norm) == null;
			from.addExit(new MapExit(from.getId(), to.getId(), norm, special, rev));
		}
		if (mAutoReverse) {
			ensureReverse(from, to, norm);
		}
		refreshConflicts();
		notifyChanged();
		return "Mapper: linked " + norm + " → " + shortId(to.getId()) + ".";
	}

	public String unlink(final String cmd) {
		MapTile from = currentTile();
		if (from == null) {
			return "Mapper: no current tile.";
		}
		String norm = normalize(cmd);
		pushUndo();
		List<MapExit> exits = from.getExits();
		boolean removed = false;
		for (int i = exits.size() - 1; i >= 0; i--) {
			MapExit e = exits.get(i);
			if (e != null && norm.equalsIgnoreCase(e.getCommand())) {
				exits.remove(i);
				removed = true;
			}
		}
		refreshConflicts();
		notifyChanged();
		return removed ? "Mapper: unlinked " + norm + "."
				: "Mapper: no exit \"" + norm + "\" on current tile.";
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
		if (mMap == null) {
			return Collections.emptyList();
		}
		List<MapConflict> open = new ArrayList<MapConflict>();
		for (MapConflict c : mMap.getConflicts()) {
			if (c != null && !c.isResolved()) {
				open.add(c);
			}
		}
		return open;
	}

	public String capturePreview(final int maxLines) {
		List<String> lines = lastBufferLines(maxLines > 0 ? maxLines : 20);
		if (lines.isEmpty()) {
			return "Mapper capture preview: (buffer empty)";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper capture preview (last ").append(lines.size()).append(" lines):\n");
		for (String line : lines) {
			sb.append(line).append("\n");
		}
		sb.append("(apply not implemented yet — stub)");
		return sb.toString();
	}

	public String captureApply() {
		return "Mapper capture apply: not implemented yet (stub).";
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

		int[] delta = isCardinalGrid(norm);
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
			String rev = MapDirections.suggestReverse(norm, directionMap());
			MapExit exit = new MapExit(from.getId(), to.getId(), norm, special, rev);
			from.addExit(exit);
			if (mAutoReverse && rev != null) {
				ensureReverse(from, to, norm);
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
		// Vertical: auto level change (v1)
		String n = norm.toLowerCase(Locale.US);
		if (n.equals("u") || n.equals("up")) {
			return tileOnRelativeLevel(from, 1, norm);
		}
		if (n.equals("d") || n.equals("down")) {
			return tileOnRelativeLevel(from, -1, norm);
		}
		// Other specials: same level, free cell near origin
		int[] free = findFreeNear(from.getLevelId(), from.getGridX(), from.getGridY());
		MapTile twin = createTileAt(from.getLevelId(), free[0], free[1]);
		if (twin.getTitle() == null) {
			twin.setTitle("(" + norm + ")");
		}
		return twin;
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
		if (tile.getTitle() == null) {
			tile.setTitle("(" + norm + ")");
		}
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
			boolean special = isCardinalGrid(revNorm) == null;
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
		for (MapExit e : tile.getExits()) {
			if (e != null && command.equalsIgnoreCase(e.getCommand())) {
				return e;
			}
		}
		return null;
	}

	/**
	 * @return [dx, dy] for grid moves, or null if special / unknown
	 */
	private int[] isCardinalGrid(final String norm) {
		if (norm == null) {
			return null;
		}
		String n = norm.toLowerCase(Locale.US);
		if (n.equals("n") || n.equals("north")) {
			return new int[] { 0, -1 };
		}
		if (n.equals("s") || n.equals("south")) {
			return new int[] { 0, 1 };
		}
		if (n.equals("e") || n.equals("east")) {
			return new int[] { 1, 0 };
		}
		if (n.equals("w") || n.equals("west")) {
			return new int[] { -1, 0 };
		}
		if (n.equals("ne") || n.equals("northeast")) {
			return new int[] { 1, -1 };
		}
		if (n.equals("nw") || n.equals("northwest")) {
			return new int[] { -1, -1 };
		}
		if (n.equals("se") || n.equals("southeast")) {
			return new int[] { 1, 1 };
		}
		if (n.equals("sw") || n.equals("southwest")) {
			return new int[] { -1, 1 };
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

	private String shiftLevel(final int delta) {
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
		if (next < 0 || next >= sorted.size()) {
			return "Mapper: already at edge of level list.";
		}
		MapLevel level = sorted.get(next);
		mMap.setCurrentLevelId(level.getId());
		notifyChanged();
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

	private static List<String> parseExits(final JSONObject o) {
		List<String> out = new ArrayList<String>();
		if (o == null || !o.has("exits")) {
			return out;
		}
		Object ex = o.opt("exits");
		if (ex instanceof JSONArray) {
			JSONArray arr = (JSONArray) ex;
			for (int i = 0; i < arr.length(); i++) {
				String s = arr.optString(i, null);
				if (s != null && s.length() > 0) {
					out.add(s);
				}
			}
		} else if (ex instanceof JSONObject) {
			JSONObject jo = (JSONObject) ex;
			JSONArray names = jo.names();
			if (names != null) {
				for (int i = 0; i < names.length(); i++) {
					String key = names.optString(i, null);
					if (key != null) {
						out.add(key);
					}
				}
			}
		} else if (ex instanceof String) {
			String s = (String) ex;
			for (String part : s.split("[,\\s]+")) {
				if (part.length() > 0) {
					out.add(part);
				}
			}
		}
		return out;
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
