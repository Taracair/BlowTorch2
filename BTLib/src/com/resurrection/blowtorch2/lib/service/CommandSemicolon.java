package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Split an outbound line on {@code ;}. {@code ;;} is a literal semicolon in
 * that command. A whole segment {@code #} followed by a single {@code ;}
 * becomes {@code ;} so {@code look;#;say hi} is look, a semicolon, say hi.
 * {@code #;;} stays one {@code #;} segment. Android-free so the split is
 * JVM-tested.
 */
public final class CommandSemicolon {

	private static final Pattern SEMICOLON = Pattern.compile(";");

	private CommandSemicolon() {
	}

	public static List<String> split(final String string) {
		List<String> list = new ArrayList<String>();
		if (string == null) {
			return list;
		}
		Matcher semiMatcher = SEMICOLON.matcher(string);
		StringBuffer commandBuilder = new StringBuffer();
		boolean matched = false;
		boolean append = false;
		boolean firstSemi = true;
		while (semiMatcher.find()) {
			matched = true;
			commandBuilder.setLength(0);

			semiMatcher.appendReplacement(commandBuilder, "");
			if (commandBuilder.length() == 0) {
				append = true;
				if (list.size() == 0) {
					if (!firstSemi) {
						list.add(";");
					} else {
						firstSemi = false; //don't add the first one, but add subsequent ones.
					}
				} else {
					list.add(list.remove(list.size() - 1) + ";");
				}
			} else {
				if (append) {
					if (list.size() == 0) {
						list.add(";");
					} else {
						list.add(list.remove(list.size() - 1) + commandBuilder.toString());
					}
					append = false;
				} else {
					String piece = commandBuilder.toString();
					if ("#".equals(piece) && !semicolonFollows(semiMatcher, string)) {
						list.add(";");
					} else {
						list.add(piece);
					}
				}

			}
		}

		if (!matched) {
			list.add(string);
		} else {
			commandBuilder.setLength(0);
			semiMatcher.appendTail(commandBuilder);
			if (append) {
				if(list.size() != 0) {
					list.add(list.remove(list.size() - 1) + commandBuilder.toString());
				}
			} else {
				list.add(commandBuilder.toString());
			}
		}

		return list;
	}

	private static boolean semicolonFollows(final Matcher matcher, final String string) {
		int at = matcher.end();
		return at < string.length() && string.charAt(at) == ';';
	}
}
