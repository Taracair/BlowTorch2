package com.resurrection.blowtorch2.lib.alias;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.responder.CaptureSubstitution;

/**
 * The second half of alias replacement: keep re-matching the text an alias
 * produced until no alias matches it any more.
 *
 * <p>Lifted out of {@code Plugin.doAliasReplacementImpl} unchanged, quirks and
 * all, so that it can be tested without a device. The three branches, the
 * semicolon split, and the sticky {@code eatTail} flag below all behave exactly
 * as they did inline; see the tests for the ones that look like mistakes.
 *
 * <p>The one deliberate difference is {@link #DEFAULT_MAX_PASSES}. The original
 * loop had no bound at all: it repeated until a pass matched nothing. An alias
 * whose replacement still contains its own trigger as a whole word — {@code kk}
 * expanding to {@code kk goblin} — grew the buffer forever on the connection
 * thread, and so did any two aliases that expand into each other. Unanchored
 * patterns are wrapped in {@code \b} by {@link AliasPattern}, so a substring
 * like {@code n} inside {@code north} is not enough to trigger it.
 */
public final class AliasRecursion {

	/**
	 * How many times the text may be re-expanded before we call it a loop.
	 * Aliases that legitimately chain settle in two or three passes; twenty is
	 * far past any real alias set and still returns instantly.
	 */
	public static final int DEFAULT_MAX_PASSES = 20;

	/** Splits on a single whitespace character, as the inline code did. */
	private static final Pattern WHITESPACE = Pattern.compile("\\s");

	/**
	 * Called each time an alias matches during recursion. Kept free of Android
	 * types so the tests here stay JVM-only; Plugin posts Set Variable from the
	 * sink.
	 */
	public interface MatchSink {
		void onMatch(AliasData alias, Map<String, String> captures);
	}

	/** The expanded text, plus whether we stopped because of the pass limit. */
	public static final class Result {
		private final String text;
		private final boolean hitLimit;

		Result(final String text, final boolean hitLimit) {
			this.text = text;
			this.hitLimit = hitLimit;
		}

		public String text() {
			return text;
		}

		/** True when expansion was cut short — the alias set almost certainly loops. */
		public boolean hitLimit() {
			return hitLimit;
		}
	}

	private AliasRecursion() {
	}

	public static Result expand(final Pattern aliasRegex, final AliasPattern aliases,
			final String input, final Map<String, String> sessionVariables) {
		return expand(aliasRegex, aliases, input, sessionVariables, DEFAULT_MAX_PASSES, null);
	}

	public static Result expand(final Pattern aliasRegex, final AliasPattern aliases,
			final String input, final Map<String, String> sessionVariables,
			final MatchSink sink) {
		return expand(aliasRegex, aliases, input, sessionVariables, DEFAULT_MAX_PASSES, sink);
	}

	public static Result expand(final Pattern aliasRegex, final AliasPattern aliases,
			final String input, final Map<String, String> sessionVariables,
			final int maxPasses) {
		return expand(aliasRegex, aliases, input, sessionVariables, maxPasses, null);
	}

	public static Result expand(final Pattern aliasRegex, final AliasPattern aliases,
			final String input, final Map<String, String> sessionVariables,
			final int maxPasses, final MatchSink sink) {
		if (aliasRegex == null || aliases == null || input == null) {
			return new Result(input == null ? "" : input, false);
		}
		StringBuffer replaced = new StringBuffer(input);
		StringBuffer buffertemp = new StringBuffer();
		Matcher m = aliasRegex.matcher("");
		int passes = 0;
		boolean recursivefound;
		do {
			if (passes >= maxPasses) {
				return new Result(replaced.toString(), true);
			}
			passes++;
			// One pass, one decision. The word-splitting branch below rewrites
			// the whole buffer, tail included, so appending the tail after it
			// would duplicate text — that is what this flag is for. It used to
			// be declared outside this loop, which made it stick: after any
			// ^alias fired, every later pass silently dropped everything past
			// its own match.
			boolean eatTail = false;
			recursivefound = false;
			m.reset(replaced.toString());
			buffertemp.setLength(0);
			while (m.find()) {
				recursivefound = true;
				int idx = AliasPattern.matchedGroup(m);
				AliasData replaceWith = aliases.aliasForGroup(idx);
				if (replaceWith == null) {
					m.appendReplacement(buffertemp, Matcher.quoteReplacement(m.group(0)));
					continue;
				}
				String pre = replaceWith.getPre();
				if (pre.startsWith("^") && !pre.endsWith("$")) {
					WordSplit split = splitAndSubstituteWithCaptures(replaceWith.getPost(),
							replaced.toString());
					notifyMatch(sink, replaceWith, split.captures);
					m.appendReplacement(buffertemp, Matcher.quoteReplacement(split.text));
					eatTail = true;
				} else if (pre.startsWith("^") && pre.endsWith("$")) {
					String matched = m.group(idx);
					notifyMatch(sink, replaceWith,
							AliasExpansion.captures(replaceWith, matched, matched));
					m.appendReplacement(buffertemp, Matcher.quoteReplacement(
							AliasExpansion.expand(replaceWith, matched, matched, sessionVariables)));
					eatTail = true;
				} else {
					String matched = m.group(idx);
					notifyMatch(sink, replaceWith,
							AliasExpansion.captures(replaceWith, replaced.toString(), matched));
					m.appendReplacement(buffertemp, Matcher.quoteReplacement(
							AliasExpansion.expand(replaceWith, replaced.toString(),
									matched, sessionVariables)));
				}
			}
			if (recursivefound) {
				if (!eatTail) {
					m.appendTail(buffertemp);
				}
				replaced.setLength(0);
				replaced.append(buffertemp);
			}
		} while (recursivefound);
		return new Result(replaced.toString(), false);
	}

	/**
	 * The word-splitting form: everything before the first semicolon becomes
	 * {@code $0}, {@code $1}, … for the alias body, and the rest of the line is
	 * put back after it.
	 *
	 * <p>Note that the split looks at the whole buffer, not at the matched text
	 * — an alias firing after a semicolon still splits on the first one in the
	 * line.
	 */
	static String splitAndSubstitute(final String post, final String buffer) {
		return splitAndSubstituteWithCaptures(post, buffer).text;
	}

	static final class WordSplit {
		final String text;
		final Map<String, String> captures;

		WordSplit(final String text, final Map<String, String> captures) {
			this.text = text;
			this.captures = captures;
		}
	}

	static WordSplit splitAndSubstituteWithCaptures(final String post, final String buffer) {
		String head = buffer;
		String rest = "";
		int semicolon = head.indexOf(";");
		if (semicolon > -1) {
			rest = head.substring(semicolon + 1, head.length());
			head = head.substring(0, semicolon);
		}
		String sepchar = rest.length() > 0 ? ";" : "";
		String[] parts = WHITESPACE.split(head);
		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < parts.length; i++) {
			map.put(Integer.toString(i), parts[i]);
		}
		return new WordSplit(CaptureSubstitution.apply(post, map) + sepchar + rest, map);
	}

	private static void notifyMatch(final MatchSink sink, final AliasData alias,
			final Map<String, String> captures) {
		if (sink == null || alias == null) {
			return;
		}
		sink.onMatch(alias, captures);
	}
}
