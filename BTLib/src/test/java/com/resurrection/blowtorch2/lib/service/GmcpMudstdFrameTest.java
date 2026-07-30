package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The mudstd.frame proposal, as BlowTorch announces it.
 *
 * <p>This is a proof of concept built for the author of the proposal, so the
 * exact spelling on the wire is the point of these tests: a client that
 * announces "Mudstd.Frame" is not announcing what the specification named.
 */
public class GmcpMudstdFrameTest {

	/** The package is offered, so it appears in Manage modules…. */
	@Test
	public void moduleIsInTheCatalog() {
		GmcpModuleRegistry r = new GmcpModuleRegistry();
		boolean found = false;
		for (GmcpModuleRegistry.ModuleInfo m : r.allKnownModules()) {
			if ("mudstd.frame".equals(m.id)) {
				found = true;
			}
		}
		assertTrue("mudstd.frame should be offered in the module catalog", found);
	}

	/** Lower case, exactly as mudstandards.org spells it. */
	@Test
	public void moduleKeepsItsLowerCaseName() {
		GmcpModuleRegistry r = new GmcpModuleRegistry();
		assertEquals("mudstd.frame", r.canonicalId("mudstd.frame"));
		assertEquals("mudstd.frame", r.canonicalId("MUDSTD.FRAME"));
	}

	/**
	 * Off unless asked for. Nothing in BlowTorch draws a frame yet, so
	 * announcing support to every server would promise what we cannot do.
	 */
	@Test
	public void moduleIsOffByDefault() {
		GmcpModuleRegistry r = new GmcpModuleRegistry();
		assertFalse(r.isEnabled("mudstd.frame"));
		assertFalse(GmcpModuleRegistry.DEFAULT_SUPPORTS.contains("mudstd"));
	}

	/**
	 * The player closing a frame. The specification has always had
	 * {@code reason: "user"} and BlowTorch could never send it, so a server had
	 * no way to learn the person reading a frame was done with it. `.frame close`
	 * sends this.
	 */
	@Test
	public void closedByTheUserSaysUser() {
		assertEquals("mudstd.frame.closed {\"id\": \"map\", \"reason\": \"user\"}",
				MudstdFrame.closedEvent("map", MudstdFrame.REASON_USER));
	}

	/** The client's own refusal is still reason "system". The two are different. */
	@Test
	public void closedBySystemStaysSystem() {
		assertEquals("mudstd.frame.closed {\"id\": \"map\", \"reason\": \"system\"}",
				MudstdFrame.closedEvent("map", MudstdFrame.REASON_SYSTEM));
	}

	/** Anything that is not "user" is "system" — never a reason of our invention. */
	@Test
	public void unknownReasonFallsBackToSystem() {
		assertTrue(MudstdFrame.closedEvent("map", "bored").endsWith(
				"\"reason\": \"system\"}"));
		assertTrue(MudstdFrame.closedEvent("map", null).endsWith(
				"\"reason\": \"system\"}"));
	}

	/**
	 * A server picks the id, so it is not ours to trust. A quote in one used to
	 * be able to produce a malformed event and leave the server author debugging
	 * their own parser over our bug.
	 */
	@Test
	public void aUserCloseEscapesTheServersId() {
		assertEquals("mudstd.frame.closed {\"id\": \"we\\\"ird\", \"reason\": \"user\"}",
				MudstdFrame.closedEvent("we\"ird", MudstdFrame.REASON_USER));
	}

	/** Turning it on puts it in Core.Supports.Set under its own spelling. */
	@Test
	public void enablingItPutsItInTheSupportsString() {
		GmcpModuleRegistry r = new GmcpModuleRegistry();
		r.setEnabledFromSupportsString(
				GmcpModuleRegistry.DEFAULT_SUPPORTS + ", \"mudstd.frame 1\"");
		assertTrue(r.isEnabled("mudstd.frame"));
		assertTrue(r.toSupportsString().contains("\"mudstd.frame 1\""));
	}
}
