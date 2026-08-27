package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Color;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.window.TextTree.NewLine;
import com.resurrection.blowtorch2.lib.window.TextTree.Unit;

/**
 * {@code .probe bleed} — what a colour trigger actually restored, on which
 * thread, and what the finished dump still held.
 *
 * <p>Off unless asked. One null check on the ordinary path. This is a
 * player-run measurement, the same shape as {@code .probe lines}. The
 * reading lives in memory (last {@link #RING} events) and in logcat tag
 * {@code BlowTorchBleed} so it can leave the phone without a screenshot.
 *
 * <p>It does not explain a leak. It records the facts that distinguish
 * "restore ops were already magenta", "the colour was left open across a
 * packet", and "the service dump was clean so the purple appeared later".
 */
public final class ColourBleedProbe {

	public static final String LOG_TAG = "BlowTorchBleed";
	static final int RING = 120;

	private static final ThreadLocal<ColourBleedProbe> BOUND =
			new ThreadLocal<ColourBleedProbe>();

	private final ArrayDeque<String> events = new ArrayDeque<String>();
	private int dropped;
	private int colorEvents;
	private int closeEvents;
	private int suspectEvents;
	private boolean colorThisDispatch;
	private boolean on = true;

	public static void bind(final ColourBleedProbe probe) {
		BOUND.set(probe);
		if (probe != null) {
			probe.colorThisDispatch = false;
		}
	}

	public static void unbind() {
		BOUND.remove();
	}

	public static ColourBleedProbe bound() {
		return BOUND.get();
	}

	public void setOn(final boolean on) {
		this.on = on;
	}

	public boolean isOn() {
		return on;
	}

	public void reset() {
		events.clear();
		dropped = 0;
		colorEvents = 0;
		closeEvents = 0;
		suspectEvents = 0;
		colorThisDispatch = false;
	}

	public void recordColor(final String triggerName, final String matched,
			final int paintFg, final int paintBg, final Color bleed,
			final Color restore, final Line line) {
		if (!on) {
			return;
		}
		colorThisDispatch = true;
		colorEvents++;
		Thread t = Thread.currentThread();
		boolean open = line != null && line.isTriggerColorOpen();
		boolean finished = lineFinished(line);
		String bleedOps = ops(bleed);
		String restoreOps = ops(restore);
		boolean magenta = looksMagenta(bleed) || looksMagenta(restore)
				|| lineLooksMagenta(line);
		StringBuilder b = new StringBuilder(256);
		b.append("COLOR name=").append(nz(triggerName));
		b.append(" match=").append(clip(matched, 80));
		b.append(" paintFg=").append(paintFg);
		b.append(" paintBg=").append(paintBg);
		b.append(" thread=").append(t.getName());
		b.append(" tid=").append(t.getId());
		b.append("\n  bleed=[").append(bleedOps).append(']');
		b.append(" restore=[").append(restoreOps).append(']');
		b.append(" openAfter=").append(open);
		b.append(" lineFinished=").append(finished);
		if (line != null) {
			b.append("\n  start=[").append(opsList(line.getServerColorAtStart()))
					.append("] end=[")
					.append(opsList(line.getServerColorAtEnd())).append(']');
			b.append("\n  line=").append(clip(plain(line), 160));
			b.append("\n  units=").append(units(line, 24));
		}
		if (magenta) {
			b.append("\n  SUSPECT_MAGENTA");
			suspectEvents++;
		}
		add(b.toString());
	}

	public void recordReplace(final String triggerName, final String matched,
			final String with, final Line line) {
		if (!on) {
			return;
		}
		colorThisDispatch = true;
		Thread t = Thread.currentThread();
		StringBuilder b = new StringBuilder(192);
		b.append("REPLACE name=").append(nz(triggerName));
		b.append(" match=").append(clip(matched, 80));
		b.append(" with=").append(clip(with, 80));
		b.append(" thread=").append(t.getName());
		b.append(" tid=").append(t.getId());
		if (line != null) {
			b.append("\n  line=").append(clip(plain(line), 160));
			b.append("\n  units=").append(units(line, 24));
			if (lineLooksMagenta(line)) {
				b.append("\n  SUSPECT_MAGENTA");
				suspectEvents++;
			}
		}
		add(b.toString());
	}

	public void recordClose(final Line line, final List<Integer> restoreOps,
			final boolean closed, final boolean wasOpen) {
		if (!on) {
			return;
		}
		closeEvents++;
		Thread t = Thread.currentThread();
		boolean magenta = looksMagentaOps(restoreOps) || lineLooksMagenta(line);
		StringBuilder b = new StringBuilder(192);
		b.append("CLOSE thread=").append(t.getName());
		b.append(" tid=").append(t.getId());
		b.append(" wasOpen=").append(wasOpen);
		b.append(" closed=").append(closed);
		b.append(" restore=[").append(opsList(restoreOps)).append(']');
		if (line != null) {
			b.append("\n  line=").append(clip(plain(line), 160));
			b.append("\n  units=").append(units(line, 24));
		}
		if (magenta) {
			b.append("\n  SUSPECT_MAGENTA");
			suspectEvents++;
		}
		add(b.toString());
	}

	/**
	 * After {@code closeAtLineEnds}: what is about to be dumped into the window
	 * buffer. Always dumps the last few lines of a dispatch that had a colour
	 * trigger, and any line that still looks magenta or still has an open
	 * trigger colour.
	 */
	public void recordDispatchDump(final String display, final TextTree finished) {
		if (!on || finished == null) {
			return;
		}
		boolean anyOpen = false;
		boolean anyMagenta = false;
		List<Line> lines = finished.getLines();
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			if (line.isTriggerColorOpen()) {
				anyOpen = true;
			}
			if (lineLooksMagenta(line)) {
				anyMagenta = true;
			}
		}
		if (!colorThisDispatch && !anyOpen && !anyMagenta) {
			return;
		}
		Thread t = Thread.currentThread();
		StringBuilder b = new StringBuilder(512);
		b.append("DUMP display=").append(nz(display));
		b.append(" thread=").append(t.getName());
		b.append(" tid=").append(t.getId());
		b.append(" lines=").append(lines.size());
		b.append(" colorThisDispatch=").append(colorThisDispatch);
		b.append(" stillOpen=").append(anyOpen);
		if (anyMagenta) {
			b.append(" SUSPECT_MAGENTA");
			suspectEvents++;
		}
		int from = Math.max(0, lines.size() - 8);
		for (int i = from; i < lines.size(); i++) {
			Line line = lines.get(i);
			boolean flag = line.isTriggerColorOpen() || lineLooksMagenta(line);
			b.append("\n  [").append(i).append(']');
			if (flag) {
				b.append(" FLAG");
			}
			b.append(' ').append(clip(plain(line), 120));
			b.append("\n    ").append(units(line, 32));
		}
		add(b.toString());
		colorThisDispatch = false;
	}

	public String report() {
		StringBuilder b = new StringBuilder();
		b.append("\nColour-bleed probe");
		if (!on) {
			b.append(" (currently off — .probe bleed on to resume)");
		}
		b.append("\nEvents kept: ").append(events.size());
		b.append("  dropped: ").append(dropped);
		b.append("  color: ").append(colorEvents);
		b.append("  close: ").append(closeEvents);
		b.append("  suspect-magenta: ").append(suspectEvents);
		b.append("\nTag: ").append(LOG_TAG);
		b.append("  (logcat -s ").append(LOG_TAG).append(")\n\n");
		if (events.isEmpty()) {
			b.append("(no events yet — turn on, play until the leak, then .probe bleed report)\n");
			return b.toString();
		}
		for (String e : events) {
			b.append(e).append("\n---\n");
		}
		return b.toString();
	}

	private void add(final String event) {
		String stamped = System.currentTimeMillis() + " " + event;
		if (events.size() >= RING) {
			events.removeFirst();
			dropped++;
		}
		events.addLast(stamped);
		android.util.Log.i(LOG_TAG, event.replace('\n', '|'));
	}

	static String ops(final Color c) {
		if (c == null) {
			return "null";
		}
		return opsList(c.getOperations());
	}

	static String opsList(final List<Integer> ops) {
		if (ops == null) {
			return "null";
		}
		if (ops.isEmpty()) {
			return "empty";
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < ops.size(); i++) {
			if (i > 0) {
				b.append(';');
			}
			b.append(ops.get(i));
		}
		return b.toString();
	}

	static boolean looksMagenta(final Color c) {
		return c != null && looksMagentaOps(c.getOperations());
	}

	static boolean looksMagentaOps(final List<Integer> ops) {
		if (ops == null) {
			return false;
		}
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 35 || op == 95 || op == 45 || op == 105) {
				return true;
			}
			if (op == 38 && i + 2 < ops.size() && ops.get(i + 1).intValue() == 5) {
				if (magentaXterm(ops.get(i + 2).intValue())) {
					return true;
				}
				i += 2;
			}
		}
		return false;
	}

	/** xterm indices that read as purple/magenta on a black field. */
	static boolean magentaXterm(final int n) {
		if (n == 5 || n == 13) {
			return true;
		}
		if (n >= 90 && n <= 93) {
			return true;
		}
		if (n >= 127 && n <= 129) {
			return true;
		}
		if (n >= 163 && n <= 165) {
			return true;
		}
		if (n >= 199 && n <= 201) {
			return true;
		}
		return n == 207 || n == 213 || n == 219;
	}

	private static boolean lineLooksMagenta(final Line line) {
		if (line == null) {
			return false;
		}
		for (Unit u : line.getData()) {
			if (u instanceof Color && looksMagenta((Color) u)) {
				return true;
			}
		}
		return false;
	}

	private static boolean lineFinished(final Line line) {
		if (line == null || line.getData().isEmpty()) {
			return false;
		}
		return line.getData().getLast() instanceof NewLine;
	}

	private static String plain(final Line line) {
		return TextTree.deColorLine(line).toString().replace('\n', '↵');
	}

	private static String units(final Line line, final int max) {
		StringBuilder b = new StringBuilder();
		int n = 0;
		for (Iterator<Unit> it = line.getData().iterator(); it.hasNext();) {
			Unit u = it.next();
			if (n > 0) {
				b.append(' ');
			}
			if (n >= max) {
				b.append("…");
				break;
			}
			if (u instanceof Color) {
				b.append('{').append(ops((Color) u)).append('}');
			} else if (u instanceof NewLine) {
				b.append("NL");
			} else if (u instanceof TextTree.Text) {
				String s = ((TextTree.Text) u).getString();
				b.append('"').append(clip(s, 24)).append('"');
			} else {
				b.append(u.getClass().getSimpleName());
			}
			n++;
		}
		return b.toString();
	}

	private static String clip(final String s, final int max) {
		if (s == null) {
			return "null";
		}
		String one = s.replace('\n', '↵');
		if (one.length() <= max) {
			return one;
		}
		return one.substring(0, max) + "…";
	}

	private static String nz(final String s) {
		return s == null ? "?" : s;
	}
}
