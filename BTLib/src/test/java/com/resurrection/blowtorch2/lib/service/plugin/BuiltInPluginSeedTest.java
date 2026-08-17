package com.resurrection.blowtorch2.lib.service.plugin;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltInPluginSeedTest {

	@Test
	public void extractTakesOnlyTheNamedPlugin() {
		String doc = "<blowtorch><plugins>"
				+ "<plugin name=\"button_window\" id=\"10\"><author>x</author></plugin>"
				+ "<plugin name=\"starter_tutorial\" id=\"20\">"
				+ "<script name=\"bootstrap\" execute=\"true\"><![CDATA[\n"
				+ "require(\"startertutorial\")\n"
				+ "]]></script>"
				+ "</plugin></plugins></blowtorch>";
		String xml = BuiltInPluginSeed.extractPluginXml(doc,
				BuiltInPluginSeed.STARTER_TUTORIAL);
		assertNotNull(xml);
		assertTrue(xml.contains("require(\"startertutorial\")"));
		assertTrue(xml.startsWith("<plugin"));
		assertTrue(xml.endsWith("</plugin>"));
		assertFalse(xml.contains("button_window"));
	}

	@Test
	public void extractIgnoresCdataThatLooksLikeACloseTag() {
		String doc = "<plugins><plugin name=\"starter_tutorial\" id=\"20\">"
				+ "<script><![CDATA[</plugin> not really]]></script>"
				+ "</plugin></plugins>";
		String xml = BuiltInPluginSeed.extractPluginXml(doc,
				BuiltInPluginSeed.STARTER_TUTORIAL);
		assertNotNull(xml);
		assertTrue(xml.contains("not really"));
		assertTrue(xml.endsWith("</plugin>"));
	}

	@Test
	public void extractReturnsNullWhenMissing() {
		assertNull(BuiltInPluginSeed.extractPluginXml(
				"<plugins><plugin name=\"button_window\" id=\"10\"></plugin></plugins>",
				BuiltInPluginSeed.STARTER_TUTORIAL));
		assertNull(BuiltInPluginSeed.extractPluginXml(null, "x"));
		assertNull(BuiltInPluginSeed.extractPluginXml("<plugin/>", null));
	}

	@Test
	public void wrapIsABlowtorchDocument() {
		byte[] bytes = BuiltInPluginSeed.wrapAsBlowtorchDocument(
				"<plugin name=\"starter_tutorial\" id=\"20\"></plugin>");
		assertNotNull(bytes);
		String doc = new String(bytes, StandardCharsets.UTF_8);
		assertTrue(doc.contains("<blowtorch"));
		assertTrue(doc.contains("<plugins>"));
		assertTrue(doc.contains("starter_tutorial"));
	}

	@Test
	public void defaultSettingsTestXmlStillShipsStarterTutorial() throws Exception {
		File file = new File("../BT_Free/config/default_settings_test.xml");
		assertTrue("expected " + file.getAbsolutePath(), file.isFile());
		String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		String plugin = BuiltInPluginSeed.extractPluginXml(xml,
				BuiltInPluginSeed.STARTER_TUTORIAL);
		assertNotNull(plugin);
		assertTrue(plugin.contains("require(\"startertutorial\")"));
		assertTrue(plugin.contains("tips_while_playing"));
	}

	@Test
	public void defaultSettingsMainXmlStillShipsStarterTutorial() throws Exception {
		File file = new File("../BT_Free/config/default_settings_main.xml");
		assertTrue("expected " + file.getAbsolutePath(), file.isFile());
		String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		String plugin = BuiltInPluginSeed.extractPluginXml(xml,
				BuiltInPluginSeed.STARTER_TUTORIAL);
		assertNotNull(plugin);
		assertTrue(plugin.contains("require(\"startertutorial\")"));
	}
}
