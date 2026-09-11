package com.resurrection.blowtorch2.lib.responder.notification;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationAlertEffectsTest {

	@Test
	public void vibrateOffIsNull() {
		assertNull(NotificationAlertEffects.vibratePattern(false, 0));
		assertNull(NotificationAlertEffects.vibratePattern(false, 3));
	}

	@Test
	public void defaultIsARealPattern() {
		long[] p = NotificationAlertEffects.vibratePattern(true, 0);
		assertArrayEquals(NotificationAlertEffects.VIBRATE_DEFAULT, p);
		assertTrue(hasBuzz(p));
	}

	@Test
	public void namedPatterns() {
		assertSame(NotificationAlertEffects.VIBRATE_VERY_SHORT,
				NotificationAlertEffects.vibratePattern(true, 1));
		assertSame(NotificationAlertEffects.VIBRATE_SHORT,
				NotificationAlertEffects.vibratePattern(true, 2));
		assertSame(NotificationAlertEffects.VIBRATE_LONG,
				NotificationAlertEffects.vibratePattern(true, 3));
		assertSame(NotificationAlertEffects.VIBRATE_SUPER_LONG,
				NotificationAlertEffects.vibratePattern(true, 4));
	}

	@Test
	public void unknownLengthUsesDefault() {
		assertSame(NotificationAlertEffects.VIBRATE_DEFAULT,
				NotificationAlertEffects.vibratePattern(true, 99));
	}

	@Test
	public void soundOff() {
		assertFalse(NotificationAlertEffects.usePhoneDefaultSound(false, ""));
		assertNull(NotificationAlertEffects.customSoundPath(false, "bundled:mid_ping"));
	}

	@Test
	public void emptyPathIsPhoneDefault() {
		assertTrue(NotificationAlertEffects.usePhoneDefaultSound(true, ""));
		assertTrue(NotificationAlertEffects.usePhoneDefaultSound(true, null));
		assertNull(NotificationAlertEffects.customSoundPath(true, ""));
		assertNull(NotificationAlertEffects.customSoundPath(true, null));
	}

	@Test
	public void storedPathIsCustom() {
		assertFalse(NotificationAlertEffects.usePhoneDefaultSound(true, "bundled:mid_ping"));
		assertEquals("bundled:mid_ping",
				NotificationAlertEffects.customSoundPath(true, "bundled:mid_ping"));
	}

	@Test
	public void settingsSymlinkIsNotASoundPoolPath() {
		assertTrue(com.resurrection.blowtorch2.lib.util.NotificationSounds
				.isSettingsContentUri("content://settings/system/notification_sound"));
		assertFalse(com.resurrection.blowtorch2.lib.util.NotificationSounds
				.isSettingsContentUri("content://media/internal/audio/media/1"));
		assertFalse(com.resurrection.blowtorch2.lib.util.NotificationSounds
				.isSettingsContentUri("bundled:mid_ping"));
		assertFalse(com.resurrection.blowtorch2.lib.util.NotificationSounds
				.isSettingsContentUri(null));
	}

	private static boolean hasBuzz(final long[] offOn) {
		for (int i = 1; i < offOn.length; i += 2) {
			if (offOn[i] > 0L) {
				return true;
			}
		}
		return false;
	}
}
