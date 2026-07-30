package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Frame events cross a process boundary, so what survives the trip is the thing
 * worth testing. Nothing here needs Android: {@code org.json} is a real
 * dependency of the JVM test source set.
 */
public class FrameEventTest {

	@Test
	public void aBatchSurvivesTheRoundTrip() {
		List<FrameEvent> out = new ArrayList<FrameEvent>();
		out.add(FrameEvent.open("map", "Map", "floating", "image", 40));
		out.add(FrameEvent.image("map", "http://eden-test.example/surrounding.png"));
		out.add(FrameEvent.close("map"));
		out.add(FrameEvent.clear());

		ArrayList<FrameEvent> back = FrameEvent.parse(FrameEvent.toJson(out));
		assertEquals(4, back.size());
		assertEquals(FrameEvent.OP_OPEN, back.get(0).getOp());
		assertEquals("Map", back.get(0).getLabel());
		assertEquals("floating", back.get(0).getType());
		assertEquals("image", back.get(0).getContent());
		assertEquals(40, back.get(0).getSizeChars());
		assertEquals("http://eden-test.example/surrounding.png", back.get(1).getImage());
		assertEquals(FrameEvent.OP_CLOSE, back.get(2).getOp());
		assertEquals(FrameEvent.OP_CLEAR, back.get(3).getOp());
	}

	/**
	 * A frame id is the server's to choose, and one already tested here has a
	 * double quote in it. It has to come back exactly as it went in, or the
	 * close event we send would name a frame the server does not recognise.
	 */
	@Test
	public void awkwardIdsComeBackIntact() {
		String id = "ma\"p\\with\nnewline";
		ArrayList<FrameEvent> one = new ArrayList<FrameEvent>();
		one.add(FrameEvent.open(id, "", "floating", "image", 0));
		ArrayList<FrameEvent> back = FrameEvent.parse(FrameEvent.toJson(one));
		assertEquals(1, back.size());
		assertEquals(id, back.get(0).getId());
	}

	/** Nothing readable means no frames, not an exception on the UI thread. */
	@Test
	public void unreadableInputGivesAnEmptyBatch() {
		assertTrue(FrameEvent.parse(null).isEmpty());
		assertTrue(FrameEvent.parse("").isEmpty());
		assertTrue(FrameEvent.parse("not json at all").isEmpty());
		assertTrue(FrameEvent.parse("{\"op\":\"open\"}").isEmpty());
	}

	/** A URL has no size on this side however big the picture behind it is. */
	@Test
	public void onlyInlinePayloadsCanBeOversized() {
		StringBuilder big = new StringBuilder("base64:");
		while (big.length() <= FrameEvent.MAX_BASE64_CHARS) {
			big.append("AAAAAAAAAAAAAAAA");
		}
		assertTrue(FrameEvent.image("map", big.toString()).isOversizedPayload());
		assertFalse(FrameEvent.image("map", "http://example.org/enormous.png")
				.isOversizedPayload());
		assertFalse(FrameEvent.image("map", "base64:AAAA").isOversizedPayload());
	}

	@Test
	public void inlineEventsCarryTheirKeyAndSpec() {
		ArrayList<FrameEvent> one = new ArrayList<FrameEvent>();
		one.add(FrameEvent.inline("btimg-3-map", "base64:AAAA"));
		ArrayList<FrameEvent> back = FrameEvent.parse(FrameEvent.toJson(one));
		assertEquals(FrameEvent.OP_INLINE, back.get(0).getOp());
		assertEquals("btimg-3-map", back.get(0).getId());
		assertEquals("base64:AAAA", back.get(0).getImage());
	}
}
