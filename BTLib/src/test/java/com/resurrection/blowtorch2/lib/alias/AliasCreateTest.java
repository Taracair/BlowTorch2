package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * NewAlias stores the editor's alias. The expected pattern and replacement
 * are the literals below. They are read back from the editor's writer
 * through {@link AliasElementListener}, which is the reader both settings
 * parsers register.
 */
public class AliasCreateTest {

	private static final String PATTERN = "^kk$";
	private static final String REPLACEMENT = "kill $1";

	@Test
	public void writerThenListenerKeepsPatternAndReplacement() throws Exception {
		assertNull(AliasCreate.rejection(PATTERN, REPLACEMENT,
				new HashMap<String, AliasData>(), null));
		AliasData created = AliasCreate.create(PATTERN, REPLACEMENT);
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		AliasParser.saveAliasToXML(out, created);
		String xml = out.toString();
		assertFalse(xml.contains("localEcho") || xml.contains("local_echo"));
		Map<String, String> attrs = attributes(xml);
		assertEquals("true", attrs.get("enabled"));

		final List<String> keys = new ArrayList<String>();
		final List<AliasData> got = new ArrayList<AliasData>();
		AliasElementListener listener = new AliasElementListener(new PluginParser.NewItemCallback() {
			public void addAlias(String key, AliasData a) {
				keys.add(key);
				got.add(a);
			}

			public void addTrigger(String key, TriggerData t) {
			}

			public void addTimer(String key, TimerData t) {
			}

			public void addScript(String name, String body, boolean execute) {
			}

			public void addWindow(String name, WindowToken w) {
			}
		}, new AliasData());
		MapAttributes read = new MapAttributes();
		read.put(BasePluginParser.ATTR_PRE, attrs.get(BasePluginParser.ATTR_PRE));
		read.put(BasePluginParser.ATTR_POST, attrs.get(BasePluginParser.ATTR_POST));
		read.put("enabled", attrs.get("enabled"));
		listener.start(read);
		listener.end();

		assertEquals(1, got.size());
		assertEquals("^kk$", got.get(0).getPre());
		assertEquals("kill $1", got.get(0).getPost());
		assertEquals("kk", keys.get(0));
		assertTrue(got.get(0).isEnabled());
		assertEquals(AliasLocalEcho.INHERIT, got.get(0).getLocalEcho());
		assertTrue(got.get(0).getSetVariables().isEmpty());
	}

	@Test
	public void writerThenListenerKeepsAQuoteInTheReplacement() throws Exception {
		String replacement = "say \"hi\"";
		AliasData created = AliasCreate.create("^talk$", replacement);
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		AliasParser.saveAliasToXML(out, created);
		Map<String, String> attrs = attributes(out.toString());
		final List<AliasData> got = new ArrayList<AliasData>();
		AliasElementListener listener = new AliasElementListener(new PluginParser.NewItemCallback() {
			public void addAlias(String key, AliasData a) {
				got.add(a);
			}

			public void addTrigger(String key, TriggerData t) {
			}

			public void addTimer(String key, TimerData t) {
			}

			public void addScript(String name, String body, boolean execute) {
			}

			public void addWindow(String name, WindowToken w) {
			}
		}, new AliasData());
		MapAttributes read = new MapAttributes();
		read.put(BasePluginParser.ATTR_PRE, attrs.get(BasePluginParser.ATTR_PRE));
		read.put(BasePluginParser.ATTR_POST, attrs.get(BasePluginParser.ATTR_POST));
		listener.start(read);
		listener.end();
		assertEquals("^talk$", got.get(0).getPre());
		assertEquals("say \"hi\"", got.get(0).getPost());
	}

	@Test
	public void refusesEmptyExistingReservedBrokenAndCircular() {
		assertEquals("pattern is empty",
				AliasCreate.rejection("^$", "north", new HashMap<String, AliasData>(), null));
		assertEquals("replacement is empty",
				AliasCreate.rejection("n", "", new HashMap<String, AliasData>(), null));
		HashMap<String, AliasData> have = new HashMap<String, AliasData>();
		have.put("kk", AliasCreate.create(PATTERN, REPLACEMENT));
		assertEquals("\"kk\" exists already",
				AliasCreate.rejection(PATTERN, "kill $1", have, null));
		assertEquals("\"map\" is reserved for a system command",
				AliasCreate.rejection("^map$", "look", new HashMap<String, AliasData>(),
						Collections.singletonList("map")));
		assertTrue(AliasCreate.rejection("(", "look", new HashMap<String, AliasData>(), null)
				.startsWith("\"(\" is not a valid pattern:"));
		assertTrue(AliasCreate.rejection("a", "a", new HashMap<String, AliasData>(), null)
				.startsWith("circular reference with aliases:"));
		assertNull(AliasCreate.rejection("n", "north", new HashMap<String, AliasData>(),
				Arrays.asList("map")));
	}

	private static Map<String, String> attributes(String xml) {
		Map<String, String> out = new HashMap<String, String>();
		int i = 0;
		while (i < xml.length()) {
			int eq = xml.indexOf("=\"", i);
			if (eq < 0) {
				break;
			}
			int nameStart = eq;
			while (nameStart > 0 && xml.charAt(nameStart - 1) != ' '
					&& xml.charAt(nameStart - 1) != '<') {
				nameStart--;
			}
			int valueStart = eq + 2;
			int valueEnd = xml.indexOf('"', valueStart);
			out.put(xml.substring(nameStart, eq).trim(),
					unescape(xml.substring(valueStart, valueEnd)));
			i = valueEnd + 1;
		}
		return out;
	}

	private static String unescape(String s) {
		return s.replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<")
				.replace("&amp;", "&");
	}

	private static final class MapAttributes implements Attributes {
		private final HashMap<String, String> map = new HashMap<String, String>();

		void put(String local, String value) {
			map.put(local, value);
		}

		@Override
		public int getLength() {
			return map.size();
		}

		@Override
		public String getURI(int index) {
			return "";
		}

		@Override
		public String getLocalName(int index) {
			return "";
		}

		@Override
		public String getQName(int index) {
			return "";
		}

		@Override
		public String getType(int index) {
			return "CDATA";
		}

		@Override
		public String getValue(int index) {
			return null;
		}

		@Override
		public int getIndex(String uri, String localName) {
			return -1;
		}

		@Override
		public int getIndex(String qName) {
			return -1;
		}

		@Override
		public String getType(String uri, String localName) {
			return "CDATA";
		}

		@Override
		public String getType(String qName) {
			return "CDATA";
		}

		@Override
		public String getValue(String uri, String localName) {
			return map.get(localName);
		}

		@Override
		public String getValue(String qName) {
			return map.get(qName);
		}
	}
}
