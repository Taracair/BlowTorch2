package com.resurrection.blowtorch2.lib.service.function;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.Processor;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;

/**
 * GMCP helper command. Servers differ; this is a pragmatic sniff / version /
 * supports / dump toolkit rather than a full protocol GUI.
 *
 * <pre>
 * .gmcp
 * .gmcp sniff [on|off]
 * .gmcp version
 * .gmcp supports [modules…]
 * .gmcp dump [path]
 * .gmcp send &lt;payload&gt;
 * </pre>
 */
public class GmcpCommand extends SpecialCommand {

	public static final String OPT_USE = "use_gmcp";
	public static final String OPT_SUPPORTS = "gmcp_supports";
	public static final String OPT_LOG = "log_gmcp";

	public GmcpCommand() {
		this.commandName = "gmcp";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		if (arg.length() == 0 || arg.equalsIgnoreCase("help") || arg.equals("?")) {
			c.sendDataToWindow(helpText());
			return null;
		}

		String[] parts = arg.split("\\s+", 2);
		String sub = parts[0].toLowerCase(Locale.US);
		String rest = parts.length > 1 ? parts[1].trim() : "";

		switch (sub) {
		case "sniff":
		case "log":
			return doSniff(c, rest);
		case "version":
		case "hello":
			return doVersion(c);
		case "supports":
		case "support":
			return doSupports(c, rest);
		case "dump":
			return doDump(c, rest);
		case "send":
			return doSend(c, rest);
		case "status":
			return doStatus(c);
		default:
			c.sendDataToWindow(getErrorMessage("GMCP usage",
					"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
			return null;
		}
	}

	private Object doStatus(Connection c) {
		boolean use = boolOpt(c, OPT_USE, false);
		boolean log = boolOpt(c, OPT_LOG, false);
		String supports = stringOpt(c, OPT_SUPPORTS, "\"char 1\"");
		Processor p = c.getProcessor();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("GMCP use=").append(use ? "on" : "off");
		sb.append("  log/sniff=").append(log ? "on" : "off").append("\n");
		sb.append("Supports string: ").append(supports).append("\n");
		sb.append("Negotiated processor: ").append(p != null ? "yes" : "no (not connected)").append("\n");
		sb.append("Enable under Options → Service → GMCP Options.\n");
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doSniff(Connection c, String rest) {
		BooleanOption opt = findBool(c, OPT_LOG);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("GMCP sniff", "log_gmcp option missing."));
			return null;
		}
		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (rest.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "GMCP sniff/log is " + (current ? "on" : "off") + ".\n"
					+ "Usage: .gmcp sniff on | .gmcp sniff off\n"
					+ "When on, handshake and packets are written to the app error log"
					+ " (and session log if enabled).\n");
			return null;
		}
		Boolean desired = parseOnOff(rest.split("\\s+")[0]);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("GMCP sniff", ".gmcp sniff on | off"));
			return null;
		}
		c.updateBooleanSetting(OPT_LOG, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "GMCP sniff/log " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	private Object doVersion(Connection c) {
		String msg = "\n" + Colorizer.getWhiteColor()
				+ "BlowTorch GMCP client hello:\n"
				+ "  core.hello {\"client\":\"BlowTorch\",\"version\":\"1.4\"}\n"
				+ "Telnet option: IAC SB GMCP (201) … IAC SE\n"
				+ "Modules vary by server — use .gmcp sniff on and connect to see what yours sends.\n"
				+ "Typical supports: \"char 1\", \"room 1\", \"comm 1\", \"core 1\"\n"
				+ "Set permanently: Options → Service → GMCP Options → Supports String\n";
		c.sendDataToWindow(msg);
		return null;
	}

	private Object doSupports(Connection c, String rest) {
		StringOption opt = findString(c, OPT_SUPPORTS);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("GMCP supports", "gmcp_supports option missing."));
			return null;
		}
		if (rest.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Current supports string: " + opt.getValue() + "\n"
					+ "Usage: .gmcp supports \"char 1\", \"room 1\"\n"
					+ "Or:    .gmcp supports char room   (adds version 1 to each)\n"
					+ "Also: Options → Service → GMCP Options → Supports String\n"
					+ "Reconnect (or toggle Use GMCP) after changing so the server sees it.\n");
			return null;
		}
		String normalized = normalizeSupports(rest);
		c.updateStringSetting(OPT_SUPPORTS, normalized);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "GMCP supports set to: " + normalized + "\n"
				+ "Reconnect or re-enable Use GMCP? to renegotiate.\n");
		return null;
	}

	private Object doDump(Connection c, String rest) {
		Processor p = c.getProcessor();
		if (p == null) {
			c.sendDataToWindow(getErrorMessage("GMCP dump", "Not connected / no processor."));
			return null;
		}
		String path = rest.length() == 0 ? "" : rest;
		HashMap<String, Object> table = p.getGMCPTable(path);
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("GMCP dump").append(path.length() == 0 ? " (root)" : " [" + path + "]").append(":\n");
		if (table == null || table.isEmpty()) {
			sb.append("  (empty — enable Use GMCP?, sniff, and play until modules arrive)\n");
		} else {
			dumpMap(sb, path, table, 0);
		}
		p.dumpGMCP();
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doSend(Connection c, String rest) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("GMCP send",
					".gmcp send <module> [json]\nExample: .gmcp send core.ping"));
			return null;
		}
		if (!boolOpt(c, OPT_USE, false)) {
			c.sendDataToWindow(getErrorMessage("GMCP send", "Use GMCP? is off (Options → Service → GMCP Options)."));
			return null;
		}
		c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_SENDGMCPDATA, rest));
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "GMCP send queued: " + rest + "\n");
		return null;
	}

	private static void dumpMap(StringBuilder sb, String prefix, HashMap<String, Object> map, int depth) {
		if (map == null || depth > 6) {
			return;
		}
		for (Map.Entry<String, Object> e : map.entrySet()) {
			String key = e.getKey();
			Object val = e.getValue();
			String path = prefix.length() == 0 ? key : prefix + "." + key;
			indent(sb, depth);
			if (val instanceof HashMap<?, ?>) {
				sb.append(key).append(":\n");
				@SuppressWarnings("unchecked")
				HashMap<String, Object> child = (HashMap<String, Object>) val;
				dumpMap(sb, path, child, depth + 1);
			} else {
				sb.append(key).append(" = ").append(val == null ? "null" : val.toString()).append("\n");
			}
		}
	}

	private static void indent(StringBuilder sb, int depth) {
		for (int i = 0; i < depth; i++) {
			sb.append("  ");
		}
	}

	/** Accept quoted JSON-ish list or bare module names. */
	static String normalizeSupports(String rest) {
		String t = rest.trim();
		if (t.contains("\"")) {
			return t;
		}
		String[] toks = t.split("[,\\s]+");
		StringBuilder sb = new StringBuilder();
		for (String tok : toks) {
			if (tok.length() == 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			String mod = tok;
			String ver = "1";
			int sp = tok.lastIndexOf(' ');
			if (sp > 0) {
				mod = tok.substring(0, sp).trim();
				ver = tok.substring(sp + 1).trim();
			} else if (tok.matches(".*\\d$") && tok.contains(".")) {
				// keep as-is if user typed room.1 style — still wrap as "room 1" if possible
			}
			sb.append("\"").append(mod).append(" ").append(ver).append("\"");
		}
		return sb.length() == 0 ? "\"char 1\"" : sb.toString();
	}

	private static String helpText() {
		return "\n" + Colorizer.getWhiteColor()
				+ "GMCP (Generic Mud Communication Protocol) is an out-of-band telnet channel\n"
				+ "(option 201) for structured JSON-ish updates (vitals, room, etc.).\n"
				+ "Servers differ — sniff first, then set Supports String for modules you need.\n\n"
				+ shortUsage()
				+ "Options → Service → GMCP Options: Use GMCP?, Supports String, Log GMCP?\n"
				+ "Lua: Send_GMCP_Packet(\"module {…}\")  Triggers: pattern %module.path\n";
	}

	private static String shortUsage() {
		return "Usage:\n"
				+ "  .gmcp                 — this help\n"
				+ "  .gmcp status          — current flags\n"
				+ "  .gmcp sniff [on|off]  — log handshake/packets to file\n"
				+ "  .gmcp version         — client hello / syntax notes\n"
				+ "  .gmcp supports […]    — show or set supports modules\n"
				+ "  .gmcp dump [path]     — dump cached GMCP table\n"
				+ "  .gmcp send <payload>  — send a GMCP packet\n";
	}

	private static Boolean parseOnOff(String token) {
		if (token == null) {
			return null;
		}
		String t = token.toLowerCase(Locale.US);
		if (t.equals("on") || t.equals("true") || t.equals("1") || t.equals("yes")) {
			return Boolean.TRUE;
		}
		if (t.equals("off") || t.equals("false") || t.equals("0") || t.equals("no")) {
			return Boolean.FALSE;
		}
		return null;
	}

	private static boolean boolOpt(Connection c, String key, boolean def) {
		BooleanOption o = findBool(c, key);
		if (o == null || !(o.getValue() instanceof Boolean)) {
			return def;
		}
		return ((Boolean) o.getValue()).booleanValue();
	}

	private static String stringOpt(Connection c, String key, String def) {
		StringOption o = findString(c, key);
		if (o == null || !(o.getValue() instanceof String)) {
			return def;
		}
		return (String) o.getValue();
	}

	private static BooleanOption findBool(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		return (o instanceof BooleanOption) ? (BooleanOption) o : null;
	}

	private static StringOption findString(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(key);
		return (o instanceof StringOption) ? (StringOption) o : null;
	}
}
