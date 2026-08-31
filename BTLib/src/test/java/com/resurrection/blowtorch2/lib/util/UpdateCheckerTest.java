package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Version comparison for the update check. Getting this wrong either nags a
 * player who is already current, or never tells them anything.
 */
public class UpdateCheckerTest {

	@Test
	public void stripsTheLeadingV() {
		assertEquals("2.1.14", UpdateChecker.normalizeVersion("v2.1.14"));
		assertEquals("2.1.14", UpdateChecker.normalizeVersion("V2.1.14"));
		assertEquals("2.1.14", UpdateChecker.normalizeVersion("  2.1.14 "));
	}

	@Test
	public void newerIsNewer() {
		assertTrue(UpdateChecker.compareVersions("2.1.14", "2.1.13") > 0);
		assertTrue(UpdateChecker.compareVersions("2.2.0", "2.1.13") > 0);
		assertTrue(UpdateChecker.compareVersions("3.0", "2.9.9") > 0);
	}

	@Test
	public void olderIsOlder() {
		assertTrue(UpdateChecker.compareVersions("2.1.12", "2.1.13") < 0);
		assertTrue(UpdateChecker.compareVersions("2.1.13", "2.2") < 0);
	}

	@Test
	public void equalIsEqual() {
		assertEquals(0, UpdateChecker.compareVersions("2.1.13", "2.1.13"));
		assertEquals(0, UpdateChecker.compareVersions("v2.1.13", "2.1.13"));
		// Trailing zeros are not a new release.
		assertEquals(0, UpdateChecker.compareVersions("2.1", "2.1.0"));
	}

	/** The trap string comparison falls into. */
	@Test
	public void tenBeatsTwo() {
		assertTrue(UpdateChecker.compareVersions("2.1.10", "2.1.2") > 0);
		assertTrue(UpdateChecker.compareVersions("2.10.0", "2.9.0") > 0);
	}

	/** A suffixed tag must not throw, and must not read as newer. */
	@Test
	public void suffixesDoNotCrashOrWin() {
		assertEquals(0, UpdateChecker.compareVersions("2.1.13-beta", "2.1.13"));
		assertTrue(UpdateChecker.compareVersions("2.1.14-rc1", "2.1.13") > 0);
		assertTrue(UpdateChecker.compareVersions("garbage", "2.1.13") < 0);
	}

	@Test
	public void nullsAreHandled() {
		assertEquals(null, UpdateChecker.normalizeVersion(null));
		assertTrue(UpdateChecker.compareVersions(null, "2.1.13") < 0);
		assertTrue(UpdateChecker.compareVersions("2.1.13", null) > 0);
	}

	/**
	 * Our Gradle and CI do not pass {@code -Pblowtorch.fdroid}. The F-Droid
	 * recipe does, and that APK must default the other way.
	 */
	@Test
	public void githubAndTestBuildsDefaultTheCheckOn() {
		assertTrue(UpdateChecker.defaultEnabled());
	}
}
