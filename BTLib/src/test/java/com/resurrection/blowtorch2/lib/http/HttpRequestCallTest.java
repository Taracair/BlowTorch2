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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	/**
	 * The batshoppe flow from the player's report: a form POST that needs its
	 * own Content-Type plus Cookie and Referer, and two cookies coming back.
	 */
	@Test
	public void headersGoOutAsGivenAndComeBackLowerCased() throws IOException {
		ServerSocket form = listen();
		final AtomicReference<String> head = new AtomicReference<String>();
		final AtomicReference<String> posted = new AtomicReference<String>();
		serve(form, "HTTP/1.1 200 OK\r\nSet-Cookie: PHPSESSID=abc; path=/\r\n"
				+ "Set-Cookie: seen=1; Expires=Wed, 21 Oct 2026 07:28:00 GMT\r\n", "found",
				head, posted);
		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("content-type", "application/x-www-form-urlencoded");
		headers.put("Cookie", "PHPSESSID=abc");
		headers.put("Referer", "http://example.org/monsieinfo.php");
		try {
			HttpRequestFetch.Outcome sent = HttpRequestFetch.fetch("POST",
					"http://127.0.0.1:" + form.getLocalPort() + "/monsieinfo.php",
					"csrf_token=t&monsiestring=orc".getBytes(UTF8), headers, true);
			assertTrue(sent.ok);
			assertEquals("csrf_token=t&monsiestring=orc", posted.get());
			assertEquals("application/x-www-form-urlencoded",
					header(head.get(), "content-type"));
			assertEquals("PHPSESSID=abc", header(head.get(), "cookie"));
			assertEquals("http://example.org/monsieinfo.php", header(head.get(), "referer"));
			assertEquals(1, count(head.get(), "content-type"));
			// The desktop JDK returns repeated headers newest first; order is not the contract.
			List<String> cookies = Arrays.asList(sent.headers.get("set-cookie").split("\n"));
			assertEquals(2, cookies.size());
			assertTrue(cookies.contains("PHPSESSID=abc; path=/"));
			assertTrue(cookies.contains("seen=1; Expires=Wed, 21 Oct 2026 07:28:00 GMT"));
		} finally {
			form.close();
		}
	}

	@Test
	public void aRedirectComesBackItselfWhenNotFollowed() throws IOException {
		ServerSocket moved = listen();
		serve(moved, "HTTP/1.1 302 Found\r\nLocation: /next\r\nSet-Cookie: s=1\r\n", "",
				null, null);
		try {
			HttpRequestFetch.Outcome got = HttpRequestFetch.fetch("GET",
					"http://127.0.0.1:" + moved.getLocalPort() + "/start", null, null, false);
			assertFalse(got.ok);
			assertEquals(302, got.status);
			assertEquals("/next", got.headers.get("location"));
			assertEquals("s=1", got.headers.get("set-cookie"));
		} finally {
			moved.close();
		}
	}

	@Test
	public void headersThatWouldBreakTheRequestAreRefused() {
		assertNull(HttpRequestCall.headerRejection(null));
		assertNull(HttpRequestCall.headerRejection(one("Referer", "http://a/b")));
		assertEquals("header X has no value", HttpRequestCall.headerRejection(one("X", null)));
		assertEquals("header X contains a line break",
				HttpRequestCall.headerRejection(one("X", "a\r\nHost: evil")));
		assertEquals("header name is not valid: Bad Name",
				HttpRequestCall.headerRejection(one("Bad Name", "v")));
		assertEquals("header Content-Length is set by the connection",
				HttpRequestCall.headerRejection(one("Content-Length", "3")));
		assertTrue(HttpRequestCall.hasHeader(one("CONTENT-TYPE", "x"), "Content-Type"));
		assertFalse(HttpRequestCall.hasHeader(null, "Content-Type"));
	}

	@Test
	public void responseHeadersSkipTheStatusLineAndMergeCase() {
		Map<String, List<String>> fields = new LinkedHashMap<String, List<String>>();
		fields.put(null, Arrays.asList("HTTP/1.1 200 OK"));
		fields.put("Set-Cookie", Arrays.asList("a=1"));
		fields.put("set-cookie", Arrays.asList("b=2"));
		Map<String, String> got = HttpRequestCall.responseHeaders(fields);
		assertEquals(1, got.size());
		assertEquals("a=1\nb=2", got.get("set-cookie"));
	}

	private static Map<String, String> one(String name, String value) {
		Map<String, String> m = new HashMap<String, String>();
		m.put(name, value);
		return m;
	}

	private static int count(String head, String name) {
		int n = 0;
		for (String line : head.split("\r\n")) {
			if (line.toLowerCase(Locale.US).startsWith(name + ":")) {
				n++;
			}
		}
		return n;
	}

	/** {@code statusAndHeaders} is the status line plus header lines, each ending CRLF. */
	private static void serve(final ServerSocket listen, final String statusAndHeaders,
			final String body, final AtomicReference<String> headOut,
			final AtomicReference<String> bodyOut) {
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
						if (headOut != null) {
							headOut.set(head);
						}
						if (bodyOut != null) {
							bodyOut.set(new String(payload, UTF8));
						}
						byte[] page = body.getBytes(UTF8);
						OutputStream out = socket.getOutputStream();
						out.write((statusAndHeaders + "Content-Length: " + page.length
								+ "\r\nConnection: close\r\n\r\n").getBytes(UTF8));
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
