package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catalog + enabled/seen state for GMCP modules. Enabled list generates
 * {@code gmcp_supports}; seen modules come from this session's inbound traffic.
 */
public final class GmcpModuleRegistry {

	public static final String DEFAULT_SUPPORTS =
			"\"Char 1\", \"Room 1\", \"Core 1\", \"Char.Login 1\", \"Client.Media 1\"";

	private static final Pattern ENTRY = Pattern.compile("\"([^\"]+)\"");

	public enum Kind {
		NATIVE,
		CATALOG,
		SEEN
	}

	public static final class ModuleInfo {
		public final String id;
		public final int version;
		public final String summary;
		public final Kind kind;
		public final boolean nativeHandler;

		public ModuleInfo(String id, int version, String summary, Kind kind, boolean nativeHandler) {
			this.id = id;
			this.version = version;
			this.summary = summary != null ? summary : "";
			this.kind = kind;
			this.nativeHandler = nativeHandler;
		}

		public String supportToken() {
			return id + " " + version;
		}
	}

	private final LinkedHashMap<String, ModuleInfo> catalog = new LinkedHashMap<String, ModuleInfo>();
	private final LinkedHashSet<String> enabled = new LinkedHashSet<String>();
	private final TreeSet<String> seen = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
	private String lastSupportsSet = "";

	public GmcpModuleRegistry() {
		seedCatalog();
		setEnabledFromSupportsString(DEFAULT_SUPPORTS);
	}

	private void seedCatalog() {
		addNative("Core", 1, "Hello / Supports / Ping");
		addNative("Char", 1, "Character vitals and status");
		addNative("Char.Login", 1, "GMCP password login (uses launcher account)");
		addNative("Client.Media", 1, "Sound and music playback");
		addNative("Room", 1, "Room info for mappers");
		addCatalog("Comm", 1, "Channels / communication");
		addCatalog("Group", 1, "Party / group data");
		addCatalog("Char.Items", 1, "Inventory / items");
		addCatalog("Char.Skills", 1, "Skills");
		addCatalog("External.Discord", 1, "Discord rich presence");
		addCatalog("Config", 1, "Client config exchange");
		addCatalog("MSDP", 1, "MSDP bridge over GMCP");
	}

	private void addNative(String id, int ver, String summary) {
		catalog.put(normKey(id), new ModuleInfo(canonicalId(id), ver, summary, Kind.NATIVE, true));
	}

	private void addCatalog(String id, int ver, String summary) {
		catalog.put(normKey(id), new ModuleInfo(canonicalId(id), ver, summary, Kind.CATALOG, false));
	}

	public synchronized ArrayList<ModuleInfo> allKnownModules() {
		ArrayList<ModuleInfo> out = new ArrayList<ModuleInfo>(catalog.values());
		for (String s : seen) {
			String key = normKey(s);
			if (!catalog.containsKey(key)) {
				out.add(new ModuleInfo(canonicalId(s), 1, "Seen from this server", Kind.SEEN, false));
			}
		}
		return out;
	}

	public synchronized ArrayList<ModuleInfo> nativeModules() {
		ArrayList<ModuleInfo> out = new ArrayList<ModuleInfo>();
		for (ModuleInfo m : catalog.values()) {
			if (m.kind == Kind.NATIVE) {
				out.add(m);
			}
		}
		return out;
	}

	public synchronized ArrayList<ModuleInfo> catalogModules() {
		ArrayList<ModuleInfo> out = new ArrayList<ModuleInfo>();
		for (ModuleInfo m : catalog.values()) {
			if (m.kind == Kind.CATALOG) {
				out.add(m);
			}
		}
		return out;
	}

	public synchronized ArrayList<String> seenModules() {
		return new ArrayList<String>(seen);
	}

	public synchronized boolean isEnabled(String id) {
		return coversModule(id);
	}

	/**
	 * True if {@code id} itself is in Supports.Set, or an ancestor package is
	 * (e.g. {@code Char} covers {@code Char.Base} / {@code Char.Vitals}).
	 */
	public synchronized boolean coversModule(String id) {
		if (id == null || id.length() == 0) {
			return false;
		}
		String key = normKey(id);
		if (enabled.contains(key)) {
			return true;
		}
		// Walk parents: Char.Vitals → Char ; Room.Info → Room
		int dot = key.lastIndexOf('.');
		while (dot > 0) {
			String parent = key.substring(0, dot);
			if (enabled.contains(parent)) {
				return true;
			}
			dot = parent.lastIndexOf('.');
		}
		return false;
	}

	public synchronized void setEnabled(String id, boolean on) {
		String key = normKey(id);
		if (on) {
			enabled.add(key);
			ensureKnown(id);
		} else {
			enabled.remove(key);
		}
	}

	public synchronized void enableMany(String[] ids) {
		if (ids == null) {
			return;
		}
		for (String id : ids) {
			if (id != null && id.trim().length() > 0) {
				setEnabled(id.trim(), true);
			}
		}
	}

	public synchronized void disableMany(String[] ids) {
		if (ids == null) {
			return;
		}
		for (String id : ids) {
			if (id != null && id.trim().length() > 0) {
				setEnabled(id.trim(), false);
			}
		}
	}

	/**
	 * Record inbound module traffic. Returns a newly seen module id worth
	 * suggesting (two-level when available), or null if already known / empty.
	 * Never auto-enables.
	 */
	public synchronized String noteSeen(String modulePath) {
		if (modulePath == null || modulePath.length() == 0) {
			return null;
		}
		String root = modulePath.trim();
		int sp = root.indexOf(' ');
		if (sp > 0) {
			root = root.substring(0, sp);
		}
		// Keep two levels when useful: Char.Login, Client.Media, else top segment family
		String[] parts = root.split("\\.");
		String interesting = null;
		boolean newlyInteresting = false;
		if (parts.length >= 2) {
			String two = parts[0] + "." + parts[1];
			if (seen.add(two)) {
				newlyInteresting = true;
				interesting = canonicalId(two);
			}
		}
		if (parts.length >= 1 && parts[0].length() > 0) {
			if (seen.add(parts[0]) && interesting == null) {
				newlyInteresting = true;
				interesting = canonicalId(parts[0]);
			}
		}
		if (!newlyInteresting || interesting == null) {
			return null;
		}
		if (isEnabled(interesting)) {
			return null;
		}
		return interesting;
	}

	public synchronized void clearSeen() {
		seen.clear();
	}

	public synchronized void setLastSupportsSet(String payload) {
		lastSupportsSet = payload != null ? payload : "";
	}

	public synchronized String getLastSupportsSet() {
		return lastSupportsSet;
	}

	public synchronized String toSupportsString() {
		StringBuilder sb = new StringBuilder();
		for (String key : enabled) {
			ModuleInfo m = catalog.get(key);
			String token = (m != null) ? m.supportToken() : (canonicalId(key) + " 1");
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append("\"").append(token).append("\"");
		}
		return sb.length() == 0 ? DEFAULT_SUPPORTS : sb.toString();
	}

	public synchronized ArrayList<String> enabledTokens() {
		ArrayList<String> out = new ArrayList<String>();
		for (String key : enabled) {
			ModuleInfo m = catalog.get(key);
			out.add(m != null ? m.supportToken() : (canonicalId(key) + " 1"));
		}
		return out;
	}

	public synchronized void setEnabledFromSupportsString(String supports) {
		enabled.clear();
		if (supports == null || supports.trim().length() == 0) {
			supports = DEFAULT_SUPPORTS;
		}
		Matcher m = ENTRY.matcher(supports);
		while (m.find()) {
			String token = m.group(1).trim();
			if (token.length() == 0) {
				continue;
			}
			String id = token;
			int ver = 1;
			int sp = token.lastIndexOf(' ');
			if (sp > 0) {
				id = token.substring(0, sp).trim();
				try {
					ver = Integer.parseInt(token.substring(sp + 1).trim());
				} catch (NumberFormatException ignored) {
					ver = 1;
				}
			}
			ensureKnown(id, ver);
			enabled.add(normKey(id));
		}
		if (enabled.isEmpty()) {
			setEnabledFromSupportsString(DEFAULT_SUPPORTS);
		}
	}

	private void ensureKnown(String id) {
		ensureKnown(id, 1);
	}

	private void ensureKnown(String id, int ver) {
		String key = normKey(id);
		if (!catalog.containsKey(key)) {
			catalog.put(key, new ModuleInfo(canonicalId(id), ver, "Custom / from supports", Kind.CATALOG, false));
		}
	}

	public synchronized String statusLine() {
		int n = enabled.size();
		int s = seen.size();
		StringBuilder sb = new StringBuilder();
		sb.append(n).append(" module").append(n == 1 ? "" : "s");
		if (s > 0) {
			sb.append(" · seen ").append(s);
			// Hint at a couple of interesting seen modules
			int shown = 0;
			for (String name : seen) {
				if (name.indexOf('.') < 0) {
					continue;
				}
				if (shown == 0) {
					sb.append(" (");
				} else {
					sb.append(", ");
				}
				sb.append(name);
				shown++;
				if (shown >= 2) {
					break;
				}
			}
			if (shown > 0) {
				sb.append(")");
			}
		}
		return sb.toString();
	}

	public static String normKey(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.US);
	}

	/** Prefer catalog casing when known. */
	public String canonicalId(String id) {
		if (id == null) {
			return "";
		}
		String key = normKey(id);
		ModuleInfo m = catalog.get(key);
		if (m != null) {
			return m.id;
		}
		// Title-case segments: char.login → Char.Login
		String[] parts = id.trim().split("\\.");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append('.');
			}
			String p = parts[i];
			if (p.length() == 0) {
				continue;
			}
			sb.append(Character.toUpperCase(p.charAt(0)));
			if (p.length() > 1) {
				sb.append(p.substring(1));
			}
		}
		return sb.toString();
	}

	/** Per-connection registry held on Processor / Connection. */
	public static GmcpModuleRegistry fromSupportsOption(String supports) {
		GmcpModuleRegistry r = new GmcpModuleRegistry();
		r.setEnabledFromSupportsString(supports);
		return r;
	}
}
