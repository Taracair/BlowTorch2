package com.resurrection.blowtorch2.lib.responder.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * What the per-trigger sound gap is counted against.
 *
 * <p>The bug this exists for: the key used to be built from the
 * {@code triggernumber} the responder is handed, which is
 * {@code StellarService.getNotificationId()} — a counter that increments on
 * every call. Every firing therefore got a key of its own and the gap never
 * suppressed anything, which is why three FlugHammers in one inventory listing
 * played the same ping three times on top of itself.
 */
public class SoundRateKeyTest {

	@Test
	public void everyFiringOfOneTriggerSharesAKey() {
		// The same trigger, matched three times on one line. Three firings, one
		// key, so the gap can see them as the repeat they are.
		assertEquals(SoundResponder.rateKey("samsaramoo", "_tappable"),
				SoundResponder.rateKey("samsaramoo", "_tappable"));
	}

	@Test
	public void twoTriggersSharingASoundDoNotSilenceEachOther() {
		assertNotEquals(SoundResponder.rateKey("samsaramoo", "_tappable"),
				SoundResponder.rateKey("samsaramoo", "tell"));
	}

	@Test
	public void theSameTriggerOnTwoWorldsIsTwoAlerts() {
		assertNotEquals(SoundResponder.rateKey("samsaramoo", "tell"),
				SoundResponder.rateKey("eden", "tell"));
	}

	@Test
	public void anUnnamedTriggerStillGetsAUsableKey() {
		assertEquals(SoundResponder.rateKey(null, null),
				SoundResponder.rateKey(null, null));
		assertNotEquals(SoundResponder.rateKey("samsaramoo", null),
				SoundResponder.rateKey("eden", null));
	}
}
