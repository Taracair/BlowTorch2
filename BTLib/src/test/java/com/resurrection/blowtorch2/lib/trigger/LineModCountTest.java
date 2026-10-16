package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.MatchShift;

public class LineModCountTest {

	@Test
	public void missingLineIsZero() {
		assertEquals(0, new LineModCount().load(0).totalDelta());
	}

	@Test
	public void aLaterLineDoesNotWipeAnEarlierLinesDelta() {
		LineModCount m = new LineModCount();
		MatchShift line0 = new MatchShift();
		line0.addFromResponder(0, 2, -2);
		m.store(0, line0);
		assertEquals(0, m.load(1).totalDelta());
		MatchShift line1 = new MatchShift();
		line1.addFromResponder(0, 2, -2);
		m.store(1, line1);
		assertEquals(-2, m.load(0).totalDelta());
		assertEquals(-2, m.load(1).totalDelta());
	}

	@Test
	public void dropForgetsAGaggedLine() {
		LineModCount m = new LineModCount();
		MatchShift line = new MatchShift();
		line.addFromResponder(0, 2, -2);
		m.store(3, line);
		m.drop(3);
		assertEquals(0, m.load(3).totalDelta());
	}

	@Test
	public void storeKeepsTheSameShiftTheLineMutates() {
		LineModCount m = new LineModCount();
		MatchShift line = new MatchShift();
		m.store(0, line);
		line.addFromResponder(0, 2, 4);
		assertEquals(4, m.load(0).totalDelta());
	}
}
