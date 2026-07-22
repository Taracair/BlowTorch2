package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * MCMP Client.Media.Stop matching / fade rules (no MediaPlayer — Android stubs).
 */
public class GmcpMediaPlayerTest {

	@Test
	public void fadeawayOnly_matchesAllTracks() {
		assertTrue(GmcpMediaPlayer.matchesStop(
				"menu.mp3", "music", "login", "menu", 40,
				"", "", "", "", false, -1));
	}

	@Test
	public void typeMusic_matchesMusicOnly() {
		assertTrue(GmcpMediaPlayer.matchesStop(
				"menu.mp3", "music", "", "", 40,
				"", "music", "", "", false, -1));
		assertFalse(GmcpMediaPlayer.matchesStop(
				"clang.wav", "sound", "", "", 40,
				"", "music", "", "", false, -1));
	}

	@Test
	public void combinedFilters_requireAnd() {
		assertTrue(GmcpMediaPlayer.matchesStop(
				"rain.wav", "sound", "combat", "wx", 40,
				"", "sound", "combat", "", false, -1));
		assertFalse(GmcpMediaPlayer.matchesStop(
				"rain.wav", "sound", "ambient", "wx", 40,
				"", "sound", "combat", "", false, -1));
	}

	@Test
	public void priority_stopsLessOrEqual() {
		assertTrue(GmcpMediaPlayer.matchesStop(
				"a", "music", "", "", 50,
				"", "", "", "", true, 50));
		assertTrue(GmcpMediaPlayer.matchesStop(
				"a", "music", "", "", 40,
				"", "", "", "", true, 50));
		assertFalse(GmcpMediaPlayer.matchesStop(
				"a", "music", "", "", 60,
				"", "", "", "", true, 50));
	}

	@Test
	public void shouldFade_onlyWhenFadeawayOrFadeoutPresent() {
		assertFalse(GmcpMediaPlayer.shouldFade(false, false));
		assertTrue(GmcpMediaPlayer.shouldFade(true, false));
		assertTrue(GmcpMediaPlayer.shouldFade(false, true));
		assertTrue(GmcpMediaPlayer.shouldFade(true, true));
	}

	@Test
	public void optTruthy_acceptsStringBooleans() throws Exception {
		assertTrue(GmcpMediaPlayer.optTruthy(new JSONObject("{\"fadeaway\":\"true\"}"), "fadeaway", false));
		assertTrue(GmcpMediaPlayer.optTruthy(new JSONObject("{\"fadeaway\":1}"), "fadeaway", false));
		assertFalse(GmcpMediaPlayer.optTruthy(new JSONObject("{\"fadeaway\":\"false\"}"), "fadeaway", true));
		assertTrue(GmcpMediaPlayer.optTruthy(new JSONObject("{}"), "continue", true));
	}
}
