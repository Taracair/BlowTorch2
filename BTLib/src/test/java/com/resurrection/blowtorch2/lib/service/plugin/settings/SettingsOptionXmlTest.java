package com.resurrection.blowtorch2.lib.service.plugin.settings;

import org.junit.Test;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Characterization for settings option value + XML emit leaf logic.
 * Full ConnectionSetttingsParser / PluginParser load paths need android.sax + Context
 * (Robolectric) — not covered on pure JVM here.
 */
public class SettingsOptionXmlTest {

	@Test
	public void stringOptionSetGetAndXml() throws Exception {
		StringOption opt = new StringOption();
		opt.setKey("gmcp_supports");
		opt.setTitle("Supports");
		opt.setDescription("GMCP modules");
		opt.setValue("\"Char 1\", \"Core 1\"");
		assertEquals("\"Char 1\", \"Core 1\"", opt.getValue());

		RecordingXmlSerializer out = new RecordingXmlSerializer();
		opt.saveToXML(out);
		String xml = out.toString();
		assertTrue(xml.contains("<string"));
		assertTrue(xml.contains("key=\"gmcp_supports\""));
		assertTrue(xml.contains("\"Char 1\", \"Core 1\""));
		assertTrue(xml.contains("</string>"));
	}

	@Test
	public void booleanOptionParsesStringAndXml() throws Exception {
		BooleanOption opt = new BooleanOption();
		opt.setKey("use_gmcp");
		opt.setTitle("Use GMCP?");
		opt.setDescription("");
		opt.setValue("true");
		assertEquals(Boolean.TRUE, opt.getValue());
		opt.setValue("false");
		assertEquals(Boolean.FALSE, opt.getValue());

		RecordingXmlSerializer out = new RecordingXmlSerializer();
		opt.setValue(Boolean.TRUE);
		opt.saveToXML(out);
		String xml = out.toString();
		assertTrue(xml.contains("<boolean"));
		assertTrue(xml.contains("key=\"use_gmcp\""));
		assertTrue(xml.contains(">true</boolean>"));
	}

	@Test
	public void integerOptionParsesDecimal() {
		IntegerOption opt = new IntegerOption();
		opt.setKey("auto_reconnect_limit");
		opt.setValue("12");
		assertEquals(Integer.valueOf(12), opt.getValue());
		opt.setValue(Integer.valueOf(3));
		assertEquals(Integer.valueOf(3), opt.getValue());
	}

	@Test
	public void integerOptionXmlSmoke() throws Exception {
		IntegerOption opt = new IntegerOption();
		opt.setKey("terminal_width");
		opt.setTitle("Width");
		opt.setDescription("cols");
		opt.setValue(80);
		RecordingXmlSerializer out = new RecordingXmlSerializer();
		opt.saveToXML(out);
		String xml = out.toString();
		assertTrue(xml.contains("<integer"));
		assertTrue(xml.contains("key=\"terminal_width\""));
		assertTrue(xml.contains(">80</integer>"));
		assertFalse(xml.contains("null"));
	}

	/**
	 * Minimal XmlSerializer that records tags for characterization assertions.
	 * Avoids android.util.Xml stubs on the unit-test classpath.
	 */
	public static final class RecordingXmlSerializer implements XmlSerializer {
		private final StringWriter writer = new StringWriter();
		private boolean pendingCloseStart;

		private void closeStartIfNeeded() {
			if (pendingCloseStart) {
				writer.write('>');
				pendingCloseStart = false;
			}
		}

		@Override
		public void setFeature(String name, boolean state) {
		}

		@Override
		public boolean getFeature(String name) {
			return false;
		}

		@Override
		public void setProperty(String name, Object value) {
		}

		@Override
		public Object getProperty(String name) {
			return null;
		}

		@Override
		public void setOutput(java.io.OutputStream os, String encoding) {
		}

		@Override
		public void setOutput(java.io.Writer writer) {
		}

		@Override
		public void startDocument(String encoding, Boolean standalone) {
		}

		@Override
		public void endDocument() {
			closeStartIfNeeded();
		}

		@Override
		public void setPrefix(String prefix, String namespace) {
		}

		@Override
		public String getPrefix(String namespace, boolean generatePrefix) {
			return "";
		}

		@Override
		public int getDepth() {
			return 0;
		}

		@Override
		public String getNamespace() {
			return "";
		}

		@Override
		public String getName() {
			return "";
		}

		@Override
		public XmlSerializer startTag(String namespace, String name) {
			closeStartIfNeeded();
			writer.write('<');
			writer.write(name);
			pendingCloseStart = true;
			return this;
		}

		@Override
		public XmlSerializer attribute(String namespace, String name, String value) {
			writer.write(' ');
			writer.write(name);
			writer.write("=\"");
			writer.write(escapeAttr(value));
			writer.write('"');
			return this;
		}

		@Override
		public XmlSerializer endTag(String namespace, String name) {
			if (pendingCloseStart) {
				writer.write('>');
				pendingCloseStart = false;
			}
			writer.write("</");
			writer.write(name);
			writer.write('>');
			return this;
		}

		@Override
		public XmlSerializer text(String text) {
			closeStartIfNeeded();
			writer.write(escapeText(text));
			return this;
		}

		@Override
		public XmlSerializer text(char[] buf, int start, int len) {
			return text(new String(buf, start, len));
		}

		@Override
		public void cdsect(String text) {
		}

		@Override
		public void entityRef(String text) {
		}

		@Override
		public void processingInstruction(String text) {
		}

		@Override
		public void comment(String text) {
		}

		@Override
		public void docdecl(String text) {
		}

		@Override
		public void ignorableWhitespace(String text) {
		}

		@Override
		public void flush() throws IOException {
			writer.flush();
		}

		@Override
		public String toString() {
			closeStartIfNeeded();
			return writer.toString();
		}

		private static String escapeAttr(String s) {
			if (s == null) {
				return "";
			}
			return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
					.replace("\"", "&quot;");
		}

		private static String escapeText(String s) {
			if (s == null) {
				return "";
			}
			return s.replace("&", "&amp;").replace("<", "&lt;");
		}
	}
}
