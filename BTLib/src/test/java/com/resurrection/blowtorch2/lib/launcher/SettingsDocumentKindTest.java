package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.launcher.SettingsDocumentKind.Kind;

/** Which picked XML is a server list, and which is one world's settings. */
public class SettingsDocumentKindTest {

	@Test
	public void blowtorchRootIsAWorld() throws Exception {
		assertEquals(Kind.WORLD_SETTINGS, SettingsDocumentKind.classify(new StringReader(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
						+ "<blowtorch xmlversion=\"2\"><windows></windows></blowtorch>")));
	}

	@Test
	public void legacyRootWithAWindowIsAWorld() throws Exception {
		assertEquals(Kind.WORLD_SETTINGS, SettingsDocumentKind.classify(new StringReader(
				"<?xml version=\"1.0\"?><root><!-- kept --><window></window></root>")));
	}

	@Test
	public void launcherListIsNotAWorld() throws Exception {
		assertEquals(Kind.LAUNCHER_LIST, SettingsDocumentKind.classify(new StringReader(
				"<root><launcher version=\"1\"></launcher></root>")));
	}

	@Test
	public void emptyRootAndPlainTextAreUnknown() throws Exception {
		assertEquals(Kind.UNKNOWN, SettingsDocumentKind.classify(new StringReader(
				"<root></root>")));
		assertEquals(Kind.UNKNOWN, SettingsDocumentKind.classify(new StringReader(
				"<root/>")));
		assertEquals(Kind.UNKNOWN, SettingsDocumentKind.classify(new StringReader(
				"not xml")));
	}
}
