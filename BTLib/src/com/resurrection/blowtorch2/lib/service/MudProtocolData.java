package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import android.util.Log;

/**
 * Minimal MSDP / MSSP parsers. Failures are logged and ignored — never crash
 * the telnet pipeline. Values are stored as flat strings (nested structures
 * become a compact textual dump) so Lua/triggers can consume them later.
 */
public final class MudProtocolData {

	private static final String TAG = "MudProto";

	private final LinkedHashMap<String, String> mMsdp = new LinkedHashMap<String, String>();
	private final LinkedHashMap<String, String> mMssp = new LinkedHashMap<String, String>();

	public synchronized void clearMsdp() {
		mMsdp.clear();
	}

	public synchronized void clearMssp() {
		mMssp.clear();
	}

	public synchronized Map<String, String> msdpSnapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<String, String>(mMsdp));
	}

	public synchronized Map<String, String> msspSnapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<String, String>(mMssp));
	}

	public synchronized String msdpStatusLine() {
		return mMsdp.isEmpty() ? "(empty)" : mMsdp.size() + " vars";
	}

	public synchronized String msspStatusLine() {
		if (mMssp.isEmpty()) {
			return "(empty)";
		}
		String name = mMssp.get("NAME");
		if (name == null) {
			name = mMssp.get("name");
		}
		return mMssp.size() + " vars" + (name != null ? " · " + name : "");
	}

	/** Parse IAC SB MSDP … payload (bytes between option and IAC SE). */
	public synchronized void absorbMsdp(final byte[] payload) {
		if (payload == null || payload.length == 0) {
			return;
		}
		try {
			parseVarVal(payload, mMsdp, true);
		} catch (Exception e) {
			Log.w(TAG, "MSDP parse failed (ignored): " + e.getMessage());
		}
	}

	/** Parse IAC SB MSSP … payload. */
	public synchronized void absorbMssp(final byte[] payload) {
		if (payload == null || payload.length == 0) {
			return;
		}
		try {
			parseVarVal(payload, mMssp, false);
		} catch (Exception e) {
			Log.w(TAG, "MSSP parse failed (ignored): " + e.getMessage());
		}
	}

	private static void parseVarVal(final byte[] payload, final LinkedHashMap<String, String> out,
			final boolean allowNested) {
		String currentVar = null;
		StringBuilder val = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < payload.length; i++) {
			int b = payload[i] & 0xFF;
			if (b == (TC.MSDP_VAR & 0xFF)) {
				if (currentVar != null && depth == 0) {
					out.put(currentVar, val.toString());
				}
				currentVar = null;
				val.setLength(0);
				depth = 0;
				StringBuilder name = new StringBuilder();
				i++;
				while (i < payload.length) {
					int c = payload[i] & 0xFF;
					if (c == (TC.MSDP_VAL & 0xFF) || c == (TC.MSDP_VAR & 0xFF)
							|| (allowNested && (c == (TC.MSDP_TABLE_OPEN & 0xFF)
									|| c == (TC.MSDP_ARRAY_OPEN & 0xFF)))) {
						i--;
						break;
					}
					name.append((char) c);
					i++;
				}
				currentVar = name.toString();
			} else if (b == (TC.MSDP_VAL & 0xFF)) {
				if (!allowNested) {
					val.setLength(0);
					i++;
					while (i < payload.length) {
						int c = payload[i] & 0xFF;
						if (c == (TC.MSDP_VAR & 0xFF) || c == (TC.MSDP_VAL & 0xFF)) {
							i--;
							break;
						}
						val.append((char) c);
						i++;
					}
					if (currentVar != null) {
						out.put(currentVar, val.toString());
						currentVar = null;
						val.setLength(0);
					}
				} else {
					// Nested-capable: accumulate until next VAR at depth 0
					val.setLength(0);
					depth = 0;
					i++;
					while (i < payload.length) {
						int c = payload[i] & 0xFF;
						if (c == (TC.MSDP_VAR & 0xFF) && depth == 0) {
							i--;
							break;
						}
						if (c == (TC.MSDP_TABLE_OPEN & 0xFF) || c == (TC.MSDP_ARRAY_OPEN & 0xFF)) {
							depth++;
							val.append(c == (TC.MSDP_TABLE_OPEN & 0xFF) ? "{" : "[");
						} else if (c == (TC.MSDP_TABLE_CLOSE & 0xFF) || c == (TC.MSDP_ARRAY_CLOSE & 0xFF)) {
							depth = Math.max(0, depth - 1);
							val.append(c == (TC.MSDP_TABLE_CLOSE & 0xFF) ? "}" : "]");
						} else if (c == (TC.MSDP_VAR & 0xFF)) {
							val.append(" VAR:");
						} else if (c == (TC.MSDP_VAL & 0xFF)) {
							val.append("=");
						} else {
							val.append((char) c);
						}
						i++;
					}
					if (currentVar != null) {
						out.put(currentVar, val.toString());
						currentVar = null;
						val.setLength(0);
					}
				}
			}
		}
		if (currentVar != null) {
			out.put(currentVar, val.toString());
		}
	}

	public synchronized String dumpMsdp() {
		return dumpMap(mMsdp);
	}

	public synchronized String dumpMssp() {
		return dumpMap(mMssp);
	}

	private static String dumpMap(LinkedHashMap<String, String> map) {
		if (map.isEmpty()) {
			return "(empty)\n";
		}
		ArrayList<String> keys = new ArrayList<String>(map.keySet());
		Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
		StringBuilder sb = new StringBuilder();
		for (String k : keys) {
			sb.append("  ").append(k).append(" = ").append(map.get(k)).append("\n");
		}
		return sb.toString();
	}
}
