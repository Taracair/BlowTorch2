package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapperOverlayGeometryTest {

	@Test
	public void fullscreenPaddingMatchesTheExistingCast() {
		MapperOverlayGeometry g = MapperOverlayGeometry.capture(
				true, 12, 48, 280, 320, 2.5f, true, 10);
		assertEquals(10, g.padLeft);
		assertEquals(20, g.padTop);
		assertEquals(20, g.padRight);
		assertEquals(10, g.padBottom);
		assertTrue(g.constrainAbove);
		assertEquals(10, g.aboveViewId);
	}

	@Test
	public void floatPaddingIsTwoDensityPixels() {
		MapperOverlayGeometry g = MapperOverlayGeometry.capture(
				false, 12, 48, 280, 320, 1.5f, true, 10);
		assertEquals(3, g.padLeft);
		assertEquals(3, g.padTop);
		assertEquals(3, g.padRight);
		assertEquals(3, g.padBottom);
		assertEquals(12, g.x);
		assertEquals(48, g.y);
		assertEquals(280, g.width);
		assertEquals(320, g.height);
		assertFalse(g.constrainAbove);
	}

	@Test
	public void repeatingFullscreenIgnoresFloatPosition() {
		MapperOverlayGeometry first = MapperOverlayGeometry.capture(
				true, 12, 48, 280, 320, 1f, true, 10);
		MapperOverlayGeometry again = MapperOverlayGeometry.capture(
				true, 40, 90, 100, 100, 1f, true, 10);
		assertTrue(first.sameAs(again));
	}

	@Test
	public void inputBarIdIsPartOfTheFullscreenFrame() {
		MapperOverlayGeometry aboveTen = MapperOverlayGeometry.capture(
				true, 0, 0, 0, 0, 1f, true, 10);
		MapperOverlayGeometry aboveNone = MapperOverlayGeometry.capture(
				true, 0, 0, 0, 0, 1f, false, 10);
		assertFalse(aboveTen.sameAs(aboveNone));
	}

	@Test
	public void aMovedFloatFrameIsNotTheSame() {
		MapperOverlayGeometry parked = MapperOverlayGeometry.capture(
				false, 12, 48, 280, 320, 1f, false, 0);
		MapperOverlayGeometry dragged = MapperOverlayGeometry.capture(
				false, 20, 48, 280, 320, 1f, false, 0);
		assertFalse(parked.sameAs(dragged));
		assertFalse(parked.sameAs(null));
	}

	@Test
	public void fullscreenAndFloatAreDifferentFrames() {
		MapperOverlayGeometry full = MapperOverlayGeometry.capture(
				true, 12, 48, 280, 320, 1f, true, 10);
		MapperOverlayGeometry floating = MapperOverlayGeometry.capture(
				false, 12, 48, 280, 320, 1f, true, 10);
		assertFalse(full.sameAs(floating));
	}
}
