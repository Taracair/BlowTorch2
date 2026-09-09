package com.resurrection.blowtorch2.lib.ping;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PingSendPolicyTest {

	@Test
	public void gmcpSettingOnIsNotEnough() {
		assertFalse(PingSendPolicy.sendGmcp(true, false));
		assertFalse(PingSendPolicy.sendGmcp(false, true));
		assertTrue(PingSendPolicy.sendGmcp(true, true));
	}

	@Test
	public void timingMarkOnlyWhenOffered() {
		assertFalse(PingSendPolicy.sendTimingMark(false));
		assertTrue(PingSendPolicy.sendTimingMark(true));
	}
}
