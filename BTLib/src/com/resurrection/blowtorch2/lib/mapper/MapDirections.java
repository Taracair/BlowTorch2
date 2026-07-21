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
	private static final Map<String, String> SHORT_TO_LONG = new HashMap<String, String>();

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

		SHORT_TO_LONG.put("n", "north");
		SHORT_TO_LONG.put("s", "south");
		SHORT_TO_LONG.put("e", "east");
		SHORT_TO_LONG.put("w", "west");
		SHORT_TO_LONG.put("u", "up");
		SHORT_TO_LONG.put("d", "down");
		SHORT_TO_LONG.put("ne", "northeast");
		SHORT_TO_LONG.put("nw", "northwest");
		SHORT_TO_LONG.put("se", "southeast");
		SHORT_TO_LONG.put("sw", "southwest");
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
	 * Strips MOO-style {@code go}/{@code walk}/{@code move} prefixes.
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
		String fullLower = trimmed.toLowerCase(Locale.US);
		String lower = stripMovementPrefix(fullLower);

		if (directions != null && !directions.isEmpty()) {
			String fromMap = matchDirectionMap(fullLower, directions);
			if (fromMap == null && !lower.equals(fullLower)) {
				fromMap = matchDirectionMap(lower, directions);
			}
			if (fromMap != null) {
				return fromMap;
			}
		}

		String alias = COMMON_ALIASES.get(lower);
		if (alias != null) {
			return alias;
		}
		return lower;
	}

	private static String matchDirectionMap(String lower,
			Map<String, DirectionData> directions) {
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
			// Match "go west" against map command after stripping prefix on either side
			if (cmd != null) {
				String cmdStripped = stripMovementPrefix(cmd.toLowerCase(Locale.US));
				if (lower.equals(cmdStripped)
						|| stripMovementPrefix(lower).equals(cmdStripped)) {
					if (dir != null && dir.length() > 0) {
						return dir.toLowerCase(Locale.US);
					}
					if (key != null) {
						return key.toLowerCase(Locale.US);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Strip MOO-style prefixes ({@code go}/{@code walk}/{@code move}) so
	 * {@code go west} normalizes like {@code west}.
	 */
	public static String stripMovementPrefix(String lowerCommand) {
		if (lowerCommand == null) {
			return "";
		}
		String s = lowerCommand.trim();
		if (s.startsWith("go ") && s.length() > 3) {
			return s.substring(3).trim();
		}
		if (s.startsWith("walk ") && s.length() > 5) {
			return s.substring(5).trim();
		}
		if (s.startsWith("move ") && s.length() > 5) {
			return s.substring(5).trim();
		}
		return s;
	}

	/** Expand short direction tokens to long form ({@code w}→{@code west}). */
	public static String toLongForm(String token) {
		if (token == null) {
			return null;
		}
		String lower = token.trim().toLowerCase(Locale.US);
		String longForm = SHORT_TO_LONG.get(lower);
		return longForm != null ? longForm : lower;
	}

	/**
	 * Command to store on an exit: keep MOO {@code go …} wording when the player
	 * typed it, otherwise the normalized token.
	 */
	public static String storeCommand(String rawCommand, String normalized) {
		if (rawCommand == null) {
			return normalized != null ? normalized : "";
		}
		String low = rawCommand.trim().toLowerCase(Locale.US);
		if (low.startsWith("go ") || low.startsWith("walk ") || low.startsWith("move ")) {
			return low;
		}
		return normalized != null ? normalized : low;
	}

	/**
	 * Opposite of a common direction (n/s, e/w, u/d, in/out, diagonals).
	 * Accepts short or long forms. Returns null when unknown.
	 */
	public static String opposite(String command) {
		if (command == null) {
			return null;
		}
		String lower = stripMovementPrefix(command.trim().toLowerCase(Locale.US));
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
	 * Preserves a leading {@code go } prefix when the forward command used one.
	 */
	public static String suggestReverse(String command, Map<String, DirectionData> directions) {
		if (command == null) {
			return null;
		}
		String raw = command.trim();
		String fullLower = raw.toLowerCase(Locale.US);
		boolean hadGo = fullLower.startsWith("go ");
		String lower = stripMovementPrefix(fullLower);
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
				} else if (d.getCommand() != null) {
					String cmdLow = d.getCommand().toLowerCase(Locale.US);
					if (fullLower.equals(cmdLow) || lower.equals(stripMovementPrefix(cmdLow))) {
						match = true;
					}
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
		String opp = opposite(command);
		if (opp == null) {
			return null;
		}
		if (hadGo) {
			return "go " + toLongForm(opp);
		}
		return opp;
	}
}
