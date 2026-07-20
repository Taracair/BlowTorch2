package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Handler;
import android.util.Log;

import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

/**
 * Mud Client Protocol 2.1 engine: line filter, handshake, negotiate, multiline,
 * native dns-org-hellmoo-status, sniff/feed.
 */
public final class McpEngine {

	private static final String TAG = "McpEngine";
	private static final Pattern KV = Pattern.compile(
			"([A-Za-z0-9_.*-]+):\\s*(?:\"([^\"]*)\"|(\\S+))");

	public interface Sink {
		void sendNetworkLine(String line);
		void notifyWindow(String message);
		String getEncoding();
		android.content.Context getContext();
		String getDisplayName();
	}

	private final Sink mSink;
	private final Handler mHandler;
	private final McpPackageRegistry mRegistry = new McpPackageRegistry();
	private final StringBuilder mPending = new StringBuilder();
	private final LinkedHashMap<String, MultilineState> mMultiline =
			new LinkedHashMap<String, MultilineState>();
	private final LinkedHashMap<String, String> mStatusCache =
			new LinkedHashMap<String, String>();
	private final ArrayList<String> mRecent = new ArrayList<String>();

	private boolean mUse = false;
	private boolean mLog = false;
	private boolean mFeed = false;
	private boolean mOmitFromOutput = true;
	private boolean mAutoNegotiate = true;
	private String mAuthKey = "";
	private boolean mHandshaken = false;
	private boolean mNegotiateDone = false;
	private String mServerMin = "";
	private String mServerMax = "";

	private static final class MultilineState {
		String messageName;
		String dataTag;
		HashMap<String, String> args = new HashMap<String, String>();
		HashMap<String, StringBuilder> multi = new HashMap<String, StringBuilder>();
		HashMap<String, Boolean> isMulti = new HashMap<String, Boolean>();
	}

	public McpEngine(Sink sink, Handler connectionHandler) {
		mSink = sink;
		mHandler = connectionHandler;
	}

	public McpPackageRegistry getRegistry() {
		return mRegistry;
	}

	public void setUse(boolean use) {
		mUse = use;
		if (!use) {
			resetSession();
		}
	}

	public boolean isUse() {
		return mUse;
	}

	public void setLog(boolean log) {
		mLog = log;
	}

	public void setFeed(boolean feed) {
		mFeed = feed;
	}

	public void setOmitFromOutput(boolean omit) {
		mOmitFromOutput = omit;
	}

	public void setAutoNegotiate(boolean auto) {
		mAutoNegotiate = auto;
	}

	public void setPackagesFromOption(String packages) {
		mRegistry.setEnabledFromPackagesString(packages);
	}

	public void resetSession() {
		mPending.setLength(0);
		mAuthKey = "";
		mHandshaken = false;
		mNegotiateDone = false;
		mServerMin = "";
		mServerMax = "";
		mMultiline.clear();
		mRegistry.clearSession();
	}

	public boolean isHandshaken() {
		return mHandshaken;
	}

	public String getAuthKey() {
		return mAuthKey;
	}

	public Map<String, String> getStatusCache() {
		return new LinkedHashMap<String, String>(mStatusCache);
	}

	public ArrayList<String> getRecentMessages() {
		return new ArrayList<String>(mRecent);
	}

	/**
	 * Filter incoming bytes: strip/handle MCP OOB lines, return in-band remainder.
	 */
	public byte[] filterIncoming(byte[] raw) throws UnsupportedEncodingException {
		if (raw == null || raw.length == 0) {
			return raw;
		}
		String enc = mSink.getEncoding() != null ? mSink.getEncoding() : "UTF-8";
		String chunk;
		try {
			chunk = new String(raw, enc);
		} catch (UnsupportedEncodingException e) {
			chunk = new String(raw, "ISO-8859-1");
			enc = "ISO-8859-1";
		}
		if (!mUse) {
			return raw;
		}
		mPending.append(chunk);
		StringBuilder out = new StringBuilder();
		int start = 0;
		for (int i = 0; i < mPending.length(); i++) {
			char ch = mPending.charAt(i);
			if (ch == '\n') {
				String line = mPending.substring(start, i);
				if (line.endsWith("\r")) {
					line = line.substring(0, line.length() - 1);
				}
				start = i + 1;
				String kept = handleNetworkLine(line);
				if (kept != null) {
					out.append(kept).append('\n');
				}
			}
		}
		if (start > 0) {
			mPending.delete(0, start);
		}
		// Cap runaway pending
		if (mPending.length() > 256 * 1024) {
			out.append(mPending);
			mPending.setLength(0);
		}
		return out.toString().getBytes(enc);
	}

	private String handleNetworkLine(String line) {
		if (line.startsWith("#$\"")) {
			// Quoted in-band
			return line.substring(3);
		}
		if (!line.startsWith("#$#")) {
			return line;
		}
		// Out-of-band MCP
		handleMcpLine(line);
		return mOmitFromOutput ? null : line;
	}

	private void handleMcpLine(String line) {
		logDir("IN", line);
		remember(line);
		if (line.startsWith("#$#* ")) {
			handleContinuation(line.substring(5).trim());
			return;
		}
		if (line.startsWith("#$#:")) {
			String tag = line.length() > 4 ? line.substring(4).trim() : "";
			finishMultiline(tag);
			return;
		}
		// #$#message ...
		String body = line.substring(3).trim();
		if (body.length() == 0) {
			return;
		}
		int sp = indexOfWhitespace(body);
		String name = sp < 0 ? body : body.substring(0, sp);
		String rest = sp < 0 ? "" : body.substring(sp).trim();
		String lower = name.toLowerCase(Locale.US);

		if ("mcp".equals(lower)) {
			onMcpHello(rest);
			return;
		}

		HashMap<String, String> args = parseArgs(rest);
		boolean hasMulti = false;
		for (String k : new ArrayList<String>(args.keySet())) {
			if (k.endsWith("*")) {
				hasMulti = true;
				break;
			}
		}
		if (hasMulti) {
			String tag = args.get("_data-tag");
			if (tag == null) {
				tag = args.get("_data-tag".toLowerCase(Locale.US));
			}
			// keys may be mixed case
			for (Map.Entry<String, String> e : args.entrySet()) {
				if ("_data-tag".equalsIgnoreCase(e.getKey())) {
					tag = e.getValue();
				}
			}
			if (tag == null || tag.length() == 0) {
				return;
			}
			MultilineState st = new MultilineState();
			st.messageName = name;
			st.dataTag = tag;
			for (Map.Entry<String, String> e : args.entrySet()) {
				String k = e.getKey();
				if (k.endsWith("*")) {
					String base = k.substring(0, k.length() - 1);
					st.isMulti.put(base, Boolean.TRUE);
					st.multi.put(base, new StringBuilder());
					st.args.put(base, "");
				} else if (!"_data-tag".equalsIgnoreCase(k)) {
					st.args.put(k, e.getValue());
				}
			}
			mMultiline.put(tag, st);
			return;
		}

		dispatchMessage(name, args);
	}

	private void handleContinuation(String rest) {
		int sp = indexOfWhitespace(rest);
		if (sp < 0) {
			return;
		}
		String tag = rest.substring(0, sp).trim();
		String kv = rest.substring(sp).trim();
		MultilineState st = mMultiline.get(tag);
		if (st == null) {
			return;
		}
		int colon = kv.indexOf(':');
		if (colon < 0) {
			return;
		}
		String key = kv.substring(0, colon).trim();
		String val = kv.substring(colon + 1);
		if (val.startsWith(" ")) {
			val = val.substring(1);
		}
		StringBuilder sb = st.multi.get(key);
		if (sb == null) {
			sb = new StringBuilder();
			st.multi.put(key, sb);
			st.isMulti.put(key, Boolean.TRUE);
		}
		if (sb.length() > 0) {
			sb.append('\n');
		}
		sb.append(val);
	}

	private void finishMultiline(String tag) {
		MultilineState st = mMultiline.remove(tag);
		if (st == null) {
			return;
		}
		for (Map.Entry<String, StringBuilder> e : st.multi.entrySet()) {
			st.args.put(e.getKey(), e.getValue().toString());
		}
		dispatchMessage(st.messageName, st.args);
	}

	private void onMcpHello(String rest) {
		HashMap<String, String> args = parseArgs(rest);
		if (args.containsKey("authentication-key")) {
			// Client hello echo or unexpected — ignore auth from server side of mcp
		}
		String ver = first(args, "version");
		String to = first(args, "to");
		if (ver != null) {
			mServerMin = ver;
		}
		if (to != null) {
			mServerMax = to;
		} else if (ver != null) {
			mServerMax = ver;
		}
		// Overlap with 2.1 / 1.0
		if (!versionOverlaps(mServerMin, mServerMax, "1.0", "2.1")) {
			logDir("NEG", "MCP version mismatch server=" + mServerMin + ".." + mServerMax);
			return;
		}
		if (mAuthKey.length() == 0) {
			mAuthKey = generateAuthKey();
		}
		mHandshaken = true;
		sendRaw("#$#mcp authentication-key: " + mAuthKey + " version: 1.0 to: 2.1");
		if (mAutoNegotiate) {
			sendNegotiate();
		}
	}

	public void sendNegotiate() {
		if (!mUse || !mHandshaken) {
			return;
		}
		for (McpPackageRegistry.PackageInfo p : mRegistry.enabledPackageInfos()) {
			sendRaw("#$#mcp-negotiate-can " + mAuthKey
					+ " package: " + p.id
					+ " min-version: " + p.minVersion
					+ " max-version: " + p.maxVersion);
		}
		sendRaw("#$#mcp-negotiate-end " + mAuthKey);
		mNegotiateDone = true;
		logDir("NEG", "mcp-negotiate-end sent (" + mRegistry.enabledTokens().size() + " packages)");
	}

	public void renegotiate() {
		if (!mUse) {
			return;
		}
		if (!mHandshaken) {
			notify("MCP not handshaken yet — wait for server #$#mcp, or reconnect.");
			return;
		}
		mRegistry.clearSession();
		mNegotiateDone = false;
		sendNegotiate();
		notify("MCP packages re-negotiated.");
	}

	private void dispatchMessage(String name, HashMap<String, String> args) {
		String lower = name.toLowerCase(Locale.US);
		String key = first(args, "authentication-key");
		// After handshake, server messages carry auth key as second token already stripped
		// into name parsing — auth is the first "bare" token before k:v pairs.
		// Our parseArgs only gets k:v; auth key is separate in message format:
		// #$#msgname AUTHKEY key: val
		// So we need to re-parse: actually parseArgs from rest after name may start with auth.

		// Re-check: handleMcpLine passed `rest` after message name into parseArgs for
		// non-mcp messages — but auth key is NOT key:value, it's a bare word.
		// Fix: extract auth from args map if we stored it, OR rework parse.

		// For messages we already routed mcp hello. For others, `args` may be wrong.
		// Looking at handleMcpLine for non-multi: parseArgs(rest) where rest is after name.
		// Spec: #$#message-name authkey key: value
		// So first token of rest is auth key if not key:.

		// We'll fix dispatch by accepting auth via special "_auth" if present.
		String auth = args.remove("_auth");
		if (mHandshaken && mAuthKey.length() > 0) {
			if (auth != null && !auth.equals(mAuthKey)) {
				logDir("DROP", "bad auth on " + name);
				return;
			}
			// If auth missing on server message after handshake, still try (some servers omit on cord)
		}

		if (lower.startsWith("mcp-negotiate-can")) {
			String pkg = first(args, "package");
			String min = first(args, "min-version");
			String max = first(args, "max-version");
			if (pkg != null) {
				mRegistry.noteSeen(pkg);
				String picked = pickVersion(min, max,
						mRegistry.get(pkg) != null ? mRegistry.get(pkg).minVersion : "1.0",
						mRegistry.get(pkg) != null ? mRegistry.get(pkg).maxVersion : "1.0");
				if (picked != null) {
					mRegistry.setNegotiatedVersion(pkg, picked);
				}
			}
			return;
		}
		if (lower.equals("mcp-negotiate-end")) {
			mNegotiateDone = true;
			return;
		}

		// Derive package from message name (dns-org-hellmoo-status-update → package dns-org-hellmoo-status)
		String pkgGuess = packageFromMessage(name);
		if (pkgGuess != null) {
			mRegistry.noteSeen(pkgGuess);
		}

		if (lower.equals("dns-org-hellmoo-status-update")
				|| lower.endsWith("-status-update")) {
			onHellmooStatus(args);
			return;
		}
		if (lower.equals("dns-com-awns-displayurl")
				|| lower.equals("dns-com-awns-display-url")) {
			String url = first(args, "url");
			if (url != null && url.length() > 0) {
				notify("\n" + Colorizer.getWhiteColor() + "[MCP URL] " + url + "\n");
			}
			return;
		}
		// Generic: feed already logged; keep cache of last args per message
		mStatusCache.put("last." + name, args.toString());
	}

	/** Parse rest that may start with bare auth key then k:v pairs. */
	private HashMap<String, String> parseArgs(String rest) {
		HashMap<String, String> map = new HashMap<String, String>();
		if (rest == null || rest.length() == 0) {
			return map;
		}
		String work = rest.trim();
		// Bare auth key if first token has no colon before space
		if (work.length() > 0 && !work.startsWith("\"")) {
			int sp = indexOfWhitespace(work);
			String firstTok = sp < 0 ? work : work.substring(0, sp);
			if (firstTok.indexOf(':') < 0) {
				map.put("_auth", firstTok);
				work = sp < 0 ? "" : work.substring(sp).trim();
			}
		}
		Matcher m = KV.matcher(work);
		while (m.find()) {
			String k = m.group(1);
			String v = m.group(2) != null ? m.group(2) : m.group(3);
			if (k != null) {
				map.put(k, v != null ? v : "");
			}
		}
		return map;
	}

	private void onHellmooStatus(HashMap<String, String> args) {
		putStatus("hp", first(args, "hp"));
		putStatus("maxhp", first(args, "maxhp"));
		putStatus("thirst", first(args, "thirst"));
		putStatus("hunger", first(args, "hunger"));
		putStatus("stress", first(args, "stress"));
		String summary = "HP " + nz(mStatusCache.get("hp")) + "/" + nz(mStatusCache.get("maxhp"))
				+ "  thirst " + nz(mStatusCache.get("thirst"))
				+ "  hunger " + nz(mStatusCache.get("hunger"))
				+ "  stress " + nz(mStatusCache.get("stress"));
		mStatusCache.put("summary", summary);
		if (mFeed) {
			notify("\n" + Colorizer.getBrightCyanColor() + "[MCP status] " + summary
					+ Colorizer.getWhiteColor() + "\n");
		}
	}

	private void putStatus(String k, String v) {
		if (v != null) {
			mStatusCache.put(k, v);
		}
	}

	private static String nz(String s) {
		return s != null ? s : "?";
	}

	public void sendRaw(String line) {
		if (line == null) {
			return;
		}
		logDir("OUT", line);
		remember(line);
		mSink.sendNetworkLine(line.endsWith("\n") ? line : (line + "\n"));
	}

	/** Send a named MCP message with auth key and simple args (key → value). */
	public void sendMessage(String name, Map<String, String> args) {
		if (!mUse) {
			notify("Use MCP? is off.");
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("#$#").append(name);
		if (mAuthKey.length() > 0) {
			sb.append(' ').append(mAuthKey);
		}
		if (args != null) {
			for (Map.Entry<String, String> e : args.entrySet()) {
				sb.append(' ').append(e.getKey()).append(": ");
				String v = e.getValue() != null ? e.getValue() : "";
				if (v.indexOf(' ') >= 0 || v.indexOf('"') >= 0) {
					sb.append('"').append(v.replace("\"", "")).append('"');
				} else {
					sb.append(v);
				}
			}
		}
		sendRaw(sb.toString());
	}

	public String statusReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("use=").append(mUse ? "on" : "off");
		sb.append(" handshaken=").append(mHandshaken);
		sb.append(" negotiate=").append(mNegotiateDone ? "done" : "pending");
		sb.append(" auth=").append(mAuthKey.length() > 0 ? "(set)" : "(none)");
		sb.append(" server=").append(mServerMin).append("..").append(mServerMax);
		sb.append(" ").append(mRegistry.statusLine());
		return sb.toString();
	}

	private void logDir(String dir, String payload) {
		if (mLog) {
			String line = "[MCP] " + dir + " " + payload;
			Log.i(TAG, line);
			try {
				BlowTorchLogger.logError(mSink.getContext(), "MCP", dir + " " + payload);
			} catch (Exception ignored) {
			}
			try {
				SessionLogger.appendMarker(mSink.getContext(), mSink.getDisplayName(),
						"MCP " + dir + " " + redact(payload));
			} catch (Exception ignored) {
			}
		}
		if (mFeed && mHandler != null) {
			final String msg = "\n" + Colorizer.getBrightCyanColor() + "[MCP " + dir + "] "
					+ redact(payload) + Colorizer.getWhiteColor() + "\n";
			mHandler.sendMessageDelayed(
					mHandler.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, msg), 1);
		}
	}

	private void remember(String line) {
		mRecent.add(redact(line));
		while (mRecent.size() > 80) {
			mRecent.remove(0);
		}
	}

	private static String redact(String s) {
		if (s == null) {
			return "";
		}
		// Soft-redact long auth keys in feed
		return s.replaceAll("authentication-key:\\s*\\S+", "authentication-key: ***");
	}

	private void notify(String msg) {
		mSink.notifyWindow(msg);
	}

	private static String first(HashMap<String, String> args, String key) {
		if (args.containsKey(key)) {
			return args.get(key);
		}
		for (Map.Entry<String, String> e : args.entrySet()) {
			if (e.getKey().equalsIgnoreCase(key)) {
				return e.getValue();
			}
		}
		return null;
	}

	private static int indexOfWhitespace(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	private static String generateAuthKey() {
		Random r = new Random();
		return String.valueOf(10000 + r.nextInt(90000));
	}

	private static boolean versionOverlaps(String aMin, String aMax, String bMin, String bMax) {
		int amin = parseVer(aMin);
		int amax = parseVer(aMax.length() > 0 ? aMax : aMin);
		int bmin = parseVer(bMin);
		int bmax = parseVer(bMax);
		return amin <= bmax && bmin <= amax;
	}

	private static String pickVersion(String aMin, String aMax, String bMin, String bMax) {
		int amin = parseVer(aMin != null ? aMin : "1.0");
		int amax = parseVer(aMax != null && aMax.length() > 0 ? aMax : aMin);
		int bmin = parseVer(bMin);
		int bmax = parseVer(bMax);
		int lo = Math.max(amin, bmin);
		int hi = Math.min(amax, bmax);
		if (lo > hi) {
			return null;
		}
		return formatVer(hi);
	}

	private static int parseVer(String v) {
		if (v == null || v.length() == 0) {
			return 0;
		}
		String[] p = v.split("\\.");
		int maj = 0;
		int min = 0;
		try {
			maj = Integer.parseInt(p[0]);
			if (p.length > 1) {
				min = Integer.parseInt(p[1]);
			}
		} catch (NumberFormatException ignored) {
		}
		return maj * 1000 + min;
	}

	private static String formatVer(int packed) {
		return (packed / 1000) + "." + (packed % 1000);
	}

	private static String packageFromMessage(String name) {
		if (name == null || name.length() == 0) {
			return null;
		}
		String n = name.toLowerCase(Locale.US);
		if (n.startsWith("mcp-negotiate")) {
			return "mcp-negotiate";
		}
		if (n.startsWith("dns-org-hellmoo-status")) {
			return "dns-org-hellmoo-status";
		}
		if (n.endsWith("-update") || n.endsWith("-set") || n.endsWith("-msg")
				|| n.endsWith("-open") || n.endsWith("-closed")) {
			int dash = name.lastIndexOf('-');
			if (dash > 0) {
				return name.substring(0, dash);
			}
		}
		return name;
	}
}
