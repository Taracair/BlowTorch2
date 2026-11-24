package com.resurrection.blowtorch2.lib.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * One GET or POST. The caller runs this off the connection thread. A status
 * line keeps its body, including a 4xx or 5xx error page. The body is empty
 * when it is over the cap, the redirect leaves http, or no status arrived.
 */
public final class HttpRequestFetch {

	private HttpRequestFetch() {
	}

	public static Outcome fetch(String method, String url, byte[] body) {
		return fetch(method, url, body, null, true);
	}

	/**
	 * {@code headers} are sent as given; Content-Type falls back to
	 * {@link HttpRequestCall#POST_CONTENT_TYPE} on POST. With
	 * {@code followRedirects} false a 3xx comes back as itself, headers and all.
	 */
	public static Outcome fetch(String method, String url, byte[] body,
			Map<String, String> headers, boolean followRedirects) {
		HttpURLConnection conn = null;
		InputStream in = null;
		int status = 0;
		Map<String, String> received = null;
		try {
			conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setConnectTimeout(HttpRequestCall.TIMEOUT_MS);
			conn.setReadTimeout(HttpRequestCall.TIMEOUT_MS);
			conn.setInstanceFollowRedirects(followRedirects);
			conn.setUseCaches(false);
			conn.setRequestMethod(method);
			if (headers != null) {
				for (Map.Entry<String, String> e : headers.entrySet()) {
					conn.setRequestProperty(e.getKey(), e.getValue());
				}
			}
			if ("POST".equals(method)) {
				byte[] payload = body == null ? new byte[0] : body;
				conn.setDoOutput(true);
				if (!HttpRequestCall.hasHeader(headers, "Content-Type")) {
					conn.setRequestProperty("Content-Type", HttpRequestCall.POST_CONTENT_TYPE);
				}
				OutputStream out = conn.getOutputStream();
				try {
					out.write(payload);
				} finally {
					out.close();
				}
			}
			status = conn.getResponseCode();
			received = HttpRequestCall.responseHeaders(conn.getHeaderFields());
			String scheme = conn.getURL() == null ? null : conn.getURL().getProtocol();
			if (scheme == null
					|| (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
				return Outcome.transport(status, "redirect left http", received);
			}
			boolean ok = status >= 200 && status <= 299;
			in = ok ? conn.getInputStream() : conn.getErrorStream();
			if (in == null) {
				return ok
						? Outcome.success(status, new byte[0], received)
						: Outcome.http(status, new byte[0], received);
			}
			HttpRequestCall.Read read = HttpRequestCall.read(in, HttpRequestCall.BODY_CAP);
			if (read.overCap) {
				return Outcome.transport(status, "body is over 256 KB", received);
			}
			return ok ? Outcome.success(status, read.bytes, received)
					: Outcome.http(status, read.bytes, received);
		} catch (Exception e) {
			String message = e.getMessage();
			if (message == null || message.length() == 0) {
				message = e.getClass().getSimpleName();
			}
			return Outcome.transport(status, message, received);
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
		/** See {@link HttpRequestCall#responseHeaders}. Empty when none arrived. */
		public final Map<String, String> headers;

		private Outcome(boolean ok, int status, byte[] body, String err,
				Map<String, String> headers) {
			this.ok = ok;
			this.status = status;
			this.body = body == null ? new byte[0] : body;
			this.err = err;
			this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
		}

		static Outcome success(int status, byte[] body, Map<String, String> headers) {
			return new Outcome(true, status, body, null, headers);
		}

		static Outcome http(int status, byte[] body, Map<String, String> headers) {
			return new Outcome(false, status, body, "server answered " + status, headers);
		}

		static Outcome transport(int status, String err, Map<String, String> headers) {
			return new Outcome(false, status, new byte[0], err, headers);
		}
	}
}
