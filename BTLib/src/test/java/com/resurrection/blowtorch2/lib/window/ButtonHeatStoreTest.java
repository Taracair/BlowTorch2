package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ButtonHeatStoreTest {

	@Test
	public void fileNameIsPrefixedXmlAndFoldsLikeCommandPairings() {
		String name = ButtonHeatStore.fileName("Hello World");
		assertEquals("buttonheat-hello_world.xml", name);
		assertFalse(name.indexOf('/') >= 0);
		assertFalse(name.indexOf('\\') >= 0);
		assertTrue(name.startsWith("buttonheat-"));
		assertTrue(name.endsWith(".xml"));
	}

	@Test
	public void twoWorldsDoNotShareAFile() {
		assertFalse(ButtonHeatStore.fileName("world-a")
				.equals(ButtonHeatStore.fileName("world-b")));
	}

	@Test
	public void emptyNameIsNotEveryWorld() {
		String empty = ButtonHeatStore.fileName("");
		String other = ButtonHeatStore.fileName("world-a");
		assertEquals("buttonheat-default.xml", empty);
		assertFalse(empty.equals(other));
	}
}
