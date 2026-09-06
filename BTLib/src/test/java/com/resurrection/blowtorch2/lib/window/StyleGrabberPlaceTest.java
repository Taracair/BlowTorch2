package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StyleGrabberPlaceTest {

	private static final float VIEW_W = 800f;
	private static final float VIEW_H = 1000f;
	private static final float PANEL_W = 200f;
	private static final float PANEL_H = 300f;
	private static final float GAP = 24f;
	private static final float MARGIN = 8f;
	private static final float ANCHOR = 0.3f;

	@Test
	public void lowFingerSticksToBottomEdge() {
		StyleGrabberPlace p = StyleGrabberPlace.of(100f, 980f, PANEL_W, PANEL_H,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		assertEquals(VIEW_H - MARGIN, p.bottom, 0.01f);
		assertTrue(p.top >= MARGIN);
	}

	@Test
	public void highFingerSticksToTopEdge() {
		StyleGrabberPlace p = StyleGrabberPlace.of(100f, 10f, PANEL_W, PANEL_H,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		assertEquals(MARGIN, p.top, 0.01f);
		assertTrue(p.bottom <= VIEW_H - MARGIN);
	}

	@Test
	public void rightFingerFlipsLeftAndStaysInside() {
		StyleGrabberPlace p = StyleGrabberPlace.of(780f, 400f, PANEL_W, PANEL_H,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		assertTrue(p.right <= VIEW_W - MARGIN + 0.01f);
		assertTrue(p.left >= MARGIN - 0.01f);
		assertTrue(p.right <= 780f - GAP + 0.01f);
	}

	@Test
	public void tallerThanViewFitsBetweenMargins() {
		StyleGrabberPlace p = StyleGrabberPlace.of(100f, 500f, PANEL_W, 5000f,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		assertEquals(MARGIN, p.top, 0.01f);
		assertEquals(VIEW_H - MARGIN, p.bottom, 0.01f);
	}

	@Test
	public void widerThanViewFitsBetweenMargins() {
		StyleGrabberPlace p = StyleGrabberPlace.of(400f, 400f, 5000f, PANEL_H,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		assertEquals(MARGIN, p.left, 0.01f);
		assertEquals(VIEW_W - MARGIN, p.right, 0.01f);
	}

	@Test
	public void roomyPlacementKeepsPreferredSide() {
		StyleGrabberPlace p = StyleGrabberPlace.of(100f, 400f, PANEL_W, PANEL_H,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		assertEquals(100f + GAP, p.left, 0.01f);
		assertEquals(400f - PANEL_H * ANCHOR, p.top, 0.01f);
		assertEquals(100f + GAP + PANEL_W, p.right, 0.01f);
	}

	@Test
	public void tapOnShrunkBottomHitsLastSlotNotALayer() {
		StyleGrabberPlace p = StyleGrabberPlace.of(100f, 500f, PANEL_W, 5000f,
				VIEW_W, VIEW_H, GAP, MARGIN, ANCHOR);
		int slots = 17;
		int last = StyleGrabberPlace.rowAt(p.bottom - 1f, p.top, p.bottom - p.top, slots);
		assertEquals(slots - 1, last);
		assertEquals(0, StyleGrabberPlace.rowAt(p.top + 1f, p.top, p.bottom - p.top, slots));
	}
}
