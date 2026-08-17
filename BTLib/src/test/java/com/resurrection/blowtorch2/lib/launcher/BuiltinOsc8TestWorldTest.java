package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BuiltinOsc8TestWorldTest {

	@Test
	public void addIfMissing_insertsOncePerHostPort() {
		LauncherSettings settings = new LauncherSettings();
		assertTrue(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinOsc8TestWorld.entry()));
		assertEquals(1, settings.getList().size());
		assertFalse("same host:port must not duplicate",
				BuiltinColorTestWorlds.addIfMissing(settings,
						BuiltinOsc8TestWorld.entry()));
		assertEquals(1, settings.getList().size());
	}

	@Test
	public void hostAndPortAreLocalListener() {
		MudConnection m = BuiltinOsc8TestWorld.entry();
		assertEquals("127.0.0.1", m.getHostName());
		assertEquals("4445", m.getPortString());
	}
}
