package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;

import org.junit.Test;

public class AliasEditorCycleTest {

	private static AliasData alias(String pre, String post) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		return a;
	}

	private static LinkedHashMap<String, AliasData> map(String[] keys, AliasData... aliases) {
		LinkedHashMap<String, AliasData> out = new LinkedHashMap<String, AliasData>();
		for (int i = 0; i < aliases.length; i++) {
			out.put(keys[i], aliases[i]);
		}
		return out;
	}

	/** {@code kk} → {@code kill goblin} settles. The editor must allow the save. */
	@Test
	public void aPlainAliasIsNotACycle() {
		assertNull(AliasEditorCycle.circularNames(
				map(new String[] { "kk" }, alias("kk", "kill goblin")), "kk"));
	}

	/** {@code a} → {@code b} → {@code a} is the loop the dialog names. */
	@Test
	public void aTwoAliasLoopIsReported() {
		String[] found = AliasEditorCycle.circularNames(
				map(new String[] { "a", "b" },
						alias("a", "b"),
						alias("b", "a")),
				"a");
		assertNotNull(found);
		assertTrue(found.length > 0);
	}

	/**
	 * A catch-all is stored under {@code (.+)} and matches the replacement
	 * text, which is not a key. {@code get(group(0))} is null; this used to
	 * throw on Done and drop the player back at server select.
	 */
	@Test
	public void catchAllMatchingReplacementTextDoesNotThrow() {
		LinkedHashMap<String, AliasData> aliases = map(
				new String[] { "(.+)", "kk" },
				alias("^(.+)$", "say $1"),
				alias("kk", "kill"));
		assertNull(AliasEditorCycle.circularNames(aliases, "kk"));
	}

	@Test
	public void nullAliasValueIsSkipped() {
		LinkedHashMap<String, AliasData> aliases = map(
				new String[] { "kk" }, alias("kk", "kill"));
		aliases.put("gone", null);
		assertNull(AliasEditorCycle.circularNames(aliases, "kk"));
	}
}
