package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PrefixCommandTest {

	@Test
	public void bareMeansStatus() {
		assertEquals(Integer.valueOf(PrefixCommand.STATUS), PrefixCommand.parse(""));
		assertEquals(Integer.valueOf(PrefixCommand.STATUS), PrefixCommand.parse("   "));
	}

	@Test
	public void onOffAndToggle() {
		assertEquals(Integer.valueOf(PrefixCommand.ON), PrefixCommand.parse("on"));
		assertEquals(Integer.valueOf(PrefixCommand.OFF), PrefixCommand.parse("off"));
		assertEquals(Integer.valueOf(PrefixCommand.TOGGLE), PrefixCommand.parse("toggle"));
		assertEquals(Integer.valueOf(PrefixCommand.TOGGLE), PrefixCommand.parse("Toggle"));
	}

	@Test
	public void trailingWordIsNotAMode() {
		assertNull(PrefixCommand.parse("on please"));
		assertNull(PrefixCommand.parse("off now"));
		assertNull(PrefixCommand.parse("toggle please"));
	}

	@Test
	public void synonymsRejected() {
		assertNull(PrefixCommand.parse("show"));
		assertNull(PrefixCommand.parse("true"));
		assertNull(PrefixCommand.parse("1"));
		assertNull(PrefixCommand.parse(null));
	}

	@Test
	public void applyFlipsOnlyOnToggle() {
		assertEquals(Boolean.TRUE, PrefixCommand.apply(false, PrefixCommand.ON));
		assertEquals(Boolean.TRUE, PrefixCommand.apply(true, PrefixCommand.ON));
		assertEquals(Boolean.FALSE, PrefixCommand.apply(true, PrefixCommand.OFF));
		assertEquals(Boolean.TRUE, PrefixCommand.apply(false, PrefixCommand.TOGGLE));
		assertEquals(Boolean.FALSE, PrefixCommand.apply(true, PrefixCommand.TOGGLE));
		assertNull(PrefixCommand.apply(false, PrefixCommand.STATUS));
	}

	@Test
	public void usageNamesTheExampleAndTheOption() {
		String usage = PrefixCommand.usage();
		assertEquals(true, usage.contains("kill goblin"));
		assertEquals(true, usage.contains(".prefix on | off | toggle"));
		assertEquals(true, usage.contains(
				"Up finds a command that starts with what you typed"));
	}
}
