package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Drag updates must be able to refresh floatX/Y on a cached model without
 * losing the rest of the snapshot — otherwise an IME rebuild snaps Mode A back.
 */
public class FloatingButtonModelTest {

	@Test
	public void withFloatPositionCopiesIdentityAndCommands() throws Exception {
		JSONObject o = new JSONObject();
		o.put("index", 4);
		o.put("label", "←");
		o.put("command", ".kb stepb");
		o.put("floatMode", FloatingButtonModel.MODE_KEYBOARD);
		o.put("floatX", FloatingLayerGeometry.UNPLACED);
		o.put("floatY", FloatingLayerGeometry.UNPLACED);
		o.put("gridX", 120);
		o.put("gridY", 800);
		o.put("width", 72);
		o.put("height", 72);
		o.put("swipeUpCommand", ".kb stepu");
		FloatingButtonModel original = new FloatingButtonModel(o);

		FloatingButtonModel moved = original.withFloatPosition(40, 900);

		assertNotSame(original, moved);
		assertEquals(4, moved.index);
		assertEquals("←", moved.label);
		assertEquals(".kb stepb", moved.command);
		assertEquals(".kb stepu", moved.swipeUpCommand);
		assertTrue(moved.isKeyboardMode());
		assertEquals(40, moved.floatX);
		assertEquals(900, moved.floatY);
		assertEquals(FloatingLayerGeometry.UNPLACED, original.floatX);
		assertEquals(120f, moved.gridX, 0.01f);
		assertEquals(72f, moved.widthDp, 0.01f);
	}

	@Test
	public void withFloatPositionSameCoordsReturnsSameInstance() throws Exception {
		JSONObject o = new JSONObject();
		o.put("index", 1);
		o.put("floatX", 10);
		o.put("floatY", 20);
		FloatingButtonModel original = new FloatingButtonModel(o);
		assertSame(original, original.withFloatPosition(10, 20));
	}

	@Test
	public void borderFieldsSurviveParseAndDragCopy() throws Exception {
		JSONObject o = new JSONObject();
		o.put("index", 2);
		o.put("floatX", 10);
		o.put("floatY", 20);
		o.put("border", true);
		o.put("borderColor", 0xE0FF00FFL);
		o.put("floatFrame", true);
		o.put("cornerRadiusPx", 24.5);
		FloatingButtonModel original = new FloatingButtonModel(o);
		assertTrue(original.border);
		assertEquals(0xE0FF00FF, original.borderColor);
		assertTrue(original.floatFrame);
		assertEquals(24.5f, original.cornerRadiusPx, 0.01f);

		FloatingButtonModel moved = original.withFloatPosition(40, 50);
		assertTrue(moved.border);
		assertEquals(0xE0FF00FF, moved.borderColor);
		assertTrue(moved.floatFrame);
		assertEquals(24.5f, moved.cornerRadiusPx, 0.01f);
	}

	/** The defect: one stored pair, so a portrait drag followed the turn. */
	@Test
	public void eachOrientationReadsItsOwnStoredPosition() throws Exception {
		JSONObject o = new JSONObject();
		o.put("index", 1);
		o.put("floatX", 10);
		o.put("floatY", 20);
		o.put("floatXLand", 700);
		o.put("floatYLand", 300);

		FloatingButtonModel portrait = new FloatingButtonModel(o, false);
		assertEquals(10, portrait.floatX);
		assertEquals(20, portrait.floatY);

		FloatingButtonModel land = portrait.forOrientation(true);
		assertEquals(700, land.floatX);
		assertEquals(300, land.floatY);
		// ...and back, without a fresh push from Lua.
		assertEquals(10, land.forOrientation(false).floatX);
	}

	/** An existing profile has no landscape pair: unplaced, so it is seeded. */
	@Test
	public void aProfileWithoutALandscapePairKeepsItsPortraitOne() throws Exception {
		JSONObject o = new JSONObject();
		o.put("index", 1);
		o.put("floatX", 10);
		o.put("floatY", 20);

		FloatingButtonModel portrait = new FloatingButtonModel(o, false);
		assertEquals(10, portrait.floatX);
		assertEquals(FloatingLayerGeometry.UNPLACED, portrait.forOrientation(true).floatX);
	}

	/** A drag writes one pair. The other orientation must not move with it. */
	@Test
	public void aDragOnlyWritesTheOrientationItHappenedIn() throws Exception {
		JSONObject o = new JSONObject();
		o.put("index", 1);
		o.put("floatX", 10);
		o.put("floatY", 20);
		o.put("floatXLand", 700);
		o.put("floatYLand", 300);

		FloatingButtonModel draggedInPortrait =
				new FloatingButtonModel(o, false).withFloatPosition(55, 66);
		assertEquals(55, draggedInPortrait.floatX);
		assertEquals(700, draggedInPortrait.getFloatXLandscape());
		assertEquals(300, draggedInPortrait.getFloatYLandscape());

		FloatingButtonModel draggedInLandscape =
				new FloatingButtonModel(o, true).withFloatPosition(800, 400);
		assertEquals(800, draggedInLandscape.floatX);
		assertEquals(10, draggedInLandscape.forOrientation(false).floatX);
	}
}
