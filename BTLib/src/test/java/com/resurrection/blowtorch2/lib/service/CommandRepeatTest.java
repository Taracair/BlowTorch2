package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The {@code #N cmd} multiplier, pinned before it goes near a device. */
public class CommandRepeatTest {

	private static List<String> list(final String... items) {
		return new ArrayList<String>(Arrays.asList(items));
	}

	@Test
	public void repeatsTheBodyExactlyNTimes() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#3 north"));
		assertEquals(Arrays.asList("north", "north", "north"), r.segments());
		assertNull(r.warning());
	}

	@Test
	public void keepsTheRestOfTheBatchInOrder() {
		CommandRepeat.Result r =
				CommandRepeat.expand(list("stand", "#2 kick troll", "sit"));
		assertEquals(Arrays.asList("stand", "kick troll", "kick troll", "sit"),
				r.segments());
	}

	@Test
	public void oneIsStillAValidCount() {
		assertEquals(Arrays.asList("look"),
				CommandRepeat.expand(list("#1 look")).segments());
	}

	@Test
	public void argumentsAndSpacingSurviveIntact() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#2 get all from   bag"));
		assertEquals(Arrays.asList("get all from   bag", "get all from   bag"),
				r.segments());
	}

	@Test
	public void doubledHashSendsOneLiteralHashAndDoesNotRepeat() {
		assertEquals(Arrays.asList("#5 north"),
				CommandRepeat.expand(list("##5 north")).segments());
	}

	@Test
	public void doubledHashOnANonNumericCommandAlsoUnescapes() {
		assertEquals(Arrays.asList("#help"),
				CommandRepeat.expand(list("##help")).segments());
	}

	@Test
	public void aHashThatIsNotAMultiplierIsLeftAlone() {
		// Worlds that use # for their own commands must keep working.
		assertEquals(Arrays.asList("#help"),
				CommandRepeat.expand(list("#help")).segments());
		assertEquals(Arrays.asList("#5"),
				CommandRepeat.expand(list("#5")).segments());
		assertEquals(Arrays.asList("say #5 is my lucky number"),
				CommandRepeat.expand(list("say #5 is my lucky number")).segments());
	}

	@Test
	public void hashInTheMiddleIsNotAMultiplier() {
		assertEquals(Arrays.asList("say cost is #3 gold"),
				CommandRepeat.expand(list("say cost is #3 gold")).segments());
	}

	@Test
	public void overTheLimitIsRefusedAndLeftAsTyped() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#500 north"));
		assertEquals(Arrays.asList("#500 north"), r.segments());
		assertNotNull(r.warning());
		assertTrue(r.warning().contains("#500 north"));
	}

	@Test
	public void exactlyTheLimitIsAllowed() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#100 north"));
		assertEquals(CommandRepeat.MAX_REPEAT, r.segments().size());
		assertNull(r.warning());
	}

	@Test
	public void zeroIsRefusedRatherThanSwallowingTheCommand() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#0 north"));
		assertEquals(Arrays.asList("#0 north"), r.segments());
		assertNotNull(r.warning());
	}

	@Test
	public void countTooBigForAnIntIsRefusedNotCrashed() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#99999999999999 north"));
		assertEquals(Arrays.asList("#99999999999999 north"), r.segments());
		assertNotNull(r.warning());
	}

	@Test
	public void holdoverSegmentsArePassedThrough() {
		// A trailing ~ is half a command; processOutputData reassembles it.
		assertEquals(Arrays.asList("#2 north~"),
				CommandRepeat.expand(list("#2 north~")).segments());
	}

	@Test
	public void aBatchWithNoHashIsReturnedUntouched() {
		List<String> in = list("north", "kill troll");
		CommandRepeat.Result r = CommandRepeat.expand(in);
		assertSame(in, r.segments());
		assertNull(r.warning());
	}

	@Test
	public void nullAndEmptySurvive() {
		assertNull(CommandRepeat.expand(null).segments());
		assertTrue(CommandRepeat.expand(new ArrayList<String>()).segments().isEmpty());
	}

	@Test
	public void everyRefusedSegmentIsNamedInTheWarning() {
		CommandRepeat.Result r =
				CommandRepeat.expand(list("#500 north", "#0 south", "#2 east"));
		assertTrue(r.warning().contains("#500 north"));
		assertTrue(r.warning().contains("#0 south"));
		assertEquals(Arrays.asList("#500 north", "#0 south", "east", "east"),
				r.segments());
	}

	@Test
	public void leadingWhitespaceStillCounts() {
		assertEquals(Arrays.asList("north", "north"),
				CommandRepeat.expand(list("  #2 north")).segments());
	}
}
