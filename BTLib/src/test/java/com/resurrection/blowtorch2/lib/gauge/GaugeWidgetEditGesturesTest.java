package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GaugeWidgetEditGesturesTest {

	@Test
	public void editHoldIsNotTwoSeconds() {
		assertEquals(450, GaugeWidgetEditGestures.EDIT_HOLD_MS);
		assertTrue(GaugeWidgetEditGestures.EDIT_HOLD_MS < 2000);
	}

	@Test
	public void notEditing_longPressEntersEdit_notHold() {
		assertTrue(GaugeWidgetEditGestures.shouldEnterEditOnHold(false, false, false, 450));
		assertTrue(GaugeWidgetEditGestures.shouldEnterEditOnHold(false, false, false, 800));
		assertFalse(GaugeWidgetEditGestures.shouldEnterEditOnHold(false, false, false, 449));
		assertFalse(GaugeWidgetEditGestures.shouldEnterEditOnHold(false, true, false, 800));
		assertFalse(GaugeWidgetEditGestures.shouldEnterEditOnHold(false, false, true, 800));
		assertFalse(GaugeWidgetEditGestures.shouldEnterEditOnHold(true, false, false, 800));
		assertFalse(GaugeWidgetEditGestures.shouldFireHold());
	}

	@Test
	public void editing_longPressExits() {
		assertTrue(GaugeWidgetEditGestures.shouldExitEditOnHold(true, false, false, 450));
		assertFalse(GaugeWidgetEditGestures.shouldExitEditOnHold(true, false, true, 800));
		assertFalse(GaugeWidgetEditGestures.shouldExitEditOnHold(false, false, false, 800));
	}

	@Test
	public void editing_shortTapExitsWithoutCommands() {
		assertTrue(GaugeWidgetEditGestures.shouldExitEditOnTap(true, false, false, false));
		assertFalse(GaugeWidgetEditGestures.shouldExitEditOnTap(true, false, true, false));
		assertFalse(GaugeWidgetEditGestures.shouldExitEditOnTap(true, false, false, true));
		assertFalse(GaugeWidgetEditGestures.shouldExitEditOnTap(true, true, false, false));
		assertFalse(GaugeWidgetEditGestures.shouldExitEditOnTap(false, false, false, false));
		assertFalse(GaugeWidgetEditGestures.shouldDispatchCommands(true, false));
		assertTrue(GaugeWidgetEditGestures.shouldDispatchCommands(false, false));
		assertFalse(GaugeWidgetEditGestures.shouldDispatchCommands(false, true));
	}

	@Test
	public void gripAndChromeOnlyWhileEditing() {
		assertTrue(GaugeWidgetEditGestures.shouldArmResize(true, true));
		assertFalse(GaugeWidgetEditGestures.shouldArmResize(false, true));
		assertFalse(GaugeWidgetEditGestures.shouldArmResize(true, false));
		assertTrue(GaugeWidgetEditGestures.shouldDrawEditChrome(true));
		assertFalse(GaugeWidgetEditGestures.shouldDrawEditChrome(false));
		assertTrue(GaugeWidgetEditGestures.shouldArmEditHoldTimer(false, true));
		assertTrue(GaugeWidgetEditGestures.shouldArmEditHoldTimer(true, false));
		assertFalse(GaugeWidgetEditGestures.shouldArmEditHoldTimer(true, true));
	}
}
