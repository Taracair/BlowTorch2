package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unplaced defaults and clamp maths for floating-layer children.
 */
public class FloatingLayerGeometryTest {

	@Test
	public void gridCenterMapsLikeButtonUpdateRect() {
		// data.x=200, width=80dp, density=2 → left = 200 - 80 = 120
		assertEquals(120, FloatingLayerGeometry.gridCenterToLeft(200f, 80f, 2f));
		// data.y=300, height=40dp, density=2, statusOffset=50 → top = 300 - 40 + 50
		assertEquals(310, FloatingLayerGeometry.gridCenterToTop(300f, 40f, 2f, 50));
	}

	@Test
	public void unplacedXUsesLeftMargin() {
		assertEquals(FloatingLayerGeometry.DEFAULT_MARGIN_DP,
				FloatingLayerGeometry.resolveX(FloatingLayerGeometry.UNPLACED));
		assertEquals(80, FloatingLayerGeometry.resolveX(80));
	}

	@Test
	public void unplacedYSitsAboveInputBar() {
		int height = 96;
		int maxBottom = 800;
		assertEquals(maxBottom - height - FloatingLayerGeometry.DEFAULT_MARGIN_DP,
				FloatingLayerGeometry.resolveY(FloatingLayerGeometry.UNPLACED, height, maxBottom));
		assertEquals(120, FloatingLayerGeometry.resolveY(120, height, maxBottom));
	}

	@Test
	public void unplacedYDoesNotGoNegative() {
		assertEquals(0, FloatingLayerGeometry.resolveY(
				FloatingLayerGeometry.UNPLACED, 200, 100));
	}

	@Test
	public void clampXKeepsChildOnScreen() {
		assertEquals(0, FloatingLayerGeometry.clampX(-10, 100, 400));
		assertEquals(300, FloatingLayerGeometry.clampX(999, 100, 400));
		assertEquals(50, FloatingLayerGeometry.clampX(50, 100, 400));
	}

	@Test
	public void clampYRespectsMaxBottom() {
		assertEquals(0, FloatingLayerGeometry.clampY(-5, 80, 500));
		assertEquals(420, FloatingLayerGeometry.clampY(999, 80, 500));
		assertEquals(100, FloatingLayerGeometry.clampY(100, 80, 500));
	}

	@Test
	public void placeResolvesThenClamps() {
		assertArrayEquals(new int[] {
				FloatingLayerGeometry.DEFAULT_MARGIN_DP,
				800 - 96 - FloatingLayerGeometry.DEFAULT_MARGIN_DP
		}, FloatingLayerGeometry.place(
				FloatingLayerGeometry.UNPLACED, FloatingLayerGeometry.UNPLACED,
				120, 96, 1080, 800));

		assertArrayEquals(new int[] { 0, 704 },
				FloatingLayerGeometry.place(-50, 900, 120, 96, 1080, 800));
	}

}
