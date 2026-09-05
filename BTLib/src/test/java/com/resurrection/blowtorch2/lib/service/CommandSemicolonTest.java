package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Pins {@link CommandSemicolon#split} to the Connection matcher loop. */
public class CommandSemicolonTest {

	private static void eq(String input, String... expected) {
		assertEquals(Arrays.asList(expected), CommandSemicolon.split(input));
	}

	@Test
	public void noSemicolonIsOneSegment() {
		eq("look", "look");
	}

	@Test
	public void plainSplit() {
		eq("a;b", "a", "b");
		eq("a;b;c", "a", "b", "c");
		eq("stand;#3 kick troll;sit", "stand", "#3 kick troll", "sit");
	}

	@Test
	public void doubledSemicolonIsLiteralInThatCommand() {
		eq("a;;b", "a;b");
		eq("say hello;;world", "say hello;world");
		eq("a;;b;c", "a;b", "c");
		eq("a;;", "a;");
	}

	@Test
	public void wholeLineDoubledSemicolonIsOneSemicolon() {
		eq(";;", ";");
	}

	@Test
	public void leadingDoubledAttachesToNext() {
		eq(";;a", ";a");
		eq(";;look", ";look");
	}

	@Test
	public void trailingSplitKeepsEmpty() {
		eq("a;", "a", "");
	}

	@Test
	public void leadingSingleSemicolonIsSwallowed() {
		assertEquals(0, CommandSemicolon.split(";a").size());
	}

	@Test
	public void hashSemicolonIsSplitToday() {
		eq("look;#;say hi", "look", "#", "say hi");
		eq("#;", "#", "");
		eq("#;;", "#;");
		eq("foo#;bar", "foo#", "bar");
	}

	@Test
	public void spaceStaysOnTheSegment() {
		eq("look; #;say hi", "look", " #", "say hi");
	}
}
