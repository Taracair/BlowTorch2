package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Title bar and close button are per-slot, and like scroll speed the slot JSON
 * is their only durable home — extra-text WindowTokens are rebuilt by
 * ensureSlots() and never reach settings.getWindows().
 *
 * <p>copy() is covered on purpose: the controller persists
 * {@code entry.slot.copy()}, so a field missing from it reverts silently on the
 * next drag or resize rather than failing anywhere visible.
 */
public class ExtraTextSlotChromeTest {

	@Test
	public void bothOnByDefault() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		assertTrue(s.isShowTitleBar());
		assertTrue(s.isShowClose());
	}

	@Test
	public void survivesJsonRoundTrip() throws Exception {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setShowTitleBar(false);
		s.setShowClose(false);
		ExtraTextSlot back = ExtraTextSlot.fromJson(s.toJson());
		assertFalse(back.isShowTitleBar());
		assertFalse(back.isShowClose());
	}

	@Test
	public void survivesCopy() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setShowTitleBar(false);
		ExtraTextSlot c = s.copy();
		assertFalse(c.isShowTitleBar());
		assertTrue(c.isShowClose());
	}

	@Test
	public void slotsWrittenBeforeTheseExistedGetTheChrome() throws Exception {
		// A float slot saved by an older build has neither key. Defaulting to off
		// would leave it the black rectangle this was added to fix.
		JSONObject legacy = new JSONObject("{\"name\":\"chat\",\"mode\":\"float\"}");
		ExtraTextSlot s = ExtraTextSlot.fromJson(legacy);
		assertTrue(s.isShowTitleBar());
		assertTrue(s.isShowClose());
	}
}
