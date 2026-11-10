package com.resurrection.blowtorch2.lib.timer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptElementListener;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * NewTimer stores the editor's timer. The expected name, seconds and
 * function are the literals below. They are read back from the editor's
 * writer through {@link TimerElementListener}, which is the reader both
 * settings parsers register.
 */
public class TimerCreateTest {

	private static final String NAME = "heal";
	private static final int SECONDS = 45;

	@Test
	public void writerThenListenerKeepsNameAndSeconds() throws Exception {
		assertNull(TimerCreate.rejection(NAME, Integer.valueOf(SECONDS), null,
				new HashMap<String, TimerData>()));
		TimerData created = TimerCreate.create(NAME, SECONDS, null);
		assertEquals(45, created.getRemainingTime());
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		TimerParser.saveTimerToXML(out, created);
		String xml = out.toString();
		assertFalse(xml.contains("group="));
		assertFalse(xml.contains("<script"));
		Map<String, String> attrs = attributes(xml, "timer");
		assertEquals("heal", attrs.get(BasePluginParser.ATTR_TIMERNAME));
		assertEquals("45", attrs.get(BasePluginParser.ATTR_SECONDS));
		assertEquals("false", attrs.get(BasePluginParser.ATTR_REPEAT));
		assertEquals("false", attrs.get(BasePluginParser.ATTR_PLAYING));

		TimerData read = readBack(attrs, null);
		assertEquals("heal", read.getName());
		assertEquals(Integer.valueOf(45), read.getSeconds());
		assertEquals(45, read.getRemainingTime());
		assertFalse(read.isRepeat());
		assertFalse(read.isPlaying());
		assertTrue(read.getResponders().isEmpty());
	}

	@Test
	public void writerThenListenerKeepsAQuoteInTheNameAndTheScript() throws Exception {
		TimerData created = TimerCreate.create("say \"hi\"", 2, "OnHeal");
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		TimerParser.saveTimerToXML(out, created);
		String xml = out.toString();
		Map<String, String> timer = attributes(xml, "timer");
		Map<String, String> script = attributes(xml, "script");
		assertEquals("say \"hi\"", timer.get(BasePluginParser.ATTR_TIMERNAME));
		assertEquals("OnHeal", script.get(BasePluginParser.ATTR_FUNCTION));
		assertEquals("always", script.get(BasePluginParser.ATTR_FIRETYPE));

		TimerData read = readBack(timer, script);
		assertEquals("say \"hi\"", read.getName());
		assertEquals(Integer.valueOf(2), read.getSeconds());
		assertEquals(1, read.getResponders().size());
		ScriptResponder saved = (ScriptResponder) read.getResponders().get(0);
		assertEquals("OnHeal", saved.getFunction());
		assertEquals(TriggerResponder.FIRE_WHEN.WINDOW_BOTH, saved.getFireType());
	}

	@Test
	public void refusesEmptyExistingAndNotAWholeSecond() {
		Map<String, TimerData> existing = new HashMap<String, TimerData>();
		existing.put("heal", TimerCreate.create("heal", 45, null));
		assertEquals("name is empty",
				TimerCreate.rejection("", Integer.valueOf(45), null, existing));
		assertEquals("\"heal\" exists already",
				TimerCreate.rejection("heal", Integer.valueOf(45), null, existing));
		assertEquals("function name is empty",
				TimerCreate.rejection("other", Integer.valueOf(45), "", existing));
		assertEquals("seconds must be a whole number",
				TimerCreate.rejection("other", null, null, existing));
		assertEquals("duration must be more than zero seconds",
				TimerCreate.rejection("other", TimerCreate.wholeSeconds(0), null, existing));
		assertEquals("duration must be more than zero seconds",
				TimerCreate.rejection("other", TimerCreate.wholeSeconds(-2), null, existing));
		assertEquals(Integer.valueOf(0), TimerCreate.wholeSeconds(0));
		assertEquals(Integer.valueOf(-2), TimerCreate.wholeSeconds(-2));
		assertNull(TimerCreate.wholeSeconds(1.5));
		assertNull(TimerCreate.wholeSeconds(Integer.MAX_VALUE + 1.0));
		assertEquals(Integer.valueOf(30), TimerCreate.wholeSeconds(30.0));
		assertEquals(Integer.valueOf(2), TimerCreate.wholeSeconds(2.0));
		assertEquals(Integer.valueOf(Integer.MAX_VALUE),
				TimerCreate.wholeSeconds(Integer.MAX_VALUE));
	}

	private static TimerData readBack(Map<String, String> timer, Map<String, String> script) {
		final List<TimerData> got = new ArrayList<TimerData>();
		TimerData current = new TimerData();
		TimerElementListener listener = new TimerElementListener(new PluginParser.NewItemCallback() {
			public void addAlias(String key,
					com.resurrection.blowtorch2.lib.alias.AliasData a) {
			}

			public void addTrigger(String key, TriggerData t) {
			}

			public void addTimer(String key, TimerData t) {
				got.add(t);
			}

			public void addScript(String name, String body, boolean execute) {
			}

			public void addWindow(String name, WindowToken w) {
			}
		}, current);
		MapAttributes read = new MapAttributes();
		read.put(BasePluginParser.ATTR_TIMERNAME, timer.get(BasePluginParser.ATTR_TIMERNAME));
		read.put(BasePluginParser.ATTR_SECONDS, timer.get(BasePluginParser.ATTR_SECONDS));
		read.put(BasePluginParser.ATTR_REPEAT, timer.get(BasePluginParser.ATTR_REPEAT));
		read.put(BasePluginParser.ATTR_PLAYING, timer.get(BasePluginParser.ATTR_PLAYING));
		listener.start(read);
		if (script != null) {
			MapAttributes scriptAttrs = new MapAttributes();
			scriptAttrs.put(BasePluginParser.ATTR_FUNCTION,
					script.get(BasePluginParser.ATTR_FUNCTION));
			scriptAttrs.put(BasePluginParser.ATTR_FIRETYPE,
					script.get(BasePluginParser.ATTR_FIRETYPE));
			new ScriptElementListener(current, null, current).start(scriptAttrs);
		}
		listener.end();
		assertEquals(1, got.size());
		return got.get(0);
	}

	/** Attributes of the first start tag whose name is {@code tag}. */
	private static Map<String, String> attributes(String xml, String tag) {
		int start = xml.indexOf("<" + tag + " ");
		assertTrue(start >= 0);
		int end = xml.indexOf('>', start);
		return attributesIn(xml.substring(start, end));
	}

	private static Map<String, String> attributesIn(String xml) {
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
