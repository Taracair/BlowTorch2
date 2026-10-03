package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InputBarRestartTest {

	@Test
	public void sameBarDoesNotRestart() {
		assertFalse(InputBarRestart.needed(1, 1, 7, 7, false, false, false, false, false, false));
	}

	@Test
	public void growLeavesMultiLineSet() {
		int multi = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
		assertFalse(InputBarRestart.needed(1 | multi, 1, 7, 7, false, false, false, false, false, true));
		assertTrue(InputBarRestart.needed(2 | multi, 1, 7, 7, false, false, false, false, false, true));
	}

	@Test
	public void aRealChangeRestarts() {
		assertTrue(InputBarRestart.needed(1, 2, 7, 7, false, false, false, false, false, false));
		assertTrue(InputBarRestart.needed(1, 1, 7, 1, false, false, false, false, false, false));
		assertTrue(InputBarRestart.needed(1, 1, 1, 1, false, true, false, false, false, false));
		assertTrue(InputBarRestart.needed(1, 1, 1, 1, false, false, true, false, false, false));
		assertTrue(InputBarRestart.needed(1, 1, 7, 7, false, false, false, false, true, false));
	}
}
