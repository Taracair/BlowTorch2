package com.resurrection.blowtorch2.lib.window;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.ConcurrentModificationException;
import java.util.LinkedList;

/**
 * TEMPORARY PROBE — where a flying mini-map row disappears.
 * Revert after one on-device round. Tag {@code BTPROF}.
 */
public final class MapSwallowProbe {

	public static final String TAG = "BTPROF";

	private static final int DUMP_MAX = 360;
	private static final long HEARTBEAT_MS = 4000L;
	private static final long DRAW_MS = 600L;

	private static long lastHeartbeat;
	private static long lastDraw;
	private static long chunks;
	private static long mapChunks;
	private static long csiM;
	private static long csiEatFinal;
	private static long csiKeepFinal;
	private static long csiAbort;
	private static long twoByte;
	private static long oscAbort;
	private static long holdovers;
	private static long closedLines;
	private static long gags;
	private static long mxpShrink;
	private static int worstLost = 0;

	private static final ThreadLocal<State> TL = new ThreadLocal<State>();

	private static final class State {
		boolean map;
		int nl;
		int cr;
		int esc;
		int closed;
		int abort;
		int eatFinal;
		int keepFinal;
		int twoByte;
		int playerIn;
		int playerOut;
	}

	private MapSwallowProbe() {}

	public static boolean looksLikeMap(final byte[] d) {
		if (d == null || d.length == 0) {
			return false;
		}
		int brackets = 0;
		for (int i = 0; i < d.length; i++) {
			int b = d[i] & 0xFF;
			if (b == '|') {
				return true;
			}
			if (b == 'o' && i + 1 < d.length && (d[i + 1] & 0xFF) == 'O') {
				return true;
			}
			if (b == '(' && i + 2 < d.length
					&& d[i + 1] == ' ' && (d[i + 2] & 0xFF) == ')') {
				return true;
			}
			if (b == ':' && i + 1 < d.length && (d[i + 1] & 0xFF) == '|') {
				return true;
			}
			if (b == '[') {
				brackets++;
				if (brackets >= 3) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean looksLikeMapText(final String s) {
		if (s == null || s.length() == 0) {
			return false;
		}
		return s.indexOf('|') >= 0 || s.indexOf("oO") >= 0
				|| s.indexOf("( )") >= 0 || s.indexOf(":|") >= 0
				|| countChar(s, '[') >= 3;
	}

	public static void beginAddBytes(final byte[] data) {
		chunks++;
		State st = new State();
		if (data != null) {
			for (int i = 0; i < data.length; i++) {
				int b = data[i] & 0xFF;
				if (b == 0x0A) {
					st.nl++;
				} else if (b == 0x0D) {
					st.cr++;
				} else if (b == 0x1B) {
					st.esc++;
				}
			}
			st.map = looksLikeMap(data);
			st.playerIn = countToken(data);
			if (st.map) {
				mapChunks++;
				Log.d(TAG, prefix() + "IN bytes=" + data.length
						+ " nl=" + st.nl + " cr=" + st.cr + " esc=" + st.esc
						+ " token=" + st.playerIn);
				logChunkRows("IN", data);
			}
		}
		TL.set(st);
		heartbeat(false);
	}

	public static void endAddBytes(final byte[] holdover) {
		State st = TL.get();
		TL.remove();
		if (st == null) {
			heartbeat(false);
			return;
		}
		int lost = st.nl - st.closed;
		if (lost > worstLost) {
			worstLost = lost;
		}
		if (holdover != null && holdover.length > 0) {
			holdovers++;
			Log.d(TAG, prefix() + "HOLD len=" + holdover.length
					+ " data=" + esc(holdover, DUMP_MAX));
		}
		boolean interesting = st.map || st.abort > 0 || st.eatFinal > 0
				|| st.twoByte > 0 || lost > 0
				|| (st.playerIn != st.playerOut);
		if (interesting) {
			Log.d(TAG, prefix() + "OUT nl=" + st.nl + " closed=" + st.closed
					+ " lost=" + lost + " cr=" + st.cr
					+ " abort=" + st.abort + " eatFinal=" + st.eatFinal
					+ " keepFinal=" + st.keepFinal + " twoByte=" + st.twoByte
					+ " tokenIn=" + st.playerIn + " tokenOut=" + st.playerOut);
		}
		heartbeat(false);
	}

	public static void csiM() {
		csiM++;
	}

	public static void csiFinal(final int ub, final boolean consumed,
			final byte[] seq) {
		State st = TL.get();
		if (consumed) {
			csiEatFinal++;
			if (st != null) {
				st.eatFinal++;
			}
			if (st != null && st.map) {
				Log.d(TAG, prefix() + "CSI_EAT final=" + fmtByte(ub)
						+ " seq=" + esc(seq, 80));
			}
		} else {
			csiKeepFinal++;
			if (st != null) {
				st.keepFinal++;
			}
			Log.d(TAG, prefix() + "CSI_KEEP final=" + fmtByte(ub)
					+ " seq=" + esc(seq, 80));
		}
	}

	public static void csiAbort(final int ub, final byte[] seq) {
		csiAbort++;
		State st = TL.get();
		if (st != null) {
			st.abort++;
		}
		Log.d(TAG, prefix() + "CSI_ABORT byte=" + fmtByte(ub)
				+ " seq=" + esc(seq, 80));
	}

	public static void twoByteEsc(final int intro) {
		twoByte++;
		State st = TL.get();
		if (st != null) {
			st.twoByte++;
		}
		int v = intro & 0xFF;
		boolean suspect = v == ' ' || v == '(' || v == ')' || v == '|'
				|| v == 0x0A || v == 0x0D || v == '[' || v == ']' || v == ':'
				|| v == 'o' || v == 'O' || v == '\\' || v == '`';
		if (suspect || (st != null && st.map)) {
			Log.d(TAG, prefix() + "ESC2 intro=" + fmtByte(intro));
		}
	}

	public static void oscAbort(final String why, final int ub) {
		oscAbort++;
		Log.d(TAG, prefix() + "OSC_ABORT why=" + why + " byte=" + fmtByte(ub));
	}

	public static void onAddLine(final TextTree.Line line) {
		State st = TL.get();
		if (st == null || line == null) {
			return;
		}
		String text = TextTree.deColorLine(line).toString();
		boolean closed = false;
		LinkedList<TextTree.Unit> data = line.getData();
		if (data != null && !data.isEmpty()
				&& data.getLast() instanceof TextTree.NewLine) {
			closed = true;
			st.closed++;
			closedLines++;
		}
		if (text.indexOf("( )") >= 0) {
			st.playerOut++;
		}
		if (st.map || looksLikeMapText(text)) {
			Log.d(TAG, prefix() + "LINE " + (closed ? "C" : "O")
					+ " len=" + text.length()
					+ " brk=" + line.getBreaks()
					+ " text=" + clip(text.replace('\n', ' '), 160));
		}
	}

	public static void onDispatch(final byte[] raw, final String stripped) {
		if (!looksLikeMap(raw) && (stripped == null || !looksLikeMapText(stripped))) {
			heartbeat(false);
			return;
		}
		int rawNl = countByte(raw, 0x0A);
		int rawCr = countByte(raw, 0x0D);
		int stripNl = stripped == null ? -1 : countChar(stripped, '\n');
		int stripCr = stripped == null ? -1 : countChar(stripped, '\r');
		int stripTok = stripped == null ? -1 : countOcc(stripped, "( )");
		Log.d(TAG, prefix() + "DISPATCH rawNl=" + rawNl + " rawCr=" + rawCr
				+ " stripNl=" + stripNl + " stripCr=" + stripCr
				+ " stripTok=" + stripTok);
		logChunkRows("DISPATCH_RAW", raw);
		if (stripped != null) {
			logTextRows("DISPATCH_STRIP", stripped);
		}
	}

	public static void onTree(final String label, final TextTree tree) {
		if (tree == null) {
			return;
		}
		LinkedList<TextTree.Line> lines = tree.getLines();
		if (lines == null || lines.isEmpty()) {
			return;
		}
		boolean map = false;
		int tok = 0;
		int n = 0;
		for (TextTree.Line line : lines) {
			String text = TextTree.deColorLine(line).toString();
			if (looksLikeMapText(text)) {
				map = true;
			}
			if (text.indexOf("( )") >= 0) {
				tok++;
			}
			n++;
			if (looksLikeMapText(text) || text.indexOf("( )") >= 0) {
				Log.d(TAG, prefix() + "TREE_" + label + " i=" + n
						+ "/" + lines.size() + " len=" + text.length()
						+ " brk=" + line.getBreaks()
						+ " text=" + clip(text.replace('\n', ' '), 160));
			}
		}
		if (map) {
			Log.d(TAG, prefix() + "TREE " + label + " lines=" + lines.size()
					+ " tok=" + tok);
		}
	}

	public static void onLineGone(final String preview) {
		gags++;
		if (preview != null && looksLikeMapText(preview)) {
			Log.d(TAG, prefix() + "LINE_GONE " + clip(preview, 160));
		}
	}

	public static void onDump(final byte[] proc) {
		if (!looksLikeMap(proc)) {
			return;
		}
		Log.d(TAG, prefix() + "DUMP bytes=" + (proc == null ? 0 : proc.length)
				+ " nl=" + countByte(proc, 0x0A)
				+ " cr=" + countByte(proc, 0x0D)
				+ " tok=" + countToken(proc));
		logChunkRows("DUMP", proc);
	}

	public static void onMxp(final byte[] in, final byte[] out) {
		if (in == null) {
			return;
		}
		int outLen = out == null ? 0 : out.length;
		if (outLen == in.length && !looksLikeMap(in)) {
			return;
		}
		if (outLen < in.length) {
			mxpShrink++;
		}
		if (outLen != in.length && (looksLikeMap(in) || looksLikeMap(out))) {
			Log.d(TAG, prefix() + "MXP in=" + in.length + " out=" + outLen);
			logChunkRows("MXP_IN", in);
			logChunkRows("MXP_OUT", out);
		}
	}

	public static void onDraw(final String windowName, final TextTree buffer) {
		heartbeat(false);
		long now = SystemClock.uptimeMillis();
		if (now - lastDraw < DRAW_MS) {
			return;
		}
		lastDraw = now;
		if (buffer == null) {
			return;
		}
		try {
			LinkedList<TextTree.Line> lines = buffer.getLines();
			if (lines == null || lines.isEmpty()) {
				return;
			}
			int mapLines = 0;
			int tok = 0;
			int seen = 0;
			for (TextTree.Line line : lines) {
				if (seen >= 16) {
					break;
				}
				seen++;
				String text = TextTree.deColorLine(line).toString();
				if (!looksLikeMapText(text)) {
					continue;
				}
				mapLines++;
				if (text.indexOf("( )") >= 0) {
					tok++;
				}
				Log.d(TAG, prefix() + "DRAW_ROW win=" + windowName
						+ " i=" + mapLines + " len=" + text.length()
						+ " brk=" + line.getBreaks()
						+ " text=" + clip(text.replace('\n', ' '), 160));
			}
			if (mapLines > 0) {
				Log.d(TAG, prefix() + "DRAW win=" + windowName
						+ " mapLines=" + mapLines + " tok=" + tok);
			}
		} catch (ConcurrentModificationException e) {
			Log.d(TAG, prefix() + "DRAW_CME");
		}
	}

	private static void heartbeat(final boolean force) {
		long now = SystemClock.uptimeMillis();
		if (!force && now - lastHeartbeat < HEARTBEAT_MS) {
			return;
		}
		lastHeartbeat = now;
		Log.d(TAG, prefix() + "HB chunks=" + chunks + " map=" + mapChunks
				+ " csiM=" + csiM + " eatFinal=" + csiEatFinal
				+ " keepFinal=" + csiKeepFinal + " abort=" + csiAbort
				+ " twoByte=" + twoByte + " oscAbort=" + oscAbort
				+ " hold=" + holdovers + " closed=" + closedLines
				+ " gone=" + gags + " mxpShrink=" + mxpShrink
				+ " worstLost=" + worstLost);
	}

	private static String prefix() {
		return "pid=" + Process.myPid() + " t=" + SystemClock.uptimeMillis() + " ";
	}

	private static void logChunkRows(final String kind, final byte[] data) {
		if (data == null || data.length == 0) {
			return;
		}
		int row = 0;
		int start = 0;
		for (int i = 0; i <= data.length; i++) {
			if (i == data.length || (data[i] & 0xFF) == 0x0A) {
				int len = i - start;
				if (!(i == data.length && len == 0)) {
					row++;
					byte[] slice = new byte[len];
					if (len > 0) {
						System.arraycopy(data, start, slice, 0, len);
					}
					Log.d(TAG, prefix() + kind + "_ROW row=" + row
							+ " closed=" + (i < data.length)
							+ " len=" + len
							+ " data=" + esc(slice, 200));
				}
				start = i + 1;
			}
		}
	}

	private static void logTextRows(final String kind, final String text) {
		if (text == null) {
			return;
		}
		int row = 0;
		int start = 0;
		for (int i = 0; i <= text.length(); i++) {
			if (i == text.length() || text.charAt(i) == '\n') {
				if (!(i == text.length() && start == text.length())) {
					row++;
					String slice = text.substring(start, i);
					Log.d(TAG, prefix() + kind + "_ROW row=" + row
							+ " closed=" + (i < text.length())
							+ " len=" + slice.length()
							+ " text=" + clip(slice.replace('\r', ' '), 160));
				}
				start = i + 1;
			}
		}
	}

	private static String fmtByte(final int ub) {
		int v = ub & 0xFF;
		if (v >= 32 && v < 127) {
			return "'" + (char) v + "'/" + v;
		}
		return String.format("0x%02X/%d", v, v);
	}

	public static String esc(final byte[] d, final int max) {
		if (d == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		int n = d.length;
		for (int i = 0; i < n; i++) {
			if (sb.length() >= max) {
				sb.append("…").append(n - i).append("more");
				break;
			}
			int b = d[i] & 0xFF;
			if (b == 0x1B) {
				sb.append("\\e");
			} else if (b == 0x0A) {
				sb.append("\\n");
			} else if (b == 0x0D) {
				sb.append("\\r");
			} else if (b == 0x09) {
				sb.append("\\t");
			} else if (b < 32 || b >= 127) {
				sb.append(String.format("\\x%02X", b));
			} else {
				sb.append((char) b);
			}
		}
		return sb.toString();
	}

	private static String clip(final String s, final int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, max) + "…";
	}

	private static int countByte(final byte[] d, final int v) {
		if (d == null) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < d.length; i++) {
			if ((d[i] & 0xFF) == v) {
				n++;
			}
		}
		return n;
	}

	private static int countChar(final String s, final char c) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == c) {
				n++;
			}
		}
		return n;
	}

	private static int countOcc(final String s, final String needle) {
		int n = 0;
		int from = 0;
		while (from < s.length()) {
			int i = s.indexOf(needle, from);
			if (i < 0) {
				break;
			}
			n++;
			from = i + needle.length();
		}
		return n;
	}

	private static int countToken(final byte[] d) {
		if (d == null) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i + 2 < d.length; i++) {
			if ((d[i] & 0xFF) == '(' && d[i + 1] == ' '
					&& (d[i + 2] & 0xFF) == ')') {
				n++;
			}
		}
		return n;
	}
}
