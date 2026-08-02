package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.StellarService;

public class EditPanelCommandTest {

	@Test
	public void bareMeansToggle() {
		assertEquals(Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_TOGGLE),
				EditPanelCommand.parseMode(""));
		assertEquals(Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_TOGGLE),
				EditPanelCommand.parseMode("   "));
	}

	@Test
	public void onOffOnly() {
		assertEquals(Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_ON),
				EditPanelCommand.parseMode("on"));
		assertEquals(Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_OFF),
				EditPanelCommand.parseMode("off"));
		assertEquals(Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_ON),
				EditPanelCommand.parseMode("on please"));
	}

	@Test
	public void synonymsRejected() {
		assertNull(EditPanelCommand.parseMode("toggle"));
		assertNull(EditPanelCommand.parseMode("show"));
		assertNull(EditPanelCommand.parseMode("hide"));
		assertNull(EditPanelCommand.parseMode("true"));
		assertNull(EditPanelCommand.parseMode("1"));
		assertNull(EditPanelCommand.parseMode(null));
	}
}
