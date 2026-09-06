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
	public void inheritFollowsMainWindowPercent() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		assertEquals(100, s.resolveScrollChoice(100));
		assertEquals(200, s.resolveScrollChoice(200));
		assertEquals(500, s.resolveScrollChoice(500));
	}

	@Test
	public void oldJsonThreeIsOneHundredFiftyPercent() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setScrollSpeed(3);
		assertEquals(150, s.getScrollSpeed());
		assertEquals(150, s.resolveScrollChoice(0));
		assertEquals(150, s.resolveScrollChoice(500));
	}

	@Test
	public void fiveHundredPercentSurvivesJsonRoundTrip() throws Exception {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setScrollSpeed(500);
		ExtraTextSlot back = ExtraTextSlot.fromJson(s.toJson());
		assertEquals(500, back.getScrollSpeed());
		assertEquals(500, s.copy().getScrollSpeed());
	}

	@Test
	public void outOfRangeFallsBackToInherit() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setScrollSpeed(10);
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT, s.getScrollSpeed());
		s.setScrollSpeed(-2);
		assertEquals(ExtraTextSlot.SCROLL_SPEED_INHERIT, s.getScrollSpeed());
	}

	@Test
	public void slotsSavedBeforeThisSettingStillLoad() {
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

	@Test
	public void fromJsonMigratesOldIndexNineToFiveHundred() {
		JSONObject o = new JSONObject();
		try {
			o.put("name", "chat");
			o.put("scroll_speed", 9);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertEquals(500, ExtraTextSlot.fromJson(o).getScrollSpeed());
	}
}
