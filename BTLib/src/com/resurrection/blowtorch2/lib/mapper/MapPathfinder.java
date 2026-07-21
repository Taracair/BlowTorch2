package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Shortest path over map exits by hop count (BFS).
 * Returns the ordered list of command strings to walk from start to goal.
 */
public final class MapPathfinder {

	private MapPathfinder() {
	}

	/**
	 * Find commands from {@code fromTileId} to {@code toTileId}.
	 *
	 * @return command list, empty if already there or unreachable / invalid ids
	 */
	public static List<String> findCommands(MudMap map, String fromTileId, String toTileId) {
		List<String> empty = Collections.emptyList();
		if (map == null || fromTileId == null || toTileId == null) {
			return empty;
		}
		if (fromTileId.equals(toTileId)) {
			return empty;
		}
		if (map.findTile(fromTileId) == null || map.findTile(toTileId) == null) {
			return empty;
		}

		Map<String, List<MapExit>> adjacency = buildAdjacency(map);
		Queue<String> queue = new LinkedList<String>();
		Set<String> visited = new HashSet<String>();
		// predecessor tile id -> exit used to reach current from predecessor
		Map<String, MapExit> cameVia = new HashMap<String, MapExit>();
		Map<String, String> parent = new HashMap<String, String>();

		queue.add(fromTileId);
		visited.add(fromTileId);

		while (!queue.isEmpty()) {
			String current = queue.poll();
			List<MapExit> outs = adjacency.get(current);
			if (outs == null) {
				continue;
			}
			for (MapExit exit : outs) {
				String next = exit.getToId();
				if (next == null || visited.contains(next)) {
					continue;
				}
				visited.add(next);
				parent.put(next, current);
				cameVia.put(next, exit);
				if (next.equals(toTileId)) {
					return reconstruct(toTileId, parent, cameVia);
				}
				queue.add(next);
			}
		}
		return empty;
	}

	private static Map<String, List<MapExit>> buildAdjacency(MudMap map) {
		Map<String, List<MapExit>> adj = new HashMap<String, List<MapExit>>();
		for (MapTile tile : map.getTiles()) {
			if (tile == null || tile.getId() == null) {
				continue;
			}
			List<MapExit> list = adj.get(tile.getId());
			if (list == null) {
				list = new ArrayList<MapExit>();
				adj.put(tile.getId(), list);
			}
			for (MapExit exit : tile.getExits()) {
				if (exit == null || exit.getToId() == null) {
					continue;
				}
				if (exit.getCommand() == null || exit.getCommand().length() == 0) {
					continue;
				}
				// Prefer exit.fromId matching tile; still accept outgoing list as authoritative.
				list.add(exit);
			}
		}
		return adj;
	}

	private static List<String> reconstruct(String goal, Map<String, String> parent,
			Map<String, MapExit> cameVia) {
		LinkedList<String> commands = new LinkedList<String>();
		String cur = goal;
		while (cameVia.containsKey(cur)) {
			MapExit via = cameVia.get(cur);
			commands.addFirst(via.getCommand());
			cur = parent.get(cur);
			if (cur == null) {
				break;
			}
		}
		return new ArrayList<String>(commands);
	}
}
