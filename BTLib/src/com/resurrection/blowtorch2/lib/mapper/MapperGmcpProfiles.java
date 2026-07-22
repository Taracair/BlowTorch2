package com.resurrection.blowtorch2.lib.mapper;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Per-host GMCP Room Sync presets (SharedPreferences JSON).
 * Neutral labels — no commercial MUD names in UI strings.
 */
public final class MapperGmcpProfiles {

	public static final String PREFS = "mapper_gmcp_host_profiles";

	/** Sparse world coords: exit-based grow, absolute coords off. */
	public static final String PROFILE_SPARSE = "sparse";
	/** Unit grid: trust absolute x,y when adjacent. */
	public static final String PROFILE_UNIT_GRID = "unit_grid";

	public static final String LABEL_SPARSE =
			"Sparse coordinates (exit-based layout)";
	public static final String LABEL_UNIT_GRID =
			"Unit grid (trust absolute x,y)";

	public static final class Profile {
		public String id = PROFILE_SPARSE;
		public String policy = MapperController.GMCP_POLICY_SYNC;
		public boolean useNum = true;
		public boolean useCoords = false;
		public boolean createExits = true;
		public boolean useGmcp = true;
	}

	private MapperGmcpProfiles() {
	}

	public static String labelFor(final String id) {
		if (PROFILE_UNIT_GRID.equals(id)) {
			return LABEL_UNIT_GRID;
		}
		return LABEL_SPARSE;
	}

	public static Profile defaultsFor(final String profileId) {
		Profile p = new Profile();
		if (PROFILE_UNIT_GRID.equals(profileId)) {
			p.id = PROFILE_UNIT_GRID;
			p.policy = MapperController.GMCP_POLICY_SYNC;
			p.useNum = true;
			p.useCoords = true;
			p.createExits = true;
			p.useGmcp = true;
		} else {
			p.id = PROFILE_SPARSE;
			p.policy = MapperController.GMCP_POLICY_SYNC;
			p.useNum = true;
			p.useCoords = false;
			p.createExits = true;
			p.useGmcp = true;
		}
		return p;
	}

	public static Profile loadForHost(final Context context, final String host) {
		String key = hostKey(host);
		if (context == null || key == null) {
			return null;
		}
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String raw = prefs.getString(key, null);
		if (raw == null || raw.length() == 0) {
			return null;
		}
		try {
			JSONObject o = new JSONObject(raw);
			Profile p = new Profile();
			p.id = o.optString("id", PROFILE_SPARSE);
			p.policy = MapperController.normalizeGmcpPolicy(
					o.optString("policy", MapperController.GMCP_POLICY_SYNC));
			p.useNum = o.optBoolean("useNum", true);
			p.useCoords = o.optBoolean("useCoords", false);
			p.createExits = o.optBoolean("createExits", true);
			p.useGmcp = o.optBoolean("useGmcp", true);
			return p;
		} catch (JSONException e) {
			return null;
		}
	}

	public static void saveForHost(final Context context, final String host,
			final Profile profile) {
		String key = hostKey(host);
		if (context == null || key == null || profile == null) {
			return;
		}
		try {
			JSONObject o = new JSONObject();
			o.put("id", profile.id != null ? profile.id : PROFILE_SPARSE);
			o.put("policy", MapperController.normalizeGmcpPolicy(profile.policy));
			o.put("useNum", profile.useNum);
			o.put("useCoords", profile.useCoords);
			o.put("createExits", profile.createExits);
			o.put("useGmcp", profile.useGmcp);
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.edit()
					.putString(key, o.toString())
					.apply();
		} catch (JSONException ignored) {
		}
	}

	public static String hostKey(final String host) {
		if (TextUtils.isEmpty(host)) {
			return null;
		}
		return host.trim().toLowerCase(java.util.Locale.US);
	}
}
