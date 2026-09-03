package com.resurrection.blowtorch2.lib.mapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;

import com.resurrection.blowtorch2.lib.ui.SDCardUtils;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Persist {@link MudMap} as versioned JSON under {@code /BlowTorch/maps/}.
 * Schema version field: {@code "version": 1}.
 */
public final class MapStore {

	public static final int SCHEMA_VERSION = 1;
	/** Legacy shared map file used before per-host separation. */
	public static final String LEGACY_DEFAULT_NAME = "default";
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private MapStore() {
	}

	/** Directory for map JSON files ({@link SDCardUtils#SUBDIR_MAPS}). */
	public static File mapsDirectory(Context context) {
		return SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_MAPS);
	}

	/**
	 * List map names (file basename without {@code .json}), sorted.
	 */
	public static List<String> listMaps(Context context) {
		File dir = mapsDirectory(context);
		String[] names = dir.list();
		if (names == null || names.length == 0) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<String>();
		for (String name : names) {
			if (name != null && name.toLowerCase(Locale.US).endsWith(".json")) {
				out.add(name.substring(0, name.length() - 5));
			}
		}
		Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/**
	 * List map names for one MUD world ({@code hostHint} or legacy naming).
	 * Blank host returns every map (admin / no-connection fallback).
	 */
	public static List<String> listMapsForHost(final Context context,
			final String host) {
		if (host == null || host.trim().length() == 0) {
			return listMaps(context);
		}
		List<String> all = listMaps(context);
		if (all.isEmpty()) {
			return all;
		}
		String h = host.trim();
		List<String> out = new ArrayList<String>();
		for (String name : all) {
			String hint = readHostHint(context, name);
			if (mapBelongsToHost(name, hint, h)) {
				out.add(name);
			}
		}
		return out;
	}

	/**
	 * Whether a saved map belongs to the given connection host.
	 *
	 * <p>Primary key is {@code hostHint} inside the JSON. Legacy files without a
	 * hint match when the basename equals {@link #safeName(String)} of the host,
	 * or when the name is {@link #LEGACY_DEFAULT_NAME} and the hint is still
	 * empty (unclaimed shared default from before per-world separation).
	 */
	public static boolean mapBelongsToHost(final String mapName,
			final String hostHint, final String connectionHost) {
		if (connectionHost == null || connectionHost.trim().length() == 0) {
			return true;
		}
		String host = connectionHost.trim();
		if (mapName == null || mapName.trim().length() == 0) {
			return false;
		}
		String name = mapName.trim();
		if (hostHint != null && hostHint.trim().length() > 0) {
			return host.equalsIgnoreCase(hostHint.trim());
		}
		if (safeName(host).equalsIgnoreCase(name)) {
			return true;
		}
		return LEGACY_DEFAULT_NAME.equalsIgnoreCase(name);
	}

	/** Read only {@code hostHint} from a map file; null when missing or unreadable.
	 *
	 * <p>This is called once per saved map by every {@link #listMapsForHost}, which
	 * in turn runs on the UI thread when the mapper or the map browser opens, and
	 * on the connection handler when a world connects. Loading the whole map to
	 * read one string put a full JSON parse per file on all three. It now reads
	 * the head of the file and remembers the answer until the file changes.
	 */
	public static String readHostHint(final Context context, final String name) {
		File file = mapFile(context, name);
		if (file == null || !file.isFile()) {
			return null;
		}
		String key = file.getAbsolutePath();
		long modified = file.lastModified();
		long length = file.length();
		synchronized (SUMMARIES) {
			CacheEntry cached = SUMMARIES.get(key);
			if (cached != null && cached.modified == modified && cached.length == length) {
				return cached.hostHint;
			}
		}
		String hint = readHostHintFromPrefix(file);
		if (hint == null) {
			// The prefix did not settle it: either the file is bigger than the
			// budget and orders its keys differently, or it was written by
			// something other than toJson. Pay for one full parse and cache it.
			try {
				MudMap map = loadFromFile(file);
				if (map == null) {
					return null;
				}
				store(key, modified, length, map.getHostHint(),
						map.getTiles() == null ? 0 : map.getTiles().size());
				return map.getHostHint();
			} catch (Exception e) {
				return null;
			}
		}
		store(key, modified, length, hint, -1);
		return hint;
	}

	/** Number of tiles in a saved map, or 0 when missing / unreadable.
	 *
	 * <p>Unlike {@link #readHostHint} this cannot be answered from the head of the
	 * file, so it parses — but only once per file version, and only for the maps a
	 * caller actually asks about rather than every map on disk.
	 */
	public static int tileCountOf(final Context context, final String name) {
		File file = mapFile(context, name);
		if (file == null || !file.isFile()) {
			return 0;
		}
		String key = file.getAbsolutePath();
		long modified = file.lastModified();
		long length = file.length();
		synchronized (SUMMARIES) {
			CacheEntry cached = SUMMARIES.get(key);
			if (cached != null && cached.modified == modified && cached.length == length
					&& cached.tileCount >= 0) {
				return cached.tileCount;
			}
		}
		int tiles;
		String hint;
		try {
			MudMap map = loadFromFile(file);
			if (map == null) {
				return 0;
			}
			tiles = map.getTiles() == null ? 0 : map.getTiles().size();
			hint = map.getHostHint();
		} catch (Exception e) {
			return 0;
		}
		store(key, modified, length, hint, tiles);
		return tiles;
	}

	/** What we remember about one map file, plus the stat it was read at.
	 *
	 * <p>Keyed on path and validated against {@code lastModified} and
	 * {@code length} on every call. That stat is deliberately not cached: the UI
	 * process and {@code :stellar} hold separate copies of this map (statics exist
	 * once per process), so a save made in one has to be noticed by the other. A
	 * stat is a syscall; a parse is the whole file.
	 */
	private static final class CacheEntry {
		private long modified;
		private long length;
		private String hostHint;
		/** -1 until something asks for it; reading the hint alone does not count tiles. */
		private int tileCount = -1;
	}

	/** How much of a map file to read when only {@code hostHint} is wanted.
	 *
	 * <p>Correctness does not rest on this number, nor on where the field sits.
	 * {@link #readHostHintFromPrefix} only answers when it can: it found the field,
	 * or it read the entire file and the field is not in it. Otherwise it says so
	 * and the caller parses properly. Key order decides how often we get the cheap
	 * answer, not whether the answer is right — which matters, because the field
	 * order of {@code JSONObject} is a property of the implementation on the
	 * device, not something the JSON format promises.
	 *
	 * <p>The budget is generous because overshooting costs one buffered read and
	 * undershooting costs a full parse.
	 */
	private static final int HINT_PREFIX_BYTES = 8192;

	private static final java.util.Map<String, CacheEntry> SUMMARIES =
			new java.util.HashMap<String, CacheEntry>();

	private static void store(final String key, final long modified, final long length,
			final String hostHint, final int tileCount) {
		synchronized (SUMMARIES) {
			CacheEntry entry = SUMMARIES.get(key);
			if (entry == null) {
				entry = new CacheEntry();
				SUMMARIES.put(key, entry);
			}
			if (entry.modified != modified || entry.length != length) {
				entry.tileCount = -1;
			}
			entry.modified = modified;
			entry.length = length;
			entry.hostHint = hostHint;
			if (tileCount >= 0) {
				entry.tileCount = tileCount;
			}
		}
	}

	/** Drop what we remember about a map file — call after writing one. */
	static void invalidateSummary(final File file) {
		if (file == null) {
			return;
		}
		synchronized (SUMMARIES) {
			SUMMARIES.remove(file.getAbsolutePath());
		}
	}

	/**
	 * Pull {@code "hostHint": "..."} out of the head of the file without building
	 * the object model.
	 *
	 * @return the hint, "" when the file says it is empty, or null when the prefix
	 *         did not settle the question and the caller should parse properly.
	 */
	// Package-private, not private: MapStoreHostFilterTest pins both this and the
	// field order in toJson that it depends on.
	static String readHostHintFromPrefix(final File file) {
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			byte[] buf = new byte[HINT_PREFIX_BYTES];
			int filled = 0;
			int n;
			while (filled < buf.length && (n = in.read(buf, filled, buf.length - filled)) > 0) {
				filled += n;
			}
			// A cut multi-byte character decodes to U+FFFD rather than throwing, and
			// the key and its value are ASCII, so a truncated tail cannot corrupt a
			// match that lies before it.
			String head = new String(buf, 0, filled, UTF8);
			java.util.regex.Matcher m = HOST_HINT_PATTERN.matcher(head);
			if (m.find()) {
				// A room title cannot fake this: a quote inside a JSON string is
				// written \" and the pattern will not match across the escape.
				return unescapeJsonString(m.group(1));
			}
			if (filled < buf.length) {
				// The whole file fit in the buffer and the field is nowhere in it,
				// so "no hint" is a fact here whatever order the keys were written
				// in — this branch never guesses from a partial read.
				return "";
			}
			// Bigger than the budget and not in the head. Do not conclude anything.
			return null;
		} catch (IOException e) {
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static final java.util.regex.Pattern HOST_HINT_PATTERN =
			java.util.regex.Pattern.compile("\"hostHint\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

	/** Just enough of JSON string unescaping for a host name. */
	private static String unescapeJsonString(final String raw) {
		if (raw == null || raw.indexOf('\\') < 0) {
			return raw;
		}
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c != '\\' || i + 1 >= raw.length()) {
				sb.append(c);
				continue;
			}
			char next = raw.charAt(++i);
			switch (next) {
			case 'n': sb.append('\n'); break;
			case 'r': sb.append('\r'); break;
			case 't': sb.append('\t'); break;
			case 'b': sb.append('\b'); break;
			case 'f': sb.append('\f'); break;
			case 'u':
				if (i + 4 < raw.length()) {
					try {
						sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
						i += 4;
					} catch (NumberFormatException e) {
						sb.append(next);
					}
				} else {
					sb.append(next);
				}
				break;
			default: sb.append(next); break;
			}
		}
		return sb.toString();
	}

	/**
	 * Load a map by display/safe name (with or without {@code .json}).
	 *
	 * @return map or null if missing / unreadable
	 */
	public static MudMap load(Context context, String name) throws IOException, JSONException {
		File file = mapFile(context, name);
		if (file == null || !file.isFile()) {
			return null;
		}
		return loadFromFile(file);
	}

	/**
	 * Load map JSON from an absolute {@link File}.
	 *
	 * @return map or null if missing / not a file
	 */
	public static MudMap loadFromFile(File file) throws IOException, JSONException {
		if (file == null || !file.isFile()) {
			return null;
		}
		String json = readFile(file);
		return fromJson(json);
	}

	/**
	 * Load map JSON from an absolute path, or a path relative to the BlowTorch root
	 * (e.g. {@code maps/backup.json} → {@code /BlowTorch/maps/backup.json}).
	 *
	 * @return map or null if missing / not a file
	 */
	public static MudMap loadFromPath(Context context, String path)
			throws IOException, JSONException {
		File file = resolveExternalPath(context, path);
		return loadFromFile(file);
	}

	/**
	 * Resolve {@code path} to a {@link File}: absolute paths as-is; otherwise under
	 * the BlowTorch root.
	 */
	public static File resolveExternalPath(Context context, String path) {
		if (TextUtils.isEmpty(path)) {
			return null;
		}
		String p = path.trim();
		if (p.startsWith("/")) {
			return new File(p);
		}
		if (context == null) {
			return new File(p);
		}
		File root = SDCardUtils.resolveBlowTorchRoot(context);
		return new File(root, p);
	}

	/**
	 * Save map using its {@link MudMap#getName()} as the file basename.
	 *
	 * @return the file written
	 */
	public static File save(Context context, MudMap map) throws IOException, JSONException {
		if (map == null) {
			throw new IllegalArgumentException("map is null");
		}
		File file = fileFor(context, map);
		return saveToFile(file, map);
	}

	/** Where this map is kept, by its own name. */
	private static File fileFor(Context context, MudMap map) {
		String name = map.getName();
		if (TextUtils.isEmpty(name)) {
			name = map.getId() != null ? map.getId() : "unnamed";
		}
		return mapFile(context, name);
	}

	/**
	 * Build JSON on the caller (live {@link MudMap} must not race a writer).
	 * File open on a daemon. Autosave was on {@code :stellar} main: StrictMode
	 * 174 {@code writeFile} hits / session, worst 15 ms. Repeated saves of one
	 * map coalesce.
	 */
	public static void saveAsync(Context context, MudMap map) throws JSONException {
		if (map == null) {
			return;
		}
		File file = fileFor(context, map);
		if (file == null) {
			return;
		}
		String json = toJson(map).toString(2);
		PENDING.put(file.getAbsolutePath(), json);
		startWriter();
		if (!WRITE_QUEUE.offer(file)) {
			// Unbounded queue; this cannot happen, but losing a map silently is
			// the one outcome worth a line in the log.
			BlowTorchLogger.logError(context, "MapStore",
					"Could not queue a map save: " + file.getAbsolutePath());
		}
	}

	/**
	 * Write everything {@link #saveAsync} has queued, on this thread, now.
	 *
	 * <p>The writer is a {@code MIN_PRIORITY} daemon, so nothing guarantees it is
	 * scheduled again before the process goes away. Any teardown that means "this
	 * is the last chance" has to drain the queue itself rather than hand it over
	 * — the same reason {@code SessionLogger.endSession} exists.
	 *
	 * <p>Safe to race with the writer: both take entries out of {@code PENDING}
	 * with {@code remove}, so exactly one of them writes each version, and a map
	 * that has already been written is a no-op here.
	 */
	public static void flushPendingWrites() {
		if (PENDING.isEmpty()) {
			return;
		}
		for (String path : new ArrayList<String>(PENDING.keySet())) {
			String json = PENDING.remove(path);
			if (json == null) {
				// The writer thread got to this one first.
				continue;
			}
			try {
				writeFile(new File(path), json);
			} catch (IOException e) {
				BlowTorchLogger.logThrowable("MapStore.flushPendingWrites", e);
			}
		}
	}

	/** Newest unwritten JSON per map file. See {@link #saveAsync}. */
	private static final java.util.concurrent.ConcurrentHashMap<String, String> PENDING =
			new java.util.concurrent.ConcurrentHashMap<String, String>();

	private static final java.util.concurrent.BlockingQueue<File> WRITE_QUEUE =
			new java.util.concurrent.LinkedBlockingQueue<File>();

	private static Thread sWriter;

	private static synchronized void startWriter() {
		if (sWriter != null) {
			return;
		}
		sWriter = new Thread(new Runnable() {
			@Override
			public void run() {
				writerLoop();
			}
		}, "bt-map-save");
		sWriter.setDaemon(true);
		sWriter.setPriority(Thread.MIN_PRIORITY);
		sWriter.start();
	}

	private static void writerLoop() {
		while (true) {
			File file;
			try {
				file = WRITE_QUEUE.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			// Null means an earlier pass already wrote a newer version of this
			// map: that is the coalescing, and the reason the queue may hold
			// more entries than there is work.
			String json = PENDING.remove(file.getAbsolutePath());
			if (json == null) {
				continue;
			}
			try {
				writeFile(file, json);
			} catch (IOException e) {
				BlowTorchLogger.logThrowable("MapStore.saveAsync", e);
			}
		}
	}

	/**
	 * Write map JSON to an arbitrary file (creates parent directories when needed).
	 *
	 * @return the file written
	 */
	public static File saveToFile(File file, MudMap map) throws IOException, JSONException {
		if (map == null) {
			throw new IllegalArgumentException("map is null");
		}
		if (file == null) {
			throw new IllegalArgumentException("file is null");
		}
		String json = toJson(map).toString(2);
		writeFile(file, json);
		return file;
	}

	/** Delete map file by name. Returns true if deleted or already absent. */
	public static boolean delete(Context context, String name) {
		File file = mapFile(context, name);
		if (file == null) {
			return false;
		}
		invalidateSummary(file);
		if (!file.exists()) {
			return true;
		}
		return file.delete();
	}

	/** True if a map JSON already exists for this display name (after sanitizing). */
	public static boolean exists(Context context, String name) {
		File file = mapFile(context, name);
		return file != null && file.isFile();
	}

	public static File mapFile(Context context, String name) {
		String safe = safeName(name);
		if (safe.length() == 0) {
			return null;
		}
		return new File(mapsDirectory(context), safe + ".json");
	}

	/**
	 * Sanitize a map name for use as a filename (no path separators).
	 */
	public static String safeName(String name) {
		if (name == null) {
			return "";
		}
		String n = name.trim();
		if (n.toLowerCase(Locale.US).endsWith(".json")) {
			n = n.substring(0, n.length() - 5);
		}
		n = n.replaceAll("[\\\\/:*?\"<>|]", "_");
		n = n.replaceAll("\\s+", "_");
		if (n.equals(".") || n.equals("..")) {
			return "";
		}
		return n;
	}

	public static JSONObject toJson(MudMap map) throws JSONException {
		JSONObject root = new JSONObject();
		root.put("version", SCHEMA_VERSION);
		root.put("id", nullToEmpty(map.getId()));
		root.put("name", nullToEmpty(map.getName()));
		root.put("hostHint", nullToEmpty(map.getHostHint()));
		root.put("currentTileId", nullToEmpty(map.getCurrentTileId()));
		root.put("currentLevelId", nullToEmpty(map.getCurrentLevelId()));

		JSONArray levels = new JSONArray();
		for (MapLevel level : map.getLevels()) {
			if (level == null) {
				continue;
			}
			JSONObject o = new JSONObject();
			o.put("id", nullToEmpty(level.getId()));
			o.put("name", nullToEmpty(level.getName()));
			o.put("index", level.getIndex());
			o.put("anchorTileId", nullToEmpty(level.getAnchorTileId()));
			o.put("anchorDir", nullToEmpty(level.getAnchorDir()));
			levels.put(o);
		}
		root.put("levels", levels);

		JSONArray tiles = new JSONArray();
		for (MapTile tile : map.getTiles()) {
			if (tile == null) {
				continue;
			}
			JSONObject o = new JSONObject();
			o.put("id", nullToEmpty(tile.getId()));
			o.put("levelId", nullToEmpty(tile.getLevelId()));
			o.put("gridX", tile.getGridX());
			o.put("gridY", tile.getGridY());
			o.put("title", nullToEmpty(tile.getTitle()));
			o.put("notes", nullToEmpty(tile.getNotes()));
			if (tile.getExternalId() != null && tile.getExternalId().length() > 0) {
				o.put("externalId", tile.getExternalId());
			}
			if (tile.isLockTitle()) {
				o.put("lockTitle", true);
			}
			if (tile.isLockPosition()) {
				o.put("lockPosition", true);
			}
			JSONArray exits = new JSONArray();
			for (MapExit exit : tile.getExits()) {
				if (exit == null) {
					continue;
				}
				JSONObject e = new JSONObject();
				e.put("fromId", nullToEmpty(exit.getFromId()));
				e.put("toId", nullToEmpty(exit.getToId()));
				e.put("command", nullToEmpty(exit.getCommand()));
				e.put("special", exit.isSpecial());
				if (exit.getReverseCommand() != null) {
					e.put("reverseCommand", exit.getReverseCommand());
				}
				if (exit.getTargetMap() != null && exit.getTargetMap().length() > 0) {
					e.put("targetMap", exit.getTargetMap());
				}
				// Written only when true, so maps without guesses are unchanged
				// and older readers ignore it.
				if (exit.isGuessed()) {
					e.put("guessed", true);
				}
				// Always written, both values: absent has to keep meaning "from
				// before this was recorded", so an unchecked exit cannot be
				// mistaken for an old one on the way back in.
				e.put("verified", exit.isVerified());
				exits.put(e);
			}
			o.put("exits", exits);
			tiles.put(o);
		}
		root.put("tiles", tiles);

		JSONArray conflicts = new JSONArray();
		for (MapConflict c : map.getConflicts()) {
			if (c == null) {
				continue;
			}
			JSONObject o = new JSONObject();
			o.put("id", nullToEmpty(c.getId()));
			o.put("type", c.getType() != null ? c.getType().name() : "");
			o.put("message", nullToEmpty(c.getMessage()));
			o.put("resolved", c.isResolved());
			JSONArray ids = new JSONArray();
			for (String tid : c.getTileIds()) {
				ids.put(tid != null ? tid : "");
			}
			o.put("tileIds", ids);
			conflicts.put(o);
		}
		root.put("conflicts", conflicts);
		return root;
	}

	public static MudMap fromJson(String json) throws JSONException {
		JSONObject root = new JSONObject(json);
		MudMap map = new MudMap();
		map.setId(root.optString("id", map.getId()));
		map.setName(root.optString("name", null));
		map.setHostHint(emptyToNull(root.optString("hostHint", "")));
		map.setCurrentTileId(emptyToNull(root.optString("currentTileId", "")));
		map.setCurrentLevelId(emptyToNull(root.optString("currentLevelId", "")));

		JSONArray levels = root.optJSONArray("levels");
		if (levels != null) {
			List<MapLevel> list = new ArrayList<MapLevel>();
			for (int i = 0; i < levels.length(); i++) {
				JSONObject o = levels.getJSONObject(i);
				MapLevel level = new MapLevel(
						o.optString("id", null),
						emptyToNull(o.optString("name", "")),
						o.optInt("index", i),
						emptyToNull(o.optString("anchorTileId", "")),
						emptyToNull(o.optString("anchorDir", "")));
				list.add(level);
			}
			map.setLevels(list);
		}

		JSONArray tiles = root.optJSONArray("tiles");
		if (tiles != null) {
			List<MapTile> list = new ArrayList<MapTile>();
			for (int i = 0; i < tiles.length(); i++) {
				JSONObject o = tiles.getJSONObject(i);
				MapTile tile = new MapTile(
						o.optString("id", null),
						emptyToNull(o.optString("levelId", "")),
						o.optInt("gridX", 0),
						o.optInt("gridY", 0));
				tile.setTitle(emptyToNull(o.optString("title", "")));
				tile.setNotes(emptyToNull(o.optString("notes", "")));
				tile.setExternalId(emptyToNull(o.optString("externalId", "")));
				tile.setLockTitle(o.optBoolean("lockTitle", false));
				tile.setLockPosition(o.optBoolean("lockPosition", false));
				JSONArray exits = o.optJSONArray("exits");
				if (exits != null) {
					for (int j = 0; j < exits.length(); j++) {
						JSONObject e = exits.getJSONObject(j);
						String reverse = e.has("reverseCommand")
								? emptyToNull(e.optString("reverseCommand", ""))
								: null;
						MapExit exit = new MapExit(
								emptyToNull(e.optString("fromId", "")),
								emptyToNull(e.optString("toId", "")),
								emptyToNull(e.optString("command", "")),
								e.optBoolean("special", false),
								reverse);
						if (e.has("targetMap")) {
							exit.setTargetMap(emptyToNull(e.optString("targetMap", "")));
						}
						// Absent in maps written before this existed, which is the
						// safe reading: those exits are never withdrawn.
						exit.setGuessed(e.optBoolean("guessed", false));
						// Absent means the map predates the record. Those exits were
						// walked; only the note of it is missing.
						exit.setVerified(e.optBoolean("verified", true));
						tile.addExit(exit);
					}
				}
				list.add(tile);
			}
			map.setTiles(list);
		}

		JSONArray conflicts = root.optJSONArray("conflicts");
		if (conflicts != null) {
			List<MapConflict> list = new ArrayList<MapConflict>();
			for (int i = 0; i < conflicts.length(); i++) {
				JSONObject o = conflicts.getJSONObject(i);
				MapConflict c = new MapConflict();
				c.setId(o.optString("id", c.getId()));
				String typeName = o.optString("type", "");
				try {
					if (typeName.length() > 0) {
						c.setType(MapConflict.Type.valueOf(typeName));
					}
				} catch (IllegalArgumentException ignored) {
					// leave type null for unknown schema extensions
				}
				c.setMessage(emptyToNull(o.optString("message", "")));
				c.setResolved(o.optBoolean("resolved", false));
				JSONArray ids = o.optJSONArray("tileIds");
				if (ids != null) {
					List<String> tileIds = new ArrayList<String>();
					for (int j = 0; j < ids.length(); j++) {
						tileIds.add(ids.optString(j, ""));
					}
					c.setTileIds(tileIds);
				}
				list.add(c);
			}
			map.setConflicts(list);
		}
		return map;
	}

	private static String nullToEmpty(String s) {
		return s != null ? s : "";
	}

	private static String emptyToNull(String s) {
		if (s == null || s.length() == 0) {
			return null;
		}
		return s;
	}

	private static String readFile(File file) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
			char[] buf = new char[4096];
			int n;
			while ((n = reader.read(buf)) >= 0) {
				sb.append(buf, 0, n);
			}
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ignored) {
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Staging sibling then rename: {@code FileOutputStream} truncates first, and
	 * this process is killed routinely. Sibling so {@code renameTo} stays on one
	 * filesystem. No {@code fsync}: {@code flushPendingWrites} /
	 * {@code onTaskRemoved} run on the service main thread; that fsync is the
	 * disconnect stall in {@code ConnectionSettingsIO}.
	 */
	private static void writeFile(File file, String content) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		if (parent == null) {
			parent = file.getAbsoluteFile().getParentFile();
		}
		// createTempFile rejects a prefix shorter than three characters, and the
		// path here does not always come from mapFile().
		String prefix = file.getName();
		if (prefix.length() < 3) {
			prefix = "map" + prefix;
		}
		File tmp = File.createTempFile(prefix, ".tmp", parent);
		boolean replaced = false;
		try {
			FileOutputStream fos = null;
			OutputStreamWriter writer = null;
			try {
				fos = new FileOutputStream(tmp);
				writer = new OutputStreamWriter(fos, UTF8);
				writer.write(content);
				writer.flush();
			} finally {
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException ignored) {
					}
				} else if (fos != null) {
					try {
						fos.close();
					} catch (IOException ignored) {
					}
				}
			}
			if (!tmp.renameTo(file)) {
				// Old file left intact on purpose — the caller gets an error and
				// still has the previous map.
				throw new IOException("could not replace " + file.getAbsolutePath());
			}
			replaced = true;
		} finally {
			if (!replaced) {
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
			}
			// Every write in this class lands here. The stat check in readHostHint
			// would catch this anyway, but not for a rewrite that keeps the length
			// and lands in the same second.
			invalidateSummary(file);
		}
	}
}
