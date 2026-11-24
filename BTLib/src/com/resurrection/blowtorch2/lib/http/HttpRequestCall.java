package com.resurrection.blowtorch2.lib.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

	/**
	 * Why these request headers cannot be sent, or null. Names are HTTP
	 * tokens; values carry no CR, LF or NUL. Length and framing headers are
	 * the connection's, not the script's.
	 */
	public static String headerRejection(Map<String, String> headers) {
		if (headers == null) {
			return null;
		}
		for (Map.Entry<String, String> e : headers.entrySet()) {
			String name = e.getKey();
			String value = e.getValue();
			if (name == null || name.length() == 0 || !isToken(name)) {
				return "header name is not valid: " + name;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if ("content-length".equals(lower) || "transfer-encoding".equals(lower)
					|| "host".equals(lower) || "connection".equals(lower)) {
				return "header " + name + " is set by the connection";
			}
			if (value == null) {
				return "header " + name + " has no value";
			}
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				if (c == '\r' || c == '\n' || c == 0) {
					return "header " + name + " contains a line break";
				}
			}
		}
		return null;
	}

	/** True when {@code headers} sets {@code name}, in any case. */
	public static boolean hasHeader(Map<String, String> headers, String name) {
		if (headers == null) {
			return false;
		}
		for (String key : headers.keySet()) {
			if (key != null && key.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Response headers for Lua: names lower-cased, repeated headers joined
	 * with "\n" (a Set-Cookie Expires date contains a comma). The status line
	 * (null key) is left out.
	 */
	public static Map<String, String> responseHeaders(Map<String, List<String>> fields) {
		Map<String, String> out = new LinkedHashMap<String, String>();
		if (fields == null) {
			return out;
		}
		for (Map.Entry<String, List<String>> e : fields.entrySet()) {
			if (e.getKey() == null || e.getValue() == null) {
				continue;
			}
			String name = e.getKey().toLowerCase(Locale.ROOT);
			StringBuilder joined = new StringBuilder();
			String earlier = out.get(name);
			if (earlier != null) {
				joined.append(earlier);
			}
			for (Iterator<String> it = e.getValue().iterator(); it.hasNext();) {
				String v = it.next();
				if (v == null) {
					continue;
				}
				if (joined.length() > 0) {
					joined.append('\n');
				}
				joined.append(v);
			}
			out.put(name, joined.toString());
		}
		return out;
	}

	private static boolean isToken(String name) {
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
					|| (c >= '0' && c <= '9') || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
			if (!ok) {
				return false;
			}
		}
		return true;
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
