package com.resurrection.blowtorch2.lib.trigger.style;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.ColorMode;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Combine;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Extras;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;

/**
 * Test one snapshot (or a span of them) against a {@link StyleMatchSpec}.
 */
public final class StyleMatcher {

	private StyleMatcher() {
	}

	public static boolean matches(final StyleSnapshot snap, final StyleMatchSpec spec) {
		return matches(snap, spec, null);
	}

	/**
	 * @param runText text of the run, or null to skip the optional text layer
	 */
	public static boolean matches(final StyleSnapshot snap, final StyleMatchSpec spec,
			final String runText) {
		if (spec == null || !spec.isActive()) {
			return true;
		}
		if (snap == null) {
			return false;
		}
		boolean any = spec.getCombine() == Combine.ANY;
		int considered = 0;
		int passed = 0;

		if (spec.getFgGate() != Gate.IGNORE) {
			boolean ok = colorSame(snap, spec, true);
			if (!ok && !any) {
				return false;
			}
			considered++;
			if (ok) {
				passed++;
			}
		}
		if (spec.getBgGate() != Gate.IGNORE) {
			boolean ok = colorSame(snap, spec, false);
			if (!ok && !any) {
				return false;
			}
			considered++;
			if (ok) {
				passed++;
			}
		}
		if (!boolLayer(spec.getWeight(), snap.weight(), any)) {
			return false;
		}
		if (spec.getWeight() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getWeight(), snap.weight())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getBright(), snap.bright, any)) {
			return false;
		}
		if (spec.getBright() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getBright(), snap.bright)) {
				passed++;
			}
		}
		if (!boolLayer(spec.getItalic(), snap.italic(), any)) {
			return false;
		}
		if (spec.getItalic() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getItalic(), snap.italic())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getUnderline(), snap.underline(), any)) {
			return false;
		}
		if (spec.getUnderline() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getUnderline(), snap.underline())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getDoubleUnderline(), snap.doubleUnderline(), any)) {
			return false;
		}
		if (spec.getDoubleUnderline() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getDoubleUnderline(), snap.doubleUnderline())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getStrike(), snap.strike(), any)) {
			return false;
		}
		if (spec.getStrike() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getStrike(), snap.strike())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getReverse(), snap.reverse(), any)) {
			return false;
		}
		if (spec.getReverse() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getReverse(), snap.reverse())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getFaint(), snap.faint(), any)) {
			return false;
		}
		if (spec.getFaint() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getFaint(), snap.faint())) {
				passed++;
			}
		}
		if (!boolLayer(spec.getBlink(), snap.blink(), any)) {
			return false;
		}
		if (spec.getBlink() != Gate.IGNORE) {
			considered++;
			if (gateBool(spec.getBlink(), snap.blink())) {
				passed++;
			}
		}
		if (spec.getHref() != Gate.IGNORE) {
			considered++;
			if (hrefOk(snap, spec)) {
				passed++;
			} else if (!any) {
				return false;
			}
		}
		if (spec.getText() != null && spec.getText().length() > 0) {
			considered++;
			boolean hit = textOk(runText, spec);
			Gate tg = spec.getTextGate();
			if (tg == Gate.IGNORE) {
				tg = Gate.REQUIRE;
			}
			boolean ok = tg == Gate.FORBID ? !hit : hit;
			if (ok) {
				passed++;
			} else if (!any) {
				return false;
			}
		}

		if (considered == 0) {
			return true;
		}
		if (any) {
			if (passed == 0) {
				return false;
			}
		} else if (passed != considered) {
			return false;
		}

		if (spec.getExtras() == Extras.FORBID) {
			int ignored = spec.ignoredOnBits();
			if ((snap.sgrBits & ignored) != 0) {
				return false;
			}
			if (spec.getBright() == Gate.IGNORE && snap.bright) {
				return false;
			}
			if (spec.getHref() == Gate.IGNORE && snap.hasHref()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Every character in {@code [start, end)} must match. Empty span is false
	 * when the spec is active.
	 */
	public static boolean matchesSpan(final StyleSnapshot[] byChar, final int start,
			final int end, final StyleMatchSpec spec, final String spanText) {
		if (spec == null || !spec.isActive()) {
			return true;
		}
		if (byChar == null || start < 0 || end > byChar.length || start >= end) {
			return false;
		}
		for (int i = start; i < end; i++) {
			if (!matches(byChar[i], spec, spanText)) {
				return false;
			}
		}
		return true;
	}

	private static boolean boolLayer(final Gate gate, final boolean on,
			final boolean any) {
		if (gate == Gate.IGNORE) {
			return true;
		}
		if (gateBool(gate, on)) {
			return true;
		}
		return any;
	}

	private static boolean gateBool(final Gate gate, final boolean on) {
		if (gate == Gate.REQUIRE) {
			return on;
		}
		if (gate == Gate.FORBID) {
			return !on;
		}
		return true;
	}

	private static boolean hrefOk(final StyleSnapshot snap, final StyleMatchSpec spec) {
		if (spec.getHref() == Gate.FORBID) {
			return !snap.hasHref();
		}
		if (spec.getHref() == Gate.REQUIRE) {
			if (!snap.hasHref()) {
				return false;
			}
			String want = spec.getHrefValue();
			if (want == null || want.length() == 0) {
				return true;
			}
			return want.equals(snap.href);
		}
		return true;
	}

	private static boolean textOk(final String runText, final StyleMatchSpec spec) {
		String want = spec.getText();
		String have = runText == null ? "" : runText;
		if (spec.isTextRegex()) {
			try {
				return Pattern.compile(want).matcher(have).find();
			} catch (PatternSyntaxException bad) {
				return have.equals(want);
			}
		}
		return have.contains(want);
	}

	private static boolean colorSame(final StyleSnapshot snap,
			final StyleMatchSpec spec, final boolean foreground) {
		Gate gate = foreground ? spec.getFgGate() : spec.getBgGate();
		ColorSpace wantSpace = foreground ? spec.getFgSpace() : spec.getBgSpace();
		int wantCode = foreground ? spec.getFgCode() : spec.getBgCode();
		ColorSpace haveSpace = foreground ? snap.fgSpace : snap.bgSpace;
		int haveCode = foreground ? snap.fgCode : snap.bgCode;
		boolean same;
		if (spec.getColorMode() == ColorMode.LOOKS) {
			StyleSnapshot want = foreground
					? new StyleSnapshot(wantSpace, wantCode, ColorSpace.ANSI16, 40,
							snap.bright, 0, null)
					: new StyleSnapshot(ColorSpace.ANSI16, 37, wantSpace, wantCode,
							false, 0, null);
			int haveLooks = foreground ? snap.fgLooksArgb() : snap.bgLooksArgb();
			int wantLooks = foreground ? want.fgLooksArgb() : want.bgLooksArgb();
			same = haveLooks == wantLooks;
		} else {
			same = haveSpace == wantSpace && haveCode == wantCode;
		}
		if (gate == Gate.REQUIRE) {
			return same;
		}
		if (gate == Gate.FORBID) {
			return !same;
		}
		return true;
	}
}
