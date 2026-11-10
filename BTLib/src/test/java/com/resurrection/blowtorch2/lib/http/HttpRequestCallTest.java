package com.resurrection.blowtorch2.lib.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * The page below is the literal {@code candle}. The cap and the refused
 * calls are the contract, not a reading of the method's return.
 */
public class HttpRequestCallTest {

	private static final Charset UTF8 = Charset.forName("UTF-8");

	@Test
	public void refusesTheCallsThatWouldNotBeGetOrPost() {
		assertEquals("function name is empty",
				HttpRequestCall.rejection("GET", "https://example.org/room/1", null, ""));
		assertEquals("method must be GET or POST",
				HttpRequestCall.rejection("PUT", "https://example.org/room/1", null, "OnHttp"));
		assertEquals("GET does not take a body",
				HttpRequestCall.rejection("GET", "https://example.org/room/1",
						new byte[] { 1 }, "OnHttp"));
		assertEquals("url is not http or https",
				HttpRequestCall.rejection("GET", "file:///tmp/x", null, "OnHttp"));
		assertEquals("url is empty",
				HttpRequestCall.rejection("GET", "  ", null, "OnHttp"));
		assertNull(HttpRequestCall.rejection("get", "http://example.org/room/1", null, "OnHttp"));
		assertNull(HttpRequestCall.rejection("POST", "https://example.org/room/1",
				"{}".getBytes(UTF8), "OnHttp"));
		assertEquals("GET", HttpRequestCall.canonicalMethod(" get "));
	}

	@Test
	public void anExactCapIsKeptAndOneExtraByteIsDropped() throws IOException {
		byte[] exact = new byte[8];
		exact[0] = 'c';
		HttpRequestCall.Read kept = HttpRequestCall.read(new ByteArrayInputStream(exact), 8);
		assertFalse(kept.overCap);
		assertArrayEquals(exact, kept.bytes);
		byte[] extra = new byte[9];
		HttpRequestCall.Read dropped = HttpRequestCall.read(new ByteArrayInputStream(extra), 8);
		assertTrue(dropped.overCap);
		assertEquals(0, dropped.bytes.length);
	}

	@Test
	public void getReadsThePageAndPostSendsTheBody() throws IOException {
		ServerSocket page = listen();
		ServerSocket missing = listen();
		ServerSocket note = listen();
		final AtomicReference<String> posted = new AtomicReference<String>();
		final AtomicReference<String> type = new AtomicReference<String>();
		accept(page, "HTTP/1.1 200 OK", "candle", null, null);
		accept(missing, "HTTP/1.1 404 Not Found", "gone", null, null);
		accept(note, "HTTP/1.1 200 OK", "saved", posted, type);
		try {
			HttpRequestFetch.Outcome got = HttpRequestFetch.fetch("GET",
					"http://127.0.0.1:" + page.getLocalPort() + "/room", null);
			assertTrue(got.ok);
			assertEquals(200, got.status);
			assertEquals("candle", new String(got.body, UTF8));
			assertNull(got.err);

			HttpRequestFetch.Outcome lost = HttpRequestFetch.fetch("GET",
					"http://127.0.0.1:" + missing.getLocalPort() + "/missing", null);
			assertFalse(lost.ok);
			assertEquals(404, lost.status);
			assertEquals("gone", new String(lost.body, UTF8));
			assertEquals("server answered 404", lost.err);

			HttpRequestFetch.Outcome sent = HttpRequestFetch.fetch("POST",
					"http://127.0.0.1:" + note.getLocalPort() + "/note",
					"altar".getBytes(UTF8));
			assertTrue(sent.ok);
			assertEquals("altar", posted.get());
			assertEquals("text/plain; charset=utf-8", type.get());
			assertEquals("saved", new String(sent.body, UTF8));
		} finally {
			page.close();
			missing.close();
			note.close();
		}
	}

	private static ServerSocket listen() throws IOException {
		return new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
	}

	private static void accept(final ServerSocket listen, final String status, final String body,
			final AtomicReference<String> posted, final AtomicReference<String> type) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					Socket socket = listen.accept();
					try {
						InputStream in = socket.getInputStream();
						String head = readHead(in);
						int length = headerInt(head, "content-length");
						byte[] payload = new byte[length];
						int off = 0;
						while (off < length) {
							int n = in.read(payload, off, length - off);
							if (n < 0) {
								break;
							}
							off += n;
						}
						if (posted != null) {
							posted.set(new String(payload, UTF8));
						}
						if (type != null) {
							type.set(header(head, "content-type"));
						}
						byte[] page = body.getBytes(UTF8);
						String reply = status + "\r\nContent-Length: " + page.length
								+ "\r\nConnection: close\r\n\r\n";
						OutputStream out = socket.getOutputStream();
						out.write(reply.getBytes(UTF8));
						out.write(page);
						out.flush();
					} finally {
						socket.close();
					}
				} catch (IOException ignored) {
				}
			}
		});
		thread.setDaemon(true);
		thread.start();
	}

	private static String readHead(InputStream in) throws IOException {
		StringBuilder head = new StringBuilder();
		int prev = 0;
		int prev2 = 0;
		int prev3 = 0;
		while (true) {
			int c = in.read();
			if (c < 0) {
				break;
			}
			head.append((char) c);
			if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && c == '\n') {
				break;
			}
			prev3 = prev2;
			prev2 = prev;
			prev = c;
		}
		return head.toString();
	}

	private static String header(String head, String name) {
		String prefix = name.toLowerCase(Locale.US) + ":";
		for (String line : head.split("\r\n")) {
			if (line.toLowerCase(Locale.US).startsWith(prefix)) {
				return line.substring(prefix.length()).trim();
			}
		}
		return null;
	}

	private static int headerInt(String head, String name) {
		String value = header(head, name);
		if (value == null || value.length() == 0) {
			return 0;
		}
		return Integer.parseInt(value);
	}
}
