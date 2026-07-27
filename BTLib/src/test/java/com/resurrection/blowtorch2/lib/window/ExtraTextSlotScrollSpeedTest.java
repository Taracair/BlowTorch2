package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Scroll speed is the one overlay setting with no home in the settings tree —
 * extra-text WindowTokens are rebuilt on every ensureSlots() and never reach
 * settings.getWindows(), so this JSON field is the only durable copy.
 */
public class ExtraTextSlotScrollSpeedTest {

	@Test
	public void defaultsToInherit() {
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT, new ExtraTextSlot("chat").getScrollSpeed());
	}

	@Test
	public void inheritFollowsMainWindowChoice() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		assertEquals(1, s.resolveScrollChoice(1));
		assertEquals(4, s.resolveScrollChoice(4));
	}

	@Test
	public void explicitSpeedIgnoresMainWindowChoice() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		// Stored value is the choice plus one, so 0 stays free to mean inherit.
		s.setScrollSpeed(3);
		assertEquals(2, s.resolveScrollChoice(0));
		assertEquals(2, s.resolveScrollChoice(4));
	}

	@Test
	public void outOfRangeFallsBackToInherit() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setScrollSpeed(9);
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT, s.getScrollSpeed());
		s.setScrollSpeed(-2);
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT, s.getScrollSpeed());
	}

	@Test
	public void survivesJsonRoundTrip() throws Exception {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setScrollSpeed(ExtraTextSlot.SCROLL_SPEED_MAX);
		ExtraTextSlot back = ExtraTextSlot.fromJson(s.toJson());
		assertEquals(ExtraTextSlot.SCROLL_SPEED_MAX, back.getScrollSpeed());
		assertEquals(ExtraTextSlot.SCROLL_SPEED_MAX, s.copy().getScrollSpeed());
	}

	@Test
	public void slotsSavedBeforeThisSettingStillLoad() {
		// Profiles written by earlier builds have no scroll_speed key at all.
		ExtraTextSlot back = ExtraTextSlot.fromJson(new JSONObject());
		assertEquals(null, back);
		JSONObject o = new JSONObject();
		try {
			o.put("name", "chat");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT,
				ExtraTextSlot.fromJson(o).getScrollSpeed());
	}

	@Test
	public void corruptStoredValueDoesNotThrow() {
		JSONObject o = new JSONObject();
		try {
			o.put("name", "chat");
			o.put("scroll_speed", 42);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT,
				ExtraTextSlot.fromJson(o).getScrollSpeed());
	}
}
