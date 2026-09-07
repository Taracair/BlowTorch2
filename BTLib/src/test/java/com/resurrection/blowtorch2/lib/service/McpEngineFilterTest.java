package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Test;

public class McpEngineFilterTest {

	@Test
	public void useOffOmitOnStripsHelloKeepsRoomAndDoesNotHandshake()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);

		byte[] out = engine.filterIncoming(
				"#$#mcp version: 2.1 to: 2.1\nYou see a room.\n".getBytes("UTF-8"));

		assertEquals("You see a room.\n", new String(out, "UTF-8"));
		assertTrue(engine.serverOfferedHello());
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffOmitOnStripsStatusUpdateAndDoesNotSend()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);

		byte[] out = engine.filterIncoming(
				"#$#dns-org-hellmoo-status-update foo: 1\n".getBytes("UTF-8"));

		assertEquals("", new String(out, "UTF-8"));
		assertFalse(engine.serverOfferedHello());
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffOmitOffKeepsHelloAndDoesNotHandshake()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(false);

		byte[] out = engine.filterIncoming(
				"#$#mcp version: 2.1 to: 2.1\n".getBytes("UTF-8"));

		assertEquals("#$#mcp version: 2.1 to: 2.1\n", new String(out, "UTF-8"));
		assertTrue(engine.serverOfferedHello());
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffUnquotesQuotedInBandHashDollar()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);

		byte[] out = engine.filterIncoming("#$\"#$#not-mcp\n".getBytes("UTF-8"));

		assertEquals("#$#not-mcp\n", new String(out, "UTF-8"));
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffCompleteGameLineKeepsOriginalBytes()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);
		byte[] raw = "You see a room.\n".getBytes("UTF-8");

		byte[] out = engine.filterIncoming(raw);

		assertSame(raw, out);
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffIncompleteGameLineKeepsOriginalBytes()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);
		byte[] raw = "Password:".getBytes("UTF-8");

		byte[] out = engine.filterIncoming(raw);

		assertSame(raw, out);
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffChannelHashIsNotHeldAsMcp()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);
		byte[] raw = "#channel talk".getBytes("UTF-8");

		byte[] out = engine.filterIncoming(raw);

		assertSame(raw, out);
	}

	@Test
	public void useOffLoneHashThenHelloStripsAndKeepsRoom()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);

		byte[] first = engine.filterIncoming("#".getBytes("UTF-8"));
		assertEquals(0, first.length);

		byte[] second = engine.filterIncoming(
				"$#mcp version: 2.1 to: 2.1\nYou see a room.\n".getBytes("UTF-8"));
		assertEquals("You see a room.\n", new String(second, "UTF-8"));
		assertTrue(engine.serverOfferedHello());
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOffOmitOnHoldsPartialHelloUntilNewline()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);

		byte[] first = engine.filterIncoming(
				"#$#mcp version: 2.1 to: 2.1".getBytes("UTF-8"));
		assertEquals("", new String(first, "UTF-8"));
		assertFalse(engine.serverOfferedHello());

		byte[] second = engine.filterIncoming(
				"\nYou see a room.\n".getBytes("UTF-8"));
		assertEquals("You see a room.\n", new String(second, "UTF-8"));
		assertTrue(engine.serverOfferedHello());
		assertTrue(sink.sent.isEmpty());
	}

	@Test
	public void useOnOmitOnStripsHelloAndSendsAuthenticationKey()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(true);
		engine.setOmitFromOutput(true);
		engine.setAutoNegotiate(false);

		byte[] out = engine.filterIncoming(
				"#$#mcp version: 2.1 to: 2.1\n".getBytes("UTF-8"));

		assertEquals("", new String(out, "UTF-8"));
		assertTrue(engine.serverOfferedHello());
		assertTrue(engine.isHandshaken());
		assertFalse(sink.sent.isEmpty());
		boolean sawAuth = false;
		for (String line : sink.sent) {
			if (line.contains("authentication-key")) {
				sawAuth = true;
				break;
			}
		}
		assertTrue(sawAuth);
	}

	@Test
	public void useOffLogOnOmitOnRecordsProtocolAndStripsWindow()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);
		engine.setLog(true);

		byte[] out = engine.filterIncoming(
				"#$#mcp version: 2.1 to: 2.1\nYou see a room.\n".getBytes("UTF-8"));

		assertEquals("You see a room.\n", new String(out, "UTF-8"));
		assertTrue(sink.sent.isEmpty());
		assertEquals(1, sink.protocol.size());
		assertTrue(sink.protocol.get(0).startsWith("MCP IN "));
		assertTrue(sink.protocol.get(0).contains("#$#mcp version: 2.1 to: 2.1"));
	}

	@Test
	public void useOffLogOffDoesNotRecordProtocol()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(false);
		engine.setOmitFromOutput(true);
		engine.setLog(false);

		engine.filterIncoming("#$#dns-org-hellmoo-status-update foo: 1\n".getBytes("UTF-8"));

		assertTrue(sink.protocol.isEmpty());
	}

	@Test
	public void useOnLogOnRecordsInboundHello()
			throws UnsupportedEncodingException {
		FakeSink sink = new FakeSink();
		McpEngine engine = engine(sink);
		engine.setUse(true);
		engine.setOmitFromOutput(true);
		engine.setAutoNegotiate(false);
		engine.setLog(true);

		engine.filterIncoming("#$#mcp version: 2.1 to: 2.1\n".getBytes("UTF-8"));

		boolean sawIn = false;
		for (int i = 0; i < sink.protocol.size(); i++) {
			String line = sink.protocol.get(i);
			if (line.startsWith("MCP IN ") && line.contains("#$#mcp")) {
				sawIn = true;
				break;
			}
		}
		assertTrue(sawIn);
	}

	private static McpEngine engine(FakeSink sink) {
		McpEngine engine = new McpEngine(sink, null);
		engine.setFeed(false);
		engine.setLog(false);
		return engine;
	}

	private static final class FakeSink implements McpEngine.Sink {
		final ArrayList<String> sent = new ArrayList<String>();
		final ArrayList<String> protocol = new ArrayList<String>();

		@Override
		public void sendNetworkLine(String line) {
			sent.add(line);
		}

		@Override
		public void notifyWindow(String message) {
		}

		@Override
		public String getEncoding() {
			return "UTF-8";
		}

		@Override
		public android.content.Context getContext() {
			return null;
		}

		@Override
		public String getDisplayName() {
			return "world-a";
		}

		@Override
		public void openUrl(String url) {
		}

		@Override
		public void openSimpleEdit(String reference, String title, String type, String content) {
		}

		@Override
		public void fireMcpTrigger(String messageName, HashMap<String, Object> data) {
		}

		@Override
		public String getClientName() {
			return "BlowTorch";
		}

		@Override
		public String getClientVersion() {
			return "2.1";
		}

		@Override
		public int getDisplayCols() {
			return 80;
		}

		@Override
		public int getDisplayRows() {
			return 24;
		}

		@Override
		public void logProtocol(String channel, String direction, String payload) {
			protocol.add(channel + " " + direction + " " + payload);
		}
	}
}
