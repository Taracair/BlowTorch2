package com.resurrection.blowtorch2.lib.responder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Capture substitution: the {@code $1} in "kill $1" and everything like it.
 *
 * <p>Every responder and both anchored alias forms go through this, and none of
 * it was covered. The cases here are the ones that produce garbage rather than
 * an error when they go wrong -- a MUD line containing a dollar sign, a capture
 * that did not match, a reference the player typed by hand.
 */
public class CaptureSubstitutionTest {

	private static Map<String, String> captures(String... pairs) {
		Map<String, String> m = new HashMap<String, String>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void substitutesASingleCapture() {
		assertEquals("kill goblin",
				CaptureSubstitution.apply("kill $1", captures("1", "goblin")));
	}

	@Test
	public void substitutesSeveralIncludingZero() {
		assertEquals("cast fireball at goblin",
				CaptureSubstitution.apply("cast $0 at $1",
						captures("0", "fireball", "1", "goblin")));
	}

	@Test
	public void handlesDoubleDigitReferences() {
		Map<String, String> m = captures("10", "ten", "1", "one");
		assertEquals("ten", CaptureSubstitution.apply("$10", m));
	}

	/**
	 * The crash this function guards against: captured game text with a dollar
	 * sign in it would otherwise be read back as a group reference.
	 */
	@Test
	public void capturedTextContainingADollarIsTakenLiterally() {
		assertEquals("say it costs $5",
				CaptureSubstitution.apply("say $1", captures("1", "it costs $5")));
	}

	@Test
	public void aReferenceWithNoCaptureIsLeftAlone() {
		assertEquals("kill $2",
				CaptureSubstitution.apply("kill $2", captures("1", "goblin")));
	}

	@Test
	public void aNullCaptureBecomesEmpty() {
		Map<String, String> m = new HashMap<String, String>();
		m.put("1", null);
		assertEquals("kill ", CaptureSubstitution.apply("kill $1", m));
	}

	/** Nothing to do means the very same string back, not a rebuilt copy. */
	@Test
	public void textWithoutReferencesIsUntouched() {
		String input = "look";
		assertSame(input, CaptureSubstitution.apply(input, captures("1", "goblin")));
		assertSame(input, CaptureSubstitution.apply(input, null));
		assertSame(input, CaptureSubstitution.apply(input, new HashMap<String, String>()));
	}

	@Test
	public void nullInputBecomesEmpty() {
		assertEquals("", CaptureSubstitution.apply(null, captures("1", "x")));
	}

	@Test
	public void emptyInputStaysEmpty() {
		assertEquals("", CaptureSubstitution.apply("", captures("1", "x")));
	}

	/** Substitution happens once; a capture that looks like a reference is data. */
	@Test
	public void substitutionIsNotRecursive() {
		assertEquals("$1", CaptureSubstitution.apply("$1", captures("1", "$1")));
	}

	@Test
	public void repeatedReferencesAllGetReplaced() {
		assertEquals("goblin and goblin",
				CaptureSubstitution.apply("$1 and $1", captures("1", "goblin")));
	}

	@Test
	public void surroundingTextIsPreserved() {
		assertEquals("[goblin] down",
				CaptureSubstitution.apply("[$1] down", captures("1", "goblin")));
	}
}
