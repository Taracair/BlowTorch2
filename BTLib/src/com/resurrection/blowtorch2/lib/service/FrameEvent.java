package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One thing that happened to a {@code mudstd.frame} frame, on its way from the
 * service process to the UI process.
 *
 * <p>The protocol arrives in {@code :stellar} and the window that shows it lives
 * in the UI process, so something has to cross the binder. This is that
 * something: a flat value with no Android in it, encoded as a JSON array the
 * same way the mapper snapshot is.
 *
 * <p>Events are queued and taken in a batch rather than delivered one per
 * binder call. A server opening a frame and filling it sends {@code open} and
 * {@code image} back to back, and the UI redrawing twice for that is work for
 * nothing.
 *
 * <p><b>The image field is not the image.</b> It is what the server put in the
 * {@code image} field: a URL, or {@code base64:…}. Fetching a URL is the UI
 * side's job precisely because it must not happen on a main thread, and the one
 * in the service is the thread that reads the socket.
 */
public final class FrameEvent {

	/** A frame appeared. Carries label, type, content and requested size. */
	public static final String OP_OPEN = "open";
	/** Content for a frame that is already open. Carries {@link #getImage}. */
	public static final String OP_IMAGE = "image";
	/**
	 * A picture to load for a marker in the game text rather than for a window.
	 *
	 * <p>The id is the marker's key, not a frame id: the marker stays where it
	 * was printed and keeps its picture while the frame moves on to the next
	 * one. See {@link InlineImageMarker}.
	 */
	public static final String OP_INLINE = "inline";
	/** The frame is gone — the server closed it, or we refused it. */
	public static final String OP_CLOSE = "close";
	/** Every frame is gone: the connection ended. Carries no id. */
	public static final String OP_CLEAR = "clear";

	/**
	 * The most base64 we will carry over the binder, in characters.
	 *
	 * <p>A binder transaction has about a megabyte to work with and it is shared
	 * across the process, so a big enough payload does not fail politely — it
	 * takes an unrelated call down with it. 512k of base64 is about 384 KB of
	 * image, far past anything a MUD needs, and past it we say so rather than
	 * gamble. A URL has no such limit because only the URL crosses.
	 */
	public static final int MAX_BASE64_CHARS = 512 * 1024;

	private final String op;
	private final String id;
	private final String label;
	private final String type;
	private final String content;
	private final int sizeChars;
	private final String image;

	public FrameEvent(final String op, final String id, final String label, final String type,
			final String content, final int sizeChars, final String image) {
		this.op = op == null ? "" : op;
		this.id = id == null ? "" : id;
		this.label = label == null ? "" : label;
		this.type = type == null ? "" : type;
		this.content = content == null ? "" : content;
		this.sizeChars = sizeChars;
		this.image = image == null ? "" : image;
	}

	public static FrameEvent open(final String id, final String label, final String type,
			final String content, final int sizeChars) {
		return new FrameEvent(OP_OPEN, id, label, type, content, sizeChars, "");
	}

	public static FrameEvent image(final String id, final String image) {
		return new FrameEvent(OP_IMAGE, id, "", "", "", 0, image);
	}

	/** @param key The marker key from {@link InlineImageMarker}, not a frame id. */
	public static FrameEvent inline(final String key, final String image) {
		return new FrameEvent(OP_INLINE, key, "", "", "", 0, image);
	}

	public static FrameEvent close(final String id) {
		return new FrameEvent(OP_CLOSE, id, "", "", "", 0, "");
	}

	public static FrameEvent clear() {
		return new FrameEvent(OP_CLEAR, "", "", "", "", 0, "");
	}

	public String getOp() {
		return op;
	}

	public String getId() {
		return id;
	}

	/** The server's human-readable name for the frame; may be empty. */
	public String getLabel() {
		return label;
	}

	public String getType() {
		return type;
	}

	public String getContent() {
		return content;
	}

	/** Requested width in characters, or 0 when the server did not ask in characters. */
	public int getSizeChars() {
		return sizeChars;
	}

	/** The raw {@code image} field: a URL, or {@code base64:…}. */
	public String getImage() {
		return image;
	}

	/**
	 * True when the payload is too big to hand across the binder.
	 *
	 * <p>Only base64 has a size on this side. A URL is a few dozen bytes here
	 * whatever the picture behind it weighs.
	 */
	public boolean isOversizedPayload() {
		return image.length() > MAX_BASE64_CHARS;
	}

	public static String toJson(final List<FrameEvent> events) {
		JSONArray arr = new JSONArray();
		if (events != null) {
			for (int i = 0; i < events.size(); i++) {
				FrameEvent e = events.get(i);
				if (e == null) {
					continue;
				}
				arr.put(e.toJsonObject());
			}
		}
		return arr.toString();
	}

	JSONObject toJsonObject() {
		JSONObject o = new JSONObject();
		try {
			o.put("op", op);
			o.put("id", id);
			if (label.length() > 0) {
				o.put("label", label);
			}
			if (type.length() > 0) {
				o.put("type", type);
			}
			if (content.length() > 0) {
				o.put("content", content);
			}
			if (sizeChars != 0) {
				o.put("sizeChars", sizeChars);
			}
			if (image.length() > 0) {
				o.put("image", image);
			}
		} catch (org.json.JSONException e) {
			// Every key here is a literal and every value a String or int, so
			// there is no input that reaches this. Returning what was built is
			// better than losing the batch.
			return o;
		}
		return o;
	}

	/**
	 * Decode a batch.
	 *
	 * @param json What {@link #toJson} produced; null, empty and malformed all
	 *        give an empty list rather than throwing. A frame batch that cannot
	 *        be read is a frame that does not appear, and that is survivable;
	 *        an exception on the UI thread is not.
	 */
	public static ArrayList<FrameEvent> parse(final String json) {
		ArrayList<FrameEvent> out = new ArrayList<FrameEvent>();
		if (json == null || json.length() == 0) {
			return out;
		}
		try {
			JSONArray arr = new JSONArray(json);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.optJSONObject(i);
				if (o == null) {
					continue;
				}
				out.add(new FrameEvent(o.optString("op", ""), o.optString("id", ""),
						o.optString("label", ""), o.optString("type", ""),
						o.optString("content", ""), o.optInt("sizeChars", 0),
						o.optString("image", "")));
			}
		} catch (org.json.JSONException e) {
			return out;
		}
		return out;
	}

	@Override
	public String toString() {
		return "FrameEvent[" + op + " " + id + "]";
	}
}
