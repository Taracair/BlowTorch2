package com.resurrection.blowtorch2.lib.service.mxp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MxpLinksTest {

	@Test
	public void roundTripSend() {
		String href = MxpLinks.sendHref("look; north");
		assertTrue(href.startsWith(MxpLinks.SEND));
		assertEquals("look; north", MxpLinks.sendCommand(href));
	}

	@Test
	public void roundTripMenu() {
		String href = MxpLinks.menuHref("click|A|B", "a|b");
		String[] p = MxpLinks.menuHintsAndCommands(href);
		assertEquals("click|A|B", p[0]);
		assertEquals("a|b", p[1]);
	}

	@Test
	public void expireIdHasNoColon() {
		String id = MxpLinks.expireId("Exits");
		assertEquals("mxp-Exits", id);
		assertEquals("Exits", MxpLinks.groupFromExpireId(id));
	}

	@Test
	public void roundTripPrompt() {
		String href = MxpLinks.promptHref("say hi");
		assertTrue(MxpLinks.isPrompt(href));
		assertEquals("say hi", MxpLinks.promptCommand(href));
	}

	@Test
	public void menuLabelsSkipTooltip() {
		String[] labels = MxpLinks.menuLabels("click|Look|Get", "look|get");
		assertEquals(2, labels.length);
		assertEquals("Look", labels[0]);
		assertEquals("Get", labels[1]);
	}

	@Test
	public void tapWordOverridesMxpCommandsNotHttp() {
		assertTrue(MxpLinks.tapWordOverrides(MxpLinks.sendHref("north")));
		assertTrue(MxpLinks.tapWordOverrides(MxpLinks.menuHref("a|b", "a|b")));
		assertTrue(MxpLinks.tapWordOverrides(MxpLinks.promptHref("say hi")));
		assertFalse(MxpLinks.tapWordOverrides("https://example.com"));
		assertFalse(MxpLinks.tapWordOverrides("mailto:a@b.c"));
		assertFalse(MxpLinks.tapWordOverrides(null));
	}
}
