package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ConnectedPreambleTest {

	@Test
	public void nothingToSendIsNull() {
		assertNull(ConnectedPreamble.concat(null));
		assertNull(ConnectedPreamble.concat(Collections.<byte[]>emptyList()));
		assertNull(ConnectedPreamble.concat(Arrays.asList((byte[]) null, new byte[0])));
	}

	@Test
	public void piecesAreJoinedInOrderAndInputsStayPut() {
		byte[] first = new byte[] { 0x1b, 'h' };
		byte[] second = new byte[] { 'i', '\n' };
		byte[] out = ConnectedPreamble.concat(Arrays.asList(null, first, new byte[0], second));
		assertArrayEquals(new byte[] { 0x1b, 'h', 'i', '\n' }, out);
		assertArrayEquals(new byte[] { 0x1b, 'h' }, first);
		assertArrayEquals(new byte[] { 'i', '\n' }, second);
		out[0] = 0;
		assertArrayEquals(new byte[] { 0x1b, 'h' }, first);
	}

	@Test
	public void onePieceIsCopied() {
		byte[] only = new byte[] { 'V', '1', '\n' };
		byte[] out = ConnectedPreamble.concat(Collections.singletonList(only));
		assertArrayEquals(only, out);
		out[0] = 'X';
		assertArrayEquals(new byte[] { 'V', '1', '\n' }, only);
	}

	@Test
	public void preambleIsWrittenAndFlushedWithNoExtraBytes() throws IOException {
		byte[] hello = new byte[] { 0x1b, 'b', 'c', ' ', '1', '\n' };
		FlushWatch out = new FlushWatch();
		DataPumper.writePreamble(out, hello);
		assertArrayEquals(hello, out.toByteArray());
		assertEquals(1, out.flushes);
	}

	@Test
	public void emptyPreambleDoesNotTouchTheStream() throws IOException {
		FlushWatch out = new FlushWatch();
		DataPumper.writePreamble(out, null);
		DataPumper.writePreamble(out, new byte[0]);
		DataPumper.writePreamble(null, new byte[] { 'x' });
		assertArrayEquals(new byte[0], out.toByteArray());
		assertEquals(0, out.flushes);
	}

	private static final class FlushWatch extends ByteArrayOutputStream {
		private int flushes;

		@Override
		public void flush() throws IOException {
			flushes++;
			super.flush();
		}
	}
}
