package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.window.GlobalGestures;

/** Screen gestures on the game text: {@code .gesture}. */
public class GestureCommand extends SpecialCommand {

	public GestureCommand() {
		this.commandName = "gesture";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		if (c == null || c.getSettings() == null) {
			return null;
		}
		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + status(c));
			return null;
		}
		String[] parts = arg.split("\\s+");
		String head = parts[0].toLowerCase();
		if ("edit".equals(head)) {
			c.getService().doRunUiAction("gesture-edit");
			return null;
		}
		if ("mode".equals(head)) {
			if (parts.length < 2) {
				usage(c);
				return null;
			}
			Integer mode = modeIndex(parts[1]);
			if (mode == null) {
				usage(c);
				return null;
			}
			c.updateIntegerSetting(GlobalGestures.KEY_MODE, mode.intValue());
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Gesture mode: " + GlobalGestures.modeLabel(mode.intValue()) + ".\n"
					+ (bool(c, GlobalGestures.KEY_SHOW_MODE, false)
							? ""
							: "Mode label is off. .gesture show on\n"));
			return null;
		}
		if ("scroll".equals(head)) {
			if (parts.length < 2) {
				usage(c);
				return null;
			}
			Integer scroll = GlobalGestures.scrollIndex(parts[1]);
			if (scroll == null) {
				usage(c);
				return null;
			}
			int mode = integer(c, GlobalGestures.KEY_MODE, GlobalGestures.MODE_CLASSIC);
			if (GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL, mode, scroll.intValue())) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Scrolling is not used in " + GlobalGestures.modeLabel(mode)
						+ ". .gesture mode 1 or both\n");
				return null;
			}
			c.updateIntegerSetting(GlobalGestures.KEY_SCROLL, scroll.intValue());
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Scrolling: " + GlobalGestures.scrollLabel(scroll.intValue()) + ".\n");
			return null;
		}
		if ("show".equals(head)) {
			Boolean on = parts.length < 2 ? null : onOff(parts[1]);
			if (on == null) {
				usage(c);
				return null;
			}
			c.updateBooleanSetting(GlobalGestures.KEY_SHOW_MODE, on.booleanValue());
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Gesture mode label " + (on.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if ("preview".equals(head)) {
			Boolean on = parts.length < 2 ? null : onOff(parts[1]);
			if (on == null) {
				usage(c);
				return null;
			}
			c.updateBooleanSetting(GlobalGestures.KEY_SHOW_ARROW, on.booleanValue());
			c.updateBooleanSetting(GlobalGestures.KEY_SHOW_COMMAND, on.booleanValue());
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Gesture preview " + (on.booleanValue() ? "on" : "off") + ".\n"
					+ "Options → Input → Global gestures can show the arrow and the command separately.\n");
			return null;
		}
		usage(c);
		return null;
	}

	private void usage(final Connection c) {
		c.sendDataToWindow(getErrorMessage("Gesture command usage:",
				".gesture\n"
						+ 				".gesture mode classic|1|2|both\n"
						+ ".gesture scroll two|hold|off\n"
						+ ".gesture edit\n"
						+ ".gesture show on|off\n"
						+ ".gesture preview on|off\n"
						+ "Options → Input → Global gestures"));
	}

	private static String status(final Connection c) {
		int mode = integer(c, GlobalGestures.KEY_MODE, GlobalGestures.MODE_CLASSIC);
		int scroll = integer(c, GlobalGestures.KEY_SCROLL, GlobalGestures.SCROLL_HOLD);
		int hold = GlobalGestures.clampHold(integer(c, GlobalGestures.KEY_HOLD_MS,
				GlobalGestures.DEFAULT_HOLD_MS));
		StringBuilder sb = new StringBuilder();
		sb.append("Global gestures: ").append(GlobalGestures.modeLabel(mode)).append(".\n");
		if (mode == GlobalGestures.MODE_ONE || mode == GlobalGestures.MODE_BOTH) {
			sb.append("Scrolling: ").append(GlobalGestures.scrollLabel(scroll));
			if (scroll == GlobalGestures.SCROLL_HOLD) {
				sb.append(" (").append(hold).append(" ms)");
			}
			sb.append(".\n");
		} else {
			sb.append("Scrolling is not used in this mode.\n");
		}
		if (mode == GlobalGestures.MODE_BOTH) {
			sb.append("Two-finger direction: ").append(onOffWord(bool(c, GlobalGestures.KEY_TWO_DIR, true)))
					.append(". Copy tap: ").append(onOffWord(bool(c, GlobalGestures.KEY_TWO_COPY, true)))
					.append(". Scroll: ").append(onOffWord(bool(c, GlobalGestures.KEY_TWO_SCROLL, false)))
					.append(".\n");
		} else if (mode == GlobalGestures.MODE_TWO) {
			sb.append("Two-finger direction: ").append(onOffWord(bool(c, GlobalGestures.KEY_TWO_DIR, true)))
					.append(". Copy tap: ").append(onOffWord(bool(c, GlobalGestures.KEY_TWO_COPY, true)))
					.append(".\n");
		}
		sb.append("Mode label: ").append(onOffWord(bool(c, GlobalGestures.KEY_SHOW_MODE, false)))
				.append(". Preview: arrow ")
				.append(onOffWord(bool(c, GlobalGestures.KEY_SHOW_ARROW, true)))
				.append(", command ")
				.append(onOffWord(bool(c, GlobalGestures.KEY_SHOW_COMMAND, true)))
				.append(".\n");
		sb.append(".gesture mode classic|1|2|both\n");
		sb.append(".gesture scroll two|hold|off\n");
		sb.append(".gesture edit    .gesture show on|off    .gesture preview on|off\n");
		sb.append("Options → Input → Global gestures\n");
		return sb.toString();
	}

	static Integer modeIndex(final String token) {
		if (token == null) {
			return null;
		}
		String t = token.toLowerCase();
		if ("classic".equals(t) || "0".equals(t)) {
			return Integer.valueOf(GlobalGestures.MODE_CLASSIC);
		}
		if ("1".equals(t) || "one".equals(t)) {
			return Integer.valueOf(GlobalGestures.MODE_ONE);
		}
		if ("2".equals(t) || "two".equals(t)) {
			return Integer.valueOf(GlobalGestures.MODE_TWO);
		}
		if ("both".equals(t) || "3".equals(t)) {
			return Integer.valueOf(GlobalGestures.MODE_BOTH);
		}
		return null;
	}

	static Boolean onOff(final String token) {
		if (token == null) {
			return null;
		}
		String t = token.toLowerCase();
		if ("on".equals(t)) {
			return Boolean.TRUE;
		}
		if ("off".equals(t)) {
			return Boolean.FALSE;
		}
		return null;
	}

	private static String onOffWord(final boolean on) {
		return on ? "on" : "off";
	}

	private static int integer(final Connection c, final String key, final int fallback) {
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		if (o != null && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return fallback;
	}

	private static boolean bool(final Connection c, final String key, final boolean fallback) {
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		if (o != null && o.getValue() instanceof Boolean) {
			return ((Boolean) o.getValue()).booleanValue();
		}
		return fallback;
	}
}
