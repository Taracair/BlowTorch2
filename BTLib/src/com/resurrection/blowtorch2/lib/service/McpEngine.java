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

import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

/**
 * Mud Client Protocol 2.1 engine: line filter, handshake, negotiate, multiline,
 * cords, HellMOO status, simpleedit, displayurl, ping, vmoo-client, Lua watchers.
 *
 * @see <a href="https://www.moo.mud.org/mcp2/mcp2.html">MCP 2.1</a>
 */
public final class McpEngine {

	public static final String TRIGGER_CHAR = "@";

	private static final String TAG = "McpEngine";
	private static final Pattern KV = Pattern.compile(
			"([A-Za-z0-9_.*-]+):\\s*(?:\"([^\"]*)\"|(\\S+))");

	public interface Sink {
		void sendNetworkLine(String line);
		void notifyWindow(String message);
		String getEncoding();
		android.content.Context getContext();
		String getDisplayName();
		void openUrl(String url);
		void openSimpleEdit(String reference, String title, String type, String content);
		void fireMcpTrigger(String messageName, HashMap<String, Object> data);
		String getClientName();
		String getClientVersion();
		int getDisplayCols();
		int getDisplayRows();
	}

	private static final class Watcher {
		final String plugin;
		final String callback;
		Watcher(String plugin, String callback) {
			this.plugin = plugin;
			this.callback = callback;
		}
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
	private final LinkedHashMap<String, CordState> mCords =
			new LinkedHashMap<String, CordState>();
	private final HashMap<String, ArrayList<Watcher>> mWatchers =
			new HashMap<String, ArrayList<Watcher>>();

	private boolean mUse = false;
	private boolean mLog = false;
	private boolean mFeed = false;
	private boolean mOmitFromOutput = true;
	private boolean mAutoNegotiate = true;
	private String mAuthKey = "";
	private boolean mHandshaken = false;
	private boolean mNegotiateDone = false;
	private boolean mServerNegotiateEnd = false;
	private String mServerMin = "";
	private String mServerMax = "";
	private int mCordSeq = 0;
	private int mDataTagSeq = 0;

	private static final class MultilineState {
		String messageName;
		String dataTag;
		HashMap<String, String> args = new HashMap<String, String>();
		HashMap<String, StringBuilder> multi = new HashMap<String, StringBuilder>();
		HashMap<String, Boolean> isMulti = new HashMap<String, Boolean>();
	}

	private static final class CordState {
		String id;
		String type;
		boolean open;
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
		mServerNegotiateEnd = false;
		mServerMin = "";
		mServerMax = "";
		mMultiline.clear();
		mCords.clear();
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

	public Map<String, String> getOpenCords() {
		LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
		for (CordState c : mCords.values()) {
			if (c.open) {
				out.put(c.id, c.type);
			}
		}
		return out;
	}

	public void clearWatchers() {
		mWatchers.clear();
	}

	public void addWatcher(String messageName, String plugin, String callback) {
		if (messageName == null || plugin == null || callback == null) {
			return;
		}
		String key = messageName.toLowerCase(Locale.US);
		ArrayList<Watcher> list = mWatchers.get(key);
		if (list == null) {
			list = new ArrayList<Watcher>();
			mWatchers.put(key, list);
		}
		list.add(new Watcher(plugin, callback));
	}

	/**
	 * Quote an in-band user line per MCP 2.1 network-line translation
	 * when it would otherwise look like OOB ({@code #$#} / {@code #$"}).
	 */
	public String quoteOutboundInBand(String line) {
		if (!mUse || line == null) {
			return line;
		}
		if (line.startsWith("#$#") || line.startsWith("#$\"")) {
			return "#$\"" + line;
		}
		return line;
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
		if (mPending.length() > 256 * 1024) {
			out.append(mPending);
			mPending.setLength(0);
		}
		return out.toString().getBytes(enc);
	}

	private String handleNetworkLine(String line) {
		if (line.startsWith("#$\"")) {
			return line.substring(3);
		}
		if (!line.startsWith("#$#")) {
			return line;
		}
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
			String tag = null;
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
		maybeSendVmooClientInfo();
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
		mServerNegotiateEnd = false;
		sendNegotiate();
		notify("MCP packages re-negotiated.");
	}

	private void dispatchMessage(String name, HashMap<String, String> args) {
		String lower = name.toLowerCase(Locale.US);
		String auth = args.remove("_auth");
		if (mHandshaken && mAuthKey.length() > 0) {
			if (auth != null && !auth.equals(mAuthKey)) {
				logDir("DROP", "bad auth on " + name);
				return;
			}
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
			fireWatchers(name, args);
			return;
		}
		if (lower.equals("mcp-negotiate-end")) {
			mNegotiateDone = true;
			mServerNegotiateEnd = true;
			maybeSendVmooClientInfo();
			fireWatchers(name, args);
			return;
		}

		String pkgGuess = packageFromMessage(name);
		if (pkgGuess != null) {
			mRegistry.noteSeen(pkgGuess);
		}

		if (lower.equals("mcp-cord-open")) {
			onCordOpen(args);
			fireWatchers(name, args);
			return;
		}
		if (lower.equals("mcp-cord-closed")) {
			onCordClosed(args);
			fireWatchers(name, args);
			return;
		}
		if (lower.equals("mcp-cord")) {
			onCordMessage(args);
			fireWatchers(name, args);
			return;
		}

		if (lower.equals("dns-org-hellmoo-status-update")
				|| lower.endsWith("-status-update")) {
			onHellmooStatus(args);
			fireWatchers(name, args);
			return;
		}
		if (lower.equals("dns-com-awns-displayurl")
				|| lower.equals("dns-com-awns-display-url")) {
			onDisplayUrl(args);
			fireWatchers(name, args);
			return;
		}
		if (lower.equals("dns-com-awns-ping")
				|| lower.equals("dns-com-awns-ping-msg")) {
			onPing(args);
			fireWatchers(name, args);
			return;
		}
		if (lower.equals("dns-org-mud-moo-simpleedit-content")
				|| lower.equals("dns-mud-moo-org-simpleedit-content")) {
			onSimpleEditContent(args);
			fireWatchers(name, args);
			return;
		}
		if (lower.startsWith("dns-com-vmoo-client")) {
			mStatusCache.put("last." + name, args.toString());
			fireWatchers(name, args);
			return;
		}

		mStatusCache.put("last." + name, args.toString());
		fireWatchers(name, args);
	}

	private void onCordOpen(HashMap<String, String> args) {
		String id = first(args, "_id");
		String type = first(args, "_type");
		if (id == null) {
			return;
		}
		CordState c = new CordState();
		c.id = id;
		c.type = type != null ? type : "";
		c.open = true;
		mCords.put(id, c);
		if (mFeed) {
			notify("\n" + Colorizer.getBrightCyanColor() + "[MCP cord open] " + id
					+ " type=" + c.type + Colorizer.getWhiteColor() + "\n");
		}
	}

	private void onCordClosed(HashMap<String, String> args) {
		String id = first(args, "_id");
		if (id == null) {
			return;
		}
		CordState c = mCords.get(id);
		if (c != null) {
			c.open = false;
		}
		if (mFeed) {
			notify("\n" + Colorizer.getBrightCyanColor() + "[MCP cord closed] " + id
					+ Colorizer.getWhiteColor() + "\n");
		}
	}

	private void onCordMessage(HashMap<String, String> args) {
		String id = first(args, "_id");
		String msg = first(args, "_message");
		if (id != null) {
			CordState c = mCords.get(id);
			if (c != null && !c.open) {
				logDir("DROP", "message on closed cord " + id);
				return;
			}
		}
		mStatusCache.put("last.cord." + (id != null ? id : "?"),
				(msg != null ? msg : "") + " " + args.toString());
		if (mFeed) {
			notify("\n" + Colorizer.getBrightCyanColor() + "[MCP cord] " + id
					+ " " + msg + Colorizer.getWhiteColor() + "\n");
		}
	}

	/** Open a cord from the client (id prefix R per MCP 2.1). */
	public String openCord(String type) {
		if (!mUse || !mHandshaken) {
			notify("MCP not ready for cords.");
			return null;
		}
		mCordSeq++;
		String id = "R" + mCordSeq;
		HashMap<String, String> args = new LinkedHashMap<String, String>();
		args.put("_id", id);
		args.put("_type", type != null ? type : "");
		sendMessage("mcp-cord-open", args);
		CordState c = new CordState();
		c.id = id;
		c.type = type != null ? type : "";
		c.open = true;
		mCords.put(id, c);
		return id;
	}

	public void closeCord(String id) {
		if (id == null) {
			return;
		}
		HashMap<String, String> args = new LinkedHashMap<String, String>();
		args.put("_id", id);
		sendMessage("mcp-cord-closed", args);
		CordState c = mCords.get(id);
		if (c != null) {
			c.open = false;
		}
	}

	public void sendCordMessage(String id, String message, Map<String, String> msgArgs) {
		HashMap<String, String> args = new LinkedHashMap<String, String>();
		args.put("_id", id);
		args.put("_message", message != null ? message : "");
		if (msgArgs != null) {
			args.putAll(msgArgs);
		}
		sendMessage("mcp-cord", args);
	}

	private void onDisplayUrl(HashMap<String, String> args) {
		String url = first(args, "url");
		if (url == null || url.length() == 0) {
			return;
		}
		notify("\n" + Colorizer.getWhiteColor() + "[MCP URL] " + url + "\n");
		try {
			mSink.openUrl(url);
		} catch (Exception e) {
			Log.w(TAG, "openUrl failed", e);
		}
	}

	private void onPing(HashMap<String, String> args) {
		String id = first(args, "id");
		if (id == null) {
			id = first(args, "Id");
		}
		HashMap<String, String> reply = new LinkedHashMap<String, String>();
		if (id != null) {
			reply.put("id", id);
		}
		// Reply with same message name family
		sendMessage("dns-com-awns-ping", reply);
		mStatusCache.put("last.ping", id != null ? id : "");
	}

	private void onSimpleEditContent(HashMap<String, String> args) {
		String reference = first(args, "reference");
		String title = first(args, "name");
		String type = first(args, "type");
		String content = first(args, "content");
		if (reference == null) {
			reference = "";
		}
		if (title == null) {
			title = reference;
		}
		if (type == null) {
			type = "string-list";
		}
		if (content == null) {
			content = "";
		}
		try {
			mSink.openSimpleEdit(reference, title, type, content);
		} catch (Exception e) {
			Log.w(TAG, "openSimpleEdit failed", e);
			notify("\n" + Colorizer.getRedColor()
					+ "MCP simpleedit: could not open editor for " + title
					+ Colorizer.getWhiteColor() + "\n");
		}
	}

	/** Client → server simpleedit-set (multiline content*). */
	public void sendSimpleEditSet(String reference, String type, String content) {
		if (!mUse || !mHandshaken) {
			notify("MCP not ready — cannot send simpleedit-set.");
			return;
		}
		if (type == null || type.length() == 0) {
			type = "string-list";
		}
		if (content == null) {
			content = "";
		}
		String tag = nextDataTag();
		StringBuilder header = new StringBuilder();
		header.append("#$#dns-org-mud-moo-simpleedit-set ").append(mAuthKey);
		header.append(" reference: ").append(quoteValue(reference != null ? reference : ""));
		header.append(" type: ").append(type);
		header.append(" content*: \"\" _data-tag: ").append(tag);
		sendRaw(header.toString());
		String[] lines = content.split("\n", -1);
		for (String line : lines) {
			sendRaw("#$#* " + tag + " content: " + line);
		}
		sendRaw("#$#:");
	}

	private void maybeSendVmooClientInfo() {
		if (!mUse || !mHandshaken) {
			return;
		}
		if (!mRegistry.isEnabled("dns-com-vmoo-client")) {
			return;
		}
		// After we advertised; ok to send once negotiate-end from us is out
		HashMap<String, String> args = new LinkedHashMap<String, String>();
		args.put("name", nz(mSink.getClientName(), "BlowTorch"));
		args.put("text-version", nz(mSink.getClientVersion(), "2.1"));
		args.put("internal-version", nz(mSink.getClientVersion(), "2.1"));
		args.put("flags", "edit");
		int cols = mSink.getDisplayCols();
		int rows = mSink.getDisplayRows();
		if (cols > 0) {
			args.put("columns", String.valueOf(cols));
		}
		if (rows > 0) {
			args.put("rows", String.valueOf(rows));
		}
		args.put("os", "Android " + Build.VERSION.RELEASE);
		sendMessage("dns-com-vmoo-client-info", args);
	}

	public void sendClientInfo() {
		maybeSendVmooClientInfo();
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

	private void fireWatchers(String name, HashMap<String, String> args) {
		HashMap<String, Object> data = new HashMap<String, Object>();
		data.put("_message", name);
		if (args != null) {
			for (Map.Entry<String, String> e : args.entrySet()) {
				data.put(e.getKey(), e.getValue());
			}
		}
		// Always offer vitals snapshot on status updates
		if (name != null && name.toLowerCase(Locale.US).contains("status-update")) {
			data.putAll(mStatusCache);
		}
		ArrayList<Watcher> list = mWatchers.get(name.toLowerCase(Locale.US));
		if (list != null) {
			for (Watcher w : list) {
				HashMap<String, Object> copy = new HashMap<String, Object>(data);
				copy.put("_plugin", w.plugin);
				copy.put("_callback", w.callback);
				mSink.fireMcpTrigger(name, copy);
			}
		}
		// Wildcard @* watchers
		ArrayList<Watcher> wild = mWatchers.get("*");
		if (wild != null) {
			for (Watcher w : wild) {
				HashMap<String, Object> copy = new HashMap<String, Object>(data);
				copy.put("_plugin", w.plugin);
				copy.put("_callback", w.callback);
				mSink.fireMcpTrigger(name, copy);
			}
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

	private static String nz(String s, String def) {
		return s != null && s.length() > 0 ? s : def;
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
				sb.append(quoteValue(e.getValue() != null ? e.getValue() : ""));
			}
		}
		sendRaw(sb.toString());
	}

	/** Parse ".mcp send name key: val …" into sendMessage, or raw #$# line. */
	public void sendFromCommand(String rest) {
		if (rest == null || rest.length() == 0) {
			return;
		}
		if (rest.startsWith("#$#")) {
			sendRaw(rest);
			return;
		}
		int sp = indexOfWhitespace(rest);
		String name = sp < 0 ? rest : rest.substring(0, sp);
		String argRest = sp < 0 ? "" : rest.substring(sp).trim();
		HashMap<String, String> args = parseArgs(argRest);
		args.remove("_auth");
		sendMessage(name, args);
	}

	public String statusReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("use=").append(mUse ? "on" : "off");
		sb.append(" handshaken=").append(mHandshaken);
		sb.append(" negotiate=").append(mNegotiateDone ? "done" : "pending");
		sb.append(" peer-end=").append(mServerNegotiateEnd);
		sb.append(" auth=").append(mAuthKey.length() > 0 ? "(set)" : "(none)");
		sb.append(" server=").append(mServerMin).append("..").append(mServerMax);
		sb.append(" cords=").append(mCords.size());
		sb.append(" ").append(mRegistry.statusLine());
		return sb.toString();
	}

	private String nextDataTag() {
		mDataTagSeq++;
		return String.valueOf(10000 + (mDataTagSeq % 90000));
	}

	private static String quoteValue(String v) {
		if (v.indexOf(' ') >= 0 || v.indexOf('"') >= 0 || v.length() == 0) {
			return "\"" + v.replace("\"", "") + "\"";
		}
		return v;
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
		return s.replaceAll("authentication-key:\\s*\\S+", "authentication-key: ***");
	}

	private void notify(String msg) {
		mSink.notifyWindow(msg);
	}

	private HashMap<String, String> parseArgs(String rest) {
		HashMap<String, String> map = new HashMap<String, String>();
		if (rest == null || rest.length() == 0) {
			return map;
		}
		String work = rest.trim();
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
		if (n.startsWith("mcp-cord")) {
			return "mcp-cord";
		}
		if (n.startsWith("dns-org-hellmoo-status")) {
			return "dns-org-hellmoo-status";
		}
		if (n.startsWith("dns-org-mud-moo-simpleedit")
				|| n.startsWith("dns-mud-moo-org-simpleedit")) {
			return "dns-org-mud-moo-simpleedit";
		}
		if (n.startsWith("dns-com-awns-displayurl") || n.startsWith("dns-com-awns-display-url")) {
			return "dns-com-awns-displayurl";
		}
		if (n.startsWith("dns-com-awns-ping")) {
			return "dns-com-awns-ping";
		}
		if (n.startsWith("dns-com-vmoo-client")) {
			return "dns-com-vmoo-client";
		}
		if (n.endsWith("-update") || n.endsWith("-set") || n.endsWith("-msg")
				|| n.endsWith("-open") || n.endsWith("-closed") || n.endsWith("-content")
				|| n.endsWith("-info")) {
			int dash = name.lastIndexOf('-');
			if (dash > 0) {
				return name.substring(0, dash);
			}
		}
		return name;
	}
}
