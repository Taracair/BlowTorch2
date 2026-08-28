package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OrientationGestureTest {

	@Test
	public void portraitToLandscapeIsLandscape() {
		assertEquals(OrientationGesture.ID_LANDSCAPE,
				OrientationGesture.idForChange(
						OrientationGesture.PORTRAIT, OrientationGesture.LANDSCAPE));
	}

	@Test
	public void landscapeToPortraitIsPortrait() {
		assertEquals(OrientationGesture.ID_PORTRAIT,
				OrientationGesture.idForChange(
						OrientationGesture.LANDSCAPE, OrientationGesture.PORTRAIT));
	}

	@Test
	public void stayingPutIsNotAGesture() {
		assertNull(OrientationGesture.idForChange(
				OrientationGesture.PORTRAIT, OrientationGesture.PORTRAIT));
		assertNull(OrientationGesture.idForChange(
				OrientationGesture.LANDSCAPE, OrientationGesture.LANDSCAPE));
	}

	@Test
	public void undefinedOrSquareIsIgnored() {
		assertNull(OrientationGesture.idForChange(OrientationGesture.PORTRAIT, 0));
		assertNull(OrientationGesture.idForChange(OrientationGesture.PORTRAIT, 3));
	}
}
