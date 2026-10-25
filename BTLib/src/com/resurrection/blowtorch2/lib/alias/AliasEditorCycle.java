package com.resurrection.blowtorch2.lib.alias;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The alias editor's "does this loop?" check. The matched text is not the map
 * key: an alias stored as {@code (.+)} has pattern {@code ^(.+)$}, so
 * {@code get(group(0))} is null. Calling {@code getPost()} on that used to
 * kill the window on Done.
 */
public final class AliasEditorCycle {

	private AliasEditorCycle() {
	}

	/**
	 * Names seen while expanding {@code testVal}, or null when it settles or
	 * the joined pattern will not compile.
	 *
	 * @param aliases Alias map, including the alias about to be saved. Null
	 *     values are skipped.
	 * @param testVal The alias name being saved, anchors already stripped.
	 */
	public static String[] circularNames(Map<String, AliasData> aliases, String testVal) {
		if (aliases == null || aliases.isEmpty() || testVal == null) {
			return null;
		}
		StringBuilder regExp = new StringBuilder();
		for (AliasData d : aliases.values()) {
			if (d == null || d.getPre() == null) {
				continue;
			}
			String prefix = d.getPre().startsWith("^") ? "" : "\\b";
			String suffix = d.getPre().endsWith("$") ? "" : "\\b";
			if (regExp.length() > 0) {
				regExp.append('|');
			}
			regExp.append('(').append(prefix).append(d.getPre()).append(suffix).append(')');
		}
		if (regExp.length() == 0) {
			return null;
		}
		Pattern bigmatch;
		try {
			bigmatch = Pattern.compile(regExp.toString());
		} catch (PatternSyntaxException bad) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"AliasEditorCycle: a saved alias will not compile,"
					+ " skipping the circular-reference check", bad);
			return null;
		}
		Matcher reMatch = bigmatch.matcher(testVal);
		StringBuffer replaceHolder = new StringBuffer();
		ArrayList<String> offenders = new ArrayList<String>();
		int tries = 0;
		int maxTries = aliases.size() + 5;
		boolean done = false;
		while (tries <= maxTries && !done) {
			boolean matched = false;
			while (reMatch.find()) {
				String hit = reMatch.group(0);
				AliasData d = hit == null ? null : aliases.get(hit);
				if (d == null) {
					reMatch.appendReplacement(replaceHolder,
							Matcher.quoteReplacement(hit == null ? "" : hit));
					continue;
				}
				matched = true;
				if (tries > 0 && !offenders.contains(hit)) {
					offenders.add(hit);
				}
				String post = d.getPost() != null ? d.getPost() : "";
				reMatch.appendReplacement(replaceHolder, Matcher.quoteReplacement(post));
			}
			if (matched) {
				reMatch.appendTail(replaceHolder);
				reMatch.reset(replaceHolder.toString());
				replaceHolder.setLength(0);
				tries++;
			} else {
				done = true;
			}
		}
		if (tries >= maxTries) {
			return offenders.toArray(new String[offenders.size()]);
		}
		return null;
	}
}
