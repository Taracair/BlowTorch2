package com.resurrection.blowtorch2.lib.responder.setvariable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions;
import com.resurrection.blowtorch2.lib.trigger.condition.SessionVariableStore;
import com.resurrection.blowtorch2.lib.util.AtomicFiles;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Kept names for one world: {@code sanitizedDisplay.vars.json} next to the
 * profile, not the world XML.
 *
 * <p>Owned from {@code :stellar} / {@link com.resurrection.blowtorch2.lib.service.Connection}
 * only. Not a {@code static} cache — that exists twice (UI and service). Lua,
 * MXP, and {@code device.*} writes never come through here.
 */
public final class SessionVariableSidecar {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final Pattern NON_WORD = Pattern.compile("\\W");

	private final LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
	private final Context app;
	private final String fileName;

	/** JVM tests: in-memory only. */
	SessionVariableSidecar() {
		this.app = null;
		this.fileName = "";
	}

	public SessionVariableSidecar(Context ctx, String display) {
		this.app = ctx == null ? null : ctx.getApplicationContext();
		this.fileName = fileNameForDisplay(display);
		load();
	}

	static String fileNameForDisplay(String display) {
		String raw = display == null ? "" : display;
		return NON_WORD.matcher(raw).replaceAll("") + ".vars.json";
	}

	public void restoreInto(SessionVariableStore store) {
		if (store == null) {
			return;
		}
		for (Map.Entry<String, String> e : values.entrySet()) {
			if (DeviceConditions.isDeviceVariable(e.getKey())) {
				continue;
			}
			store.set(e.getKey(), e.getValue());
		}
	}

	/**
	 * Persist one name after an action with Keep after restart. {@code device.*}
	 * never lands here. {@code valueOrNull} null removes the key.
	 */
	public void remember(String key, String valueOrNull) {
		if (key == null || key.length() == 0) {
			return;
		}
		if (DeviceConditions.isDeviceVariable(key)) {
			return;
		}
		if (valueOrNull == null) {
			values.remove(key);
		} else {
			values.put(key, valueOrNull);
		}
		flush();
	}

	Map<String, String> snapshot() {
		return new LinkedHashMap<String, String>(values);
	}

	static String toJson(Map<String, String> map) {
		JSONObject o = new JSONObject();
		if (map != null) {
			for (Map.Entry<String, String> e : map.entrySet()) {
				if (e.getKey() == null || e.getKey().length() == 0) {
					continue;
				}
				if (DeviceConditions.isDeviceVariable(e.getKey())) {
					continue;
				}
				try {
					o.put(e.getKey(), e.getValue() != null ? e.getValue() : "");
				} catch (JSONException ignored) {
				}
			}
		}
		return o.toString();
	}

	static void mergeFromJson(Map<String, String> dest, String json) {
		if (dest == null || json == null || json.trim().length() == 0) {
			return;
		}
		try {
			JSONObject o = new JSONObject(json);
			Iterator<?> keys = o.keys();
			while (keys.hasNext()) {
				String k = String.valueOf(keys.next());
				if (k.length() == 0 || DeviceConditions.isDeviceVariable(k)) {
					continue;
				}
				dest.put(k, o.optString(k, ""));
			}
		} catch (JSONException ignored) {
		}
	}

	private void load() {
		if (app == null || fileName.length() == 0) {
			return;
		}
		File live = new File(app.getFilesDir(), fileName);
		if (!live.isFile() || live.length() == 0) {
			return;
		}
		FileInputStream in = null;
		try {
			in = new FileInputStream(live);
			byte[] buf = new byte[(int) live.length()];
			int off = 0;
			while (off < buf.length) {
				int n = in.read(buf, off, buf.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			mergeFromJson(values, new String(buf, 0, off, UTF8));
		} catch (IOException e) {
			BlowTorchLogger.logMinor("SessionVariableSidecar.load", e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private void flush() {
		if (app == null || fileName.length() == 0) {
			return;
		}
		try {
			AtomicFiles.writeInternal(app, fileName, toJson(values).getBytes(UTF8),
					true);
		} catch (IOException e) {
			BlowTorchLogger.logThrowable("SessionVariableSidecar.flush", e);
		}
	}
}
