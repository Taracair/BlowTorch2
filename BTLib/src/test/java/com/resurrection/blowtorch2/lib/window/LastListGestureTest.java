package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LastListGestureTest {

	@Test
	public void aTapSendsThatRow() {
		LastListGesture gesture = new LastListGesture();
		gesture.down(2);
		assertEquals(3, gesture.up());
	}

	@Test
	public void aGapSendsNothing() {
		LastListGesture gesture = new LastListGesture();
		gesture.down(-1);
		assertEquals(0, gesture.up());
	}

	@Test
	public void aScrollCancelsTheTap() {
		LastListGesture gesture = new LastListGesture();
		gesture.down(0);
		gesture.cancelTouch();
		assertFalse(gesture.tracking());
		assertEquals(0, gesture.up());
	}

	@Test
	public void aSecondFingerOutsideCancelsTheSend() {
		LastListGesture gesture = new LastListGesture();
		gesture.down(0);
		assertTrue(LastListGesture.cancelTap(true, false));
		gesture.secondFinger(false, true);
		assertEquals(0, gesture.up());
	}

	@Test
	public void aSecondFingerOnAMinimalListBringsTheFrameBackAndDoesNotSend() {
		LastListGesture gesture = new LastListGesture();
		gesture.down(0);
		assertTrue(LastListGesture.exitFrame(true, true));
		gesture.secondFinger(true, false);
		assertEquals(0, gesture.up());
	}

	@Test
	public void aSecondFingerOnTheListDoesNotCancelWhenTheFrameIsShowing() {
		assertFalse(LastListGesture.exitFrame(false, true));
		assertFalse(LastListGesture.cancelTap(true, true));
		LastListGesture gesture = new LastListGesture();
		gesture.down(2);
		gesture.secondFinger(false, false);
		assertEquals(3, gesture.up());
	}

	@Test
	public void outsideTheListIsOutsideTheWindowRect() {
		assertTrue(LastListGesture.contains(10, 10, 0, 0, 40, 40));
		assertFalse(LastListGesture.contains(40, 10, 0, 0, 40, 40));
		assertFalse(LastListGesture.contains(-1, 10, 0, 0, 40, 40));
	}

	@Test
	public void screenPositionSurvivesAScrollOffset() {
		assertEquals(500, LastListGesture.screenAxis(500f, 300f, 300f));
		assertEquals(420, LastListGesture.screenAxis(420f, 260f, 260f));
	}
}
