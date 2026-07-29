package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import org.junit.Test;

/**
 * The root-element scanner that replaced two full SAX parses of the settings
 * file. The cases that matter are the shapes a real settings file takes, plus
 * the prolog constructs that could hide the root element from a naive scan.
 */
public class XmlRootProbeTest {

	private static XmlRootProbe.Root probe(String xml) throws Exception {
		return XmlRootProbe.probe(new StringReader(xml));
	}

	/** The current settings format. */
	@Test
	public void findsTheVersionTwoRoot() throws Exception {
		XmlRootProbe.Root r = probe(
				"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
						+ "<blowtorch xmlversion=\"2\">\n  <plugins/>\n</blowtorch>");
		assertEquals("blowtorch", r.name());
		assertEquals(2, r.intAttribute("xmlversion", -1));
	}

	/** The version 1 format is recognised by its root name alone. */
	@Test
	public void findsTheLegacyRoot() throws Exception {
		XmlRootProbe.Root r = probe("<?xml version=\"1.0\"?><root><settings/></root>");
		assertEquals("root", r.name());
		assertEquals(-1, r.intAttribute("xmlversion", -1));
	}

	/** No declaration at all is still a valid document. */
	@Test
	public void worksWithoutAnXmlDeclaration() throws Exception {
		assertEquals("blowtorch", probe("<blowtorch xmlversion=\"2\"/>").name());
	}

	/** Comments before the root must not be mistaken for it. */
	@Test
	public void skipsCommentsBeforeTheRoot() throws Exception {
		XmlRootProbe.Root r = probe(
				"<!-- <notroot> a decoy --><blowtorch xmlversion=\"2\">");
		assertEquals("blowtorch", r.name());
		assertEquals(2, r.intAttribute("xmlversion", -1));
	}

	/** A doctype with an internal subset contains '>' that does not end it. */
	@Test
	public void skipsDoctypeWithInternalSubset() throws Exception {
		XmlRootProbe.Root r = probe(
				"<!DOCTYPE blowtorch [ <!ELEMENT blowtorch (#PCDATA)> ]>"
						+ "<blowtorch xmlversion=\"2\"/>");
		assertEquals("blowtorch", r.name());
	}

	/** A byte order mark survives decoding and must not stop the scan. */
	@Test
	public void skipsAByteOrderMark() throws Exception {
		assertEquals("blowtorch", probe("﻿<blowtorch xmlversion=\"2\"/>").name());
	}

	/** Attribute values may hold '>' without ending the tag. */
	@Test
	public void handlesAngleBracketsInsideAttributeValues() throws Exception {
		XmlRootProbe.Root r = probe("<blowtorch note=\"a > b\" xmlversion=\"2\"/>");
		assertEquals("blowtorch", r.name());
		assertEquals("a > b", r.attribute("note"));
		assertEquals(2, r.intAttribute("xmlversion", -1));
	}

	/** Single quotes are as valid as double ones. */
	@Test
	public void handlesSingleQuotedAttributes() throws Exception {
		assertEquals(2, probe("<blowtorch xmlversion='2'/>").intAttribute("xmlversion", -1));
	}

	/** Attributes spread over several lines still belong to the root. */
	@Test
	public void handlesAttributesAcrossLines() throws Exception {
		XmlRootProbe.Root r = probe("<blowtorch\n\txmlversion=\"2\"\n\tname=\"eden\">");
		assertEquals("blowtorch", r.name());
		assertEquals(2, r.intAttribute("xmlversion", -1));
		assertEquals("eden", r.attribute("name"));
	}

	/** A version that is not a number falls back rather than throwing. */
	@Test
	public void nonNumericVersionFallsBack() throws Exception {
		assertEquals(-1, probe("<blowtorch xmlversion=\"two\"/>").intAttribute("xmlversion", -1));
	}

	/** An empty document has no root, and says so instead of throwing. */
	@Test
	public void emptyDocumentHasNoRoot() throws Exception {
		XmlRootProbe.Root r = probe("");
		assertFalse(r.found());
		assertNull(r.name());
	}

	/** Text with no markup at all is not a document. */
	@Test
	public void garbageHasNoRoot() throws Exception {
		assertFalse(probe("this is not xml at all").found());
	}

	/** A truncated declaration leaves nothing to find, and does not hang. */
	@Test
	public void truncatedPrologHasNoRoot() throws Exception {
		assertFalse(probe("<?xml version=\"1.0\"").found());
	}

	/** A null stream is answered, not thrown at. */
	@Test
	public void nullInputHasNoRoot() throws Exception {
		assertFalse(XmlRootProbe.probe((java.io.Reader) null).found());
	}

	/** The stream overload decodes UTF-8. */
	@Test
	public void readsFromAUtf8Stream() throws Exception {
		byte[] bytes = "<blowtorch xmlversion=\"2\" who=\"zażółć\"/>".getBytes("UTF-8");
		XmlRootProbe.Root r = XmlRootProbe.probe(new ByteArrayInputStream(bytes));
		assertEquals("blowtorch", r.name());
		assertEquals("zażółć", r.attribute("who"));
	}

	/**
	 * The point of the exercise: a large file must not be read past its root.
	 * A quarter-megabyte profile used to be streamed end to end, twice.
	 */
	@Test
	public void stopsReadingAfterTheRootElement() throws Exception {
		StringBuilder big = new StringBuilder("<blowtorch xmlversion=\"2\">");
		for (int i = 0; i < 20000; i++) {
			big.append("<alias pre=\"a").append(i).append("\" post=\"b\"/>");
		}
		big.append("</blowtorch>");
		CountingReader counting = new CountingReader(big.toString());
		XmlRootProbe.Root r = XmlRootProbe.probe(counting);
		assertEquals("blowtorch", r.name());
		assertTrue("read " + counting.count + " chars of " + big.length(),
				counting.count < 100);
	}

	private static final class CountingReader extends java.io.Reader {
		private final StringReader delegate;
		int count;

		CountingReader(String s) {
			delegate = new StringReader(s);
		}

		@Override
		public int read(char[] buf, int off, int len) throws java.io.IOException {
			int n = delegate.read(buf, off, len);
			if (n > 0) {
				count += n;
			}
			return n;
		}

		@Override
		public void close() throws java.io.IOException {
			delegate.close();
		}
	}
}
