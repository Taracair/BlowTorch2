package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class OutboundHookTest {

	@Test
	public void emptyCommandIsNotShownToFilters() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("", "\n",
				Collections.singletonList(new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] text) {
						fail("bare Enter is not a hook argument");
						return text;
					}
				}), "UTF-8");
		assertEquals("", batch.command);
		assertEquals("\n", batch.echo);
	}

	@Test
	public void nullCommandIsNotShownToFilters() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply(null, null,
				Collections.singletonList(new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] text) {
						fail("null command is not a hook argument");
						return text;
					}
				}), "UTF-8");
		assertEquals(null, batch.command);
		assertEquals(null, batch.echo);
	}

	@Test
	public void leaveKeepsTheCommandAndThePreHookEcho() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("kill goblin\r\n", "kill goblin;",
				Collections.singletonList(new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] text) {
						return null;
					}
				}), "UTF-8");
		assertEquals("kill goblin\r\n", batch.command);
		assertEquals("kill goblin;", batch.echo);
	}

	@Test
	public void theSameBytesLeaveTheEchoAlone() throws Exception {
		final String command = "café\r\n";
		OutboundHook.Batch batch = OutboundHook.apply(command, "café;",
				Collections.singletonList(new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] text) {
						return Arrays.copyOf(text, text.length);
					}
				}), "UTF-8");
		assertEquals(command, batch.command);
		assertEquals("café;", batch.echo);
	}

	@Test
	public void aDifferentStringReplacesTheCommandAndTheEcho() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("kill goblin\r\n", "kill goblin;",
				Collections.singletonList(replace("cast 'kill' goblin\r\n")), "UTF-8");
		assertEquals("cast 'kill' goblin\r\n", batch.command);
		assertEquals("cast 'kill' goblin\r\n", batch.echo);
	}

	@Test
	public void aDifferentStringKeepsASuppressedEchoSuppressed() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("kill goblin\r\n", "",
				Collections.singletonList(replace("look\r\n")), "UTF-8");
		assertEquals("look\r\n", batch.command);
		assertEquals("", batch.echo);

		OutboundHook.Batch hidden = OutboundHook.apply("kill goblin\r\n", null,
				Collections.singletonList(replace("look\r\n")), "UTF-8");
		assertEquals("look\r\n", hidden.command);
		assertEquals(null, hidden.echo);
	}

	@Test
	public void anEmptyArrayDropsTheBatchAndItsEcho() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("kill goblin\r\n", "kill goblin;",
				Collections.singletonList(new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] text) {
						return new byte[0];
					}
				}), "UTF-8");
		assertEquals("", batch.command);
		assertEquals("", batch.echo);
	}

	@Test
	public void oneFilterThrowsAndTheNextStillReplaces() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("kill goblin\r\n", "kill goblin;",
				Arrays.asList(
						new InboundTextHook.Filter() {
							@Override
							public byte[] apply(final byte[] text) {
								throw new IllegalStateException("boom");
							}
						},
						replace("slain goblin\r\n")), "UTF-8");
		assertEquals("slain goblin\r\n", batch.command);
		assertEquals("slain goblin\r\n", batch.echo);
	}

	@Test
	public void filtersRunInOrderOnTheWholeBatch() throws Exception {
		OutboundHook.Batch batch = OutboundHook.apply("north\r\nsouth\r\n", "north;south",
				Arrays.asList(
						new InboundTextHook.Filter() {
							@Override
							public byte[] apply(final byte[] text) throws UnsupportedEncodingException {
								String line = new String(text, "UTF-8");
								assertEquals("north\r\nsouth\r\n", line);
								return ("west\r\n" + line).getBytes("UTF-8");
							}
						},
						new InboundTextHook.Filter() {
							@Override
							public byte[] apply(final byte[] text) throws UnsupportedEncodingException {
								String line = new String(text, "UTF-8");
								assertEquals("west\r\nnorth\r\nsouth\r\n", line);
								return line.replace("south", "down").getBytes("UTF-8");
							}
						}), "UTF-8");
		assertEquals("west\r\nnorth\r\ndown\r\n", batch.command);
		assertEquals("west\r\nnorth\r\ndown\r\n", batch.echo);
	}

	@Test
	public void aDisabledPluginIsSkipped() throws Exception {
		List<InboundTextHook.Filter> filters = new ArrayList<InboundTextHook.Filter>();
		add(filters, true, true, replace("main\r\n"));
		add(filters, false, true, replace("disabled\r\n"));
		add(filters, true, false, replace("no-function\r\n"));
		add(filters, true, true, new InboundTextHook.Filter() {
			@Override
			public byte[] apply(final byte[] text) throws UnsupportedEncodingException {
				String line = new String(text, "UTF-8");
				assertEquals("main\r\n", line);
				return (line + "second\r\n").getBytes("UTF-8");
			}
		});
		OutboundHook.Batch batch = OutboundHook.apply("kill goblin\r\n", "kill goblin;",
				filters, "UTF-8");
		assertEquals("main\r\nsecond\r\n", batch.command);
		assertEquals("main\r\nsecond\r\n", batch.echo);
	}

	@Test
	public void includeRejectsADisabledPluginAndAPluginWithNoHook() {
		assertFalse(OutboundHook.include(false, true));
		assertFalse(OutboundHook.include(true, false));
		assertFalse(OutboundHook.include(false, false));
		assertTrue(OutboundHook.include(true, true));
	}

	@Test
	public void anUnchangedFlushLeavesEveryEchoPiece() {
		List<OutboundHook.EchoPiece> parts = Arrays.asList(
				piece("name;", true),
				piece("[alias=>x];", false));
		assertEquals("name;", OutboundHook.sentEcho(parts));
		OutboundHook.FlushEcho flush = OutboundHook.flushEcho(
				"name\r\n", "name\r\n", "name;", parts);
		assertEquals(false, flush.replaced);
		assertEquals("", flush.echoNow);
		assertEquals("name\r\n", flush.command);
		assertEquals(2, flush.kept.size());
		assertEquals("name;", flush.kept.get(0).text);
		assertEquals("[alias=>x];", flush.kept.get(1).text);
	}

	@Test
	public void aRewrittenFlushEchoesTheNewLineAndKeepsAClientNote() {
		List<OutboundHook.EchoPiece> parts = Arrays.asList(
				piece("name;", true),
				piece("[alias=>x];", false),
				piece("look;", true));
		assertEquals("name;look;", OutboundHook.sentEcho(parts));
		OutboundHook.FlushEcho flush = OutboundHook.flushEcho(
				"name\r\nlook\r\n", "cast\r\n", "cast\r\n", parts);
		assertEquals(true, flush.replaced);
		assertEquals("cast\r\n", flush.command);
		assertEquals("cast\r\n", flush.echoNow);
		assertEquals(1, flush.kept.size());
		assertEquals("[alias=>x];", flush.kept.get(0).text);
		assertEquals(false, flush.kept.get(0).sent);
	}

	@Test
	public void aDroppedFlushEchoesNothingAndDropsTheSentEcho() {
		List<OutboundHook.EchoPiece> parts = Arrays.asList(
				piece("name;", true),
				piece("[alias=>x];", false));
		OutboundHook.FlushEcho flush = OutboundHook.flushEcho(
				"name\r\n", "", "", parts);
		assertEquals(true, flush.replaced);
		assertEquals("", flush.command);
		assertEquals("", flush.echoNow);
		assertEquals(1, flush.kept.size());
		assertEquals("[alias=>x];", flush.kept.get(0).text);
	}

	@Test
	public void aLaterFlushDoesNotResendParkedEcho() {
		List<OutboundHook.EchoPiece> parked = OutboundHook.parkAll(Arrays.asList(
				piece("name;", true),
				piece("[alias=>x];", false)));
		assertEquals("", OutboundHook.sentEcho(parked));
		List<OutboundHook.EchoPiece> parts = new ArrayList<OutboundHook.EchoPiece>(parked);
		parts.add(piece("look;", true));
		assertEquals("look;", OutboundHook.sentEcho(parts));
		OutboundHook.FlushEcho flush = OutboundHook.flushEcho(
				"look\r\n", "cast\r\n", "cast\r\n", parts);
		assertEquals("cast\r\n", flush.echoNow);
		assertEquals(2, flush.kept.size());
		assertEquals("name;", flush.kept.get(0).text);
		assertEquals("[alias=>x];", flush.kept.get(1).text);
	}

	@Test
	public void rewritingTheRemainderKeepsTheParkedEcho() {
		assertEquals("name\r\n", OutboundHook.formatEcho("name;", "\r\n"));
		assertEquals("name;look\r\n", OutboundHook.formatEcho("name;look;", "\r\n"));
		assertEquals("", OutboundHook.formatEcho("", "\r\n"));
		assertEquals("name;look\r\n", OutboundHook.echoAfterRemainder(
				"name;look\r\n", "look\r\n", "look\r\n", "look;", "name\r\n"));
		assertEquals("name\r\ncast\r\n", OutboundHook.echoAfterRemainder(
				"name;look\r\n", "look\r\n", "cast\r\n", "cast\r\n", "name\r\n"));
		assertEquals("name\r\n", OutboundHook.echoAfterRemainder(
				"name;look\r\n", "look\r\n", "", "", "name\r\n"));
	}

	@Test
	public void aLaterRewritePaintsTheUnshownPrefixFirst() {
		List<OutboundHook.EchoPiece> parts = new ArrayList<OutboundHook.EchoPiece>(
				OutboundHook.parkAll(Collections.singletonList(piece("name;", true))));
		parts.add(piece("look;", true));
		OutboundHook.FlushEcho flush = OutboundHook.flushEcho(
				"look\r\n", "cast\r\n", "cast\r\n", parts);
		OutboundHook.FlushPaint paint = OutboundHook.paint(flush, "\r\n");
		assertEquals("name\r\ncast\r\n", paint.echoNow);
		assertEquals(0, paint.stillUnshown.size());

		OutboundHook.FlushEcho dropped = OutboundHook.flushEcho(
				"look\r\n", "", "", parts);
		OutboundHook.FlushPaint dropPaint = OutboundHook.paint(dropped, "\r\n");
		assertEquals("", dropPaint.echoNow);
		assertEquals(1, dropPaint.stillUnshown.size());
		assertEquals("name;", dropPaint.stillUnshown.get(0).text);
	}

	private static OutboundHook.EchoPiece piece(final String text, final boolean sent) {
		return new OutboundHook.EchoPiece(text, sent);
	}

	private static void add(final List<InboundTextHook.Filter> filters,
			final boolean enabled, final boolean offers,
			final InboundTextHook.Filter filter) {
		boolean defined = enabled && offers;
		if (OutboundHook.include(enabled, defined)) {
			filters.add(filter);
		}
	}

	private static InboundTextHook.Filter replace(final String s) {
		return new InboundTextHook.Filter() {
			@Override
			public byte[] apply(final byte[] text) throws UnsupportedEncodingException {
				return s.getBytes("UTF-8");
			}
		};
	}
}
