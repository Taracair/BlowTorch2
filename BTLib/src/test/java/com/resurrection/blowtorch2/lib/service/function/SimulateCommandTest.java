package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SimulateCommandTest {

	@Test
	public void backslashNIsANewLineAndTheLastLineIsFinished() {
		assertEquals("@roomid a1\n@dir north, south\n",
				SimulateCommand.incomingText("@roomid a1\\n@dir north, south"));
		assertEquals("one\n", SimulateCommand.incomingText("one\n"));
	}
}
