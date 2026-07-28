package com.resurrection.blowtorch2.lib.responder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Session variables inside alias and responder text: the {@code ${name}} form. */
public class VariableSubstitutionTest {

	private static Map<String, String> vars(String... pairs) {
		Map<String, String> m = new HashMap<String, String>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void substitutesANamedVariable() {
		assertEquals("kill goblin",
				VariableSubstitution.apply("kill ${target}", vars("target", "goblin")));
	}

	@Test
	public void substitutesSeveral() {
		assertEquals("cast fire at goblin",
				VariableSubstitution.apply("cast ${spell} at ${target}",
						vars("spell", "fire", "target", "goblin")));
	}

	/** Visibly wrong beats silently wrong: "kill" with no target is worse. */
	@Test
	public void anUnsetVariableIsLeftAsWritten() {
		assertEquals("kill ${target}",
				VariableSubstitution.apply("kill ${target}", vars("other", "x")));
	}

	@Test
	public void anEmptyVariableSubstitutesEmpty() {
		assertEquals("kill ", VariableSubstitution.apply("kill ${target}", vars("target", "")));
	}

	/** Braces are required, so numeric captures are never touched. */
	@Test
	public void numericCapturesAreNotVariables() {
		String input = "kill $1";
		assertSame(input, VariableSubstitution.apply(input, vars("1", "goblin")));
	}

	/** A bare dollar in game text is not a variable reference. */
	@Test
	public void aBareDollarIsLeftAlone() {
		String input = "it costs $5 and ${x}";
		assertEquals("it costs $5 and 7", VariableSubstitution.apply(input, vars("x", "7")));
	}

	@Test
	public void aVariableHoldingADollarIsTakenLiterally() {
		assertEquals("say $5", VariableSubstitution.apply("say ${p}", vars("p", "$5")));
	}

	@Test
	public void nothingToDoReturnsTheSameString() {
		String input = "look";
		assertSame(input, VariableSubstitution.apply(input, vars("a", "b")));
		assertSame(input, VariableSubstitution.apply(input, null));
		assertSame(input, VariableSubstitution.apply(input, new HashMap<String, String>()));
	}

	@Test
	public void nullInputBecomesEmpty() {
		assertEquals("", VariableSubstitution.apply(null, vars("a", "b")));
	}

	@Test
	public void repeatedReferencesAllGetReplaced() {
		assertEquals("goblin vs goblin",
				VariableSubstitution.apply("${t} vs ${t}", vars("t", "goblin")));
	}
}
