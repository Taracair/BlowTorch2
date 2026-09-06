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
 * it, and the stored number must map onto the gain the touch handler multiplies by.
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
	public void optionIsRegisteredWithPercentLabelsAndDefaultsToOneHundred() {
		ListOption o = findScrollOption();
		assertEquals(ScrollSensitivity.ALLOWED.length, o.getItems().size());
		assertEquals("50%", o.getItems().get(0));
		assertEquals("500%", o.getItems().get(o.getItems().size() - 1));
		assertEquals(Integer.valueOf(WindowToken.DEFAULT_SCROLL_SENSITIVITY), o.getValue());
		assertEquals(Integer.valueOf(100), o.getValue());
		assertEquals(1.0f, Window.scrollSensitivityFromChoice((Integer) o.getValue()), 0.0001f);
	}

	@Test
	public void xmlLoaderMigratesOldIndexThreeToTwoHundredPercent() {
		WindowToken token = new WindowToken();
		int migrated = ScrollSensitivity.migrateWindowXml(3);
		token.getSettings().setOption("scroll_sensitivity", Integer.toString(migrated));
		ListOption o = (ListOption) token.getSettings().findOptionByKey("scroll_sensitivity");
		assertEquals(Integer.valueOf(200), o.getValue());
		assertEquals(2.0f, Window.scrollSensitivityFromChoice((Integer) o.getValue()), 0.0001f);
	}

	@Test
	public void unmigratedOldIndexStillGainsTheOldAmount() {
		assertEquals(2.0f, Window.scrollSensitivityFromChoice(Integer.valueOf(3)), 0.0001f);
		assertEquals(3.0f, Window.scrollSensitivityFromChoice(Integer.valueOf(4)), 0.0001f);
		assertEquals(5.0f, Window.scrollSensitivityFromChoice(Integer.valueOf(8)), 0.0001f);
		assertEquals(0.5f, Window.scrollSensitivityFromChoice(Integer.valueOf(50)), 0.0001f);
		assertEquals(5.0f, Window.scrollSensitivityFromChoice(Integer.valueOf(500)), 0.0001f);
	}

	@Test
	public void everyAllowedPercentMapsToItsOwnGainAndTheyRise() {
		ListOption o = findScrollOption();
		float previous = 0f;
		for (int i = 0; i < o.getItems().size(); i++) {
			float gain = Window.scrollSensitivityFromChoice(
					Integer.valueOf(ScrollSensitivity.ALLOWED[i]));
			assertTrue("item " + o.getItems().get(i) + " does not increase the gain",
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
