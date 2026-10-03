package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Recorded shakes kept on this phone. A name is matched when the live stroke
 * is closer to one of its recordings than to any other name, and close enough
 * for that name's slider. No Android types.
 */
public final class CustomShakeLibrary {

	public static final int POINTS = 16;
	public static final int MIN_TRACES = 3;
	public static final int MAX_TRACES = 12;
	/** Decode treats a longer document as broken, so a 41st name must not be stored. */
	public static final int MAX_NAMES = 40;
	public static final int DEFAULT_TOLERANCE = 40;

	private static final Set<String> EXTRA_RESERVED = new HashSet<String>();

	static {
		String[] words = {
			"all", "caps", "sensors", "help", "examples", "why", "threshold",
			"calibrate", "fire", "test", "list", "state", "watch", "on", "off",
			"master"
		};
		for (int i = 0; i < words.length; i++) {
			EXTRA_RESERVED.add(words[i]);
		}
	}

	/** One named shake: the slider and the strokes the player drew. */
	public static final class Entry {
		private final String name;
		private final int tolerance;
		private final List<ShakeTrace> traces;

		public Entry(final String name, final int tolerance, final List<ShakeTrace> traces) {
			this.name = name;
			this.tolerance = clampTolerance(tolerance);
			List<ShakeTrace> copy = new ArrayList<ShakeTrace>();
			if (traces != null) {
				for (int i = 0; i < traces.size() && copy.size() < MAX_TRACES; i++) {
					ShakeTrace trace = traces.get(i);
					if (trace != null && trace.size() >= 2) {
						copy.add(trace.resampled(POINTS));
					}
				}
			}
			this.traces = Collections.unmodifiableList(copy);
		}

		public String getName() {
			return name;
		}

		public int getTolerance() {
			return tolerance;
		}

		public List<ShakeTrace> getTraces() {
			return traces;
		}

		public boolean canMatch() {
			return traces.size() >= MIN_TRACES;
		}
	}

	private final List<Entry> entries;

	private CustomShakeLibrary(final List<Entry> entries) {
		this.entries = Collections.unmodifiableList(entries);
	}

	public static CustomShakeLibrary empty() {
		return new CustomShakeLibrary(new ArrayList<Entry>());
	}

	public List<Entry> entries() {
		return entries;
	}

	public Entry byName(final String name) {
		String id = normalizeName(name);
		if (id == null) {
			return null;
		}
		for (int i = 0; i < entries.size(); i++) {
			if (id.equals(entries.get(i).getName())) {
				return entries.get(i);
			}
		}
		return null;
	}

	public CustomShakeLibrary with(final Entry entry) {
		if (entry == null || entry.getName() == null) {
			return this;
		}
		List<Entry> next = new ArrayList<Entry>();
		boolean replaced = false;
		for (int i = 0; i < entries.size(); i++) {
			Entry have = entries.get(i);
			if (entry.getName().equals(have.getName())) {
				next.add(entry);
				replaced = true;
			} else {
				next.add(have);
			}
		}
		if (!replaced) {
			if (entries.size() >= MAX_NAMES) {
				return this;
			}
			next.add(entry);
		}
		return new CustomShakeLibrary(next);
	}

	public CustomShakeLibrary without(final String name) {
		String id = normalizeName(name);
		if (id == null) {
			return this;
		}
		List<Entry> next = new ArrayList<Entry>();
		for (int i = 0; i < entries.size(); i++) {
			if (!id.equals(entries.get(i).getName())) {
				next.add(entries.get(i));
			}
		}
		return new CustomShakeLibrary(next);
	}

	/**
	 * The recorded name closest to {@code live}, or null when nothing is close
	 * enough. Distance is to the nearest of that name's strokes, so extra
	 * recordings cover the player's variation and the slider still rejects a
	 * different shape.
	 */
	public String match(final ShakeTrace live) {
		if (live == null || live.size() < 2) {
			return null;
		}
		ShakeTrace probe = live.resampled(POINTS).normalized();
		String bestName = null;
		float best = Float.MAX_VALUE;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (!entry.canMatch()) {
				continue;
			}
			float nearest = Float.MAX_VALUE;
			List<ShakeTrace> traces = entry.getTraces();
			for (int t = 0; t < traces.size(); t++) {
				float d = probe.distanceTo(traces.get(t).normalized());
				if (d < nearest) {
					nearest = d;
				}
			}
			if (nearest <= maxDistance(entry.getTolerance()) && nearest < best) {
				best = nearest;
				bestName = entry.getName();
			}
		}
		return bestName;
	}

	/** How far a live stroke may sit from the nearest recording, after normalisation. */
	public static float maxDistance(final int tolerance) {
		int t = clampTolerance(tolerance);
		return 0.12f + ((t / 100f) * 0.70f);
	}

	public static int clampTolerance(final int tolerance) {
		if (tolerance < 0) {
			return 0;
		}
		if (tolerance > 100) {
			return 100;
		}
		return tolerance;
	}

	/**
	 * Lowercase name, or null. Two to sixteen letters and digits, starting with
	 * a letter, so it can be typed after {@code .sensor}.
	 */
	public static String normalizeName(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim().toLowerCase(Locale.US);
		if (s.length() < 2 || s.length() > 16) {
			return null;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean digit = c >= '0' && c <= '9';
			boolean letter = c >= 'a' && c <= 'z';
			if (i == 0 && !letter) {
				return null;
			}
			if (!letter && !digit) {
				return null;
			}
		}
		return s;
	}

	/** Built-in readings and {@code .sensor} subcommands, which a custom name must not reuse. */
	public static boolean isReserved(final String normalizedName) {
		if (normalizedName == null) {
			return true;
		}
		if (EXTRA_RESERVED.contains(normalizedName)) {
			return true;
		}
		for (GestureCatalog.Gesture g : GestureCatalog.all()) {
			if (normalizedName.equals(g.getId())) {
				return true;
			}
		}
		return false;
	}

	public String encode() {
		StringBuilder out = new StringBuilder();
		out.append("1\n");
		out.append(entries.size()).append('\n');
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			out.append(entry.getName()).append(' ').append(entry.getTolerance()).append('\n');
			List<ShakeTrace> traces = entry.getTraces();
			out.append(traces.size()).append('\n');
			for (int t = 0; t < traces.size(); t++) {
				ShakeTrace trace = traces.get(t);
				out.append(trace.size()).append('\n');
				for (int p = 0; p < trace.size(); p++) {
					out.append(String.format(Locale.US, "%.4f %.4f %.4f%n",
							trace.x(p), trace.y(p), trace.z(p)));
				}
			}
		}
		return out.toString();
	}

	/** A broken document comes back empty rather than throwing on the sensor thread. */
	public static CustomShakeLibrary decode(final String text) {
		if (text == null || text.length() == 0) {
			return empty();
		}
		try {
			String[] lines = text.split("\n");
			int at = 0;
			if (at >= lines.length || !"1".equals(lines[at].trim())) {
				return empty();
			}
			at++;
			int count = Integer.parseInt(lines[at].trim());
			at++;
			if (count < 0 || count > MAX_NAMES) {
				return empty();
			}
			List<Entry> parsed = new ArrayList<Entry>();
			for (int i = 0; i < count; i++) {
				String[] head = lines[at].trim().split(" ");
				at++;
				String name = normalizeName(head[0]);
				int tolerance = Integer.parseInt(head[1]);
				if (name == null || isReserved(name)) {
					return empty();
				}
				int ntraces = Integer.parseInt(lines[at].trim());
				at++;
				if (ntraces < 0 || ntraces > MAX_TRACES) {
					return empty();
				}
				List<ShakeTrace> traces = new ArrayList<ShakeTrace>();
				for (int t = 0; t < ntraces; t++) {
					int npoints = Integer.parseInt(lines[at].trim());
					at++;
					if (npoints < 2 || npoints > POINTS) {
						return empty();
					}
					float[] xs = new float[npoints];
					float[] ys = new float[npoints];
					float[] zs = new float[npoints];
					for (int p = 0; p < npoints; p++) {
						String[] xyz = lines[at].trim().split(" ");
						at++;
						xs[p] = Float.parseFloat(xyz[0]);
						ys[p] = Float.parseFloat(xyz[1]);
						zs[p] = Float.parseFloat(xyz[2]);
					}
					traces.add(new ShakeTrace(xs, ys, zs));
				}
				parsed.add(new Entry(name, tolerance, traces));
			}
			return new CustomShakeLibrary(parsed);
		} catch (RuntimeException bad) {
			return empty();
		}
	}
}
