package com.resurrection.blowtorch2.lib.responder.sound;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VolumePercentInputTest {

	@Test
	public void emptyAndRange() {
		assertTrue(VolumePercentInput.allow(""));
		assertTrue(VolumePercentInput.allow("0"));
		assertTrue(VolumePercentInput.allow("100"));
		assertTrue(VolumePercentInput.allow("00"));
	}

	@Test
	public void rejectsOver100() {
		assertFalse(VolumePercentInput.allow("101"));
		assertFalse(VolumePercentInput.allow("500"));
		assertFalse(VolumePercentInput.allow("-1"));
		assertFalse(VolumePercentInput.allow("1a"));
	}
}
