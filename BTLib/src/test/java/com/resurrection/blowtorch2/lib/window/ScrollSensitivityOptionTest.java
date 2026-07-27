package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;

/**
 * The scroll sensitivity option has to survive three separate hops: it must exist in
 * the window settings group at all, it must accept the string form the XML loader hands
 * it, and the chosen index must map onto the gain the touch handler multiplies by.
 * Getting one of those wrong leaves an option that shows up in the dialog and does
 * nothing, which is exactly how the window settings failed before.
 */
public class ScrollSensitivityOptionTest {

	private ListOption findScrollOption() {
		WindowToken token = new WindowToken();
		BaseOption o = (BaseOption) token.getSettings().findOptionByKey("scroll_sensitivity");
		assertNotNull("scroll_sensitivity is not registered in the window settings group", o);
		assertTrue("scroll_sensitivity should be a list, not a bare number field",
				o instanceof ListOption);
		return (ListOption) o;
	}

	@Test
	public void optionIsRegisteredWithChoicesAndDefaultsToNormal() {
		ListOption o = findScrollOption();
		assertEquals("every gain in scrollSensitivityFromChoice needs a visible item",
				5, o.getItems().size());
		assertEquals(Integer.valueOf(WindowToken.DEFAULT_SCROLL_SENSITIVITY), o.getValue());
		assertEquals("the default has to be the setting that changes nothing",
				1.0f, Window.scrollSensitivityFromChoice((Integer) o.getValue()), 0.0001f);
	}

	@Test
	public void optionAcceptsTheStringFormTheXmlLoaderUses() {
		WindowToken token = new WindowToken();
		// WindowOptionElementListener.end() hands every saved option through as text.
		token.getSettings().setOption("scroll_sensitivity", "3");
		ListOption o = (ListOption) token.getSettings().findOptionByKey("scroll_sensitivity");
		assertEquals(Integer.valueOf(3), o.getValue());
		assertEquals(2.0f, Window.scrollSensitivityFromChoice((Integer) o.getValue()), 0.0001f);
	}

	@Test
	public void everyChoiceMapsToItsOwnGainAndTheyRise() {
		ListOption o = findScrollOption();
		float previous = 0f;
		for (int i = 0; i < o.getItems().size(); i++) {
			float gain = Window.scrollSensitivityFromChoice(Integer.valueOf(i));
			assertTrue("choice " + i + " (" + o.getItems().get(i) + ") does not increase the gain",
					gain > previous);
			previous = gain;
		}
	}

	@Test
	public void nonsenseChoicesFallBackToTrackingTheFinger() {
		assertEquals(1.0f, Window.scrollSensitivityFromChoice(null), 0.0001f);
		assertEquals(1.0f, Window.scrollSensitivityFromChoice(Integer.valueOf(-1)), 0.0001f);
		assertEquals(1.0f, Window.scrollSensitivityFromChoice(Integer.valueOf(99)), 0.0001f);
	}
}
