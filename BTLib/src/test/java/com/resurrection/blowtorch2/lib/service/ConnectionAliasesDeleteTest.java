package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Deleting an alias removes it from the map the list shows. The matcher is a
 * separate object built earlier; without {@code buildAliases()} the removed
 * alias keeps expanding commands.
 */
public class ConnectionAliasesDeleteTest {

	@Test
	public void deleteRebuildsTheMatcher() throws Exception {
		String src = read("BTLib/src/com/resurrection/blowtorch2/lib/service/ConnectionAliases.java");
		int del = src.indexOf("void deleteAlias");
		int delPlugin = src.indexOf("void deletePluginAlias");
		int next = src.indexOf("HashMap<String, AliasData> getAliases");
		assertTrue(del >= 0 && delPlugin > del && next > delPlugin);
		String main = src.substring(del, delPlugin);
		String plugin = src.substring(delPlugin, next);
		assertTrue(main.contains("buildAliases()"));
		assertTrue(plugin.contains("p.buildAliases()"));
	}

	private static String read(String relative) throws Exception {
		java.io.File[] candidates = new java.io.File[] {
				new java.io.File(relative),
				new java.io.File("../" + relative),
		};
		for (java.io.File f : candidates) {
			if (f.isFile()) {
				return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing " + relative);
	}
}
