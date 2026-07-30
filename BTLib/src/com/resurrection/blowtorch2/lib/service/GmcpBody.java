package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The shape of a GMCP packet body, decided before anything tries to use it.
 *
 * <p>GMCP is <code>Module.Name</code> followed by an optional JSON value. The
 * decode path here used to assume that value was always a JSON <em>object</em>
 * and called <code>new JSONObject(body)</code> on it, so a server sending an
 * array got a red <code>[GMCP ERR] parse failed</code> line and had its packet
 * dropped. We send <code>core.supports.set ["Char 1", …]</code> as an array
 * ourselves, so that rejected a shape we rely on.
 *
 * <p>Classification is by first character and then a strict check, deliberately
 * <em>not</em> by handing the text to {@link org.json.JSONTokener} and seeing
 * what falls out. Both org.json implementations in play here — Android's on the
 * device, the reference one on the JVM test classpath — are lenient enough to
 * turn <code>()</code> into the string <code>"()"</code>, which would classify
 * every piece of garbage as a legal scalar and silence the error path
 * altogether. The point of this class is to narrow what counts as an error, not
 * to remove it.
 *
 * <p>Nothing here touches Android, so it is covered by {@code GmcpBodyTest}.
 */
public final class GmcpBody {

	/** What arrived after the module name. */
	public enum Shape {
		/** No body at all: <code>Core.Ping</code>. */
		ABSENT,
		/** A JSON object: <code>{"hp": 100}</code>. */
		OBJECT,
		/** A JSON array: <code>["Char 1", "Room 1"]</code>. */
		ARRAY,
		/** A legal JSON scalar: a quoted string, number, true, false or null. */
		SCALAR,
		/** Not JSON. This is the only shape that is an error. */
		MALFORMED
	}

	/** An absent body, shared: it carries no state. */
	private static final GmcpBody ABSENT_BODY =
			new GmcpBody(Shape.ABSENT, "", null, null, null);

	private final Shape shape;
	private final String raw;
	private final JSONObject object;
	private final JSONArray array;
	private final String error;

	private GmcpBody(final Shape shape, final String raw, final JSONObject object,
			final JSONArray array, final String error) {
		this.shape = shape;
		this.raw = raw;
		this.object = object;
		this.array = array;
		this.error = error;
	}

	/**
	 * Classify the text that followed a module name.
	 *
	 * @param body The body as it arrived, already trimmed; null or empty for a
	 *             packet that carried no body.
	 * @return Never null.
	 */
	public static GmcpBody of(final String body) {
		if (body == null) {
			return ABSENT_BODY;
		}
		String text = body.trim();
		if (text.length() == 0) {
			return ABSENT_BODY;
		}
		char first = text.charAt(0);
		if (first == '{') {
			try {
				return new GmcpBody(Shape.OBJECT, text, new JSONObject(text), null, null);
			} catch (JSONException e) {
				return malformed(text, e);
			}
		}
		if (first == '[') {
			try {
				return new GmcpBody(Shape.ARRAY, text, null, new JSONArray(text), null);
			} catch (JSONException e) {
				return malformed(text, e);
			}
		}
		if (isJsonScalar(text)) {
			return new GmcpBody(Shape.SCALAR, text, null, null, null);
		}
		return new GmcpBody(Shape.MALFORMED, text, null, null, "not JSON: " + text);
	}

	private static GmcpBody malformed(final String text, final JSONException e) {
		String message = e.getMessage();
		return new GmcpBody(Shape.MALFORMED, text, null, null,
				message == null ? "unparseable JSON" : message);
	}

	/**
	 * True for the JSON values that are neither object nor array.
	 *
	 * <p>Hand-checked rather than delegated, for the leniency reason in the class
	 * comment. A scalar body is legal JSON and is not worth an error line, but
	 * there is nothing to put in the GMCP table either.
	 */
	private static boolean isJsonScalar(final String text) {
		if ("null".equals(text) || "true".equals(text) || "false".equals(text)) {
			return true;
		}
		if (text.charAt(0) == '"') {
			return isJsonString(text);
		}
		return isJsonNumber(text);
	}

	/** A complete double-quoted JSON string and nothing after it. */
	private static boolean isJsonString(final String text) {
		if (text.length() < 2 || text.charAt(text.length() - 1) != '"') {
			return false;
		}
		int i = 1;
		int last = text.length() - 1;
		while (i < last) {
			char c = text.charAt(i);
			if (c == '\\') {
				// Whatever it escapes, it is not a terminator. A trailing
				// backslash then runs i past last and fails below, correctly.
				i += 2;
				continue;
			}
			if (c == '"') {
				// Closed early: there is trailing junk, e.g. "a" "b".
				return false;
			}
			i++;
		}
		return i == last;
	}

	/** JSON's number grammar, which is narrower than {@code Double.parseDouble}. */
	private static boolean isJsonNumber(final String text) {
		int i = 0;
		int n = text.length();
		if (i < n && text.charAt(i) == '-') {
			i++;
		}
		int intStart = i;
		while (i < n && isDigit(text.charAt(i))) {
			i++;
		}
		if (i == intStart) {
			return false;
		}
		if (text.charAt(intStart) == '0' && i - intStart > 1) {
			return false; // 007 is not JSON
		}
		if (i < n && text.charAt(i) == '.') {
			i++;
			int fracStart = i;
			while (i < n && isDigit(text.charAt(i))) {
				i++;
			}
			if (i == fracStart) {
				return false;
			}
		}
		if (i < n && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
			i++;
			if (i < n && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
				i++;
			}
			int expStart = i;
			while (i < n && isDigit(text.charAt(i))) {
				i++;
			}
			if (i == expStart) {
				return false;
			}
		}
		return i == n;
	}

	private static boolean isDigit(final char c) {
		return c >= '0' && c <= '9';
	}

	public Shape shape() {
		return shape;
	}

	/** The body as it arrived, trimmed; empty string when {@link Shape#ABSENT}. */
	public String raw() {
		return raw;
	}

	/** The parsed object, or null unless {@link Shape#OBJECT}. */
	public JSONObject object() {
		return object;
	}

	/** The parsed array, or null unless {@link Shape#ARRAY}. */
	public JSONArray array() {
		return array;
	}

	/** Why it did not parse, or null unless {@link Shape#MALFORMED}. */
	public String error() {
		return error;
	}

	/**
	 * The text to hand a consumer that wants JSON — extra text slots, Lua.
	 *
	 * <p>Object and array come back re-serialised, absent comes back as
	 * <code>{}</code>, and anything else comes back exactly as it arrived.
	 *
	 * <p>For object, absent and malformed that is byte-for-byte what those
	 * callers received before this class existed. An array body is the one
	 * difference: it used to arrive as the raw text, because it only ever reached
	 * a consumer down the failed-parse path. It is re-serialised now, for the
	 * same reason the object path always was.
	 */
	public String json() {
		switch (shape) {
		case OBJECT:
			return object.toString();
		case ARRAY:
			return array.toString();
		case ABSENT:
			return "{}";
		default:
			return raw;
		}
	}

	/**
	 * An array body read as a list of strings, dropping empty entries.
	 *
	 * <p>This is the shape a supports list arrives in: <code>["Core 1",
	 * "Char 1", …]</code>. Non-string entries are coerced the way both org.json
	 * implementations coerce them, so a number arrives as its digits; that is
	 * better than dropping an entry a server meant to send.
	 *
	 * @return Empty unless {@link Shape#ARRAY}.
	 */
	public ArrayList<String> asStringList() {
		ArrayList<String> out = new ArrayList<String>();
		if (array == null) {
			return out;
		}
		for (int i = 0; i < array.length(); i++) {
			String token = array.optString(i, "").trim();
			if (token.length() > 0) {
				out.add(token);
			}
		}
		return out;
	}
}
