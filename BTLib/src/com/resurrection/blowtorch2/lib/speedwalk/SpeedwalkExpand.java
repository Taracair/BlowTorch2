package com.resurrection.blowtorch2.lib.speedwalk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.resurrection.blowtorch2.lib.mapper.MapDirections;

/**
 * Expand a {@code .run} / {@code .rev} string against the Speedwalk Directions
 * map. Pure: no {@code Connection}, so the loop can be tested without the
 * service process.
 *
 * <p>{@link #forward} is the historic {@code .run} loop (including the extra
 * CRLF a comma inserts). {@link #reverse} tokenises, reverses the token list,
 * and maps each letter through Reverse then {@link MapDirections#suggestReverse}.
 * Comma text stays as written ({@code open door} is not guessed as
 * {@code close door}).
 */
public final class SpeedwalkExpand {

	public static final String CRLF = "\r\n";

	public static final class Result {
		public final boolean ok;
		public final String cmd;
		public final int errorIndex;
		public final String errorBit;
		public final String missingLetter;
		public final String missingCommand;

		private Result(boolean ok, String cmd, int errorIndex, String errorBit,
				String missingLetter, String missingCommand) {
			this.ok = ok;
			this.cmd = cmd == null ? "" : cmd;
			this.errorIndex = errorIndex;
			this.errorBit = errorBit == null ? "" : errorBit;
			this.missingLetter = missingLetter;
			this.missingCommand = missingCommand;
		}

		static Result success(String cmd) {
			return new Result(true, cmd, -1, "", null, null);
		}

		static Result invalid(int index, String bit) {
			return new Result(false, "", index, bit, null, null);
		}

		static Result missingReverse(int index, String letter, String command) {
			return new Result(false, "", index, letter, letter, command);
		}
	}

	private static final class Token {
		final boolean literal;
		final String letter;
		final int count;
		final String text;
		final int index;

		Token(boolean literal, String letter, int count, String text, int index) {
			this.literal = literal;
			this.letter = letter;
			this.count = count;
			this.text = text == null ? "" : text;
			this.index = index;
		}
	}

	private SpeedwalkExpand() {
	}

	/**
	 * Default compass row: letter, command, and Reverse from
	 * {@link MapDirections#opposite} when that pair is known.
	 */
	public static DirectionData compassEntry(String letter, String command) {
		DirectionData d = new DirectionData(letter, command);
		String opp = MapDirections.opposite(command);
		if (opp != null && opp.length() > 0) {
			d.setReverse(opp);
		}
		return d;
	}

	/**
	 * Historic {@code .run} expansion. Invalid letters fail; leftover digits
	 * with no letter are ignored, as before.
	 */
	public static Result forward(String str, Map<String, DirectionData> directions) {
		if (str == null) {
			str = "";
		}
		Map<String, DirectionData> dirs = directions == null
				? new HashMap<String, DirectionData>() : directions;
		StringBuffer buf = new StringBuffer();
		boolean commanding = false;
		LinkedList<Integer> runtable = new LinkedList<Integer>();
		for (int i = 0; i < str.length(); i++) {
			char theChar = str.charAt(i);
			String bit = String.valueOf(theChar);
			if (commanding) {
				if (bit.equals(",")) {
					commanding = false;
					buf.append(CRLF);
				} else {
					buf.append(bit);
				}
			} else {
				try {
					int num = Integer.parseInt(bit);
					runtable.add(Integer.valueOf(num));
				} catch (NumberFormatException e) {
					boolean valid = false;
					String respString = "";
					String testVal = Character.toString(theChar);
					if (testVal.equals(",")) {
						commanding = true;
						buf.append(CRLF);
					} else if (dirs.containsKey(testVal)) {
						valid = true;
						DirectionData d = dirs.get(testVal);
						respString = d == null ? "" : d.getCommand();
					}
					if (valid) {
						int run = countFrom(runtable);
						for (int j = 0; j < run; j++) {
							buf.append(respString).append(CRLF);
						}
						runtable.clear();
					} else if (!commanding) {
						return Result.invalid(i, bit);
					}
				}
			}
		}
		return stripTrailingCrlf(buf);
	}

	/**
	 * Reverse of {@code .run}: same letters and commas, opposite walking order.
	 * Reverse for a letter is {@link DirectionData#getReverse()} when filled,
	 * else the compass opposite of the forward command. Custom letters
	 * (door, cave) with neither fail so the player fills Reverse.
	 */
	public static Result reverse(String str, Map<String, DirectionData> directions) {
		if (str == null) {
			str = "";
		}
		Map<String, DirectionData> dirs = directions == null
				? new HashMap<String, DirectionData>() : directions;
		List<Token> tokens = new ArrayList<Token>();
		boolean commanding = false;
		StringBuilder literal = new StringBuilder();
		int literalStart = -1;
		LinkedList<Integer> runtable = new LinkedList<Integer>();
		for (int i = 0; i < str.length(); i++) {
			char theChar = str.charAt(i);
			String bit = String.valueOf(theChar);
			if (commanding) {
				if (bit.equals(",")) {
					tokens.add(new Token(true, "", 1, literal.toString(), literalStart));
					literal.setLength(0);
					commanding = false;
					literalStart = -1;
				} else {
					if (literalStart < 0) {
						literalStart = i;
					}
					literal.append(bit);
				}
			} else {
				try {
					int num = Integer.parseInt(bit);
					runtable.add(Integer.valueOf(num));
				} catch (NumberFormatException e) {
					String testVal = Character.toString(theChar);
					if (testVal.equals(",")) {
						commanding = true;
						literal.setLength(0);
						literalStart = -1;
					} else if (dirs.containsKey(testVal)) {
						DirectionData d = dirs.get(testVal);
						String cmd = d == null || d.getCommand() == null ? "" : d.getCommand();
						tokens.add(new Token(false, testVal, countFrom(runtable), cmd, i));
						runtable.clear();
					} else {
						return Result.invalid(i, bit);
					}
				}
			}
		}
		if (commanding) {
			tokens.add(new Token(true, "", 1, literal.toString(), literalStart));
		}
		StringBuffer buf = new StringBuffer();
		for (int t = tokens.size() - 1; t >= 0; t--) {
			Token tok = tokens.get(t);
			if (tok.literal) {
				buf.append(tok.text).append(CRLF);
				continue;
			}
			DirectionData d = dirs.get(tok.letter);
			String rev = resolvedReverse(d, dirs);
			if (rev == null) {
				String cmd = d == null ? "" : d.getCommand();
				return Result.missingReverse(tok.index, tok.letter, cmd);
			}
			for (int j = 0; j < tok.count; j++) {
				buf.append(rev).append(CRLF);
			}
		}
		return stripTrailingCrlf(buf);
	}

	/**
	 * Command {@code .rev} should send for this row, or null when the player
	 * must fill Reverse (non-compass letters).
	 */
	public static String resolvedReverse(DirectionData data,
			Map<String, DirectionData> directions) {
		if (data == null) {
			return null;
		}
		String filled = data.getReverse();
		if (filled != null && filled.trim().length() > 0) {
			return filled.trim();
		}
		String cmd = data.getCommand();
		if (cmd == null || cmd.length() == 0) {
			return null;
		}
		String suggested = MapDirections.suggestReverse(cmd, directions);
		if (suggested == null || suggested.trim().length() == 0) {
			return null;
		}
		return suggested.trim();
	}

	private static int countFrom(LinkedList<Integer> runtable) {
		int run = 1;
		if (runtable.size() > 0) {
			run = 0;
			int tmpPlace = runtable.size() - 1;
			for (Integer tmp : runtable) {
				run += (int) (Math.pow(10, tmpPlace) * tmp.intValue());
				tmpPlace--;
			}
		}
		return run;
	}

	private static Result stripTrailingCrlf(StringBuffer buf) {
		String cmd = buf.toString();
		if (cmd.length() >= 2) {
			cmd = cmd.substring(0, cmd.length() - 2);
		}
		return Result.success(cmd);
	}
}
