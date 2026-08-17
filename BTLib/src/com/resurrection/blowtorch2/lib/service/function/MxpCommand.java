package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.Processor;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * {@code .mxp} — MUD eXtension Protocol (telnet option 91).
 *
 * <p>Connection option, not a window one. Reconnect after changing so the
 * server sees DO or DONT.
 */
public class MxpCommand extends SpecialCommand {

	public static final String OPT_USE = "use_mxp";
	public static final String OPT_LOG = "log_mxp";
	public static final String OPT_FEED = "mxp_feed";

	public MxpCommand() {
		this.commandName = "mxp";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String raw = o == null ? "" : ((String) o).trim();
		String arg = raw.toLowerCase(Locale.US);
		if (arg.length() == 0 || arg.equals("status") || arg.equals("help")
				|| arg.equals("?")) {
			c.sendDataToWindow(status(c));
			return null;
		}
		Boolean desired = parseOnOff(arg.split("\\s+")[0]);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Mxp command usage:",
					".mxp on | .mxp off | .mxp status\n"
							+ "Also: Options → Service → MUD Protocols → Use MXP?\n"
							+ "Sample without a MUD: .probe mxp\n"));
			return null;
		}
		boolean on = boolOpt(c, OPT_USE, true);
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "MXP already " + (on ? "on" : "off") + ".\n");
			return null;
		}
		c.updateBooleanSetting(OPT_USE, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "MXP " + (desired.booleanValue() ? "on" : "off")
				+ ". Reconnect so the server sees the new DO/DONT.\n");
		return null;
	}

	private static String status(final Connection c) {
		boolean on = boolOpt(c, OPT_USE, true);
		Processor p = c.getProcessor();
		boolean active = p != null && p.getMxp() != null && p.getMxp().isActive();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("MXP use=").append(on ? "on" : "off");
		sb.append(" active=").append(active ? "yes" : "no");
		if (p != null && p.getMxp() != null) {
			sb.append(" mode=").append(p.getMxp().getMode().name().toLowerCase(Locale.US));
		}
		sb.append("\n");
		sb.append("Clickable SEND links, colours, custom elements, EXPIRE.\n");
		sb.append("Options → Service → MUD Protocols → Use MXP?\n");
		sb.append(".probe mxp dumps a tappable sample here.\n");
		if (!on) {
			sb.append("Enable and reconnect to answer IAC WILL MXP.\n");
		}
		return sb.toString();
	}

	private static boolean boolOpt(final Connection c, final String key, final boolean def) {
		if (c == null || c.getSettings() == null) {
			return def;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		if (!(o instanceof BooleanOption) || !(o.getValue() instanceof Boolean)) {
			return def;
		}
		return ((Boolean) o.getValue()).booleanValue();
	}

	private static Boolean parseOnOff(final String token) {
		if (token == null) {
			return null;
		}
		if (token.equals("on") || token.equals("true") || token.equals("1")
				|| token.equals("yes")) {
			return Boolean.TRUE;
		}
		if (token.equals("off") || token.equals("false") || token.equals("0")
				|| token.equals("no")) {
			return Boolean.FALSE;
		}
		return null;
	}
}
