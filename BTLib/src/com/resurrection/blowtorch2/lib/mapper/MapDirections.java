package com.resurrection.blowtorch2.lib.mapper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;

/**
 * Direction helpers for mapper exits: normalize player input against the
 * connection direction map, and suggest common / configured reverses.
 */
public final class MapDirections {

	private static final Map<String, String> COMMON_OPPOSITES = new HashMap<String, String>();
	private static final Map<String, String> COMMON_ALIASES = new HashMap<String, String>();

	static {
		putPair("n", "s");
		putPair("north", "south");
		putPair("e", "w");
		putPair("east", "west");
		putPair("u", "d");
		putPair("up", "down");
		putPair("in", "out");
		putPair("ne", "sw");
		putPair("northeast", "southwest");
		putPair("nw", "se");
		putPair("northwest", "southeast");

		COMMON_ALIASES.put("north", "n");
		COMMON_ALIASES.put("south", "s");
		COMMON_ALIASES.put("east", "e");
		COMMON_ALIASES.put("west", "w");
		COMMON_ALIASES.put("up", "u");
		COMMON_ALIASES.put("down", "d");
		COMMON_ALIASES.put("northeast", "ne");
		COMMON_ALIASES.put("northwest", "nw");
		COMMON_ALIASES.put("southeast", "se");
		COMMON_ALIASES.put("southwest", "sw");
	}

	private MapDirections() {
	}

	private static void putPair(String a, String b) {
		COMMON_OPPOSITES.put(a, b);
		COMMON_OPPOSITES.put(b, a);
	}

	/**
	 * Normalize a player-typed command to a canonical exit token.
	 * Uses the connection direction map when provided: matches either the
	 * direction key or the outbound command, preferring the direction key.
	 * Falls back to common aliases (north→n), else trimmed lowercase input.
	 *
	 * @param command   raw player command (may be null)
	 * @param directions connection direction map; may be null or empty
	 * @return canonical form, or empty string if command is null/blank
	 */
	public static String normalize(String command, Map<String, DirectionData> directions) {
		if (command == null) {
			return "";
		}
		String trimmed = command.trim();
		if (trimmed.length() == 0) {
			return "";
		}
		String lower = trimmed.toLowerCase(Locale.US);

		if (directions != null && !directions.isEmpty()) {
			for (Map.Entry<String, DirectionData> e : directions.entrySet()) {
				DirectionData d = e.getValue();
				if (d == null) {
					continue;
				}
				String key = e.getKey() != null ? e.getKey() : d.getDirection();
				if (key != null && lower.equals(key.toLowerCase(Locale.US))) {
					return key.toLowerCase(Locale.US);
				}
				String dir = d.getDirection();
				if (dir != null && lower.equals(dir.toLowerCase(Locale.US))) {
					return dir.toLowerCase(Locale.US);
				}
				String cmd = d.getCommand();
				if (cmd != null && lower.equals(cmd.toLowerCase(Locale.US))) {
					if (dir != null && dir.length() > 0) {
						return dir.toLowerCase(Locale.US);
					}
					if (key != null) {
						return key.toLowerCase(Locale.US);
					}
				}
			}
		}

		String alias = COMMON_ALIASES.get(lower);
		if (alias != null) {
			return alias;
		}
		return lower;
	}

	/**
	 * Opposite of a common direction (n/s, e/w, u/d, in/out, diagonals).
	 * Accepts short or long forms. Returns null when unknown.
	 */
	public static String opposite(String command) {
		if (command == null) {
			return null;
		}
		String lower = command.trim().toLowerCase(Locale.US);
		if (lower.length() == 0) {
			return null;
		}
		String opp = COMMON_OPPOSITES.get(lower);
		if (opp != null) {
			return opp;
		}
		String canonical = COMMON_ALIASES.get(lower);
		if (canonical != null) {
			return COMMON_OPPOSITES.get(canonical);
		}
		return null;
	}

	/**
	 * Suggest a reverse command: prefer {@link DirectionData#getReverse()} when
	 * the command matches an entry in the direction map; else {@link #opposite}.
	 */
	public static String suggestReverse(String command, Map<String, DirectionData> directions) {
		if (command == null) {
			return null;
		}
		String lower = command.trim().toLowerCase(Locale.US);
		if (directions != null) {
			for (Map.Entry<String, DirectionData> e : directions.entrySet()) {
				DirectionData d = e.getValue();
				if (d == null) {
					continue;
				}
				boolean match = false;
				String key = e.getKey();
				if (key != null && lower.equals(key.toLowerCase(Locale.US))) {
					match = true;
				} else if (d.getDirection() != null
						&& lower.equals(d.getDirection().toLowerCase(Locale.US))) {
					match = true;
				} else if (d.getCommand() != null
						&& lower.equals(d.getCommand().toLowerCase(Locale.US))) {
					match = true;
				}
				if (match) {
					String rev = d.getReverse();
					if (rev != null && rev.trim().length() > 0) {
						return rev.trim();
					}
					break;
				}
			}
		}
		return opposite(command);
	}
}
