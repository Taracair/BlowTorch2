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

/**
 * Persist {@link MudMap} as versioned JSON under {@code /BlowTorch/maps/}.
 * Schema version field: {@code "version": 1}.
 */
public final class MapStore {

	public static final int SCHEMA_VERSION = 1;
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
		String name = map.getName();
		if (TextUtils.isEmpty(name)) {
			name = map.getId() != null ? map.getId() : "unnamed";
		}
		File file = mapFile(context, name);
		return saveToFile(file, map);
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
		if (!file.exists()) {
			return true;
		}
		return file.delete();
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

	private static void writeFile(File file, String content) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		FileOutputStream fos = null;
		OutputStreamWriter writer = null;
		try {
			fos = new FileOutputStream(file);
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
	}
}
