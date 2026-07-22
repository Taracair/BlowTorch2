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
		PathResult r = findPath(map, fromTileId, toTileId);
		return r != null ? r.commands : Collections.<String>emptyList();
	}

	/**
	 * Ordered tile ids along the shortest path (includes start and goal).
	 * Empty if unreachable; single-element if already there.
	 */
	public static List<String> findTileIds(MudMap map, String fromTileId, String toTileId) {
		PathResult r = findPath(map, fromTileId, toTileId);
		if (r == null) {
			return Collections.emptyList();
		}
		return r.tileIds;
	}

	private static final class PathResult {
		final List<String> commands;
		final List<String> tileIds;

		PathResult(List<String> commands, List<String> tileIds) {
			this.commands = commands;
			this.tileIds = tileIds;
		}
	}

	private static PathResult findPath(MudMap map, String fromTileId, String toTileId) {
		if (map == null || fromTileId == null || toTileId == null) {
			return null;
		}
		if (map.findTile(fromTileId) == null || map.findTile(toTileId) == null) {
			return null;
		}
		if (fromTileId.equals(toTileId)) {
			List<String> one = new ArrayList<String>();
			one.add(fromTileId);
			return new PathResult(Collections.<String>emptyList(), one);
		}

		Map<String, List<MapExit>> adjacency = buildAdjacency(map);
		Queue<String> queue = new LinkedList<String>();
		Set<String> visited = new HashSet<String>();
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
					return reconstructFull(fromTileId, toTileId, parent, cameVia);
				}
				queue.add(next);
			}
		}
		return null;
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

	private static PathResult reconstructFull(String start, String goal,
			Map<String, String> parent, Map<String, MapExit> cameVia) {
		LinkedList<String> commands = new LinkedList<String>();
		LinkedList<String> tiles = new LinkedList<String>();
		String cur = goal;
		tiles.addFirst(goal);
		while (cameVia.containsKey(cur)) {
			MapExit via = cameVia.get(cur);
			commands.addFirst(via.getCommand());
			cur = parent.get(cur);
			if (cur == null) {
				break;
			}
			tiles.addFirst(cur);
		}
		if (tiles.isEmpty() || !start.equals(tiles.getFirst())) {
			tiles.addFirst(start);
		}
		return new PathResult(new ArrayList<String>(commands), new ArrayList<String>(tiles));
	}
}
