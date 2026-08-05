package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * An alias named inside a trigger pattern: the {@code $alias{name}} form.
 *
 * <p>The case that started it: an alias {@code _tappable1} typing
 * {@code circuit}, and a trigger meant to mark the word the alias names.
 */
public class TriggerAliasReferenceTest {

	private static Map<String, String> bodies(String... pairs) {
		Map<String, String> m = new HashMap<String, String>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void pastesTheAliasBodyIn() {
		assertEquals("circuit",
				TriggerAliasReference.resolve("$alias{_tappable1}",
						bodies("_tappable1", "circuit")));
	}

	@Test
	public void pastesInTheMiddleOfAPattern() {
		assertEquals("(\\w+) picks up the circuit",
				TriggerAliasReference.resolve("(\\w+) picks up the $alias{_tappable1}",
						bodies("_tappable1", "circuit")));
	}

	@Test
	public void pastesEveryReference() {
		assertEquals("circuit and wire",
				TriggerAliasReference.resolve("$alias{a} and $alias{b}",
						bodies("a", "circuit", "b", "wire")));
	}

	/**
	 * Visibly wrong beats silently wrong, the same choice VariableSubstitution
	 * makes: a trigger watching for "$alias{nope}" and never firing is easier to
	 * understand than one quietly watching for something else.
	 */
	@Test
	public void anUnknownAliasIsLeftAsWritten() {
		String input = "$alias{nope}";
		assertSame(input, TriggerAliasReference.resolve(input, bodies("other", "x")));
	}

	/** Several commands are not one piece of text the game can print. */
	@Test
	public void anAliasOfSeveralCommandsIsRefused() {
		String input = "$alias{_climb}";
		assertSame(input, TriggerAliasReference.resolve(input,
				bodies("_climb", "N;s;Get cig")));
	}

	/** $1 comes from what the player typed, and a trigger has nothing to fill it from. */
	@Test
	public void anAliasWithTypedCapturesIsRefused() {
		String input = "$alias{gfbb}";
		assertSame(input, TriggerAliasReference.resolve(input,
				bodies("gfbb", "get $1 $2 from 1cont")));
	}

	/** One level only, so two aliases naming each other cannot loop. */
	@Test
	public void anAliasNamingAnotherAliasIsRefused() {
		String input = "$alias{a}";
		assertSame(input, TriggerAliasReference.resolve(input,
				bodies("a", "$alias{b}", "b", "circuit")));
	}

	/** A pattern naming nothing comes back untouched, object and all. */
	@Test
	public void aPatternWithNoReferenceIsUntouched() {
		String input = "You see a circuit here";
		assertSame(input, TriggerAliasReference.resolve(input, bodies("a", "b")));
	}

	/** $1 in the pattern itself is a capture, not a reference: braces are required. */
	@Test
	public void aBareDollarIsNotAReference() {
		String input = "costs $5 and $1";
		assertSame(input, TriggerAliasReference.resolve(input, bodies("5", "x", "1", "y")));
		assertFalse(TriggerAliasReference.isReferencedIn(input));
	}

	/**
	 * A body is pasted as text, so a $ or a backslash in it must not be read as
	 * a replacement instruction by the appendReplacement machinery.
	 */
	@Test
	public void aBodyWithDollarsAndBackslashesIsPastedLiterally() {
		assertEquals("a \\d+ credits$ thing",
				TriggerAliasReference.resolve("a $alias{x} thing", bodies("x", "\\d+ credits$")));
	}

	/** The anchors the alias editor's checkboxes add are not part of the name. */
	@Test
	public void anchorsAreStrippedFromTheName() {
		Map<String, com.resurrection.blowtorch2.lib.alias.AliasData> aliases =
				new HashMap<String, com.resurrection.blowtorch2.lib.alias.AliasData>();
		aliases.put("^gfbb", new com.resurrection.blowtorch2.lib.alias.AliasData(
				"^gfbb", "get from bag", true));
		Map<String, String> b = TriggerAliasReference.bodies(aliases);
		assertEquals("get from bag", b.get("gfbb"));
	}

	/**
	 * Disabling an alias stops it expanding what you type. It must not also
	 * silently stop a trigger matching -- the trigger is using it as text.
	 */
	@Test
	public void aDisabledAliasStillProvidesItsText() {
		Map<String, com.resurrection.blowtorch2.lib.alias.AliasData> aliases =
				new HashMap<String, com.resurrection.blowtorch2.lib.alias.AliasData>();
		aliases.put("_tappable1", new com.resurrection.blowtorch2.lib.alias.AliasData(
				"_tappable1", "circuit", false));
		assertEquals("circuit", TriggerAliasReference.resolve("$alias{_tappable1}",
				TriggerAliasReference.bodies(aliases)));
	}

	/** The plain form: the whole pattern is the alias's name. */
	@Test
	public void aWholePatternThatIsAnAliasNameBecomesItsText() {
		assertEquals("circuit",
				TriggerAliasReference.resolve("_tappable1", bodies("_tappable1", "circuit")));
	}

	/** Surrounding space is the player's, not a different pattern. */
	@Test
	public void aWholePatternIsTrimmedBeforeItIsLookedUp() {
		assertEquals("circuit",
				TriggerAliasReference.resolve("  _tappable1 ", bodies("_tappable1", "circuit")));
	}

	/**
	 * Only the whole pattern. A name with anything else around it is a pattern
	 * of its own -- which is also how a player writes a trigger on the literal
	 * text of a name.
	 */
	@Test
	public void anAliasNameWithAnythingAroundItIsNotReplaced() {
		Map<String, String> b = bodies("Ch", "Corpnet history");
		assertSame("^Ch$", TriggerAliasReference.resolve("^Ch$", b));
		assertSame("Ch arrives", TriggerAliasReference.resolve("Ch arrives", b));
	}

	/** A refused body leaves the name as the pattern, not an empty one. */
	@Test
	public void aWholePatternNamingARefusedAliasKeepsTheName() {
		String input = "_climb";
		assertSame(input, TriggerAliasReference.resolve(input, bodies("_climb", "N;s;Get cig")));
	}

	/** The editor has to be able to say "found alias X, watching for Y". */
	@Test
	public void explainNamesTheAliasFoundInAWholePattern() {
		List<String> out = TriggerAliasReference.explain("_tappable1",
				bodies("_tappable1", "circuit"));
		assertEquals(1, out.size());
		assertTrue(out.get(0), out.get(0).contains("_tappable1"));
		assertTrue(out.get(0), out.get(0).contains("circuit"));
	}

	/** And why it could not, for the whole-pattern form too. */
	@Test
	public void explainSaysWhyAWholePatternAliasWasRefused() {
		List<String> out = TriggerAliasReference.explain("gfbb",
				bodies("gfbb", "get $1 $2 from 1cont"));
		assertEquals(1, out.size());
		assertTrue(out.get(0), out.get(0).contains("captures"));
	}

	/** The case that started all of this, end to end through a trigger. */
	@Test
	public void theBarePatternTheMaintainerTypedNowWatchesForTheAliasText() {
		TriggerData t = new TriggerData();
		t.setPattern("_tappable1");
		t.setInterpretAsRegex(false);
		assertTrue(t.resolveAliases(bodies("_tappable1", "circuit")));
		assertTrue(t.getMatcher().reset("You see a circuit here.").find());
		assertEquals("_tappable1", t.getPattern());
		assertEquals("circuit", t.getEffectivePattern());
	}

	@Test
	public void explainNamesWhatWasReadAndWhatItWatchesFor() {
		List<String> out = TriggerAliasReference.explain("$alias{_tappable1}",
				bodies("_tappable1", "circuit"));
		assertEquals(1, out.size());
		assertTrue(out.get(0), out.get(0).contains("_tappable1"));
		assertTrue(out.get(0), out.get(0).contains("circuit"));
	}

	@Test
	public void explainSaysWhyARefusedAliasWasRefused() {
		List<String> out = TriggerAliasReference.explain("$alias{_climb}",
				bodies("_climb", "N;s;Get cig"));
		assertEquals(1, out.size());
		assertTrue(out.get(0), out.get(0).contains("several commands"));
	}

	@Test
	public void explainSaysNothingAboutAPatternWithNoReference() {
		assertTrue(TriggerAliasReference.explain("You see a circuit", bodies("a", "b")).isEmpty());
	}

	/** The whole point: the resolved pattern is what the trigger compiles. */
	@Test
	public void aTriggerCompilesAgainstTheResolvedPattern() {
		TriggerData t = new TriggerData();
		t.setPattern("You see a $alias{_tappable1} here");
		t.setInterpretAsRegex(false);
		assertFalse("unresolved, it waits for text nobody sends",
				t.getMatcher().reset("You see a circuit here").find());

		assertTrue("resolving must change the compiled pattern",
				t.resolveAliases(bodies("_tappable1", "circuit")));
		assertTrue(t.getMatcher().reset("You see a circuit here").find());
		assertEquals("the player's text is what is saved and shown",
				"You see a $alias{_tappable1} here", t.getPattern());
		assertEquals("You see a circuit here", t.getEffectivePattern());
	}

	/** Editing the pattern throws the old resolution away rather than keeping it. */
	@Test
	public void changingThePatternDropsTheResolution() {
		TriggerData t = new TriggerData();
		t.setPattern("$alias{_tappable1}");
		t.resolveAliases(bodies("_tappable1", "circuit"));
		assertEquals("circuit", t.getEffectivePattern());
		t.setPattern("something else");
		assertEquals("something else", t.getEffectivePattern());
	}

	/** Resolving twice with the same aliases must not rebuild anything. */
	@Test
	public void resolvingAgainWithNoChangeReportsNoChange() {
		TriggerData t = new TriggerData();
		t.setPattern("$alias{a}");
		assertTrue(t.resolveAliases(bodies("a", "circuit")));
		assertFalse(t.resolveAliases(bodies("a", "circuit")));
	}

	/** Following the alias: editing its body changes what the trigger watches for. */
	@Test
	public void aChangedAliasBodyChangesTheTrigger() {
		TriggerData t = new TriggerData();
		t.setPattern("$alias{a}");
		t.resolveAliases(bodies("a", "circuit"));
		assertTrue(t.getMatcher().reset("circuit").find());
		assertTrue(t.resolveAliases(bodies("a", "capacitor")));
		assertTrue(t.getMatcher().reset("capacitor").find());
		assertFalse(t.getMatcher().reset("circuit").find());
	}

	/** A regex alias body stays a regex when the trigger is in regex mode. */
	@Test
	public void aRegexBodyIsPastedAsRegexInRegexMode() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(true);
		t.setPattern("^$alias{a} arrives$");
		t.resolveAliases(bodies("a", "(\\w+)"));
		assertTrue(t.getMatcher().reset("Taracair arrives").find());
		assertEquals("Taracair", t.getMatcher().group(1));
	}

	/** In literal mode the body is text, brackets and all. */
	@Test
	public void aBodyIsLiteralTextInLiteralMode() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(false);
		t.setPattern("$alias{a}");
		t.resolveAliases(bodies("a", "(\\w+)"));
		assertTrue(t.getMatcher().reset("a (\\w+) here").find());
		assertFalse(t.getMatcher().reset("a word here").find());
	}
}
