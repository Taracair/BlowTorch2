package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.MudProtocolData;
import com.resurrection.blowtorch2.lib.service.Processor;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * Diagnostics for optional MSDP / MSSP. Protocols stay off unless enabled under
 * Options → Service → MUD Protocols.
 */
public class ProtocolsCommand extends SpecialCommand {

	private final boolean mMsdp;

	public ProtocolsCommand(boolean msdp) {
		this.mMsdp = msdp;
		this.commandName = msdp ? "msdp" : "mssp";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("help") || arg.equals("?")) {
			c.sendDataToWindow(help());
			return null;
		}
		if (mMsdp) {
			return dumpMsdp(c);
		}
		return dumpMssp(c);
	}

	private Object dumpMssp(Connection c) {
		boolean on = boolOpt(c, "use_mssp", false);
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("MSSP use=").append(on ? "on" : "off")
				.append(" (Options → Service → MUD Protocols)\n");
		Processor p = c.getProcessor();
		if (p == null) {
			sb.append("Not connected.\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		MudProtocolData data = p.getMudProtocols();
		sb.append("Cached: ").append(data.msspStatusLine()).append("\n");
		sb.append(data.dumpMssp());
		if (!on) {
			sb.append("(Enable Use MSSP? and reconnect to receive server listing data.)\n");
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object dumpMsdp(Connection c) {
		boolean on = boolOpt(c, "use_msdp", false);
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("MSDP use=").append(on ? "on" : "off")
				.append(" (Options → Service → MUD Protocols)\n");
		Processor p = c.getProcessor();
		if (p == null) {
			sb.append("Not connected.\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		MudProtocolData data = p.getMudProtocols();
		sb.append("Cached: ").append(data.msdpStatusLine()).append("\n");
		sb.append(data.dumpMsdp());
		if (!on) {
			sb.append("(Enable Use MSDP? and reconnect to receive variables.)\n");
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private static String help() {
		return "\n" + Colorizer.getWhiteColor()
				+ "Optional MUD protocols (off by default):\n"
				+ "  .mssp   — dump MSSP server status cache\n"
				+ "  .msdp   — dump MSDP variable cache\n"
				+ "Enable under Options → Service → MUD Protocols, then reconnect.\n";
	}

	private static boolean boolOpt(Connection c, String key, boolean def) {
		if (c == null || c.getSettings() == null) {
			return def;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		if (!(o instanceof BooleanOption) || !(o.getValue() instanceof Boolean)) {
			return def;
		}
		return ((Boolean) o.getValue()).booleanValue();
	}
}
