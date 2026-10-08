package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .debug renderer show on|off} — a top-left label of the path that
 * painted the game text this frame. Session only; not a saved setting.
 */
public class DebugCommand extends SpecialCommand {

	static final int KIND_STATUS = 0;
	static final int KIND_SET = 1;
	static final int KIND_USAGE = 2;

	static final int FLAG_OFF = 0;
	static final int FLAG_ON = 1;
	static final int FLAG_TOGGLE = 2;

	public DebugCommand() {
		this.commandName = "debug";
	}

	static final class Parsed {
		final int kind;
		final int flag;

		Parsed(final int kind, final int flag) {
			this.kind = kind;
			this.flag = flag;
		}
	}

	/**
	 * Blank and {@code renderer} are status. {@code renderer show on|off|toggle}
	 * sets the label. Anything else, including a trailing word, is usage.
	 */
	static Parsed parse(final String raw) {
		final String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		if (arg.length() == 0 || "renderer".equals(arg)) {
			return new Parsed(KIND_STATUS, FLAG_OFF);
		}
		final String[] parts = arg.split("\\s+");
		if (parts.length == 3 && "renderer".equals(parts[0]) && "show".equals(parts[1])) {
			final Integer flag = onOffToggle(parts[2]);
			if (flag != null) {
				return new Parsed(KIND_SET, flag.intValue());
			}
		}
		return new Parsed(KIND_USAGE, FLAG_OFF);
	}

	static Integer onOffToggle(final String token) {
		if ("on".equals(token)) {
			return Integer.valueOf(FLAG_ON);
		}
		if ("off".equals(token)) {
			return Integer.valueOf(FLAG_OFF);
		}
		if ("toggle".equals(token)) {
			return Integer.valueOf(FLAG_TOGGLE);
		}
		return null;
	}

	static String usage() {
		return ".debug\n"
				+ ".debug renderer\n"
				+ ".debug renderer show on|off|toggle\n"
				+ "tiles — stored line bitmap (glyphs not redrawn this frame)\n"
				+ "bake — glyphs drawn into a new line bitmap this frame\n"
				+ "typeset — glyphs drawn on the window canvas\n"
				+ "hw / sw — that window canvas";
	}

	static String status(final boolean on) {
		return "\n" + Colorizer.getWhiteColor()
				+ "Font renderer label is " + (on ? "on" : "off") + ".\n"
				+ "Top-left of the text. typeset, tiles, and bake stay up, with a line count.\n"
				+ usage() + "\n";
	}

	@Override
	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		final Parsed parsed = parse(o == null ? "" : (String) o);
		if (parsed.kind == KIND_STATUS) {
			c.sendDataToWindow(status(c.rendererDebugLabel()));
			return null;
		}
		if (parsed.kind == KIND_USAGE) {
			c.sendDataToWindow(getErrorMessage("Debug command usage:", usage()));
			return null;
		}
		final boolean now = c.rendererDebugLabel();
		final boolean next = parsed.flag == FLAG_TOGGLE ? !now : parsed.flag == FLAG_ON;
		c.setRendererDebugLabel(next);
		if (next == now) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Font renderer label already " + (next ? "on" : "off") + ".\n");
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Font renderer label " + (next ? "on" : "off") + ".\n"
				+ "Top-left of the text. typeset, tiles, and bake stay up, with a line count.\n");
		return null;
	}
}
