package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.GmcpModuleRegistry;
import com.resurrection.blowtorch2.lib.service.Processor;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

/**
 * GMCP helper command. Servers differ; this is a pragmatic sniff / version /
 * supports / dump / module toolkit rather than a full protocol GUI.
 *
 * <pre>
 * .gmcp
 * .gmcp ask | handshake
 * .gmcp modules
 * .gmcp enable|disable &lt;mods…&gt;
 * .gmcp renegotiate
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
	public static final String OPT_FEED = "gmcp_feed";

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
		case "feed":
		case "echo":
		case "show":
			return doFeed(c, rest);
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
		case "ask":
		case "handshake":
			return doAsk(c);
		case "modules":
		case "module":
			return doModules(c);
		case "enable":
		case "add":
			return doEnableDisable(c, rest, true);
		case "disable":
		case "remove":
			return doEnableDisable(c, rest, false);
		case "renegotiate":
		case "renego":
		case "resync":
			return doRenegotiate(c);
		default:
			c.sendDataToWindow(getErrorMessage("GMCP usage",
					"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
			return null;
		}
	}

	private Object doAsk(Connection c) {
		boolean use = boolOpt(c, OPT_USE, true);
		String supports = stringOpt(c, OPT_SUPPORTS, GmcpModuleRegistry.DEFAULT_SUPPORTS);
		Processor p = c.getProcessor();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("GMCP handshake / discovery (honest report):\n");
		sb.append("  Use GMCP? ").append(use ? "on" : "off").append("\n");
		if (p != null) {
			sb.append("  Client Hello: ").append(p.getGmcpHello()).append("\n");
			GmcpModuleRegistry reg = p.getModuleRegistry();
			sb.append("  Last Supports.Set: ").append(reg.getLastSupportsSet()).append("\n");
			sb.append("  Enabled (we declare): ").append(join(reg.enabledTokens())).append("\n");
			ArrayList<String> nativeIds = new ArrayList<String>();
			for (GmcpModuleRegistry.ModuleInfo m : reg.nativeModules()) {
				nativeIds.add(m.id);
			}
			sb.append("  Native handlers: ").append(join(nativeIds)).append("\n");
			ArrayList<String> seen = reg.seenModules();
			sb.append("  Seen this session (server sent): ")
					.append(seen.isEmpty() ? "(none yet)" : join(seen)).append("\n");
		} else {
			sb.append("  Not connected — Supports string: ").append(supports).append("\n");
			sb.append("  Seen: (no session)\n");
		}
		sb.append("  Note: there is no universal \"list all server modules\" API.\n");
		sb.append("  Seen ≠ auto-enable. Manage: Options → Manage modules… or .gmcp enable …\n");
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doModules(Connection c) {
		Processor p = c.getProcessor();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		if (p == null) {
			String supports = stringOpt(c, OPT_SUPPORTS, GmcpModuleRegistry.DEFAULT_SUPPORTS);
			sb.append("GMCP modules (offline): ").append(supports).append("\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		GmcpModuleRegistry reg = p.getModuleRegistry();
		sb.append("Enabled: ").append(join(reg.enabledTokens())).append("\n");
		ArrayList<String> seen = reg.seenModules();
		sb.append("Seen:    ").append(seen.isEmpty() ? "(none)" : join(seen)).append("\n");
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doEnableDisable(Connection c, String rest, boolean enable) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("GMCP " + (enable ? "enable" : "disable"),
					".gmcp " + (enable ? "enable" : "disable") + " <Module> [Module…]"));
			return null;
		}
		String[] toks = rest.split("[,\\s]+");
		ArrayList<String> ids = new ArrayList<String>();
		for (String t : toks) {
			if (t.length() > 0) {
				ids.add(t);
			}
		}
		if (ids.isEmpty()) {
			c.sendDataToWindow(getErrorMessage("GMCP", "No module names given."));
			return null;
		}

		String current = stringOpt(c, OPT_SUPPORTS, GmcpModuleRegistry.DEFAULT_SUPPORTS);
		GmcpModuleRegistry reg = GmcpModuleRegistry.fromSupportsOption(current);
		Processor p = c.getProcessor();
		if (p != null) {
			// Prefer live registry so seen/catalog stay consistent
			reg = p.getModuleRegistry();
		}
		StringBuilder tokens = new StringBuilder();
		for (String id : ids) {
			reg.setEnabled(id, enable);
			if (tokens.length() > 0) {
				tokens.append(", ");
			}
			tokens.append("\"").append(id).append(" 1\"");
		}
		String supports = reg.toSupportsString();
		c.updateStringSetting(OPT_SUPPORTS, supports);

		if (p != null && boolOpt(c, OPT_USE, true)) {
			String verb = enable ? "Core.Supports.Add" : "Core.Supports.Remove";
			p.sendGmcpPacket(verb + " [ " + tokens.toString() + " ]");
		}

		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "GMCP " + (enable ? "enabled" : "disabled") + ": " + join(ids) + "\n"
				+ "Supports now: " + supports + "\n"
				+ (p != null ? "(live " + (enable ? "Add" : "Remove") + " sent)\n"
						: "(not connected — saved for next handshake)\n"));
		return null;
	}

	private Object doRenegotiate(Connection c) {
		if (!boolOpt(c, OPT_USE, true)) {
			c.sendDataToWindow(getErrorMessage("GMCP renegotiate", "Use GMCP? is off."));
			return null;
		}
		Processor p = c.getProcessor();
		if (p == null) {
			c.sendDataToWindow(getErrorMessage("GMCP renegotiate", "Not connected."));
			return null;
		}
		c.renegotiateGmcp();
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "GMCP Hello + Supports.Set re-sent.\n");
		return null;
	}

	private Object doStatus(Connection c) {
		boolean use = boolOpt(c, OPT_USE, true);
		boolean log = boolOpt(c, OPT_LOG, false);
		boolean feed = boolOpt(c, OPT_FEED, false);
		String supports = stringOpt(c, OPT_SUPPORTS,
				GmcpModuleRegistry.DEFAULT_SUPPORTS);
		Processor p = c.getProcessor();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("GMCP use=").append(use ? "on" : "off");
		sb.append("  log/sniff=").append(log ? "on" : "off");
		sb.append("  feed=").append(feed ? "on" : "off").append("\n");
		if (p != null) {
			sb.append("Status: ").append(c.getGmcpModuleStatus()).append("\n");
		}
		sb.append("Supports string: ").append(supports).append("\n");
		if (p != null) {
			sb.append("Hello: ").append(p.getGmcpHello()).append("\n");
		}
		sb.append("Negotiated processor: ").append(p != null ? "yes" : "no (not connected)").append("\n");
		sb.append("Enable under Options → Service → GMCP Options.\n");
		sb.append(sniffLogLocations(c));
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doFeed(Connection c, String rest) {
		BooleanOption opt = findBool(c, OPT_FEED);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("GMCP feed", "gmcp_feed option missing."));
			return null;
		}
		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (rest.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "GMCP window feed is " + (current ? "on" : "off") + ".\n"
					+ "Usage: .gmcp feed on | off\n"
					+ "Also: Options → Service → GMCP → Show GMCP in game window?\n");
			return null;
		}
		Boolean desired = parseOnOff(rest.split("\\s+")[0]);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("GMCP feed", ".gmcp feed on | off"));
			return null;
		}
		c.updateBooleanSetting(OPT_FEED, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "GMCP window feed " + (desired.booleanValue() ? "on" : "off")
				+ " (saved to this profile).\n");
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
					+ "Usage: .gmcp sniff on | off | tail [0-100]\n"
					+ sniffLogLocations(c));
			return null;
		}
		String[] toks = rest.split("\\s+");
		String first = toks[0].toLowerCase(Locale.US);
		if (first.equals("tail")) {
			int lines = 40;
			if (toks.length > 1) {
				try {
					lines = Integer.parseInt(toks[1]);
				} catch (NumberFormatException e) {
					c.sendDataToWindow(getErrorMessage("GMCP sniff tail",
							".gmcp sniff tail [0-100]"));
					return null;
				}
			}
			if (lines < 0) {
				lines = 0;
			}
			if (lines > 100) {
				lines = 100;
			}
			return doSniffTail(c, lines);
		}
		Boolean desired = parseOnOff(first);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("GMCP sniff",
					".gmcp sniff on | off | tail [0-100]"));
			return null;
		}
		c.updateBooleanSetting(OPT_LOG, desired.booleanValue());
		StringBuilder msg = new StringBuilder();
		msg.append("\n").append(Colorizer.getWhiteColor())
				.append("GMCP sniff/log ").append(desired.booleanValue() ? "on" : "off").append(".\n");
		if (desired.booleanValue()) {
			msg.append(sniffLogLocations(c));
		}
		c.sendDataToWindow(msg.toString());
		return null;
	}

	private Object doSniffTail(Connection c, int lines) {
		java.io.File logFile = BlowTorchLogger.getLogFile(c.getContext());
		StringBuilder out = new StringBuilder();
		out.append("\n").append(Colorizer.getWhiteColor());
		out.append("GMCP sniff tail (").append(lines).append(" lines)");
		if (logFile != null) {
			out.append(" from ").append(logFile.getAbsolutePath());
		}
		out.append(":\n");
		if (lines == 0) {
			out.append("(0 lines requested)\n");
			c.sendDataToWindow(out.toString());
			return null;
		}
		if (logFile == null || !logFile.isFile()) {
			out.append(Colorizer.getRedColor()).append("(log file missing)\n");
			c.sendDataToWindow(out.toString());
			return null;
		}
		java.util.ArrayList<String> gmcpLines = new java.util.ArrayList<String>();
		java.io.BufferedReader reader = null;
		try {
			reader = new java.io.BufferedReader(new java.io.InputStreamReader(
					new java.io.FileInputStream(logFile), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				String lower = line.toLowerCase(Locale.US);
				if (lower.contains("gmcp") || lower.contains("[gmcp]")) {
					gmcpLines.add(line);
					if (gmcpLines.size() > 500) {
						gmcpLines.remove(0);
					}
				}
			}
		} catch (Exception e) {
			out.append(Colorizer.getRedColor()).append("Read failed: ")
					.append(e.getMessage()).append("\n");
			c.sendDataToWindow(out.toString());
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception ignored) {
				}
			}
		}
		if (gmcpLines.isEmpty()) {
			out.append("(no GMCP lines in log yet — enable sniff, use GMCP, play until packets arrive)\n");
			c.sendDataToWindow(out.toString());
			return null;
		}
		int start = Math.max(0, gmcpLines.size() - lines);
		for (int i = start; i < gmcpLines.size(); i++) {
			out.append(gmcpLines.get(i)).append("\n");
		}
		c.sendDataToWindow(out.toString());
		return null;
	}

	/** Where sniff lines land (always error log; session log when enabled). */
	private static String sniffLogLocations(Connection c) {
		StringBuilder sb = new StringBuilder();
		sb.append("Writes to app error log");
		if (c != null && c.getContext() != null) {
			BlowTorchLogger.ensureLogFile(c.getContext());
			sb.append(":\n  ").append(BlowTorchLogger.getLogFile(c.getContext()).getAbsolutePath());
		} else {
			sb.append(" (files/logs/blowtorch2.log)");
		}
		sb.append("\n");
		sb.append("Also appended to the session log when Options → Service → Log Session to File? is on");
		if (c != null && c.getContext() != null && SessionLogger.isEnabled(c.getContext())) {
			java.io.File current = SessionLogger.getCurrentLogFile();
			if (current != null) {
				sb.append(":\n  ").append(current.getAbsolutePath());
			} else {
				sb.append(":\n  ").append(SessionLogger.getLogDirectory(c.getContext()).getAbsolutePath());
			}
		} else {
			sb.append(" (currently off).");
		}
		sb.append("\nView via Overflow → Crash report → Show log.\n");
		return sb.toString();
	}

	private Object doVersion(Connection c) {
		Processor p = c.getProcessor();
		String hello = (p != null) ? p.getGmcpHello()
				: "core.hello {\"client\":\"BlowTorch\",\"version\":\"(connect to resolve)\"}";
		String msg = "\n" + Colorizer.getWhiteColor()
				+ "BlowTorch GMCP client hello:\n"
				+ "  " + hello + "\n"
				+ "Telnet option: IAC SB GMCP (201) … IAC SE\n"
				+ "Native modules: Char.Login (password), Client.Media (sound/music).\n"
				+ "Typical supports: \"Char 1\", \"Room 1\", \"Core 1\", \"Char.Login 1\", \"Client.Media 1\"\n"
				+ "Manage: Options → Service → GMCP → Manage modules…\n";
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
					+ "Prefer: Options → Manage modules… or .gmcp enable / disable\n"
					+ "Then .gmcp renegotiate (or Apply & renegotiate).\n");
			return null;
		}
		String normalized = normalizeSupports(rest);
		c.updateStringSetting(OPT_SUPPORTS, normalized);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "GMCP supports set to: " + normalized + "\n"
				+ "Use .gmcp renegotiate or reconnect to push Supports.Set.\n");
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
		if (!boolOpt(c, OPT_USE, true)) {
			c.sendDataToWindow(getErrorMessage("GMCP send", "Use GMCP? is off (Options → Service → GMCP Options)."));
			return null;
		}
		c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_SENDGMCPDATA, rest));
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "GMCP send queued: " + rest + "\n");
		return null;
	}

	private static String join(ArrayList<String> list) {
		if (list == null || list.isEmpty()) {
			return "(none)";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(list.get(i));
		}
		return sb.toString();
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
			}
			sb.append("\"").append(mod).append(" ").append(ver).append("\"");
		}
		return sb.length() == 0
				? GmcpModuleRegistry.DEFAULT_SUPPORTS
				: sb.toString();
	}

	private static String helpText() {
		return "\n" + Colorizer.getWhiteColor()
				+ "GMCP (Generic Mud Communication Protocol) is an out-of-band telnet channel\n"
				+ "(option 201) for structured JSON-ish updates (vitals, room, media, login).\n"
				+ "On by default for new profiles. Servers differ — sniff if something looks off.\n\n"
				+ shortUsage()
				+ "Options → Service → GMCP: Use GMCP?, Manage modules…, Log GMCP?\n"
				+ "Native: Char.Login uses launcher account login/password; Client.Media plays sound/music.\n"
				+ "Lua: Send_GMCP_Packet(\"module {…}\")  Triggers: pattern %module.path\n";
	}

	private static String shortUsage() {
		return "Usage:\n"
				+ "  .gmcp                 — this help\n"
				+ "  .gmcp ask|handshake   — Hello / enabled / native / seen (honest)\n"
				+ "  .gmcp modules         — enabled vs seen\n"
				+ "  .gmcp enable|disable  — toggle modules (+ live Add/Remove)\n"
				+ "  .gmcp renegotiate     — re-send Hello + Supports.Set\n"
				+ "  .gmcp status          — current flags\n"
				+ "  .gmcp sniff [on|off]  — log handshake/packets to app error log\n"
				+ "  .gmcp sniff tail [N]  — show last N GMCP log lines in-game (0–100, default 40)\n"
				+ "  .gmcp feed [on|off]   — live IN/OUT GMCP lines in the mud window\n"
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
