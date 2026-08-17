package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class BuiltinMxpWorldsTest {

	@Test
	public void addIfMissing_insertsOncePerHostPort() {
		LauncherSettings settings = new LauncherSettings();
		assertTrue(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinMxpWorlds.discworld()));
		assertEquals(1, settings.getList().size());
		assertFalse("same host:port must not duplicate",
				BuiltinColorTestWorlds.addIfMissing(settings,
						BuiltinMxpWorlds.discworld()));
		assertEquals(1, settings.getList().size());
	}

	@Test
	public void addIfMissing_skipsDifferentNameSameHost() {
		LauncherSettings settings = new LauncherSettings();
		MudConnection already = new MudConnection();
		already.setDisplayName("my discworld");
		already.setHostName(BuiltinMxpWorlds.DISCWORLD_HOST);
		already.setPortString(BuiltinMxpWorlds.DISCWORLD_PORT);
		settings.getList().put(already.getDisplayName(), already);

		assertFalse(BuiltinColorTestWorlds.addIfMissing(settings,
				BuiltinMxpWorlds.discworld()));
		assertEquals(1, settings.getList().size());
	}

	@Test
	public void fourWorldsAreDistinctHostPorts() {
		LauncherSettings settings = new LauncherSettings();
		Set<String> keys = new HashSet<String>();
		for (MudConnection entry : BuiltinMxpWorlds.entries()) {
			assertTrue(BuiltinColorTestWorlds.addIfMissing(settings, entry));
			keys.add(entry.getHostName().toLowerCase() + ":"
					+ entry.getPortString());
		}
		assertEquals(4, settings.getList().size());
		assertEquals(4, keys.size());
	}

	@Test
	public void hostAndPortMatchPublicListings() {
		MudConnection dw = BuiltinMxpWorlds.discworld();
		assertEquals("discworld.starturtle.net", dw.getHostName());
		assertEquals("4242", dw.getPortString());

		MudConnection th = BuiltinMxpWorlds.threshold();
		assertEquals("thresholdrpg.com", th.getHostName());
		assertEquals("3333", th.getPortString());

		MudConnection an = BuiltinMxpWorlds.ansalon();
		assertEquals("ansalon.net", an.getHostName());
		assertEquals("8679", an.getPortString());

		MudConnection ms = BuiltinMxpWorlds.midnightSun();
		assertEquals("midnightsun2.org", ms.getHostName());
		assertEquals("3000", ms.getPortString());
	}
}
