package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.window.InputHyphenation;

/** {@code .hyphen} — draw a hyphen in a long input word. Not sent. */
public class HyphenCommand extends SpecialCommand {

	public static final String KEY_ENABLED = "input_hyphenate";
	public static final String KEY_LANG = "input_hyphen_lang";
	public static final String KEY_FULL = "input_hyphen_full";

	public HyphenCommand() {
		this.commandName = "hyphen";
	}

	public Object execute(Object o, Connection c) {
		BooleanOption enabled = findBoolean(c, KEY_ENABLED);
		ListOption lang = findList(c, KEY_LANG);
		BooleanOption full = findBoolean(c, KEY_FULL);
		if (enabled == null || lang == null || full == null) {
			c.sendDataToWindow(getErrorMessage("Hyphen command error",
					"Hyphenate long words is not available yet."));
			return null;
		}
		InputHyphenation.Parsed parsed = InputHyphenation.parse(o == null ? "" : (String) o);
		if (parsed.action == InputHyphenation.Action.BAD) {
			c.sendDataToWindow(getErrorMessage("Hyphen command usage:", usage()));
			return null;
		}
		if (parsed.action == InputHyphenation.Action.STATUS) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + status(c, enabled, lang, full));
			return null;
		}
		if (parsed.action == InputHyphenation.Action.ENABLED) {
			setEnabled(c, enabled, parsed.on);
			return null;
		}
		if (parsed.action == InputHyphenation.Action.LANG) {
			setLang(c, enabled, lang, parsed.lang);
			return null;
		}
		setFull(c, enabled, full, parsed.on);
		return null;
	}

	private static void setEnabled(Connection c, BooleanOption enabled, boolean want) {
		boolean current = ((Boolean) enabled.getValue()).booleanValue();
		if (want == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Input hyphenation already " + (want ? "on" : "off") + ".\n");
			return;
		}
		c.updateBooleanSetting(KEY_ENABLED, want);
		String extra = "";
		if (want && !growOn(c)) {
			extra = " Grow Input Bar is off, so nothing breaks until .wrap on.";
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Input hyphenation " + (want ? "on" : "off") + "." + extra + "\n");
	}

	private static void setLang(Connection c, BooleanOption enabled, ListOption lang, int want) {
		int current = ((Integer) lang.getValue()).intValue();
		if (InputHyphenation.clampLang(current) == want) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Hyphenation language is already "
					+ InputHyphenation.languageName(want) + ".\n");
			return;
		}
		c.updateIntegerSetting(KEY_LANG, want);
		String extra = enabledOn(enabled) ? "" : " Hyphenation itself is off.";
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Hyphenation language is " + InputHyphenation.languageName(want)
				+ "." + extra + "\n");
	}

	private static void setFull(Connection c, BooleanOption enabled, BooleanOption full, boolean want) {
		boolean current = ((Boolean) full.getValue()).booleanValue();
		if (want == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Hyphenation already breaks "
					+ (want ? "more often" : "sparingly") + ".\n");
			return;
		}
		c.updateBooleanSetting(KEY_FULL, want);
		String extra = enabledOn(enabled) ? "" : " Hyphenation itself is off.";
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ (want
						? "Hyphenation breaks more often."
						: "Hyphenation keeps at least 5 letters before the hyphen.")
				+ extra + "\n");
	}

	private static String status(Connection c, BooleanOption enabled, ListOption lang,
			BooleanOption full) {
		boolean on = enabledOn(enabled);
		int which = lang.getValue() instanceof Integer
				? ((Integer) lang.getValue()).intValue() : InputHyphenation.LANG_EN;
		boolean dense = ((Boolean) full.getValue()).booleanValue();
		String idle = "";
		if (on && !growOn(c)) {
			idle = "Grow Input Bar is off, so nothing breaks until .wrap on.\n";
		}
		return "Input hyphenation (.hyphen) is " + (on ? "on" : "off") + ".\n"
				+ "Language: " + InputHyphenation.languageName(which)
				+ ". Breaks: " + (dense ? "more often" : "sparing") + ".\n"
				+ idle
				+ "Usage: .hyphen on|off · .hyphen lang en|phone · .hyphen full on|off\n"
				+ "The hyphen is drawn, not sent. A password line does not break.\n"
				+ "Also: Options → Typing → Hyphenate long words?\n";
	}

	private static String usage() {
		return ".hyphen on|off\n"
				+ ".hyphen lang en|phone\n"
				+ ".hyphen full on|off\n"
				+ "Draws a hyphen when a long word does not fit the space left on the line. The game receives the whole word.\n"
				+ "Needs Grow Input Bar (.wrap). Off by default.\n"
				+ "Also: Options → Typing → Hyphenate long words?";
	}

	private static boolean enabledOn(BooleanOption enabled) {
		return ((Boolean) enabled.getValue()).booleanValue();
	}

	private static boolean growOn(Connection c) {
		BooleanOption grow = findBoolean(c, WrapCommand.OPTION_KEY);
		return grow != null && ((Boolean) grow.getValue()).booleanValue();
	}

	private static BooleanOption findBoolean(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		if (o instanceof BooleanOption) {
			return (BooleanOption) o;
		}
		return null;
	}

	private static ListOption findList(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		if (o instanceof ListOption) {
			return (ListOption) o;
		}
		return null;
	}
}
