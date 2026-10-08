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

	@Test
	public void pacedRepeatWaitsBetweenCopiesNotAfterTheLast() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#3/1s north"));
		assertEquals(Arrays.asList(
				"north", ".wait 1s", "north", ".wait 1s", "north"),
				r.segments());
		assertNull(r.warning());
		CommandWait.Result wait = CommandWait.parseSegment(r.segments().get(1));
		assertSame(CommandWait.Kind.DELAY, wait.kind);
		assertEquals(1000L, wait.delayMs);
	}

	@Test
	public void pacedRepeatKeepsTheRestOfTheBatchAfterTheLastCopy() {
		CommandRepeat.Result r =
				CommandRepeat.expand(list("stand", "#2/1s kick troll", "sit"));
		assertEquals(Arrays.asList(
				"stand", "kick troll", ".wait 1s", "kick troll", "sit"),
				r.segments());
		assertNull(r.warning());
	}

	@Test
	public void oneCopyWithAGapSendsTheCommandOnce() {
		assertEquals(Arrays.asList("look"),
				CommandRepeat.expand(list("#1/2s look")).segments());
	}

	@Test
	public void bareNumberGapIsSeconds() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#2/5 get all"));
		assertEquals(Arrays.asList("get all", ".wait 5", "get all"), r.segments());
		assertEquals(5000L, CommandWait.parseSegment(".wait 5").delayMs);
	}

	@Test
	public void millisecondAndCombinedGapsKeepTheirText() {
		assertEquals(Arrays.asList("kick troll", ".wait 500ms", "kick troll"),
				CommandRepeat.expand(list("#2/500ms kick troll")).segments());
		assertEquals(Arrays.asList("wave", ".wait 5m10s", "wave"),
				CommandRepeat.expand(list("#2/5m10s wave")).segments());
		assertEquals(Arrays.asList("look", ".wait 1.5s", "look"),
				CommandRepeat.expand(list("#2/1.5s look")).segments());
	}

	@Test
	public void anHourIsTheLongestGap() {
		CommandRepeat.Result ok = CommandRepeat.expand(list("#2/1h wave"));
		assertEquals(Arrays.asList("wave", ".wait 1h", "wave"), ok.segments());
		assertNull(ok.warning());
		CommandRepeat.Result over = CommandRepeat.expand(list("#2/2h wave"));
		assertEquals(Arrays.asList("#2/2h wave"), over.segments());
		assertTrue(over.warning().contains("longer than 1h"));
		CommandRepeat.Result sum = CommandRepeat.expand(list("#2/1h1ms wave"));
		assertEquals(Arrays.asList("#2/1h1ms wave"), sum.segments());
		assertTrue(sum.warning().contains("longer than 1h"));
	}

	@Test
	public void aZeroGapIsRefusedRatherThanCancellingWaits() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#3/0s north"));
		assertEquals(Arrays.asList("#3/0s north"), r.segments());
		assertTrue(r.warning().contains("more than zero"));
		assertEquals(Arrays.asList("#2/0 north"),
				CommandRepeat.expand(list("#2/0 north")).segments());
		assertEquals(Arrays.asList("#2/stop north"),
				CommandRepeat.expand(list("#2/stop north")).segments());
	}

	@Test
	public void aGapThatIsNotADurationIsLeftAsTyped() {
		CommandRepeat.Result r = CommandRepeat.expand(list("#2/abc north"));
		assertEquals(Arrays.asList("#2/abc north"), r.segments());
		assertTrue(r.warning().contains("not understood"));
	}

	@Test
	public void pacedCountStillStopsAtOneHundred() {
		CommandRepeat.Result over = CommandRepeat.expand(list("#500/1s north"));
		assertEquals(Arrays.asList("#500/1s north"), over.segments());
		assertTrue(over.warning().contains("allowed 1-100"));
		CommandRepeat.Result ok = CommandRepeat.expand(list("#100/1s x"));
		assertEquals(100 + 99, ok.segments().size());
		assertEquals("x", ok.segments().get(0));
		assertEquals(".wait 1s", ok.segments().get(1));
		assertEquals("x", ok.segments().get(ok.segments().size() - 1));
		assertNull(ok.warning());
	}

	@Test
	public void doubledHashDoesNotPace() {
		assertEquals(Arrays.asList("#5/1s north"),
				CommandRepeat.expand(list("##5/1s north")).segments());
	}

	@Test
	public void pacedHoldoverIsPassedThrough() {
		assertEquals(Arrays.asList("#2/1s north~"),
				CommandRepeat.expand(list("#2/1s north~")).segments());
	}

	@Test
	public void pacedBodyKeepsItsSpacing() {
		assertEquals(Arrays.asList(
				"get all from   bag", ".wait 1s", "get all from   bag"),
				CommandRepeat.expand(list("#2/1s get all from   bag")).segments());
	}

	@Test
	public void aGapWithNoCommandIsNotARepeat() {
		assertEquals(Arrays.asList("#5/1s"),
				CommandRepeat.expand(list("#5/1s")).segments());
	}
}
