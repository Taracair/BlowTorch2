package com.resurrection.blowtorch2.lib.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * One GET or POST. The caller runs this off the connection thread. A status
 * line keeps its body, including a 4xx or 5xx error page. The body is empty
 * when it is over the cap, the redirect leaves http, or no status arrived.
 */
public final class HttpRequestFetch {

	private HttpRequestFetch() {
	}

	public static Outcome fetch(String method, String url, byte[] body) {
		HttpURLConnection conn = null;
		InputStream in = null;
		int status = 0;
		try {
			conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setConnectTimeout(HttpRequestCall.TIMEOUT_MS);
			conn.setReadTimeout(HttpRequestCall.TIMEOUT_MS);
			conn.setInstanceFollowRedirects(true);
			conn.setUseCaches(false);
			conn.setRequestMethod(method);
			if ("POST".equals(method)) {
				byte[] payload = body == null ? new byte[0] : body;
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", HttpRequestCall.POST_CONTENT_TYPE);
				OutputStream out = conn.getOutputStream();
				try {
					out.write(payload);
				} finally {
					out.close();
				}
			}
			status = conn.getResponseCode();
			String scheme = conn.getURL() == null ? null : conn.getURL().getProtocol();
			if (scheme == null
					|| (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
				return Outcome.transport(status, "redirect left http");
			}
			boolean ok = status >= 200 && status <= 299;
			in = ok ? conn.getInputStream() : conn.getErrorStream();
			if (in == null) {
				return ok
						? Outcome.success(status, new byte[0])
						: Outcome.http(status, new byte[0]);
			}
			HttpRequestCall.Read read = HttpRequestCall.read(in, HttpRequestCall.BODY_CAP);
			if (read.overCap) {
				return Outcome.transport(status, "body is over 256 KB");
			}
			return ok ? Outcome.success(status, read.bytes) : Outcome.http(status, read.bytes);
		} catch (Exception e) {
			String message = e.getMessage();
			if (message == null || message.length() == 0) {
				message = e.getClass().getSimpleName();
			}
			return Outcome.transport(status, message);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	public static final class Outcome {
		public final boolean ok;
		public final int status;
		public final byte[] body;
		/** Null when {@link #ok} is true, so Lua receives nil. */
		public final String err;

		private Outcome(boolean ok, int status, byte[] body, String err) {
			this.ok = ok;
			this.status = status;
			this.body = body == null ? new byte[0] : body;
			this.err = err;
		}

		static Outcome success(int status, byte[] body) {
			return new Outcome(true, status, body, null);
		}

		static Outcome http(int status, byte[] body) {
			return new Outcome(false, status, body, "server answered " + status);
		}

		static Outcome transport(int status, String err) {
			return new Outcome(false, status, new byte[0], err);
		}
	}
}
