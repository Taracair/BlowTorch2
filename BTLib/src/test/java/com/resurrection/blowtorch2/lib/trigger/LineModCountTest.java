package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LineModCountTest {

	@Test
	public void missingLineIsZero() {
		assertEquals(0, new LineModCount().load(0));
	}

	@Test
	public void aLaterLineDoesNotWipeAnEarlierLinesDelta() {
		LineModCount m = new LineModCount();
		m.store(0, -2);
		assertEquals(0, m.load(1));
		m.store(1, -2);
		assertEquals(-2, m.load(0));
		assertEquals(-2, m.load(1));
	}

	@Test
	public void dropForgetsAGaggedLine() {
		LineModCount m = new LineModCount();
		m.store(3, -2);
		m.drop(3);
		assertEquals(0, m.load(3));
	}
}
