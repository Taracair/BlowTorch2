package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * What a tappable word puts into a command. {@code $word} is the part that was
 * tappable — which is not always the whole match, since a trigger usually needs
 * context to recognise the line and only one piece of it should light up.
 */
public class TapCommandSubstitutionTest {

	private static Matcher matched(String pattern, String line) {
		Matcher m = Pattern.compile(pattern).matcher(line);
		if (!m.find()) {
			throw new IllegalStateException("pattern did not match the test line");
		}
		return m;
	}

	@Test
	public void wordIsTheTappableText() {
		Matcher m = matched("You see (.+) lying here", "You see a rusty sword lying here");
		assertEquals("get a rusty sword",
				Window.fillTapCommand("get $word", "a rusty sword", m));
	}

	@Test
	public void zeroIsTheWholeMatchAndGroupsAreTheBrackets() {
		Matcher m = matched("(\\w+) drops (\\w+)", "Goblin drops sword");
		assertEquals("say Goblin drops sword", Window.fillTapCommand("say $0", "Goblin", m));
		assertEquals("kill Goblin", Window.fillTapCommand("kill $1", "Goblin", m));
		assertEquals("get sword", Window.fillTapCommand("get $2", "Goblin", m));
	}

	@Test
	public void severalTokensInOneCommand() {
		Matcher m = matched("(\\w+) drops (\\w+)", "Goblin drops sword");
		assertEquals("get sword;kill Goblin;look Goblin",
				Window.fillTapCommand("get $2;kill $1;look $word", "Goblin", m));
	}

	/**
	 * A group the pattern does not have becomes empty. Sending the game a
	 * literal "$7" because a bracket was removed is worse than sending nothing.
	 */
	@Test
	public void aMissingGroupIsEmptyNotADollarSign() {
		Matcher m = matched("(\\w+) drops", "Goblin drops");
		assertEquals("get ", Window.fillTapCommand("get $7", "Goblin", m));
	}

	@Test
	public void aDollarThatIsNotATokenIsLeftAlone() {
		Matcher m = matched("gold", "you find gold");
		assertEquals("buy $tuff", Window.fillTapCommand("buy $tuff", "gold", m));
		assertEquals("cost$", Window.fillTapCommand("cost$", "gold", m));
	}

	@Test
	public void aCommandWithoutTokensIsUntouched() {
		Matcher m = matched("gold", "you find gold");
		assertEquals("look", Window.fillTapCommand("look", "gold", m));
	}
}
