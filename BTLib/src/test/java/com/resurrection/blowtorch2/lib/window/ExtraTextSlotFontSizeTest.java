package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Font size lives on the slot JSON — extra-text WindowTokens are rebuilt by
 * ensureSlots() and never reach settings.getWindows().
 *
 * <p>copy() is covered on purpose: the controller persists
 * {@code entry.slot.copy()}, so a field missing from it reverts on the next
 * drag rather than failing anywhere visible.
 */
public class ExtraTextSlotFontSizeTest {

	@Test
	public void defaultsToTwenty() {
		assertEquals(ExtraTextSlot.FONT_SIZE_DEFAULT, new ExtraTextSlot("chat").getFontSize());
	}

	@Test
	public void clampMatchesFontCommandRange() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setFontSize(3);
		assertEquals(ExtraTextSlot.FONT_SIZE_MIN, s.getFontSize());
		s.setFontSize(200);
		assertEquals(ExtraTextSlot.FONT_SIZE_MAX, s.getFontSize());
	}

	@Test
	public void survivesJsonRoundTripAndCopy() throws Exception {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setFontSize(28);
		assertEquals(28, ExtraTextSlot.fromJson(s.toJson()).getFontSize());
		assertEquals(28, s.copy().getFontSize());
	}

	@Test
	public void slotsSavedBeforeThisSettingStillLoad() {
		JSONObject o = new JSONObject();
		try {
			o.put("name", "chat");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertEquals(ExtraTextSlot.FONT_SIZE_DEFAULT,
				ExtraTextSlot.fromJson(o).getFontSize());
	}
}
