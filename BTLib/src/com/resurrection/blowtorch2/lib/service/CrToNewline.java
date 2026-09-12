package com.resurrection.blowtorch2.lib.service;

/**
 * Telnet text is not a terminal: {@code \\r} does not move to column 0.
 * CRLF and LFCR are one newline; a bare CR becomes a newline. A CR at the
 * end of a chunk is held in case the next chunk is the LF of a CRLF pair.
 * 12 Sep 2026: a SMAUG banner was 32 LFCR and 2 CRLF, 0 bare CR.
 */
public final class CrToNewline {

	static final byte CR = 0x0D;
	static final byte LF = 0x0A;

	private boolean pendingCr;
	/** Previous wire byte was LF, including across chunks (LFCR split). */
	private boolean lastWireWasLf;

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
			if (in[0] != LF && !lastWireWasLf) {
				out[n++] = LF;
			}
			lastWireWasLf = false;
		}
		for (; i < in.length; i++) {
			final byte b = in[i];
			if (b != CR) {
				out[n++] = b;
				lastWireWasLf = (b == LF);
				continue;
			}
			if (i + 1 >= in.length) {
				if (lastWireWasLf) {
					lastWireWasLf = false;
					break;
				}
				pendingCr = true;
				break;
			}
			if (in[i + 1] == LF || lastWireWasLf) {
				lastWireWasLf = false;
				continue;
			}
			out[n++] = LF;
			lastWireWasLf = false;
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
