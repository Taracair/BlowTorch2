package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScrollSensitivityTest {

	@Test
	public void oldWindowIndexThreeIsTwoHundredPercentNotThreePercent() {
		assertEquals(200, ScrollSensitivity.migrateWindowXml(3));
		assertEquals(2.0f, ScrollSensitivity.gain(Integer.valueOf(3)), 0.0001f);
		assertEquals(2.0f, ScrollSensitivity.gain(Integer.valueOf(200)), 0.0001f);
	}

	@Test
	public void oldExtraTextThreeIsOneHundredFiftyNotWindowTable() {
		assertEquals(150, ScrollSensitivity.migrateExtraTextJson(3));
		assertEquals(0, ScrollSensitivity.migrateExtraTextJson(0));
		assertEquals(500, ScrollSensitivity.migrateExtraTextJson(9));
		assertEquals(50, ScrollSensitivity.migrateExtraTextJson(50));
	}

	@Test
	public void oldWindowTable() {
		assertEquals(75, ScrollSensitivity.migrateWindowXml(0));
		assertEquals(100, ScrollSensitivity.migrateWindowXml(1));
		assertEquals(150, ScrollSensitivity.migrateWindowXml(2));
		assertEquals(300, ScrollSensitivity.migrateWindowXml(4));
		assertEquals(500, ScrollSensitivity.migrateWindowXml(8));
	}

	@Test
	public void gapNineToFortyNineIsDefaultNotPercent() {
		assertEquals(100, ScrollSensitivity.migrateWindowXml(9));
		assertEquals(100, ScrollSensitivity.migrateWindowXml(42));
		assertEquals(0, ScrollSensitivity.migrateExtraTextJson(10));
		assertEquals(0, ScrollSensitivity.migrateExtraTextJson(42));
	}

	@Test
	public void labelsMatchAllowed() {
		String[] labels = ScrollSensitivity.labels();
		assertEquals(ScrollSensitivity.ALLOWED.length, labels.length);
		assertEquals("50%", labels[0]);
		assertEquals("100%", labels[2]);
		assertEquals("500%", labels[labels.length - 1]);
		for (int i = 0; i < ScrollSensitivity.ALLOWED.length; i++) {
			assertEquals(i, ScrollSensitivity.indexOf(ScrollSensitivity.ALLOWED[i]));
		}
	}

	@Test
	public void extraTextSpinnerRoundTrip() {
		assertEquals(0, ScrollSensitivity.extraTextSpinnerIndex(0));
		assertEquals(0, ScrollSensitivity.extraTextStoredFromSpinner(0));
		assertEquals(50, ScrollSensitivity.extraTextStoredFromSpinner(1));
		assertEquals(1, ScrollSensitivity.extraTextSpinnerIndex(50));
		assertEquals(500, ScrollSensitivity.extraTextStoredFromSpinner(
				ScrollSensitivity.ALLOWED.length));
	}

	@Test
	public void nonsenseGainIsFingerTracking() {
		assertEquals(1.0f, ScrollSensitivity.gain(null), 0.0001f);
		assertEquals(1.0f, ScrollSensitivity.gain(Integer.valueOf(-1)), 0.0001f);
		assertEquals(1.0f, ScrollSensitivity.gain(Integer.valueOf(99)), 0.0001f);
	}

	@Test
	public void allowedGainsRise() {
		float previous = 0f;
		for (int p : ScrollSensitivity.ALLOWED) {
			float g = ScrollSensitivity.gain(Integer.valueOf(p));
			assertTrue("percent " + p + " does not increase", g > previous);
			previous = g;
		}
	}
}
