package com.resurrection.blowtorch2.lib.trigger.style;

import java.util.ArrayList;
import java.util.List;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;

/**
 * Human clipboard text for a grabber copy. Not XML — the editor path writes the
 * spec onto a new trigger.
 */
public final class StyleClipboard {

	private StyleClipboard() {
	}

	public static String describe(final StyleSnapshot snap, final String glyphText,
			final boolean looksMode) {
		if (snap == null) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		b.append("BlowTorch style");
		b.append(looksMode ? " (looks the same)\n" : " (exact recipe)\n");
		if (glyphText != null && glyphText.length() > 0) {
			b.append("text: ").append(glyphText).append('\n');
		}
		b.append("fg: ").append(snap.fgLabel()).append('\n');
		b.append("bg: ").append(snap.bgLabel()).append('\n');
		if (snap.bright) {
			b.append("bright (SGR 1)\n");
		}
		if (snap.weight()) {
			b.append("bold\n");
		}
		if (snap.italic()) {
			b.append("italic\n");
		}
		if (snap.doubleUnderline()) {
			b.append("double underline\n");
		} else if (snap.underline()) {
			b.append("underline\n");
		}
		if (snap.strike()) {
			b.append("strike\n");
		}
		if (snap.reverse()) {
			b.append("reverse\n");
		}
		if (snap.faint()) {
			b.append("faint\n");
		}
		if (snap.blink()) {
			b.append("blink\n");
		}
		if (snap.hasHref()) {
			b.append("link: ").append(snap.href).append('\n');
		}
		return b.toString();
	}

	/** Clipboard text for the layers the player ticked in the grabber. */
	public static String describeChecked(final StyleSnapshot snap, final String glyphText,
			final boolean[] checked, final boolean looksMode) {
		List<LayerRow> rows = layers(snap, glyphText);
		StringBuilder b = new StringBuilder();
		b.append("BlowTorch style");
		b.append(looksMode ? " (looks the same)\n" : " (exact recipe)\n");
		for (int i = 0; i < rows.size(); i++) {
			if (checked != null && i < checked.length && checked[i]) {
				b.append(rows.get(i).label).append('\n');
			}
		}
		return b.toString();
	}

	public static List<LayerRow> layers(final StyleSnapshot snap, final String glyphText) {
		ArrayList<LayerRow> rows = new ArrayList<LayerRow>();
		if (snap == null) {
			return rows;
		}
		rows.add(new LayerRow("fg", "Foreground: " + snap.fgLabel(), true));
		if (hasPaintedBackground(snap)) {
			rows.add(new LayerRow("bg", "Background: " + snap.bgLabel(), false));
		}
		if (snap.bright) {
			rows.add(new LayerRow("bright", "Bright (SGR 1): on", true));
		}
		if (snap.weight()) {
			rows.add(new LayerRow("weight", "Bold: on", true));
		}
		if (snap.italic()) {
			rows.add(new LayerRow("italic", "Italic: on", true));
		}
		if (snap.underline() || snap.doubleUnderline()) {
			String ul = snap.doubleUnderline() ? "double" : "on";
			rows.add(new LayerRow("underline", "Underline: " + ul, true));
		}
		if (snap.strike()) {
			rows.add(new LayerRow("strike", "Strike: on", true));
		}
		if (snap.reverse()) {
			rows.add(new LayerRow("reverse", "Reverse: on", true));
		}
		if (snap.faint()) {
			rows.add(new LayerRow("faint", "Faint: on", true));
		}
		if (snap.blink()) {
			rows.add(new LayerRow("blink", "Blink: on", true));
		}
		if (snap.hasHref()) {
			rows.add(new LayerRow("href", "Link: " + snap.href, true));
		}
		if (glyphText != null && glyphText.length() > 0) {
			rows.add(new LayerRow("text", "Text: " + glyphText, false));
		}
		return rows;
	}

	/** ANSI 40 is the unset paper, not a colour the MUD painted. */
	static boolean hasPaintedBackground(final StyleSnapshot snap) {
		if (snap == null) {
			return false;
		}
		if (snap.bgSpace != StyleSnapshot.ColorSpace.ANSI16) {
			return true;
		}
		return snap.bgCode != 40;
	}

	/**
	 * Build a spec from grabber checkboxes. Checked rows become REQUIRE with the
	 * snapshot's values. Unchecked stay IGNORE.
	 */
	public static StyleMatchSpec specFromChecks(final StyleSnapshot snap,
			final String glyphText, final boolean[] checked, final boolean looksMode,
			final boolean extrasForbid, final boolean combineAny) {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setColorMode(looksMode ? StyleMatchSpec.ColorMode.LOOKS
				: StyleMatchSpec.ColorMode.EXACT);
		spec.setExtras(extrasForbid ? StyleMatchSpec.Extras.FORBID
				: StyleMatchSpec.Extras.ALLOW);
		spec.setCombine(combineAny ? StyleMatchSpec.Combine.ANY
				: StyleMatchSpec.Combine.ALL);
		if (snap == null || checked == null) {
			return spec;
		}
		List<LayerRow> rows = layers(snap, glyphText);
		for (int i = 0; i < rows.size() && i < checked.length; i++) {
			if (!checked[i]) {
				continue;
			}
			applyRequire(spec, rows.get(i).id, snap, glyphText);
		}
		return spec;
	}

	static void applyRequire(final StyleMatchSpec spec, final String id,
			final StyleSnapshot snap, final String glyphText) {
		if ("fg".equals(id)) {
			spec.setFg(Gate.REQUIRE, snap.fgSpace, snap.fgCode);
		} else if ("bg".equals(id)) {
			spec.setBg(Gate.REQUIRE, snap.bgSpace, snap.bgCode);
		} else if ("bright".equals(id)) {
			spec.setBright(snap.bright ? Gate.REQUIRE : Gate.FORBID);
		} else if ("weight".equals(id)) {
			spec.setWeight(snap.weight() ? Gate.REQUIRE : Gate.FORBID);
		} else if ("italic".equals(id)) {
			spec.setItalic(snap.italic() ? Gate.REQUIRE : Gate.FORBID);
		} else if ("underline".equals(id)) {
			if (snap.doubleUnderline()) {
				spec.setDoubleUnderline(Gate.REQUIRE);
				spec.setUnderline(Gate.REQUIRE);
			} else if (snap.underline()) {
				spec.setUnderline(Gate.REQUIRE);
			} else {
				spec.setUnderline(Gate.FORBID);
			}
		} else if ("strike".equals(id)) {
			spec.setStrike(snap.strike() ? Gate.REQUIRE : Gate.FORBID);
		} else if ("reverse".equals(id)) {
			spec.setReverse(snap.reverse() ? Gate.REQUIRE : Gate.FORBID);
		} else if ("faint".equals(id)) {
			spec.setFaint(snap.faint() ? Gate.REQUIRE : Gate.FORBID);
		} else if ("blink".equals(id)) {
			spec.setBlink(snap.blink() ? Gate.REQUIRE : Gate.FORBID);
		} else if ("href".equals(id)) {
			spec.setHref(snap.hasHref() ? Gate.REQUIRE : Gate.FORBID, snap.href);
		} else if ("text".equals(id)) {
			spec.setText(glyphText == null ? "" : glyphText);
			spec.setTextRegex(false);
		}
	}

	public static final class LayerRow {
		public final String id;
		public final String label;
		public final boolean defaultOn;

		LayerRow(final String id, final String label, final boolean defaultOn) {
			this.id = id;
			this.label = label;
			this.defaultOn = defaultOn;
		}
	}
}
