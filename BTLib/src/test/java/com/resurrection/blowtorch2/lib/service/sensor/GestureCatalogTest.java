package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * What counts as a gesture pattern. Getting this wrong in either direction is
 * bad in a way nobody would report as a sensor problem: too wide and a text
 * trigger stops matching text, too narrow and the gesture never fires.
 */
public class GestureCatalogTest {

	@Test
	public void aGesturePatternIsThePrefixPlusAKnownName() {
		assertTrue(GestureCatalog.isGesturePattern("!wave", true));
		assertTrue(GestureCatalog.isGesturePattern("!shake", true));
		assertEquals("wave", GestureCatalog.fromPattern("!wave", true).getId());
	}

	@Test
	public void anUnknownNameAfterThePrefixIsOrdinaryText() {
		// The reservation is deliberately narrow. A player whose world shouts
		// "!!!" keeps their trigger, and a profile from a later version naming a
		// gesture this build has never heard of matches nothing rather than
		// swallowing a line of game text.
		assertFalse(GestureCatalog.isGesturePattern("!!!", true));
		assertFalse(GestureCatalog.isGesturePattern("!wobble", true));
		assertNull(GestureCatalog.fromPattern("!!!", true));
	}

	@Test
	public void aRegexIsNeverAGesture() {
		// "!wave" is a perfectly good regular expression, and a player who ticked
		// the regex box meant it as one.
		assertFalse(GestureCatalog.isGesturePattern("!wave", false));
		assertNull(GestureCatalog.fromPattern("!wave", false));
	}

	@Test
	public void plainGameTextIsNeverAGesture() {
		assertFalse(GestureCatalog.isGesturePattern("You are hungry.", true));
		assertFalse(GestureCatalog.isGesturePattern("", true));
		assertFalse(GestureCatalog.isGesturePattern(null, true));
	}

	@Test
	public void everyGestureSaysHowItCouldBeMeasured() {
		// A gesture with no providers could never be resolved on any device, and
		// would sit in the list for ever as "unavailable" with no reason.
		for (GestureCatalog.Gesture g : GestureCatalog.all()) {
			assertFalse("id must not be empty", g.getId().isEmpty());
			assertTrue("needs at least one provider: " + g.getId(),
					g.getProviders().size() >= 1);
			assertEquals("!" + g.getId(), g.getPattern());
			assertNotNull(GestureCatalog.byId(g.getId()));
		}
	}

	@Test
	public void namesAreCaseInsensitiveBecausePlayersTypeThem() {
		assertNotNull(GestureCatalog.byId("WAVE"));
		assertNotNull(GestureCatalog.byId("  Shake "));
	}

	@Test
	public void waveFallsBackToLightWhenThereIsNoProximitySensor() {
		// The order is the contract: the resolver walks it and takes the first
		// sensor the device really has.
		assertEquals(java.util.Arrays.asList(
				GestureCatalog.BY_PROXIMITY, GestureCatalog.BY_LIGHT),
				GestureCatalog.byId("wave").getProviders());
	}
}
