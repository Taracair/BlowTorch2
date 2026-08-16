package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BuiltinColorTestWorldsTest {

	@Test
	public void addIfMissing_insertsOncePerHostPort() {
		LauncherSettings settings = new LauncherSettings();
		assertTrue(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinColorTestWorlds.arx()));
		assertEquals(1, settings.getList().size());
		assertFalse("same host:port must not duplicate",
				BuiltinColorTestWorlds.addIfMissing(settings,
						BuiltinColorTestWorlds.arx()));
		assertEquals(1, settings.getList().size());
	}

	@Test
	public void addIfMissing_skipsDifferentNameSameHost() {
		LauncherSettings settings = new LauncherSettings();
		MudConnection already = new MudConnection();
		already.setDisplayName("my arx");
		already.setHostName(BuiltinColorTestWorlds.ARX_HOST);
		already.setPortString(BuiltinColorTestWorlds.ARX_PORT);
		settings.getList().put(already.getDisplayName(), already);

		assertFalse(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinColorTestWorlds.arx()));
		assertEquals(1, settings.getList().size());
	}

	@Test
	public void tempestIsADifferentHost() {
		LauncherSettings settings = new LauncherSettings();
		assertTrue(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinColorTestWorlds.arx()));
		assertTrue(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinColorTestWorlds.tempest()));
		assertEquals(2, settings.getList().size());
	}
}
