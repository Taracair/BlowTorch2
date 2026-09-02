package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;

/**
 * Other enabled line-triggers that {@code find()} a sample the player typed.
 *
 * <p>Not regex intersection: one string, same {@code MULTILINE} compile as
 * {@link TriggerCascade}. Fresh matchers — never {@link TriggerData#getMatcher()}
 * on the UI thread.
 */
public final class TriggerSampleHits {

	/** Prefixes {@link com.resurrection.blowtorch2.lib.service.Connection} skips. */
	static final String GMCP_PREFIX = "%";
	static final String MCP_PREFIX = "@";

	/** Same value as {@code PluginFilterSelectionDialog.MAIN_SETTINGS}. */
	public static final String MAIN_PLUGIN = "bt_main_settings";

	public static final int DEFAULT_CAP = 8;

	private TriggerSampleHits() {
	}

	public static final class Candidate {
		public final String plugin;
		public final String name;
		final int sequence;
		final Pattern multiline;

		Candidate(final String plugin, final String name, final int sequence,
				final Pattern multiline) {
			this.plugin = plugin;
			this.name = name;
			this.sequence = sequence;
			this.multiline = multiline;
		}

		public String label() {
			if (isMainPlugin(plugin)) {
				return name;
			}
			return plugin + ": " + name;
		}

		/**
		 * @return Null when this trigger is not a line match, or its pattern
		 *     will not compile {@code MULTILINE}.
		 */
		public static Candidate tryCreate(final String plugin, final TriggerData t) {
			if (!isLineTrigger(t) || t.getCompiledPattern() == null) {
				return null;
			}
			try {
				Pattern dispatch = Pattern.compile(
						t.getCompiledPattern().pattern(), Pattern.MULTILINE);
				return new Candidate(plugin, t.getName(), t.getSequence(), dispatch);
			} catch (PatternSyntaxException e) {
				return null;
			}
		}
	}

	/**
	 * Same skip rules as {@code Connection.isMatchableTrigger}: disabled,
	 * empty, GMCP {@code %}, MCP {@code @}, device gestures.
	 */
	public static boolean isLineTrigger(final TriggerData t) {
		if (t == null || !t.isEnabled() || t.getPattern() == null
				|| t.getPattern().length() == 0 || t.getName() == null
				|| t.getName().length() == 0) {
			return false;
		}
		if (!t.isInterpretAsRegex() && (t.getPattern().startsWith(GMCP_PREFIX)
				|| t.getPattern().startsWith(MCP_PREFIX))) {
			return false;
		}
		if (GestureCatalog.isGesturePattern(t.getPattern(), !t.isInterpretAsRegex())) {
			return false;
		}
		return true;
	}

	/**
	 * Labels of candidates that match {@code sample}, excluding names in
	 * {@code skipNames} on {@code skipPlugin} (the row being edited, and the
	 * name currently typed so a rename does not list the old row).
	 *
	 * <p>Empty sample: empty list, no walk. Walk order is sequence then name,
	 * same as {@link TriggerOrder}.
	 */
	public static List<String> matchingLabels(final String sample,
			final List<Candidate> candidates, final String skipPlugin,
			final Collection<String> skipNames, final int maxNames) {
		ArrayList<String> hits = new ArrayList<String>();
		if (sample == null || sample.length() == 0 || candidates == null) {
			return hits;
		}
		int cap = maxNames < 1 ? DEFAULT_CAP : maxNames;
		ArrayList<Candidate> ordered = new ArrayList<Candidate>(candidates);
		Collections.sort(ordered, CANDIDATE_ORDER);
		int extra = 0;
		for (Candidate c : ordered) {
			if (c == null) {
				continue;
			}
			if (sameTrigger(c, skipPlugin, skipNames)) {
				continue;
			}
			if (!c.multiline.matcher(sample).find()) {
				continue;
			}
			if (hits.size() < cap) {
				hits.add(c.label());
			} else {
				extra++;
			}
		}
		if (extra > 0) {
			hits.add("and " + extra + " more");
		}
		return hits;
	}

	private static final Comparator<Candidate> CANDIDATE_ORDER =
			new Comparator<Candidate>() {
		@Override
		public int compare(final Candidate a, final Candidate b) {
			if (a == b) {
				return 0;
			}
			if (a == null) {
				return 1;
			}
			if (b == null) {
				return -1;
			}
			if (a.sequence != b.sequence) {
				return a.sequence < b.sequence ? -1 : 1;
			}
			int n = nameOf(a).compareToIgnoreCase(nameOf(b));
			if (n != 0) {
				return n;
			}
			return pluginOf(a).compareToIgnoreCase(pluginOf(b));
		}
	};

	private static String nameOf(final Candidate c) {
		return c.name == null ? "" : c.name;
	}

	private static String pluginOf(final Candidate c) {
		return c.plugin == null ? "" : c.plugin;
	}

	public static String formatHits(final List<String> labels) {
		if (labels == null || labels.isEmpty()) {
			return "No other enabled triggers match this line.";
		}
		StringBuilder out = new StringBuilder("Also matches: ");
		for (int i = 0; i < labels.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(labels.get(i));
		}
		return out.toString();
	}

	static boolean isMainPlugin(final String plugin) {
		return plugin == null || plugin.length() == 0
				|| "Main".equals(plugin)
				|| MAIN_PLUGIN.equals(plugin);
	}

	private static boolean sameTrigger(final Candidate c, final String skipPlugin,
			final Collection<String> skipNames) {
		if (skipNames == null || skipNames.isEmpty()) {
			return false;
		}
		boolean nameHit = false;
		for (String skipName : skipNames) {
			if (skipName != null && skipName.length() > 0 && skipName.equals(c.name)) {
				nameHit = true;
				break;
			}
		}
		if (!nameHit) {
			return false;
		}
		String a = c.plugin == null ? "" : c.plugin;
		String b = skipPlugin == null ? "" : skipPlugin;
		if (isMainPlugin(a) && isMainPlugin(b)) {
			return true;
		}
		return a.equals(b);
	}
}
