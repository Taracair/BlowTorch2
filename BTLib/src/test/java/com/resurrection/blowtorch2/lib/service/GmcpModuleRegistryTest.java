package com.resurrection.blowtorch2.lib.service;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization tests for GMCP supports parsing and module registry leaf logic.
 */
public class GmcpModuleRegistryTest {

	@Test
	public void defaultConstructorEnablesDefaultSupports() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		assertTrue(reg.isEnabled("Char"));
		assertTrue(reg.isEnabled("Room"));
		assertTrue(reg.isEnabled("Core"));
		assertTrue(reg.isEnabled("Char.Login"));
		assertTrue(reg.isEnabled("Client.Media"));
		assertTrue(reg.coversModule("Char.Vitals"));
		assertFalse(reg.isEnabled("Comm"));
	}

	@Test
	public void setEnabledFromSupportsStringParsesQuotedTokens() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		reg.setEnabledFromSupportsString("\"Char 1\", \"Room 2\", \"Custom.Mod 3\"");
		assertTrue(reg.isEnabled("char"));
		assertTrue(reg.isEnabled("Room"));
		assertTrue(reg.isEnabled("Custom.Mod"));
		assertFalse(reg.isEnabled("Core"));
		ArrayList<String> tokens = reg.enabledTokens();
		assertTrue(tokens.contains("Char 1"));
		assertTrue(tokens.contains("Room 1") || tokens.contains("Room 2"));
	}

	@Test
	public void nullOrEmptySupportsFallsBackToDefault() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		reg.setEnabledFromSupportsString(null);
		assertTrue(reg.isEnabled("Core"));
		reg.setEnabledFromSupportsString("   ");
		assertTrue(reg.isEnabled("Char"));
		assertEquals(GmcpModuleRegistry.DEFAULT_SUPPORTS, reg.toSupportsString());
	}

	@Test
	public void toSupportsStringRoundTripsEnabledSet() {
		GmcpModuleRegistry reg = GmcpModuleRegistry.fromSupportsOption(
				"\"Core 1\", \"Char 1\"");
		String supports = reg.toSupportsString();
		assertTrue(supports.contains("\"Core 1\""));
		assertTrue(supports.contains("\"Char 1\""));
		GmcpModuleRegistry again = GmcpModuleRegistry.fromSupportsOption(supports);
		assertTrue(again.isEnabled("Core"));
		assertTrue(again.isEnabled("Char"));
		assertFalse(again.isEnabled("Room"));
	}

	@Test
	public void coversModuleWalksParentPackages() {
		GmcpModuleRegistry reg = GmcpModuleRegistry.fromSupportsOption("\"Char 1\"");
		assertTrue(reg.coversModule("Char"));
		assertTrue(reg.coversModule("Char.Base"));
		assertTrue(reg.coversModule("Char.Vitals"));
		assertFalse(reg.coversModule("Room.Info"));
		assertFalse(reg.coversModule(null));
		assertFalse(reg.coversModule(""));
	}

	@Test
	public void enableDisableAndCustomModule() {
		GmcpModuleRegistry reg = GmcpModuleRegistry.fromSupportsOption("\"Core 1\"");
		reg.setEnabled("Comm", true);
		assertTrue(reg.isEnabled("Comm"));
		reg.setEnabled("Comm", false);
		assertFalse(reg.isEnabled("Comm"));
		reg.enableMany(new String[]{"Foo.Bar", "  ", null, "Baz"});
		assertTrue(reg.isEnabled("Foo.Bar"));
		assertTrue(reg.isEnabled("Baz"));
	}

	@Test
	public void noteSeenSuggestsNewModuleOnce() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		// Char is already enabled via defaults — no suggestion
		assertNull(reg.noteSeen("Char.Vitals"));
		String suggested = reg.noteSeen("Comm.Channel");
		assertNotNull(suggested);
		assertEquals("Comm.Channel", suggested);
		assertNull(reg.noteSeen("Comm.Channel"));
		assertTrue(reg.seenModules().contains("Comm")
				|| reg.seenModules().contains("Comm.Channel"));
	}

	@Test
	public void nativeAndCatalogSeeded() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		assertFalse(reg.nativeModules().isEmpty());
		assertFalse(reg.catalogModules().isEmpty());
		boolean foundLogin = false;
		for (GmcpModuleRegistry.ModuleInfo m : reg.nativeModules()) {
			if ("Char.Login".equals(m.id) && m.nativeHandler) {
				foundLogin = true;
				assertEquals(GmcpModuleRegistry.Kind.NATIVE, m.kind);
			}
		}
		assertTrue(foundLogin);
	}

	@Test
	public void normKeyAndCanonicalId() {
		assertEquals("char.login", GmcpModuleRegistry.normKey(" Char.Login "));
		assertEquals("", GmcpModuleRegistry.normKey(null));
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		assertEquals("Char.Login", reg.canonicalId("char.login"));
		assertEquals("Foo.Bar", reg.canonicalId("foo.bar"));
	}

	@Test
	public void moduleInfoSupportToken() {
		GmcpModuleRegistry.ModuleInfo info = new GmcpModuleRegistry.ModuleInfo(
				"Room", 1, "summary", GmcpModuleRegistry.Kind.NATIVE, true);
		assertEquals("Room 1", info.supportToken());
	}

	@Test
	public void statusLineMentionsEnabledCount() {
		GmcpModuleRegistry reg = GmcpModuleRegistry.fromSupportsOption("\"Core 1\"");
		String status = reg.statusLine();
		assertTrue(status.contains("1 module"));
	}

	/** A server's own list is kept in the order it sent, and blanks dropped. */
	@Test
	public void serverSupportsIsRecordedInOrder() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		assertTrue(reg.getServerSupports().isEmpty());
		ArrayList<String> offered = new ArrayList<String>();
		offered.add("Core 1");
		offered.add("  mudstd.room 1  ");
		offered.add("   ");
		offered.add("WebView");
		reg.setServerSupports(offered);
		ArrayList<String> got = reg.getServerSupports();
		assertEquals(3, got.size());
		assertEquals("Core 1", got.get(0));
		assertEquals("mudstd.room 1", got.get(1));
		assertEquals("WebView", got.get(2));
	}

	/**
	 * Being told a server can do something is not the player asking for it, and
	 * it is a property of this connection only.
	 */
	@Test
	public void serverSupportsNeitherEnablesNorSurvivesDisconnect() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		ArrayList<String> offered = new ArrayList<String>();
		offered.add("mudstd.tilemap 1");
		reg.setServerSupports(offered);
		assertFalse(reg.isEnabled("mudstd.tilemap"));
		assertFalse(reg.toSupportsString().contains("tilemap"));
		reg.clearSeen();
		assertTrue(reg.getServerSupports().isEmpty());
	}

	/** Our outbound set and the server's list are different things. */
	@Test
	public void serverSupportsIsNotLastSupportsSet() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		reg.setLastSupportsSet("core.supports.set [\"Core 1\"]");
		ArrayList<String> offered = new ArrayList<String>();
		offered.add("Beip 1");
		reg.setServerSupports(offered);
		assertEquals("core.supports.set [\"Core 1\"]", reg.getLastSupportsSet());
		assertEquals("Beip 1", reg.getServerSupports().get(0));
	}

	/** A null list is an empty list, not a crash. */
	@Test
	public void serverSupportsToleratesNull() {
		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		reg.setServerSupports(null);
		assertTrue(reg.getServerSupports().isEmpty());
	}
}
