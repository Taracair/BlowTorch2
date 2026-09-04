package com.resurrection.blowtorch2.lib.service;

/**
 * Telnet text is not a terminal: {@code \\r} does not move to column 0.
 * Processor used to drop every CR, so {@code \\r\\n} still became a newline
 * but a bare CR between two rows vanished and concatenated them. A CR at the
 * end of a chunk is held in case the next chunk is the LF of a CRLF pair.
 */
public final class CrToNewline {

	static final byte CR = 0x0D;
	static final byte LF = 0x0A;

	private boolean pendingCr;

	/**
	 * @return a new array; never null. Empty input leaves a pending CR held.
	 */
	public byte[] apply(final byte[] in) {
		if (in == null || in.length == 0) {
			return in == null ? new byte[0] : in;
		}
		byte[] out = new byte[in.length + 1];
		int n = 0;
		int i = 0;
		if (pendingCr) {
			pendingCr = false;
			if (in[0] != LF) {
				out[n++] = LF;
			}
		}
		for (; i < in.length; i++) {
			final byte b = in[i];
			if (b != CR) {
				out[n++] = b;
				continue;
			}
			if (i + 1 >= in.length) {
				pendingCr = true;
				break;
			}
			if (in[i + 1] != LF) {
				out[n++] = LF;
			}
		}
		if (n == 0) {
			return new byte[0];
		}
		if (n == out.length) {
			return out;
		}
		byte[] trunc = new byte[n];
		System.arraycopy(out, 0, trunc, 0, n);
		return trunc;
	}

	boolean hasPendingCr() {
		return pendingCr;
	}
}
