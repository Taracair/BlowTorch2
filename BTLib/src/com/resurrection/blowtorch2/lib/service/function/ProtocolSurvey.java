package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.service.Colorizer;

/**
 * Plain-language report of what this world offered versus what is switched on.
 * No Android, no Connection — fill a {@link Snapshot} and format it.
 */
public final class ProtocolSurvey {

	public static final String KEY_GMCP = "use_gmcp";
	public static final String KEY_MXP = "use_mxp";
	public static final String KEY_MCCP = "use_mccp";
	public static final String KEY_MSDP = "use_msdp";
	public static final String KEY_MSSP = "use_mssp";
	public static final String KEY_MCP = "use_mcp";
	public static final String KEY_OSC8 = "osc8_links";

	public static final class Snapshot {
		public boolean connected;
		public boolean offeredGmcp;
		public boolean useGmcp;
		public boolean offeredMxp;
		public boolean useMxp;
		public boolean mxpActive;
		public boolean offeredMccp;
		public boolean useMccp;
		public boolean compressed;
		public boolean offeredMsdp;
		public boolean useMsdp;
		public boolean offeredMssp;
		public boolean useMssp;
		public boolean offeredMcp;
		public boolean useMcp;
		public boolean mcpHandshaken;
		public boolean sawOsc8;
		public boolean osc8On;
	}

	private ProtocolSurvey() {
	}

	public static String format(final Snapshot s) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		if (s == null || !s.connected) {
			sb.append("Not connected — connect first so we can see what this"
					+ " world offers.\n");
			sb.append("Then: .protocols          what was offered vs what is on\n");
			sb.append("      .protocols enable   turn on offered-but-off switches\n");
			return sb.toString();
		}
		sb.append("This connection:\n");
		int rows = 0;
		rows += row(sb, "GMCP", s.offeredGmcp, s.useGmcp, s.useGmcp,
				"server offered", gmcpNote(s));
		if (s.sawOsc8) {
			rows += row(sb, "OSC 8", true, s.osc8On, s.osc8On,
					"seen in output", null);
		}
		rows += row(sb, "MXP", s.offeredMxp, s.useMxp, s.mxpActive,
				"server offered", s.mxpActive ? "links active" : null);
		rows += row(sb, "MCCP", s.offeredMccp, s.useMccp, s.compressed,
				"server offered", s.compressed ? "compression on" : null);
		rows += row(sb, "MSDP", s.offeredMsdp, s.useMsdp, s.useMsdp,
				"server offered", null);
		rows += row(sb, "MSSP", s.offeredMssp, s.useMssp, s.useMssp,
				"server offered", null);
		rows += row(sb, "MCP", s.offeredMcp, s.useMcp, s.mcpHandshaken,
				"server said #$#mcp", s.mcpHandshaken ? "handshaken" : null);
		if (rows == 0) {
			sb.append("  (nothing offered or switched on yet this session)\n");
		}
		List<String> pending = keysToEnable(s);
		sb.append(".protocols enable  — turn on offered-but-off switches");
		if (pending.isEmpty()) {
			sb.append(" (none right now)");
		}
		sb.append("\n");
		return sb.toString();
	}

	public static List<String> keysToEnable(final Snapshot s) {
		ArrayList<String> keys = new ArrayList<String>();
		if (s == null) {
			return keys;
		}
		addIfOfferedAndOff(keys, KEY_GMCP, s.offeredGmcp, s.useGmcp);
		addIfOfferedAndOff(keys, KEY_MXP, s.offeredMxp, s.useMxp);
		addIfOfferedAndOff(keys, KEY_MCCP, s.offeredMccp, s.useMccp);
		addIfOfferedAndOff(keys, KEY_MSDP, s.offeredMsdp, s.useMsdp);
		addIfOfferedAndOff(keys, KEY_MSSP, s.offeredMssp, s.useMssp);
		addIfOfferedAndOff(keys, KEY_MCP, s.offeredMcp, s.useMcp);
		addIfOfferedAndOff(keys, KEY_OSC8, s.sawOsc8, s.osc8On);
		return keys;
	}

	public static boolean needsReconnect(final String key) {
		return KEY_GMCP.equals(key) || KEY_MXP.equals(key) || KEY_MCCP.equals(key)
				|| KEY_MSDP.equals(key) || KEY_MSSP.equals(key) || KEY_MCP.equals(key);
	}

	private static String gmcpNote(final Snapshot s) {
		if (s.sawOsc8 && !s.useGmcp) {
			return "StickMUD item hashes need GMCP on; OSC 8 alone is not enough";
		}
		return null;
	}

	private static int row(final StringBuilder sb, final String name,
			final boolean offered, final boolean use, final boolean live,
			final String offeredPhrase, final String extra) {
		if (!offered && !use && !live) {
			return 0;
		}
		sb.append("  ").append(pad(name)).append(" ");
		if (offered) {
			sb.append(offeredPhrase);
			if (!use) {
				sb.append(" · we refused (Use ").append(name).append("? off)");
			} else {
				sb.append(" · on");
			}
		} else if (use) {
			sb.append("on (server has not offered this session)");
		} else {
			sb.append("seen");
		}
		if (extra != null && extra.length() > 0 && (use || live)) {
			if (use && offered) {
				sb.append(" · ").append(extra);
			} else if (!offered) {
				sb.append(" · ").append(extra);
			}
		}
		sb.append("\n");
		if (extra != null && extra.length() > 0 && offered && !use) {
			sb.append("         ").append(extra).append("\n");
		}
		return 1;
	}

	private static void addIfOfferedAndOff(final List<String> keys,
			final String key, final boolean offered, final boolean on) {
		if (offered && !on) {
			keys.add(key);
		}
	}

	private static String pad(final String name) {
		StringBuilder b = new StringBuilder(name);
		while (b.length() < 6) {
			b.append(' ');
		}
		return b.toString();
	}
}
