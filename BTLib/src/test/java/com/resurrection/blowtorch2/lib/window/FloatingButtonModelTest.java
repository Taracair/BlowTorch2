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
}
