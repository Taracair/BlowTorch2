package com.resurrection.blowtorch2.lib.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The file name ConnectionSettingsIO opens for a display name. */
public class SettingsFileNamesTest {

	@Test
	public void dropsSpacesAndPunctuation() {
		assertEquals("HelloWorld.xml", SettingsFileNames.xmlFileName("Hello World"));
		assertEquals("worlda.xml", SettingsFileNames.xmlFileName("world-a"));
		assertEquals("a_b.xml", SettingsFileNames.xmlFileName("a_b"));
		assertEquals("ab.xml", SettingsFileNames.xmlFileName("a/b"));
		assertEquals("x.xml", SettingsFileNames.xmlFileName("../x"));
	}

	@Test
	public void nonAsciiLettersDoNotMakeAStem() {
		assertEquals(".xml", SettingsFileNames.xmlFileName(""));
		assertEquals(".xml", SettingsFileNames.xmlFileName("\u0441"));
		assertEquals("ab.xml", SettingsFileNames.xmlFileName("a\u0441b"));
	}

	@Test
	public void launcherListNameIsReserved() {
		assertEquals("blowtorchlauncherlist.xml",
				SettingsFileNames.xmlFileName("blowtorch launcher list"));
		assertEquals("blowtorch_launcher_list.xml",
				SettingsFileNames.xmlFileName("blowtorch_launcher_list"));
		assertTrue(SettingsFileNames.isLauncherList(
				SettingsFileNames.xmlFileName("blowtorch_launcher_list")));
		assertFalse(SettingsFileNames.isLauncherList("HelloWorld.xml"));
	}

	@Test
	public void suggestDisplayNameUsesTheFileStem() {
		assertEquals("Imported", SettingsFileNames.suggestDisplayName(null));
		assertEquals("Imported", SettingsFileNames.suggestDisplayName("  .xml"));
		assertEquals("last", SettingsFileNames.suggestDisplayName("last.xml"));
		assertEquals("last", SettingsFileNames.suggestDisplayName("/tmp/last.XML"));
		assertEquals("Hello World", SettingsFileNames.suggestDisplayName("Hello World.xml"));
	}
}
