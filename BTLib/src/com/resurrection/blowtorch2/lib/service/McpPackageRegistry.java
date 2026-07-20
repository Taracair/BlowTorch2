package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP package catalog / enabled set (mirrors GmcpModuleRegistry for Mud Client Protocol).
 * Supports string format: {@code "pkg min max"} or {@code "pkg ver"} (min=max).
 */
public final class McpPackageRegistry {

	public enum Kind {
		NATIVE, CATALOG, SEEN
	}

	public static final class PackageInfo {
		public final String id;
		public final String minVersion;
		public final String maxVersion;
		public final String summary;
		public final Kind kind;
		public final boolean enabledByDefault;

		public PackageInfo(String id, String minVersion, String maxVersion, String summary,
				Kind kind, boolean enabledByDefault) {
			this.id = id;
			this.minVersion = minVersion != null ? minVersion : "1.0";
			this.maxVersion = maxVersion != null ? maxVersion : this.minVersion;
			this.summary = summary != null ? summary : "";
			this.kind = kind != null ? kind : Kind.CATALOG;
			this.enabledByDefault = enabledByDefault;
		}
	}

	public static final String DEFAULT_PACKAGES =
			"\"mcp-negotiate 1.0 2.0\", \"dns-org-hellmoo-status 1.0\"";

	private static final Pattern TOKEN = Pattern.compile(
			"\"\\s*([^\"\\s]+)\\s+([^\"\\s]+)(?:\\s+([^\"\\s]+))?\\s*\"|([^\\s,]+)\\s+([^\\s,]+)(?:\\s+([^\\s,]+))?");

	private final LinkedHashMap<String, PackageInfo> mCatalog = new LinkedHashMap<String, PackageInfo>();
	private final LinkedHashSet<String> mEnabled = new LinkedHashSet<String>();
	private final LinkedHashSet<String> mSeen = new LinkedHashSet<String>();
	private final LinkedHashMap<String, String> mNegotiatedVersion = new LinkedHashMap<String, String>();

	public McpPackageRegistry() {
		seedCatalog();
		setEnabledFromPackagesString(DEFAULT_PACKAGES);
	}

	public static McpPackageRegistry fromPackagesOption(String packages) {
		McpPackageRegistry r = new McpPackageRegistry();
		r.setEnabledFromPackagesString(packages != null ? packages : DEFAULT_PACKAGES);
		return r;
	}

	private void seedCatalog() {
		addCat(new PackageInfo("mcp-negotiate", "1.0", "2.0",
				"Required MCP package negotiation", Kind.NATIVE, true));
		addCat(new PackageInfo("dns-org-hellmoo-status", "1.0", "1.0",
				"HellMOO / SamsaraMoo vitals bar (hp, thirst, hunger, stress)", Kind.NATIVE, true));
		addCat(new PackageInfo("mcp-cord", "1.0", "1.0",
				"Optional multi-channel cords", Kind.CATALOG, false));
		addCat(new PackageInfo("dns-org-mud-moo-simpleedit", "1.0", "1.0",
				"Remote text editor (MOO simpleedit)", Kind.CATALOG, false));
		addCat(new PackageInfo("dns-com-awns-displayurl", "1.0", "1.0",
				"Open a URL in the browser", Kind.CATALOG, false));
		addCat(new PackageInfo("dns-com-awns-ping", "1.0", "1.0",
				"Latency ping", Kind.CATALOG, false));
		addCat(new PackageInfo("dns-com-vmoo-client", "1.0", "1.0",
				"Client identity / screen size (VMoo)", Kind.CATALOG, false));
	}

	private void addCat(PackageInfo p) {
		mCatalog.put(normKey(p.id), p);
	}

	public static String normKey(String id) {
		return id == null ? "" : id.trim().toLowerCase(Locale.US);
	}

	public void setEnabledFromPackagesString(String raw) {
		mEnabled.clear();
		if (raw == null) {
			raw = "";
		}
		Matcher m = TOKEN.matcher(raw);
		boolean any = false;
		while (m.find()) {
			String id = m.group(1) != null ? m.group(1) : m.group(4);
			String min = m.group(2) != null ? m.group(2) : m.group(5);
			String max = m.group(3) != null ? m.group(3) : m.group(6);
			if (id == null || id.length() == 0) {
				continue;
			}
			any = true;
			if (max == null || max.length() == 0) {
				max = min;
			}
			ensureKnown(id, min, max, Kind.CATALOG);
			mEnabled.add(normKey(id));
			PackageInfo existing = mCatalog.get(normKey(id));
			if (existing != null) {
				mCatalog.put(normKey(id), new PackageInfo(existing.id, min, max,
						existing.summary, existing.kind, existing.enabledByDefault));
			}
		}
		if (!any) {
			for (PackageInfo p : mCatalog.values()) {
				if (p.enabledByDefault) {
					mEnabled.add(normKey(p.id));
				}
			}
		}
		// Always keep mcp-negotiate if anything is enabled
		if (!mEnabled.isEmpty()) {
			mEnabled.add(normKey("mcp-negotiate"));
		}
	}

	public String toPackagesString() {
		StringBuilder sb = new StringBuilder();
		for (String key : mEnabled) {
			PackageInfo p = mCatalog.get(key);
			if (p == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			if (p.minVersion.equals(p.maxVersion)) {
				sb.append("\"").append(p.id).append(" ").append(p.minVersion).append("\"");
			} else {
				sb.append("\"").append(p.id).append(" ").append(p.minVersion)
						.append(" ").append(p.maxVersion).append("\"");
			}
		}
		return sb.length() == 0 ? DEFAULT_PACKAGES : sb.toString();
	}

	public void setEnabled(String id, boolean enabled) {
		if (id == null || id.length() == 0) {
			return;
		}
		ensureKnown(id, "1.0", "1.0", Kind.CATALOG);
		String key = normKey(id);
		if (enabled) {
			mEnabled.add(key);
			mEnabled.add(normKey("mcp-negotiate"));
		} else {
			if (!"mcp-negotiate".equals(key)) {
				mEnabled.remove(key);
			}
		}
	}

	public boolean isEnabled(String id) {
		return mEnabled.contains(normKey(id));
	}

	public void noteSeen(String id) {
		if (id == null || id.length() == 0) {
			return;
		}
		ensureKnown(id, "1.0", "1.0", Kind.SEEN);
		mSeen.add(normKey(id));
	}

	public void setNegotiatedVersion(String id, String version) {
		if (id == null) {
			return;
		}
		mNegotiatedVersion.put(normKey(id), version != null ? version : "");
	}

	public String getNegotiatedVersion(String id) {
		String v = mNegotiatedVersion.get(normKey(id));
		return v != null ? v : "";
	}

	public void clearSession() {
		mSeen.clear();
		mNegotiatedVersion.clear();
	}

	private void ensureKnown(String id, String min, String max, Kind kind) {
		String key = normKey(id);
		if (!mCatalog.containsKey(key)) {
			mCatalog.put(key, new PackageInfo(id, min, max, "User / seen package", kind, false));
		}
	}

	public ArrayList<PackageInfo> nativePackages() {
		return filterKind(Kind.NATIVE);
	}

	public ArrayList<PackageInfo> catalogPackages() {
		return filterKind(Kind.CATALOG);
	}

	private ArrayList<PackageInfo> filterKind(Kind kind) {
		ArrayList<PackageInfo> out = new ArrayList<PackageInfo>();
		for (PackageInfo p : mCatalog.values()) {
			if (p.kind == kind) {
				out.add(p);
			}
		}
		return out;
	}

	public ArrayList<String> enabledTokens() {
		ArrayList<String> out = new ArrayList<String>();
		for (String key : mEnabled) {
			PackageInfo p = mCatalog.get(key);
			if (p != null) {
				out.add(p.id + " " + p.minVersion
						+ (p.minVersion.equals(p.maxVersion) ? "" : ("-" + p.maxVersion)));
			}
		}
		return out;
	}

	public ArrayList<String> seenPackages() {
		ArrayList<String> out = new ArrayList<String>();
		for (String key : mSeen) {
			PackageInfo p = mCatalog.get(key);
			out.add(p != null ? p.id : key);
		}
		Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	public ArrayList<PackageInfo> enabledPackageInfos() {
		ArrayList<PackageInfo> out = new ArrayList<PackageInfo>();
		for (String key : mEnabled) {
			PackageInfo p = mCatalog.get(key);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	public String statusLine() {
		return "enabled=" + mEnabled.size() + " seen=" + mSeen.size()
				+ " negotiated=" + mNegotiatedVersion.size();
	}

	public PackageInfo get(String id) {
		return mCatalog.get(normKey(id));
	}

	public Map<String, PackageInfo> all() {
		return Collections.unmodifiableMap(mCatalog);
	}
}
