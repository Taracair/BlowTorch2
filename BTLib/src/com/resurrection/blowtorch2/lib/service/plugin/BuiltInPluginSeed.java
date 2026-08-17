package com.resurrection.blowtorch2.lib.service.plugin;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pull a single built-in {@code <plugin>} out of {@code default_settings} so
 * existing worlds can grow it. Profiles saved before the plugin shipped never
 * see edits to the defaults file.
 */
public final class BuiltInPluginSeed {

	public static final String STARTER_TUTORIAL = "starter_tutorial";

	private BuiltInPluginSeed() {
	}

	/**
	 * The {@code <plugin name="…">…</plugin>} element, or null when the
	 * document has no such plugin.
	 */
	public static String extractPluginXml(final String document, final String pluginName) {
		if (document == null || pluginName == null || pluginName.length() == 0) {
			return null;
		}
		Pattern startPat = Pattern.compile(
				"<plugin\\b[^>]*\\bname\\s*=\\s*([\"'])"
						+ Pattern.quote(pluginName)
						+ "\\1[^>]*>",
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher start = startPat.matcher(document);
		if (!start.find()) {
			return null;
		}
		int from = start.start();
		int pos = start.end();
		int depth = 1;
		while (depth > 0 && pos < document.length()) {
			int cdata = document.indexOf("<![CDATA[", pos);
			int open = document.indexOf("<plugin", pos);
			int close = document.indexOf("</plugin", pos);
			int next = earliest(cdata, open, close);
			if (next < 0) {
				return null;
			}
			if (next == cdata) {
				int endCdata = document.indexOf("]]>", cdata + 9);
				if (endCdata < 0) {
					return null;
				}
				pos = endCdata + 3;
				continue;
			}
			if (next == open) {
				depth++;
				pos = open + 7;
				continue;
			}
			depth--;
			int end = document.indexOf('>', close);
			if (end < 0) {
				return null;
			}
			pos = end + 1;
			if (depth == 0) {
				return document.substring(from, pos);
			}
		}
		return null;
	}

	/**
	 * A full settings document containing only that plugin, so
	 * {@code PluginParser} can load it the same way as an internal plugin.
	 */
	public static byte[] wrapAsBlowtorchDocument(final String pluginXml) {
		if (pluginXml == null || pluginXml.length() == 0) {
			return null;
		}
		String doc = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n"
				+ "<blowtorch xmlversion=\"2\">\n"
				+ "<plugins>\n"
				+ pluginXml
				+ "\n</plugins>\n"
				+ "</blowtorch>\n";
		return doc.getBytes(StandardCharsets.UTF_8);
	}

	private static int earliest(final int a, final int b, final int c) {
		int best = -1;
		if (a >= 0) {
			best = a;
		}
		if (b >= 0 && (best < 0 || b < best)) {
			best = b;
		}
		if (c >= 0 && (best < 0 || c < best)) {
			best = c;
		}
		return best;
	}
}
