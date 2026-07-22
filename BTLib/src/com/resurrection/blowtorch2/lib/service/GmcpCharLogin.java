package com.resurrection.blowtorch2.lib.service;

import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.resurrection.blowtorch2.lib.launcher.LauncherSAXParser;
import com.resurrection.blowtorch2.lib.launcher.LauncherSettings;
import com.resurrection.blowtorch2.lib.launcher.MudConnection;
import com.resurrection.blowtorch2.lib.launcher.ServerAccount;

/**
 * Native handler for GMCP Char.Login (password-credentials). OAuth is not implemented.
 * <p>
 * Per <a href="https://wiki.mudlet.org/w/Standards:GMCP_Authentication">GMCP Authentication</a>,
 * after {@code Char.Login.Default} with {@code password-credentials} the client
 * <b>must</b> reply with either filled {@code Char.Login.Credentials} or an empty
 * {@code Char.Login.Credentials {}} (cancel / hand off to in-band login). Silent
 * ignore leaves servers such as Eden waiting on the GMCP password prompt.
 * <p>
 * Filled credentials are delayed briefly so servers that still probe telnet
 * capabilities (e.g. Eden / GraphicMUD) are less likely to reject an early packet.
 */
public final class GmcpCharLogin {

	private static final String TAG = "GmcpLogin";
	/** Delay after Char.Login.Default before sending filled Credentials (ms). */
	private static final long CREDENTIALS_DELAY_MS = 2000L;

	public interface Sender {
		void sendGmcp(String payload);
		void notifyWindow(String message);
	}

	private final android.content.Context mContext;
	private final String mDisplayName;
	private final Sender mSender;
	private Handler mHandler;
	private String mAccount = "";
	private String mPassword = "";
	private boolean mCredentialsLoaded;
	private boolean mCredentialsSent;
	private Runnable mPendingCredentials;

	public GmcpCharLogin(final android.content.Context context, final String displayName, final Sender sender) {
		mContext = context;
		mDisplayName = displayName != null ? displayName : "";
		mSender = sender;
	}

	private Handler handler() {
		if (mHandler == null) {
			mHandler = new Handler(Looper.getMainLooper());
		}
		return mHandler;
	}

	public void setCredentials(final String account, final String password) {
		mAccount = account != null ? account : "";
		mPassword = password != null ? password : "";
		mCredentialsLoaded = true;
	}

	/** Cancel any pending delayed Credentials (e.g. on disconnect). */
	public void release() {
		cancelPendingCredentials();
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
		// New offer from the server — allow another Credentials / cancel cycle.
		mCredentialsSent = false;
		cancelPendingCredentials();

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
			// OAuth-only (or unknown) — we cannot fulfill; cancel so the server
			// falls back to interactive / in-band login instead of hanging.
			Log.i(TAG, "Char.Login.Default without password-credentials — sending empty Credentials");
			sendEmptyCredentials();
			return;
		}
		if (mAccount.length() == 0 || mPassword.length() == 0) {
			Log.i(TAG, "Char.Login: no stored login/password for \"" + mDisplayName
					+ "\" — Char.Login.Credentials {} (in-band login)");
			sendEmptyCredentials();
			mSender.notifyWindow("\n" + Colorizer.getBrightYellowColor()
					+ "GMCP Char.Login: no launcher account/password — using normal in-game login."
					+ Colorizer.getWhiteColor() + "\n");
			return;
		}
		mPendingCredentials = new Runnable() {
			@Override
			public void run() {
				mPendingCredentials = null;
				sendCredentialsNow();
			}
		};
		Log.i(TAG, "Char.Login.Default — scheduling Credentials in " + CREDENTIALS_DELAY_MS + "ms");
		handler().postDelayed(mPendingCredentials, CREDENTIALS_DELAY_MS);
	}

	/** Spec cancel / “I cannot fulfill this method”: empty Credentials object. */
	private void sendEmptyCredentials() {
		if (mCredentialsSent) {
			return;
		}
		mCredentialsSent = true;
		Log.i(TAG, "Char.Login.Credentials {}");
		mSender.sendGmcp("Char.Login.Credentials {}");
	}

	private void sendCredentialsNow() {
		if (mCredentialsSent) {
			return;
		}
		if (mAccount.length() == 0 || mPassword.length() == 0) {
			sendEmptyCredentials();
			return;
		}
		try {
			JSONObject creds = new JSONObject();
			// Spec field is "account"; some servers also look at "name".
			creds.put("account", mAccount);
			creds.put("name", mAccount);
			creds.put("password", mPassword);
			String payload = "Char.Login.Credentials " + creds.toString();
			Log.i(TAG, "Char.Login.Credentials sending account=\"" + mAccount
					+ "\" passwordLen=" + mPassword.length());
			mCredentialsSent = true;
			mSender.sendGmcp(payload);
		} catch (JSONException e) {
			Log.w(TAG, "Char.Login.Credentials build failed", e);
			mCredentialsSent = false;
			sendEmptyCredentials();
		}
	}

	private void onResult(final JSONObject body) {
		cancelPendingCredentials();
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
				+ message + Colorizer.getWhiteColor()
				+ "\n(If in-band login works, check login name case — some MUDs are case-sensitive for GMCP Char.Login.)\n");
	}

	private void cancelPendingCredentials() {
		if (mPendingCredentials != null && mHandler != null) {
			mHandler.removeCallbacks(mPendingCredentials);
			mPendingCredentials = null;
		} else if (mPendingCredentials != null) {
			mPendingCredentials = null;
		}
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
