package com.offsetnull.bt.alias;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds capture maps for anchored (^...$) alias patterns by re-running the
 * alias regex against the matched input text.
 */
public final class AnchoredAliasCaptures {

	private AnchoredAliasCaptures() {
	}

	public static HashMap<String, String> fromMatch(String aliasPre, String matchedText) {
		HashMap<String, String> captureMap = new HashMap<String, String>();
		if (matchedText == null) {
			return captureMap;
		}

		Matcher harvest = Pattern.compile(aliasPre).matcher(matchedText);
		if (harvest.find()) {
			for (int group = 0; group <= harvest.groupCount(); group++) {
				String value = harvest.group(group);
				captureMap.put(Integer.toString(group), value != null ? value : "");
			}
		} else {
			captureMap.put("0", matchedText);
		}
		return captureMap;
	}
}
