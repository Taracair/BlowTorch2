package com.resurrection.blowtorch2.lib.service.mxp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MxpTagTest {

	@Test
	public void sendWithNamedHref() {
		MxpTag t = MxpTag.parse("SEND href=\"look north\"");
		assertEquals("send", t.canonical());
		assertEquals("look north", t.attr("href"));
		assertFalse(t.closing);
	}

	@Test
	public void positionalSend() {
		MxpTag t = MxpTag.parse("SEND \"buy bread\"");
		assertEquals("buy bread", t.attrOrPos("href", 0));
	}

	@Test
	public void elementOpenFlag() {
		MxpTag t = MxpTag.parse("!ELEMENT Auction '<FONT COLOR=red>' TAG=20 OPEN");
		assertTrue(t.definition);
		assertEquals("element", t.canonical());
		assertTrue(t.hasFlag("open"));
		assertEquals("Auction", t.positional.get(0));
		assertEquals("20", t.attr("tag"));
	}

	@Test
	public void closingRejectsArgs() {
		assertNull(MxpTag.parse("/B foo"));
	}

	@Test
	public void promptIsAFlag() {
		MxpTag t = MxpTag.parse("SEND href=\"say hi\" prompt");
		assertTrue(t.hasFlag("prompt"));
		assertEquals("say hi", t.attr("href"));
	}
}
