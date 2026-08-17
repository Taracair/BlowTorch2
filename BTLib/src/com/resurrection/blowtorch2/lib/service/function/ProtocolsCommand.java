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
 * Options → Service → Telnet.
 */
public class ProtocolsCommand extends SpecialCommand {

	private final boolean mMsdp;

	public ProtocolsCommand(boolean msdp) {
		this.mMsdp = msdp;
		this.commandName = msdp ? "msdp" : "mssp";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String raw = o == null ? "" : ((String) o).trim();
		String arg = raw.toLowerCase(Locale.US);
		if (arg.equals("help") || arg.equals("?")) {
			c.sendDataToWindow(help());
			return null;
		}
		if (mMsdp && raw.length() > 0) {
			String[] parts = raw.split("\\s+", 2);
			String sub = parts[0].toLowerCase(Locale.US);
			String rest = parts.length > 1 ? parts[1].trim() : "";
			if (sub.equals("list") || sub.equals("send") || sub.equals("report")
					|| sub.equals("unreport") || sub.equals("reset")) {
				return doMsdpCommand(c, sub, rest);
			}
		}
		if (mMsdp) {
			return dumpMsdp(c);
		}
		return dumpMssp(c);
	}

	/**
	 * Ask the server for something. MSDP is the one optional protocol here that
	 * is two-way, and until now the client could only listen.
	 */
	private Object doMsdpCommand(Connection c, String sub, String rest) {
		Processor p = c.getProcessor();
		if (p == null) {
			c.sendDataToWindow("\nMSDP: not connected.\n");
			return null;
		}
		if (!boolOpt(c, "use_msdp", false)) {
			c.sendDataToWindow("\nMSDP is off — enable Use MSDP? under"
					+ " Options → Service → Telnet and reconnect.\n");
			return null;
		}
		String command = sub.toUpperCase(Locale.US);
		String argument = rest;
		if (argument.length() == 0) {
			// LIST with no argument is the useful default: ask what there is.
			// The others genuinely need a name, so say so rather than send junk.
			if (command.equals("LIST")) {
				argument = "COMMANDS";
			} else {
				c.sendDataToWindow("\nUsage: .msdp " + sub + " <variable>\n");
				return null;
			}
		}
		boolean sent = p.sendMsdpCommand(command, argument);
		c.sendDataToWindow(sent
				? "\nMSDP → " + command + " " + argument
						+ "\n(replies land in the cache; .msdp to dump it)\n"
				: "\nMSDP: could not send (option not negotiated).\n");
		return null;
	}

	private Object dumpMssp(Connection c) {
		boolean on = boolOpt(c, "use_mssp", false);
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("MSSP use=").append(on ? "on" : "off")
				.append(" (Options → Service → Telnet)\n");
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
				.append(" (Options → Service → Telnet)\n");
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
				+ "  .mssp                     dump MSSP server status cache\n"
				+ "  .msdp                     dump MSDP variable cache\n"
				+ "  .msdp list [COMMANDS]     ask what the server supports\n"
				+ "  .msdp send <var>          ask for a variable once\n"
				+ "  .msdp report <var>        ask to be told whenever it changes\n"
				+ "  .msdp unreport <var>      stop those updates\n"
				+ "  .msdp reset <group>       reset a group of variables\n"
				+ "Enable under Options → Service → Telnet, then reconnect.\n"
				+ "MSSP is one-way (server announces); MSDP is two-way, so it needs\n"
				+ "you to ask before most servers send anything.\n";
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
