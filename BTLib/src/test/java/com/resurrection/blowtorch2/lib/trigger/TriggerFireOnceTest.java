package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TriggerFireOnceTest {

	@Test
	public void xmlAbsentAndFalseAreOff() {
		assertEquals(TriggerFireOnce.OFF, TriggerFireOnce.fromXml(null));
		assertEquals(TriggerFireOnce.OFF, TriggerFireOnce.fromXml("false"));
		assertEquals(TriggerFireOnce.OFF, TriggerFireOnce.fromXml("TRUE"));
		assertNull(TriggerFireOnce.OFF.xmlValue());
	}

	@Test
	public void xmlTrueIsUntilEnable() {
		assertEquals(TriggerFireOnce.UNTIL_ENABLE, TriggerFireOnce.fromXml("true"));
		assertEquals("true", TriggerFireOnce.UNTIL_ENABLE.xmlValue());
	}

	@Test
	public void xmlSendIsUntilSend() {
		assertEquals(TriggerFireOnce.UNTIL_SEND, TriggerFireOnce.fromXml("send"));
		assertEquals("send", TriggerFireOnce.UNTIL_SEND.xmlValue());
	}

	@Test
	public void parcelZeroOneStayCompatible() {
		assertEquals(TriggerFireOnce.OFF, TriggerFireOnce.fromParcel(0));
		assertEquals(TriggerFireOnce.UNTIL_ENABLE, TriggerFireOnce.fromParcel(1));
		assertEquals(0, TriggerFireOnce.OFF.toParcel());
		assertEquals(1, TriggerFireOnce.UNTIL_ENABLE.toParcel());
	}

	@Test
	public void parcelTwoIsUntilSend() {
		assertEquals(TriggerFireOnce.UNTIL_SEND, TriggerFireOnce.fromParcel(2));
		assertEquals(2, TriggerFireOnce.UNTIL_SEND.toParcel());
		assertEquals(TriggerFireOnce.OFF, TriggerFireOnce.fromParcel(99));
	}

	@Test
	public void booleanSetterIsUntilEnable() {
		TriggerData t = new TriggerData();
		t.setFireOnce(true);
		assertEquals(TriggerFireOnce.UNTIL_ENABLE, t.getFireOnce());
		assertTrue(t.isFireOnce());
		t.setFireOnce(false);
		assertEquals(TriggerFireOnce.OFF, t.getFireOnce());
		assertFalse(t.isFireOnce());
	}

	@Test
	public void enablingClearsFired() {
		TriggerData t = new TriggerData();
		t.setFireOnce(TriggerFireOnce.UNTIL_ENABLE);
		t.setFired(true);
		t.setEnabled(false);
		assertTrue(t.isFired());
		t.setEnabled(true);
		assertFalse(t.isFired());
	}
}
