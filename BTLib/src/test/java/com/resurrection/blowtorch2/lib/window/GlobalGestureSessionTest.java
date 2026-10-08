package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.GlobalGestureSession.Decision;

public class GlobalGestureSessionTest {

	private static GlobalGestures gestures(final int mode, final int scroll,
			final boolean twoDir, final boolean twoCopy, final boolean twoScroll,
			final String bindings) {
		return new GlobalGestures(mode, scroll, 200, false, true, true,
				twoDir, twoCopy, twoScroll, bindings);
	}

	private static GlobalGestureSession session(final GlobalGestures config) {
		GlobalGestureSession s = new GlobalGestureSession();
		s.setConfig(config, 10f, 40f);
		return s;
	}

	@Test
	public void classicIgnoresTheSequence() {
		GlobalGestureSession s = session(GlobalGestures.defaults());
		assertEquals(Decision.Kind.IGNORE, s.onDown(0, 0f, 0f).kind);
		assertEquals(Decision.Kind.IGNORE, s.onMove(20, 1, 0f, -80f, 0f, 0f).kind);
		assertEquals(Decision.Kind.IGNORE, s.onPointerDown(30, 2, 0f, 0f, 40f, 40f).kind);
	}

	@Test
	public void oneFingerFiresBoundDirectionOnUp() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north"));
		assertEquals(Decision.Kind.IGNORE, s.onDown(0, 0f, 0f).kind);
		Decision preview = s.onMove(30, 1, 0f, -80f, 0f, 0f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals("n", preview.direction);
		assertEquals("north", preview.command);
		Decision up = s.onUp(40, 0f, -80f);
		assertEquals(Decision.Kind.FIRE, up.kind);
		assertEquals("north", up.command);
		assertEquals(Decision.Kind.EAT, s.onUp(50, 0f, -80f).kind);
	}

	@Test
	public void unboundDirectionDoesNotFireWhenScrollIsOff() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		assertEquals(Decision.Kind.EAT, s.onMove(30, 1, 80f, 0f, 0f, 0f).kind);
		assertEquals(Decision.Kind.EAT, s.onUp(40, 80f, 0f).kind);
	}

	@Test
	public void secondFingerDuringAOneFingerPreviewCancelsWithoutCopy() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		s.onMove(30, 1, 0f, -80f, 0f, 0f);
		assertEquals(Decision.Kind.CLEAR,
				s.onPointerDown(40, 2, 0f, -80f, 30f, -80f).kind);
		assertEquals(Decision.Kind.EAT, s.onPointerUp(45, 1, 1).kind);
		Decision up = s.onUp(50, 0f, -80f);
		assertNull(up.command);
		assertEquals(Decision.Kind.EAT, up.kind);
	}

	@Test
	public void secondFingerDuringHoldGestureCancelsWithoutCopy() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		Decision preview = s.onMove(250, 1, 0f, -80f, 0f, 0f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals(Decision.Kind.CLEAR,
				s.onPointerDown(260, 2, 0f, -80f, 40f, -80f).kind);
		assertEquals(Decision.Kind.EAT, s.onPointerUp(270, 1, 1).kind);
		Decision up = s.onUp(280, 0f, -80f);
		assertNull(up.command);
		assertEquals(Decision.Kind.EAT, up.kind);
	}

	@Test
	public void holdPolicyScrollsBeforeTheHoldAndFiresAfter() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		assertEquals(Decision.Kind.IGNORE, s.onMove(50, 1, 0f, -80f, 0f, 0f).kind);
		s.onDown(0, 0f, 0f);
		assertEquals(Decision.Kind.EAT, s.onMove(250, 1, 0f, -5f, 0f, 0f).kind);
		Decision preview = s.onMove(300, 1, 0f, -80f, 0f, 0f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals("north", s.onUp(320, 0f, -80f).command);
	}

	@Test
	public void holdPolicyUnboundDirectionScrollsAfterTheHold() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		Decision started = s.onMove(250, 1, 80f, 0f, 0f, 0f);
		assertEquals(Decision.Kind.SCROLL, started.kind);
		assertEquals(80f, started.dx, 0.01f);
		assertEquals(0f, started.dy, 0.01f);
		Decision kept = s.onMove(300, 1, 120f, 0f, 0f, 0f);
		assertEquals(Decision.Kind.SCROLL, kept.kind);
		assertEquals(40f, kept.dx, 0.01f);
		Decision up = s.onUp(320, 120f, 0f);
		assertEquals(Decision.Kind.EAT, up.kind);
		assertNull(up.command);
	}

	@Test
	public void holdPolicyUnboundDirectionStillScrollsBeforeTheHold() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		assertEquals(Decision.Kind.IGNORE, s.onMove(50, 1, 80f, 0f, 0f, 0f).kind);
	}

	@Test
	public void leavingABoundDirectionForABlankOneScrolls() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		assertEquals("north", s.onMove(250, 1, 0f, -80f, 0f, 0f).command);
		Decision turned = s.onMove(280, 1, 40f, -80f, 0f, 0f);
		assertEquals(Decision.Kind.SCROLL, turned.kind);
		assertEquals(40f, turned.dx, 0.01f);
		assertEquals(0f, turned.dy, 0.01f);
		Decision up = s.onUp(300, 40f, -80f);
		assertEquals(Decision.Kind.EAT, up.kind);
		assertNull(up.command);
	}

	@Test
	public void previewFollowsAChangeOfDirection() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north\n1.s=south"));
		s.onDown(0, 0f, 0f);
		Decision north = s.onMove(30, 1, 0f, -80f, 0f, 0f);
		assertEquals("north", north.command);
		Decision south = s.onMove(50, 1, 0f, 80f, 0f, 0f);
		assertEquals(Decision.Kind.PREVIEW, south.kind);
		assertEquals("s", south.direction);
		assertEquals("south", south.command);
		assertEquals("south", s.onUp(60, 0f, 80f).command);
	}

	@Test
	public void reversingALittleChangesDirectionWithoutReturningToTheStart() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north\n1.s=south"));
		s.onDown(0, 0f, 0f);
		assertEquals("north", s.onMove(30, 1, 0f, -80f, 0f, 0f).command);
		Decision turned = s.onMove(40, 1, 0f, -68f, 0f, 0f);
		assertEquals(Decision.Kind.PREVIEW, turned.kind);
		assertEquals("south", turned.command);
	}

	@Test
	public void aLongerSwipeDoesNotGrowTheReverseDistance() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north\n1.s=south"));
		s.onDown(0, 0f, 0f);
		assertEquals("north", s.onMove(30, 1, 0f, -300f, 0f, 0f).command);
		assertEquals("south", s.onMove(40, 1, 0f, -288f, 0f, 0f).command);
	}

	@Test
	public void secondFingerAfterHoldKeepsLookingForAGesture() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.e=east\n2.n=look"));
		s.onDown(0, 0f, 0f);
		assertEquals(Decision.Kind.IGNORE, s.onMove(50, 1, 0f, -40f, 0f, 0f).kind);
		Decision taken = s.onMove(60, 2, 0f, -40f, 30f, -40f);
		assertEquals(Decision.Kind.EAT, taken.kind);
		Decision preview = s.onMove(80, 2, 0f, -40f, 30f, -120f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals("look", preview.command);
	}

	@Test
	public void anchorDriftDoesNotTurnATwoFingerGestureIntoAScroll() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision preview = s.onMove(40, 2, 0f, -15f, 30f, -80f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals("look", preview.command);
	}

	@Test
	public void holdingTheSecondFingerDoesNotStartAScroll() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		assertEquals(Decision.Kind.IGNORE, s.onDown(0, 0f, 0f).kind);
		assertEquals(Decision.Kind.EAT, s.onPointerDown(10, 2, 0f, 0f, 40f, 0f).kind);
		assertEquals(Decision.Kind.EAT, s.onMove(800, 2, 0f, 15f, 40f, 12f).kind);
		Decision swipe = s.onMove(900, 2, 0f, 15f, 40f, -80f);
		assertEquals(Decision.Kind.PREVIEW, swipe.kind);
		assertEquals("look", swipe.command);
		Decision later = s.onMove(910, 1, 0f, -40f, 40f, -80f);
		assertEquals(Decision.Kind.PREVIEW, later.kind);
	}

	@Test
	public void liftingTheAnchorFingerCancelsATwoFingerGesture() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, false, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 20f, 0f);
		Decision preview = s.onMove(40, 2, 0f, 0f, 20f, -80f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals(1, preview.finger);
		Decision lifted = s.onPointerUp(50, 1, 0);
		assertEquals(Decision.Kind.CLEAR, lifted.kind);
		assertNull(lifted.command);
		Decision up = s.onUp(60, 20f, -80f);
		assertNull(up.command);
		assertEquals(Decision.Kind.EAT, up.kind);
	}

	@Test
	public void liftingTheGesturingFingerFires() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, false, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 20f, 0f);
		assertEquals("look", s.onMove(40, 2, 0f, 0f, 20f, -80f).command);
		Decision lifted = s.onPointerUp(50, 1, 1);
		assertEquals(Decision.Kind.FIRE, lifted.kind);
		assertEquals("look", lifted.command);
		assertEquals(Decision.Kind.EAT, s.onUp(60, 0f, 0f).kind);
	}

	@Test
	public void anchoredSecondFingerFiresTheTwoFingerBinding() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, false, "2.n=look"));
		assertEquals(Decision.Kind.IGNORE, s.onDown(0, 0f, 0f).kind);
		assertEquals(Decision.Kind.EAT, s.onPointerDown(10, 2, 0f, 0f, 20f, 0f).kind);
		Decision preview = s.onMove(40, 2, 0f, 0f, 20f, -80f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals("look", preview.command);
		assertEquals("look", s.onUp(50, 0f, 0f).command);
	}

	@Test
	public void bothFingersMovingScrollWhenThatSwitchIsOn() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision scroll = s.onMove(40, 2, 0f, -40f, 30f, -40f);
		assertEquals(Decision.Kind.SCROLL, scroll.kind);
	}

	@Test
	public void twoFingerModeIgnoresTheBothFingersScrollSwitch() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision preview = s.onMove(40, 2, 0f, -40f, 30f, -40f);
		assertEquals(Decision.Kind.PREVIEW, preview.kind);
		assertEquals("look", preview.command);
	}

	@Test
	public void oneFingerModeScrollsAsSoonAsBothFingersPassTheSlop() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_TWO, true, true, false, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision started = s.onMove(40, 2, 0f, -15f, 30f, -15f);
		assertEquals(Decision.Kind.SCROLL, started.kind);
		assertNull(started.command);
		Decision kept = s.onMove(50, 2, 0f, -15f, 30f, -40f);
		assertEquals(Decision.Kind.SCROLL, kept.kind);
	}

	@Test
	public void shortTwoFingerTapCopiesWhenCopyIsOn() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, true, false, ""));
		s.onDown(0, 10f, 10f);
		s.onPointerDown(10, 2, 10f, 10f, 40f, 12f);
		assertEquals(Decision.Kind.COPY, s.onPointerUp(20, 1).kind);
		assertEquals(Decision.Kind.EAT, s.onUp(30, 40f, 12f).kind);
	}

	@Test
	public void twoFingerTapDoesNotCopyWhenCopyIsOff() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_TWO,
				GlobalGestures.SCROLL_HOLD, true, false, false, ""));
		s.onDown(0, 10f, 10f);
		s.onPointerDown(10, 2, 10f, 10f, 40f, 12f);
		assertEquals(Decision.Kind.EAT, s.onUp(30, 10f, 10f).kind);
	}

	@Test
	public void thirdFingerCancelsATwoFingerPreview() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_OFF, true, true, false, "2.e=east"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 10f, 0f);
		s.onMove(30, 2, 0f, 0f, 80f, 0f);
		assertEquals(Decision.Kind.CLEAR, s.onPointerDown(40, 3, 0f, 0f, 80f, 0f).kind);
		Decision up = s.onUp(50, 80f, 0f);
		assertNull(up.command);
		assertEquals(Decision.Kind.EAT, up.kind);
	}

	@Test
	public void oneFingerModeWithTwoFingerScrollDoesNotRunDirectionCommands() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_TWO, true, true, false, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 20f, 0f);
		Decision moved = s.onMove(40, 2, 0f, 0f, 20f, -80f);
		assertEquals(Decision.Kind.SCROLL, moved.kind);
		assertNull(moved.command);
	}

	@Test
	public void liftingOneFingerStopsATwoFingerScroll() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_TWO, true, true, false, ""));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		assertEquals(Decision.Kind.SCROLL, s.onMove(40, 2, 0f, -20f, 30f, -20f).kind);
		assertEquals(Decision.Kind.EAT, s.onPointerUp(50, 1).kind);
		assertEquals(Decision.Kind.EAT, s.onMove(60, 1, 0f, -40f, 0f, 0f).kind);
	}

	@Test
	public void aSidewaysTurnChangesTheSlice() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, true, true, false, "1.n=north\n1.e=east"));
		s.onDown(0, 0f, 0f);
		assertEquals("north", s.onMove(30, 1, 0f, -80f, 0f, 0f).command);
		assertEquals("east", s.onMove(40, 1, 20f, -80f, 0f, 0f).command);
	}

	@Test
	public void secondFingerDuringHoldDoesNotFallThrough() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_HOLD, true, true, false, "1.n=north"));
		s.onDown(0, 0f, 0f);
		assertEquals(Decision.Kind.EAT, s.onPointerDown(20, 2, 0f, 0f, 40f, 0f).kind);
		assertEquals(Decision.Kind.EAT, s.onMove(30, 2, 0f, -40f, 40f, 0f).kind);
	}

	@Test
	public void bothModeWithTwoFingersScrollsPastTheSlop() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_TWO, true, true, false, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision started = s.onMove(40, 2, 0f, -15f, 30f, -15f);
		assertEquals(Decision.Kind.SCROLL, started.kind);
		assertNull(started.command);
	}

	@Test
	public void twoFingerScrollUsesTheMidpointWhenBothMove() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_TWO, true, true, false, ""));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision moved = s.onMove(40, 2, 0f, -12f, 30f, -48f);
		assertEquals(Decision.Kind.SCROLL, moved.kind);
		assertEquals(0f, moved.dx, 0.01f);
		assertEquals(-30f, moved.dy, 0.01f);
	}

	@Test
	public void twoFingerScrollAveragesAPinchThatAlsoTravels() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_TWO, true, true, false, ""));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision pinch = s.onMove(40, 2, -30f, -40f, 60f, -50f);
		assertEquals(Decision.Kind.SCROLL, pinch.kind);
		assertEquals(0f, pinch.dx, 0.01f);
		assertEquals(-45f, pinch.dy, 0.01f);
	}

	@Test
	public void twoFingerScrollAveragesOppositeDirections() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_TWO, true, true, false, ""));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 30f, 0f);
		Decision pinch = s.onMove(40, 2, 0f, -40f, 30f, 40f);
		assertEquals(Decision.Kind.SCROLL, pinch.kind);
		assertEquals(0f, pinch.dy, 0.01f);
	}

	@Test
	public void slowerFingerStillScrollsWhenBothMoveTheSameWay() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 40f, 0f);
		Decision moved = s.onMove(40, 2, 0f, -80f, 40f, -20f);
		assertEquals(Decision.Kind.SCROLL, moved.kind);
		assertNull(moved.command);
		assertEquals(-50f, moved.dy, 0.01f);
	}

	@Test
	public void aStillFingerKeepsLookingForATwoFingerGesture() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 40f, 0f);
		Decision swipe = s.onMove(40, 2, 0f, 4f, 40f, -80f);
		assertEquals(Decision.Kind.PREVIEW, swipe.kind);
		assertEquals("look", swipe.command);
	}

	@Test
	public void aFingerInsideTwoSlopsStillDrawsTheGesture() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 40f, 0f);
		Decision swipe = s.onMove(40, 2, 0f, -15f, 40f, -80f);
		assertEquals(Decision.Kind.PREVIEW, swipe.kind);
		assertEquals("look", swipe.command);
	}

	@Test
	public void fingersAboutSeventyDegreesApartDoNotScroll() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 40f, 0f);
		// 80° from straight up: dot about 0.17, outside the 0.35 gate.
		Decision apart = s.onMove(40, 2, 0f, -80f, 40f + 39.4f, -6.9f);
		assertEquals(Decision.Kind.PREVIEW, apart.kind);
		assertEquals("look", apart.command);
	}

	@Test
	public void fingersInsideSeventyDegreesScroll() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 40f, 0f);
		// 60° from straight up: dot 0.5.
		Decision moved = s.onMove(40, 2, 0f, -80f, 40f + 34.6f, -20f);
		assertEquals(Decision.Kind.SCROLL, moved.kind);
		assertNull(moved.command);
	}

	@Test
	public void sidewaysScrollStaysOnTheBacklogWhenTheLineIsNotWider() {
		assertEquals(2, GlobalGestureSession.scrollAxis(80f, 5f, false));
		assertEquals(1, GlobalGestureSession.scrollAxis(80f, 5f, true));
		assertEquals(2, GlobalGestureSession.scrollAxis(5f, 80f, true));
		assertEquals(2, GlobalGestureSession.scrollAxis(5f, 80f, false));
	}
	@Test
	public void aTwoFingerScrollKeepsGoingWhenOneFingerPauses() {
		GlobalGestureSession s = session(gestures(GlobalGestures.MODE_BOTH,
				GlobalGestures.SCROLL_HOLD, true, true, true, "2.n=look"));
		s.onDown(0, 0f, 0f);
		s.onPointerDown(10, 2, 0f, 0f, 40f, 0f);
		assertEquals(Decision.Kind.SCROLL, s.onMove(40, 2, 0f, -50f, 40f, -50f).kind);
		Decision back = s.onMove(60, 2, 0f, -5f, 40f, -50f);
		assertEquals(Decision.Kind.SCROLL, back.kind);
		assertNull(back.command);
		assertEquals(45f, back.dy, 0.01f);
	}
}
