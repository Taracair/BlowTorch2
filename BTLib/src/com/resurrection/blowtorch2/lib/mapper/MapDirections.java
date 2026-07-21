package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;

/**
 * Movement lexicon for the mapper: synonyms → canonical tokens, opposites,
 * and how planar moves shift tiles on the grid ({@code dx}/{@code dy}).
 * <p>
 * Grid convention (screen): {@code +x = east}, {@code +y = south}
 * (north decreases {@code y}).
 * Level changes ({@code up}/{@code down}) are not grid offsets — see
 * {@link #levelDelta(String)}.
 */
public final class MapDirections {

	private static final Map<String, String> COMMON_OPPOSITES = new HashMap<String, String>();
	private static final Map<String, String> COMMON_ALIASES = new HashMap<String, String>();
	private static final Map<String, String> SHORT_TO_LONG = new HashMap<String, String>();
	/** Canonical token → {@code {dx, dy}} on the map grid. */
	private static final Map<String, int[]> GRID_DELTA = new LinkedHashMap<String, int[]>();
	/** Canonical token → level index delta (+1 up / -1 down). */
	private static final Map<String, Integer> LEVEL_DELTA = new HashMap<String, Integer>();

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
		putPair("enter", "leave");
		putPair("climb", "descend");

		// Long / short forms
		alias("north", "n");
		alias("south", "s");
		alias("east", "e");
		alias("west", "w");
		alias("up", "u");
		alias("down", "d");
		alias("northeast", "ne");
		alias("northwest", "nw");
		alias("southeast", "se");
		alias("southwest", "sw");

		// Extra movement synonyms → canonical short token
		alias("northern", "n");
		alias("southern", "s");
		alias("eastern", "e");
		alias("western", "w");
		alias("nort", "n");
		alias("nord", "n");
		alias("sud", "s");
		alias("ost", "e");
		alias("ouest", "w");
		alias("neast", "ne");
		alias("nwest", "nw");
		alias("seast", "se");
		alias("swest", "sw");
		alias("climb", "u");
		alias("ascend", "u");
		alias("descend", "d");
		alias("leave", "out");
		alias("exit", "out");
		alias("enter", "in");

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
		SHORT_TO_LONG.put("in", "enter");
		SHORT_TO_LONG.put("out", "leave");

		// Planar grid: +x east, +y south
		putGrid("n", 0, -1);
		putGrid("s", 0, 1);
		putGrid("e", 1, 0);
		putGrid("w", -1, 0);
		putGrid("ne", 1, -1);
		putGrid("nw", -1, -1);
		putGrid("se", 1, 1);
		putGrid("sw", -1, 1);
		putGrid("north", 0, -1);
		putGrid("south", 0, 1);
		putGrid("east", 1, 0);
		putGrid("west", -1, 0);
		putGrid("northeast", 1, -1);
		putGrid("northwest", -1, -1);
		putGrid("southeast", 1, 1);
		putGrid("southwest", -1, 1);

		LEVEL_DELTA.put("u", Integer.valueOf(1));
		LEVEL_DELTA.put("up", Integer.valueOf(1));
		LEVEL_DELTA.put("d", Integer.valueOf(-1));
		LEVEL_DELTA.put("down", Integer.valueOf(-1));
	}

	private MapDirections() {
	}

	private static void putPair(String a, String b) {
		COMMON_OPPOSITES.put(a, b);
		COMMON_OPPOSITES.put(b, a);
	}

	private static void alias(String from, String toCanonical) {
		COMMON_ALIASES.put(from, toCanonical);
	}

	private static void putGrid(String token, int dx, int dy) {
		GRID_DELTA.put(token, new int[] { dx, dy });
	}

	/**
	 * Grid offset for a (preferably normalized) movement token.
	 *
	 * @return {@code {dx, dy}} or {@code null} if not a planar grid move
	 */
	public static int[] gridDelta(String token) {
		if (token == null) {
			return null;
		}
		String n = token.trim().toLowerCase(Locale.US);
		if (n.length() == 0) {
			return null;
		}
		int[] d = GRID_DELTA.get(n);
		if (d != null) {
			return new int[] { d[0], d[1] };
		}
		String canon = COMMON_ALIASES.get(n);
		if (canon != null) {
			d = GRID_DELTA.get(canon);
			if (d != null) {
				return new int[] { d[0], d[1] };
			}
		}
		return null;
	}

	/**
	 * Level-index delta for up/down style moves.
	 *
	 * @return +1 / -1 or {@code null} if not a level change
	 */
	public static Integer levelDelta(String token) {
		if (token == null) {
			return null;
		}
		String n = token.trim().toLowerCase(Locale.US);
		Integer d = LEVEL_DELTA.get(n);
		if (d != null) {
			return d;
		}
		String canon = COMMON_ALIASES.get(n);
		if (canon != null) {
			return LEVEL_DELTA.get(canon);
		}
		return null;
	}

	/** True when the token builds a neighbor on the same level grid. */
	public static boolean isPlanarMove(String token) {
		return gridDelta(token) != null;
	}

	/** True when the token changes map level (up/down). */
	public static boolean isLevelChange(String token) {
		return levelDelta(token) != null;
	}

	/**
	 * Human-readable lexicon lines for {@code .map dirs} / help.
	 */
	public static List<String> lexiconSummary() {
		List<String> out = new ArrayList<String>();
		out.add("Grid (+x east, +y south):");
		out.add("  n/north     → (0,-1)");
		out.add("  s/south     → (0,+1)");
		out.add("  e/east      → (+1,0)");
		out.add("  w/west      → (-1,0)");
		out.add("  ne/nw/se/sw → diagonals");
		out.add("Levels:");
		out.add("  u/up/climb/ascend → level +1");
		out.add("  d/down/descend    → level -1");
		out.add("Special (off-grid neighbor):");
		out.add("  in/enter, out/leave/exit, and any other command");
		out.add("Prefixes stripped: go | walk | move  (e.g. go west → w)");
		out.add("Also uses connection Speedwalk direction map when set.");
		return Collections.unmodifiableList(out);
	}

	/**
	 * Normalize a player-typed command to a canonical exit token.
	 * Strips MOO-style {@code go}/{@code walk}/{@code move} prefixes, then
	 * prefers the built-in movement lexicon ({@code se}, {@code west}, …)
	 * before the connection Speedwalk map.
	 * <p>
	 * Speedwalk defaults bind keys {@code h}/{@code j}/{@code k}/{@code l} to
	 * commands {@code nw}/{@code ne}/{@code sw}/{@code se}. Matching the
	 * <em>command</em> must not collapse {@code se}→{@code l}, or recording
	 * treats diagonals as specials and places them via {@code findFreeNear}.
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

		// Built-in lexicon first (cardinals, diagonals, up/down, in/out).
		String lex = lexiconToken(lower);
		if (lex != null) {
			return lex;
		}

		if (directions != null && !directions.isEmpty()) {
			String fromMap = matchDirectionMap(fullLower, directions);
			if (fromMap == null && !lower.equals(fullLower)) {
				fromMap = matchDirectionMap(lower, directions);
			}
			if (fromMap != null) {
				String mappedLex = lexiconToken(fromMap);
				return mappedLex != null ? mappedLex : fromMap;
			}
		}

		return lower;
	}

	/**
	 * Canonical built-in movement token, or null if unknown to the lexicon.
	 */
	public static String lexiconToken(String token) {
		if (token == null) {
			return null;
		}
		String n = token.trim().toLowerCase(Locale.US);
		if (n.length() == 0) {
			return null;
		}
		String alias = COMMON_ALIASES.get(n);
		if (alias != null) {
			return alias;
		}
		if (GRID_DELTA.containsKey(n) || LEVEL_DELTA.containsKey(n)
				|| COMMON_OPPOSITES.containsKey(n)) {
			return n;
		}
		return null;
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
				return mapperTokenFromDirectionEntry(key, d);
			}
			String dir = d.getDirection();
			if (dir != null && lower.equals(dir.toLowerCase(Locale.US))) {
				return mapperTokenFromDirectionEntry(dir, d);
			}
			String cmd = d.getCommand();
			if (cmd != null && lower.equals(cmd.toLowerCase(Locale.US))) {
				return mapperTokenFromCommandMatch(cmd, dir, key);
			}
			if (cmd != null) {
				String cmdStripped = stripMovementPrefix(cmd.toLowerCase(Locale.US));
				if (lower.equals(cmdStripped)
						|| stripMovementPrefix(lower).equals(cmdStripped)) {
					return mapperTokenFromCommandMatch(cmd, dir, key);
				}
			}
		}
		return null;
	}

	/**
	 * When the player typed a Speedwalk <em>key</em> ({@code l}), prefer the
	 * outbound command if it is a known compass move ({@code se}).
	 */
	private static String mapperTokenFromDirectionEntry(String matched,
			DirectionData d) {
		String matchedLex = lexiconToken(matched);
		if (matchedLex != null) {
			return matchedLex;
		}
		if (d != null && d.getCommand() != null) {
			String cmdLex = lexiconToken(stripMovementPrefix(
					d.getCommand().toLowerCase(Locale.US)));
			if (cmdLex != null) {
				return cmdLex;
			}
		}
		return matched.toLowerCase(Locale.US);
	}

	/**
	 * When the player typed the outbound command ({@code se} / {@code go se}),
	 * keep that compass token — do not replace it with the Speedwalk key.
	 */
	private static String mapperTokenFromCommandMatch(String cmd, String dir,
			String key) {
		String cmdLex = lexiconToken(stripMovementPrefix(cmd.toLowerCase(Locale.US)));
		if (cmdLex != null) {
			return cmdLex;
		}
		if (dir != null && dir.length() > 0) {
			String dirLex = lexiconToken(dir);
			return dirLex != null ? dirLex : dir.toLowerCase(Locale.US);
		}
		if (key != null) {
			String keyLex = lexiconToken(key);
			return keyLex != null ? keyLex : key.toLowerCase(Locale.US);
		}
		return cmd.toLowerCase(Locale.US);
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
	 * Opposite of a common direction. Accepts short or long forms.
	 * Returns null when unknown.
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
