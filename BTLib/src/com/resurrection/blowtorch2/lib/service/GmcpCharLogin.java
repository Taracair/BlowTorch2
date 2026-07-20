package com.resurrection.blowtorch2.lib.service;

import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.resurrection.blowtorch2.lib.launcher.LauncherSAXParser;
import com.resurrection.blowtorch2.lib.launcher.LauncherSettings;
import com.resurrection.blowtorch2.lib.launcher.MudConnection;
import com.resurrection.blowtorch2.lib.launcher.ServerAccount;

/**
 * Native handler for GMCP Char.Login (password-credentials). OAuth is not implemented.
 */
public final class GmcpCharLogin {

	private static final String TAG = "GmcpLogin";

	public interface Sender {
		void sendGmcp(String payload);
		void notifyWindow(String message);
	}

	private final android.content.Context mContext;
	private final String mDisplayName;
	private final Sender mSender;
	private String mAccount = "";
	private String mPassword = "";
	private boolean mCredentialsLoaded;

	public GmcpCharLogin(final android.content.Context context, final String displayName, final Sender sender) {
		mContext = context;
		mDisplayName = displayName != null ? displayName : "";
		mSender = sender;
	}

	public void setCredentials(final String account, final String password) {
		mAccount = account != null ? account : "";
		mPassword = password != null ? password : "";
		mCredentialsLoaded = true;
	}

	public void handle(final String module, final JSONObject body) {
		String action = lastSegment(module).toLowerCase(Locale.US);
		if ("default".equals(action)) {
			onDefault(body != null ? body : new JSONObject());
		} else if ("result".equals(action)) {
			onResult(body != null ? body : new JSONObject());
		}
	}

	private void onDefault(final JSONObject body) {
		ensureCredentials();
		JSONArray types = body.optJSONArray("type");
		boolean wantsPassword = false;
		if (types != null) {
			for (int i = 0; i < types.length(); i++) {
				String t = types.optString(i, "").toLowerCase(Locale.US);
				if ("password-credentials".equals(t)) {
					wantsPassword = true;
					break;
				}
			}
		}
		if (!wantsPassword) {
			Log.i(TAG, "Char.Login.Default without password-credentials — sending empty Credentials");
			mSender.sendGmcp("Char.Login.Credentials {}");
			return;
		}
		if (mAccount.length() == 0 || mPassword.length() == 0) {
			Log.i(TAG, "Char.Login: no stored login/password for \"" + mDisplayName + "\"");
			mSender.sendGmcp("Char.Login.Credentials {}");
			return;
		}
		try {
			JSONObject creds = new JSONObject();
			creds.put("account", mAccount);
			creds.put("password", mPassword);
			mSender.sendGmcp("Char.Login.Credentials " + creds.toString());
		} catch (JSONException e) {
			Log.w(TAG, "Char.Login.Credentials build failed", e);
			mSender.sendGmcp("Char.Login.Credentials {}");
		}
	}

	private void onResult(final JSONObject body) {
		boolean success = false;
		Object raw = body.opt("success");
		if (raw instanceof Boolean) {
			success = ((Boolean) raw).booleanValue();
		} else if (raw != null) {
			String s = String.valueOf(raw).toLowerCase(Locale.US);
			success = "true".equals(s) || "1".equals(s) || "yes".equals(s);
		}
		if (success) {
			Log.i(TAG, "Char.Login.Result success");
			return;
		}
		String message = body.optString("message", "Login failed");
		Log.w(TAG, "Char.Login.Result failed: " + message);
		mSender.notifyWindow("\n" + Colorizer.getRedColor() + "GMCP Char.Login: "
				+ message + Colorizer.getWhiteColor() + "\n");
	}

	private void ensureCredentials() {
		if (mCredentialsLoaded) {
			return;
		}
		mCredentialsLoaded = true;
		if (mDisplayName.length() == 0 || mContext == null) {
			return;
		}
		try {
			LauncherSAXParser parser = new LauncherSAXParser("blowtorch_launcher_list.xml", mContext);
			LauncherSettings settings = parser.load();
			if (settings == null || settings.getList() == null) {
				return;
			}
			MudConnection mud = settings.getList().get(mDisplayName);
			if (mud == null) {
				return;
			}
			ServerAccount acc = mud.primaryAccount();
			if (acc != null) {
				mAccount = acc.getLogin();
				mPassword = acc.getPassword();
			}
		} catch (Exception e) {
			Log.w(TAG, "Failed loading launcher credentials", e);
		}
	}

	private static String lastSegment(final String module) {
		if (module == null) {
			return "";
		}
		int dot = module.lastIndexOf('.');
		if (dot < 0 || dot + 1 >= module.length()) {
			return module;
		}
		return module.substring(dot + 1);
	}
}
