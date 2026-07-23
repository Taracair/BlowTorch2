package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
	public void rejectsNonUrls() {
		assertNull(TextTree.extractUrl("just some text"));
		assertNull(TextTree.extractUrl("v1.2"));
		assertNull(TextTree.extractUrl("file.txt"));
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
		assertNotNull(TextTree.extractUrl("http://a.com"));
	}
}
