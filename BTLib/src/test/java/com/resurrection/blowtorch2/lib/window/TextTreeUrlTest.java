package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.junit.Test;

public class TextTreeUrlTest {

	@Test
	public void extractsHttpAndHttps() {
		assertEquals("http://example.com/path",
				TextTree.extractUrl("see http://example.com/path today"));
		assertEquals("https://mud.org/join",
				TextTree.extractUrl("https://mud.org/join"));
	}

	@Test
	public void extractsWwwAndBareDomain() {
		assertEquals("www.example.com",
				TextTree.extractUrl("visit www.example.com please"));
		assertEquals("example.com",
				TextTree.extractUrl("go to example.com"));
		assertEquals("example.com/foo",
				TextTree.extractUrl("example.com/foo."));
	}

	@Test
	public void normalizeAddsScheme() {
		assertEquals("http://www.example.com",
				TextTree.normalizeUrl("www.example.com"));
		assertEquals("http://example.com",
				TextTree.normalizeUrl("example.com"));
		assertEquals("https://secure.example.com",
				TextTree.normalizeUrl("https://secure.example.com"));
	}

	@Test
	public void normalizeUrlLeavesMailtoAlone() {
		assertEquals("mailto:nobody@example.com",
				TextTree.normalizeUrl("mailto:nobody@example.com"));
	}

	@Test
	public void rejectsNonUrls() {
		assertNull(TextTree.extractUrl("just some text"));
		assertNull(TextTree.extractUrl("v1.2"));
		assertNull(TextTree.extractUrl("file.txt"));
	}

	@Test
	public void trailingBoundaryBlocksPrefixTldInsideLongerWord() {
		// Measured false positive: 2.chudstopper matched as 2.ch (Switzerland).
		assertNull(TextTree.extractUrl("2.chudstopper"));
		assertNull(TextTree.extractUrl(" 2.chudstopper "));
	}

	@Test
	public void shortWordLikeTldsNotBuiltIn() {
		assertNull(TextTree.extractUrl("want.to"));
		assertNull(TextTree.extractUrl("1.ai"));
		assertNull(TextTree.extractUrl("bob.to"));
		assertNull(TextTree.extractUrl("swiss.ch"));
	}

	@Test
	public void mudStyleBareDomainsStillMatch() {
		assertEquals("achaea.com", TextTree.extractUrl("achaea.com"));
		assertEquals("achaea.com:23", TextTree.extractUrl("achaea.com:23"));
		assertEquals("mud.org/path", TextTree.extractUrl("mud.org/path"));
	}

	@Test
	public void bareOffLeavesExplicitSchemes() {
		Pattern bareOff = UrlLinkPatterns.build(false, "");
		assertNull(TextTree.extractUrl("achaea.com", bareOff));
		assertEquals("https://achaea.com",
				TextTree.extractUrl("https://achaea.com", bareOff));
		assertEquals("www.example.com",
				TextTree.extractUrl("www.example.com", bareOff));
	}

	@Test
	public void extrasEnableShortTld() {
		Pattern withTo = UrlLinkPatterns.build(true, "to,ai");
		assertEquals("want.to", TextTree.extractUrl("want.to", withTo));
		assertEquals("foo.ai", TextTree.extractUrl("foo.ai", withTo));
		// Prefix inside a longer word still blocked.
		assertNull(TextTree.extractUrl("2.chudstopper",
				UrlLinkPatterns.build(true, "ch")));
	}

	@Test
	public void parseExtraTldsValidatesAndCaps() {
		assertEquals(0, UrlLinkPatterns.parseExtraTlds("").length);
		assertEquals(0, UrlLinkPatterns.parseExtraTlds(null).length);
		String[] got = UrlLinkPatterns.parseExtraTlds(" AI , .to;ch  bad!  ");
		assertEquals(new HashSet<String>(Arrays.asList("ai", "to", "ch")),
				new HashSet<String>(Arrays.asList(got)));
		assertNull(UrlLinkPatterns.normalizeTldToken("a"));
		assertNull(UrlLinkPatterns.normalizeTldToken("has.dot"));
		assertNull(UrlLinkPatterns.normalizeTldToken("bad!"));
	}

	@Test
	public void textUnitMarksLinks() {
		TextTree tree = new TextTree();
		tree.setLinkify(true);
		TextTree.Text http = tree.new Text("http://a.com");
		assertTrue(http.isLink());
		TextTree.Text bare = tree.new Text("example.com");
		assertTrue(bare.isLink());
		TextTree.Text plain = tree.new Text("hello");
		assertTrue(!plain.isLink());
		TextTree.Text falsePos = tree.new Text("2.chudstopper");
		assertFalse(falsePos.isLink());
		assertNotNull(TextTree.extractUrl("http://a.com"));
	}

	@Test
	public void treeHonoursUrlLinkSettings() {
		TextTree tree = new TextTree();
		tree.setLinkify(true);
		tree.setUrlLinkSettings(false, "");
		assertFalse(tree.new Text("example.com").isLink());
		assertTrue(tree.new Text("https://example.com").isLink());
		tree.setUrlLinkSettings(true, "to");
		assertTrue(tree.new Text("want.to").isLink());
	}

	@Test
	public void setBufferKeepsUrlLinkSettings() {
		// Mirrors MainWindow.initWindow: settings applied, then service buffer adopted.
		TextTree constructorTree = new TextTree();
		constructorTree.setLinkify(true);
		constructorTree.setUrlLinkSettings(true, "to");
		TextTree serviceTree = new TextTree();
		serviceTree.setLinkify(false);
		// Adopt like Window.setBuffer: copy linkify, then re-apply settings.
		boolean linkify = constructorTree.isLinkify();
		serviceTree.setLinkify(linkify);
		serviceTree.setUrlLinkSettings(true, "to");
		assertTrue(serviceTree.new Text("want.to").isLink());
		assertFalse(serviceTree.new Text("2.chudstopper").isLink());
	}
}
