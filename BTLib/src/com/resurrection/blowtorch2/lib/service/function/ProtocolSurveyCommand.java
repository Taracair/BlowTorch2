package com.resurrection.blowtorch2.lib.service.function;

import java.util.List;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.DataPumper;
import com.resurrection.blowtorch2.lib.service.McpEngine;
import com.resurrection.blowtorch2.lib.service.OptionNegotiator;
import com.resurrection.blowtorch2.lib.service.Processor;
import com.resurrection.blowtorch2.lib.service.TC;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * {@code .protocols} — what this world offered versus what is switched on.
 *
 * <p>{@code .msdp} / {@code .mssp} stay as they were. This is the beginner
 * survey: one screen, then {@code .protocols enable} to turn on offered-but-off
 * switches.
 */
public class ProtocolSurveyCommand extends SpecialCommand {

	public ProtocolSurveyCommand() {
		this.commandName = "protocols";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("help") || arg.equals("?")) {
			c.sendDataToWindow(help());
			return null;
		}
		if (arg.equals("enable") || arg.equals("on")) {
			c.sendDataToWindow(enable(c));
			return null;
		}
		c.sendDataToWindow(report(c));
		return null;
	}

	public static String report(final Connection c) {
		return ProtocolSurvey.format(collect(c));
	}

	static ProtocolSurvey.Snapshot collect(final Connection c) {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		if (c == null) {
			return s;
		}
		Processor p = c.getProcessor();
		s.connected = p != null;
		s.useGmcp = boolOpt(c, ProtocolSurvey.KEY_GMCP, false);
		s.useMxp = boolOpt(c, ProtocolSurvey.KEY_MXP, true);
		s.useMccp = boolOpt(c, ProtocolSurvey.KEY_MCCP, true);
		s.useMsdp = boolOpt(c, ProtocolSurvey.KEY_MSDP, false);
		s.useMssp = boolOpt(c, ProtocolSurvey.KEY_MSSP, false);
		s.useMcp = boolOpt(c, ProtocolSurvey.KEY_MCP, false);
		s.osc8On = c.getMainWindowBooleanOption(ProtocolSurvey.KEY_OSC8, true);
		if (p != null) {
			OptionNegotiator n = p.getOptionHandler();
			if (n != null) {
				s.offeredGmcp = n.serverOffered(TC.GMCP);
				s.offeredMxp = n.serverOffered(TC.MXP);
				s.offeredMccp = n.serverOffered(TC.COMPRESS2);
				s.offeredMsdp = n.serverOffered(TC.MSDP);
				s.offeredMssp = n.serverOffered(TC.MSSP);
			}
			s.sawOsc8 = p.sawOsc8();
			s.mxpActive = p.getMxp() != null && p.getMxp().isActive();
		}
		DataPumper pump = c.getPump();
		s.compressed = pump != null && pump.isCompressed();
		McpEngine mcp = c.getMcpEngine();
		if (mcp != null) {
			s.offeredMcp = mcp.serverOfferedHello();
			s.mcpHandshaken = mcp.isHandshaken();
		}
		return s;
	}

	private static String enable(final Connection c) {
		ProtocolSurvey.Snapshot s = collect(c);
		if (!s.connected) {
			return ProtocolSurvey.format(s);
		}
		List<String> keys = ProtocolSurvey.keysToEnable(s);
		if (keys.isEmpty()) {
			return "\n" + Colorizer.getWhiteColor()
					+ "Nothing to turn on — offered switches are already on,"
					+ " or this world has not offered any yet.\n";
		}
		boolean reconnect = false;
		boolean mcp = false;
		boolean osc8Only = true;
		StringBuilder flipped = new StringBuilder();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			c.updateBooleanSetting(key, true);
			if (flipped.length() > 0) {
				flipped.append(", ");
			}
			flipped.append(labelFor(key));
			if (ProtocolSurvey.needsReconnect(key)) {
				reconnect = true;
				osc8Only = false;
			}
			if (ProtocolSurvey.KEY_MCP.equals(key)) {
				mcp = true;
			}
			if (!ProtocolSurvey.KEY_OSC8.equals(key)) {
				osc8Only = false;
			}
		}
		StringBuilder out = new StringBuilder();
		out.append("\n").append(Colorizer.getWhiteColor());
		out.append("Turned on: ").append(flipped).append(".\n");
		if (reconnect) {
			out.append("Reconnect so the server sees the new DO/DONT.\n");
		}
		if (mcp) {
			out.append("MCP: reconnect, or wait for the next #$#mcp hello.\n");
		}
		if (osc8Only) {
			out.append("OSC 8 does not need a reconnect.\n");
		}
		return out.toString();
	}

	private static String labelFor(final String key) {
		if (ProtocolSurvey.KEY_GMCP.equals(key)) {
			return "Use GMCP?";
		}
		if (ProtocolSurvey.KEY_MXP.equals(key)) {
			return "Use MXP?";
		}
		if (ProtocolSurvey.KEY_MCCP.equals(key)) {
			return "Use MCCP?";
		}
		if (ProtocolSurvey.KEY_MSDP.equals(key)) {
			return "Use MSDP?";
		}
		if (ProtocolSurvey.KEY_MSSP.equals(key)) {
			return "Use MSSP?";
		}
		if (ProtocolSurvey.KEY_MCP.equals(key)) {
			return "Use MCP?";
		}
		if (ProtocolSurvey.KEY_OSC8.equals(key)) {
			return "Use OSC 8?";
		}
		return key;
	}

	private static String help() {
		return "\n" + Colorizer.getWhiteColor()
				+ ".protocols          what this world offered vs what is on\n"
				+ ".protocols enable   turn on offered-but-off switches\n"
				+ "GMCP / MCP / MXP live under Options → Service → Protocols.\n"
				+ "MTTS / MSDP / MSSP / MCCP live under Options → Service → Telnet.\n"
				+ "Use OSC 8? is Options → Window. .msdp and .mssp dump caches.\n";
	}

	private static boolean boolOpt(final Connection c, final String key,
			final boolean def) {
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
