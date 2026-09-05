package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * Android fling must exist on the window group, default off, and accept the
 * XML loader's string form. A missing key would show in Options and do
 * nothing — the same hole scroll_sensitivity had.
 */
public class AndroidFlingOptionTest {

	private BooleanOption find() {
		WindowToken token = new WindowToken();
		BaseOption o = (BaseOption) token.getSettings().findOptionByKey("android_fling");
		assertNotNull("android_fling is not registered in the window settings group", o);
		assertTrue(o instanceof BooleanOption);
		return (BooleanOption) o;
	}

	@Test
	public void optionDefaultsOff() {
		assertFalse(Boolean.TRUE.equals(find().getValue()));
	}

	@Test
	public void optionAcceptsTheStringFormTheXmlLoaderUses() {
		WindowToken token = new WindowToken();
		token.getSettings().setOption("android_fling", "true");
		BooleanOption o = (BooleanOption) token.getSettings().findOptionByKey("android_fling");
		assertEquals(Boolean.TRUE, o.getValue());
	}
}
