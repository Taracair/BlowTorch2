package com.resurrection.blowtorch2.lib.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * What {@code HttpRequest} may send. GET and POST, http and https. A body
 * on GET is refused: writing one makes {@code HttpURLConnection} send POST.
 */
public final class HttpRequestCall {

	public static final int BODY_CAP = 256 * 1024;
	public static final int TIMEOUT_MS = 10_000;
	public static final String POST_CONTENT_TYPE = "text/plain; charset=utf-8";

	private HttpRequestCall() {
	}

	/** Why this call cannot start, or null when it can. */
	public static String rejection(String method, String url, byte[] body, String function) {
		if (function == null || function.length() == 0) {
			return "function name is empty";
		}
		String verb = canonicalMethod(method);
		if (verb == null) {
			return "method must be GET or POST";
		}
		if ("GET".equals(verb) && body != null && body.length > 0) {
			return "GET does not take a body";
		}
		return schemeProblem(url);
	}

	/** {@code GET} or {@code POST}, or null. */
	public static String canonicalMethod(String method) {
		if (method == null) {
			return null;
		}
		String verb = method.trim();
		if ("GET".equalsIgnoreCase(verb)) {
			return "GET";
		}
		if ("POST".equalsIgnoreCase(verb)) {
			return "POST";
		}
		return null;
	}

	/**
	 * Read at most {@code cap} bytes. Over the cap the bytes are empty:
	 * a truncated page is not a result.
	 */
	public static Read read(InputStream in, int cap) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		long total = 0;
		int n;
		while ((n = in.read(buf)) > 0) {
			if (total + n > cap) {
				return new Read(true, new byte[0]);
			}
			total += n;
			out.write(buf, 0, n);
		}
		return new Read(false, out.toByteArray());
	}

	private static String schemeProblem(String url) {
		if (url == null || url.trim().length() == 0) {
			return "url is empty";
		}
		URI uri;
		try {
			uri = new URI(url.trim());
		} catch (URISyntaxException e) {
			return "url is not http or https";
		}
		String scheme = uri.getScheme();
		if (scheme == null
				|| (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
			return "url is not http or https";
		}
		if (uri.getHost() == null || uri.getHost().length() == 0) {
			return "url is not http or https";
		}
		return null;
	}

	public static final class Read {
		public final boolean overCap;
		public final byte[] bytes;

		Read(boolean overCap, byte[] bytes) {
			this.overCap = overCap;
			this.bytes = bytes;
		}
	}
}
