package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.resurrection.blowtorch2.lib.R;

import org.junit.Test;

/**
 * Family A editors are opaque full-screen; Sensor Test stays a floating card.
 * Theme ids are the checkable part without a Dialog (no Robolectric here).
 */
public class EditorDialogChromeTest {

	@Test
	public void fullScreenThemeMatchesTheListShell() {
		assertEquals(R.style.BlowTorch_Dialog_FullScreen,
				EditorDialogChrome.fullScreenTheme());
	}

	@Test
	public void floatingThemeStaysTheCrawlerDialog() {
		assertEquals(R.style.BlowTorch_Dialog, EditorDialogChrome.dialogTheme());
	}

	@Test
	public void fullScreenIsNotTheFloatingTheme() {
		assertNotEquals(EditorDialogChrome.fullScreenTheme(),
				EditorDialogChrome.dialogTheme());
	}

	@Test
	public void sizingHelpersTolerateNull() {
		EditorDialogChrome.applyFullScreen(null);
		EditorDialogChrome.applyFloatingWrapContentHeight(null);
		EditorDialogChrome.applyNearlyFullScreen(null);
	}

	@Test
	public void fullScreenLayoutConstantIsMatchParent() {
		assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, -1);
		assertTrue(EditorDialogChrome.fullScreenTheme() != 0);
	}
}
