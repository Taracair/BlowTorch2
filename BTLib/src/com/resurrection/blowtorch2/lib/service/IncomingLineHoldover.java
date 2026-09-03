package com.resurrection.blowtorch2.lib.service;

/**
 * Hold a mid-chunk half-line from matching <em>and</em> from the display until
 * the rest arrives (measured 11 of 105 chunks on a live world). Flush on a
 * timeout so prompts appear. Bytes, after telnet and before decode: {@code 0x0A}
 * cannot sit inside a multi-byte character or an ANSI escape.
 */
public final class IncomingLineHoldover {

	/**
	 * How long a half-line may wait for the rest of itself. Long enough that a
	 * continuation arriving on the next packet is always joined, short enough
	 * that a prompt appearing this late is not something a player can perceive.
	 */
	public static final int DEFAULT_FLUSH_MS = 150;

	/**
	 * Never hold more than this. A world that sends a very long stretch with no
	 * newline in it — ASCII art, a dump, a broken encoder — must not grow this
	 * buffer without bound or vanish from the screen while it does. Past this,
	 * what is held is released even though it is still incomplete: showing it
	 * late beats not showing it.
	 */
	public static final int MAX_HELD_BYTES = 64 * 1024;

	private static final byte NEWLINE = (byte) '\n';

	private static final byte[] EMPTY = new byte[0];

	private byte[] held = EMPTY;

	/**
	 * Take a chunk, give back the part that is safe to process now.
	 *
	 * <p>Anything held from last time is joined onto the front, so the caller
	 * sees whole lines in the order they were sent.
	 *
	 * @param incoming the chunk as it arrived; not modified.
	 * @return the leading whole-line part, which may be empty when the whole
	 *         chunk was a fragment. Never null.
	 */
	public byte[] accept(final byte[] incoming) {
		byte[] combined;
		if (held.length == 0) {
			combined = incoming == null ? EMPTY : incoming;
		} else if (incoming == null || incoming.length == 0) {
			combined = held;
		} else {
			combined = new byte[held.length + incoming.length];
			System.arraycopy(held, 0, combined, 0, held.length);
			System.arraycopy(incoming, 0, combined, held.length, incoming.length);
		}
		if (combined.length == 0) {
			held = EMPTY;
			return EMPTY;
		}

		int lastNewline = -1;
		for (int i = combined.length - 1; i >= 0; i--) {
			if (combined[i] == NEWLINE) {
				lastNewline = i;
				break;
			}
		}

		if (lastNewline < 0) {
			// Not one whole line yet. Hold it all, unless holding it has stopped
			// being reasonable.
			if (combined.length >= MAX_HELD_BYTES) {
				held = EMPTY;
				return combined;
			}
			held = combined;
			return EMPTY;
		}

		int completeLength = lastNewline + 1;
		int tailLength = combined.length - completeLength;
		byte[] complete = new byte[completeLength];
		System.arraycopy(combined, 0, complete, 0, completeLength);
		if (tailLength == 0) {
			held = EMPTY;
		} else {
			held = new byte[tailLength];
			System.arraycopy(combined, completeLength, held, 0, tailLength);
		}
		return complete;
	}

	/** True when a fragment is waiting, so the caller should arm its timer. */
	public boolean hasHeld() {
		return held.length > 0;
	}

	/**
	 * Give up waiting and hand back what is held — the prompt case, and the
	 * disconnect case, where the rest is never coming.
	 *
	 * @return the held bytes, possibly empty. Never null.
	 */
	public byte[] flush() {
		byte[] out = held;
		held = EMPTY;
		return out;
	}

	/** Drop anything held without emitting it, for a connection being torn down. */
	public void clear() {
		held = EMPTY;
	}
}
