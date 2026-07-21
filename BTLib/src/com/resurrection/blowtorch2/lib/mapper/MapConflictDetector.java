package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scans a {@link MudMap} for structural conflicts:
 * asymmetric links, duplicate commands from one tile, grid collisions on a level.
 * Does not invent GMCP mismatches (those need live GMCP input).
 */
public final class MapConflictDetector {

	private MapConflictDetector() {
	}

	/**
	 * Run a full scan and return newly detected conflicts (resolved=false).
	 * Does not mutate the map; caller may merge into {@link MudMap#getConflicts()}.
	 */
	public static List<MapConflict> scan(MudMap map) {
		List<MapConflict> found = new ArrayList<MapConflict>();
		if (map == null) {
			return found;
		}
		found.addAll(findGridCollisions(map));
		found.addAll(findDuplicateExits(map));
		found.addAll(findAsymmetricLinks(map));
		return found;
	}

	/**
	 * Replace unresolved auto-detectable conflicts on the map with a fresh scan.
	 * Preserves resolved conflicts and any {@link MapConflict.Type#GMCP_MISMATCH}.
	 */
	public static void refreshConflicts(MudMap map) {
		if (map == null) {
			return;
		}
		List<MapConflict> keep = new ArrayList<MapConflict>();
		for (MapConflict c : map.getConflicts()) {
			if (c == null) {
				continue;
			}
			if (c.isResolved() || c.getType() == MapConflict.Type.GMCP_MISMATCH) {
				keep.add(c);
			}
		}
		keep.addAll(scan(map));
		map.setConflicts(keep);
	}

	static List<MapConflict> findGridCollisions(MudMap map) {
		List<MapConflict> out = new ArrayList<MapConflict>();
		// key: levelId|x|y -> tile ids
		Map<String, List<String>> buckets = new HashMap<String, List<String>>();
		for (MapTile tile : map.getTiles()) {
			if (tile == null || tile.getId() == null) {
				continue;
			}
			String level = tile.getLevelId() != null ? tile.getLevelId() : "";
			String key = level + "|" + tile.getGridX() + "|" + tile.getGridY();
			List<String> ids = buckets.get(key);
			if (ids == null) {
				ids = new ArrayList<String>();
				buckets.put(key, ids);
			}
			ids.add(tile.getId());
		}
		for (Map.Entry<String, List<String>> e : buckets.entrySet()) {
			List<String> ids = e.getValue();
			if (ids.size() < 2) {
				continue;
			}
			String[] parts = e.getKey().split("\\|", -1);
			String msg = "Grid collision at (" + parts[1] + "," + parts[2] + ") on level "
					+ parts[0] + ": " + ids.size() + " tiles";
			out.add(new MapConflict(MapConflict.Type.GRID_COLLISION, msg,
					new ArrayList<String>(ids)));
		}
		return out;
	}

	static List<MapConflict> findDuplicateExits(MudMap map) {
		List<MapConflict> out = new ArrayList<MapConflict>();
		for (MapTile tile : map.getTiles()) {
			if (tile == null || tile.getId() == null) {
				continue;
			}
			Map<String, List<MapExit>> byCmd = new HashMap<String, List<MapExit>>();
			for (MapExit exit : tile.getExits()) {
				if (exit == null || exit.getCommand() == null) {
					continue;
				}
				String cmd = normalizeCmd(exit.getCommand());
				if (cmd.length() == 0) {
					continue;
				}
				List<MapExit> list = byCmd.get(cmd);
				if (list == null) {
					list = new ArrayList<MapExit>();
					byCmd.put(cmd, list);
				}
				list.add(exit);
			}
			for (Map.Entry<String, List<MapExit>> e : byCmd.entrySet()) {
				if (e.getValue().size() < 2) {
					continue;
				}
				Set<String> targets = new HashSet<String>();
				for (MapExit ex : e.getValue()) {
					if (ex.getToId() != null) {
						targets.add(ex.getToId());
					}
				}
				String msg = "Duplicate exit '" + e.getKey() + "' from tile " + tile.getId()
						+ " (" + e.getValue().size() + " edges, " + targets.size() + " targets)";
				out.add(new MapConflict(MapConflict.Type.DUPLICATE_EXIT, msg,
						Arrays.asList(tile.getId())));
			}
		}
		return out;
	}

	static List<MapConflict> findAsymmetricLinks(MudMap map) {
		List<MapConflict> out = new ArrayList<MapConflict>();
		Set<String> reported = new HashSet<String>();

		Map<String, MapTile> byId = new HashMap<String, MapTile>();
		for (MapTile tile : map.getTiles()) {
			if (tile != null && tile.getId() != null) {
				byId.put(tile.getId(), tile);
			}
		}

		for (MapTile from : map.getTiles()) {
			if (from == null || from.getId() == null) {
				continue;
			}
			for (MapExit exit : from.getExits()) {
				if (exit == null || exit.getToId() == null) {
					continue;
				}
				if (exit.isSpecial()) {
					// Special exits often have no natural reverse; skip asymmetry.
					continue;
				}
				String cmd = exit.getCommand();
				if (cmd == null || cmd.trim().length() == 0) {
					continue;
				}
				MapTile to = byId.get(exit.getToId());
				if (to == null) {
					continue;
				}
				String expectedReverse = exit.getReverseCommand();
				if (expectedReverse == null || expectedReverse.trim().length() == 0) {
					expectedReverse = MapDirections.opposite(cmd);
				}
				if (expectedReverse == null) {
					continue;
				}
				if (hasExit(to, from.getId(), expectedReverse)) {
					continue;
				}
				String pairKey = orderedPairKey(from.getId(), to.getId(), normalizeCmd(cmd));
				if (reported.contains(pairKey)) {
					continue;
				}
				reported.add(pairKey);
				String msg = "Asymmetric link: " + from.getId() + " --[" + cmd + "]--> "
						+ to.getId() + " (missing reverse '" + expectedReverse + "')";
				out.add(new MapConflict(MapConflict.Type.ASYMMETRIC, msg,
						Arrays.asList(from.getId(), to.getId())));
			}
		}
		return out;
	}

	private static boolean hasExit(MapTile tile, String toId, String command) {
		String want = normalizeCmd(command);
		for (MapExit exit : tile.getExits()) {
			if (exit == null) {
				continue;
			}
			if (!toId.equals(exit.getToId())) {
				continue;
			}
			if (want.equals(normalizeCmd(exit.getCommand()))) {
				return true;
			}
		}
		return false;
	}

	private static String orderedPairKey(String a, String b, String cmd) {
		if (a.compareTo(b) <= 0) {
			return a + "|" + b + "|" + cmd;
		}
		return b + "|" + a + "|" + cmd;
	}

	private static String normalizeCmd(String command) {
		if (command == null) {
			return "";
		}
		return command.trim().toLowerCase(Locale.US);
	}
}
