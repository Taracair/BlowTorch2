package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableApply;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableOp;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * Set Variable on an alias: empty list is today's alias, then nested XML,
 * captures, and dispatch ops. Full android.sax load is not on the JVM.
 */
public class AliasSetVariableTest {

	private static AliasData alias(String pre, String post) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		a.setEnabled(true);
		return a;
	}

	@Test
	public void copyAndEqualsOfPrePostOnlyStillHold() {
		AliasData a = alias("^kk", "kill $1");
		AliasData b = a.copy();
		assertEquals(a, b);
		assertNotSame(a, b);
		assertTrue(a.getSetVariables().isEmpty());
		assertTrue(b.getSetVariables().isEmpty());
		assertNotSame(a.getSetVariables(), b.getSetVariables());
	}

	@Test
	public void copyIncludesSetVariableRows() {
		AliasData a = alias("^kk", "kill $1");
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("kills");
		r.setVariableValue("1");
		r.setMode(SetVariableApply.MODE_ADD);
		r.setPersist(true);
		a.getSetVariables().add(r);
		AliasData b = a.copy();
		assertEquals(a, b);
		assertEquals(1, b.getSetVariables().size());
		assertEquals(SetVariableApply.MODE_ADD, b.getSetVariables().get(0).getMode());
		assertTrue(b.getSetVariables().get(0).isPersist());
		b.getSetVariables().get(0).setVariableValue("2");
		assertFalse(a.equals(b));
		assertEquals("1", a.getSetVariables().get(0).getVariableValue());
	}

	@Test
	public void saveWithoutSetVariablesHasNoNestedTag() throws Exception {
		AliasData a = alias("^kk", "kill $1");
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		AliasParser.saveAliasToXML(out, a);
		String xml = out.toString();
		assertTrue(xml.contains("<alias"));
		assertTrue(xml.contains("pre="));
		assertFalse(xml.contains("<setVariable"));
	}

	@Test
	public void saveWritesNestedSetVariableWithAddMode() throws Exception {
		AliasData a = alias("^kk", "kill $1");
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("kills");
		r.setVariableValue("1");
		r.setMode(SetVariableApply.MODE_ADD);
		a.getSetVariables().add(r);
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		AliasParser.saveAliasToXML(out, a);
		String xml = out.toString();
		assertTrue(xml.contains("<setVariable"));
		assertTrue(xml.contains("name=\"kills\""));
		assertTrue(xml.contains("mode=\"add\""));
		assertFalse(xml.contains("persist="));
		assertTrue(xml.contains("</alias>"));
	}

	@Test
	public void elementListenerCopiesOnEndAfterChildrenAttach() {
		final List<AliasData> got = new ArrayList<AliasData>();
		AliasData current = new AliasData();
		AliasElementListener listener = new AliasElementListener(callback(got), current);
		MapAttributes attrs = new MapAttributes();
		attrs.put(BasePluginParser.ATTR_PRE, "^kk");
		attrs.put(BasePluginParser.ATTR_POST, "kill $1");
		listener.start(attrs);
		assertTrue("copy on start() would drop nested setVariable", got.isEmpty());
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("target");
		r.setVariableValue("$1");
		current.getSetVariables().add(r);
		listener.end();
		assertEquals(1, got.size());
		assertEquals("kk", keyFor(got.get(0)));
		assertEquals(1, got.get(0).getSetVariables().size());
		assertEquals("target", got.get(0).getSetVariables().get(0).getVariableName());
		assertEquals("$1", got.get(0).getSetVariables().get(0).getVariableValue());
	}

	@Test
	public void startClearsAReusedCurrentAlias() {
		final List<AliasData> got = new ArrayList<AliasData>();
		AliasData current = new AliasData();
		AliasElementListener listener = new AliasElementListener(callback(got), current);
		MapAttributes first = new MapAttributes();
		first.put(BasePluginParser.ATTR_PRE, "^kk");
		first.put(BasePluginParser.ATTR_POST, "kill $1");
		listener.start(first);
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("kills");
		r.setVariableValue("1");
		current.getSetVariables().add(r);
		listener.end();
		MapAttributes second = new MapAttributes();
		second.put(BasePluginParser.ATTR_PRE, "n");
		second.put(BasePluginParser.ATTR_POST, "north");
		listener.start(second);
		assertTrue(current.getSetVariables().isEmpty());
		listener.end();
		assertEquals(2, got.size());
		assertTrue(got.get(1).getSetVariables().isEmpty());
	}

	@Test
	public void elementListenerAttachesSetVariableFromSelector() {
		AliasData current = new AliasData();
		com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableElementListener el =
				new com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableElementListener(
						current, null, null);
		MapAttributes a = new MapAttributes();
		a.put(BasePluginParser.ATTR_NAME, "kills");
		a.put(BasePluginParser.ATTR_CONDITION_VALUE, "1");
		a.put("mode", "add");
		el.start(a);
		assertEquals(1, current.getSetVariables().size());
		assertEquals(SetVariableApply.MODE_ADD, current.getSetVariables().get(0).getMode());
	}

	@Test
	public void emptyListProducesNoOps() {
		assertTrue(AliasSetVariables.ops(alias("^kk", "kill $1"),
				new HashMap<String, String>()).isEmpty());
		assertTrue(AliasSetVariables.ops(null, new HashMap<String, String>()).isEmpty());
	}

	@Test
	public void opsTranslateDollarOneAfterCaptures() {
		AliasData a = alias("^kk", "kill $1");
		SetVariableResponder set = new SetVariableResponder();
		set.setVariableName("target");
		set.setVariableValue("$1");
		a.getSetVariables().add(set);
		SetVariableResponder add = new SetVariableResponder();
		add.setVariableName("kills");
		add.setVariableValue("1");
		add.setMode(SetVariableApply.MODE_ADD);
		a.getSetVariables().add(add);
		HashMap<String, String> caps = AliasSetVariables.captures(a, "kk goblin", "kk");
		assertEquals("goblin", caps.get("1"));
		List<SetVariableOp> ops = AliasSetVariables.ops(a, caps);
		assertEquals(2, ops.size());
		assertEquals("target", ops.get(0).key);
		assertEquals("goblin", ops.get(0).value);
		assertEquals(SetVariableApply.MODE_SET, ops.get(0).mode);
		assertEquals("kills", ops.get(1).key);
		assertEquals("1", ops.get(1).value);
		assertEquals(SetVariableApply.MODE_ADD, ops.get(1).mode);
	}

	@Test
	public void emptyNameIsSkipped() {
		AliasData a = alias("n", "north");
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("");
		r.setVariableValue("1");
		a.getSetVariables().add(r);
		assertTrue(AliasSetVariables.ops(a, new HashMap<String, String>()).isEmpty());
	}

	@Test
	public void fireNeverDoesNotFire() {
		SetVariableResponder r = new SetVariableResponder();
		r.setFireType(FIRE_WHEN.WINDOW_NEVER);
		assertFalse(AliasSetVariables.shouldFire(r, true));
		assertFalse(AliasSetVariables.shouldFire(r, false));
		r.setFireType(FIRE_WHEN.WINDOW_BOTH);
		assertTrue(AliasSetVariables.shouldFire(r, true));
		assertTrue(AliasSetVariables.shouldFire(r, false));
		r.setFireType(FIRE_WHEN.WINDOW_OPEN);
		assertTrue(AliasSetVariables.shouldFire(r, true));
		assertFalse(AliasSetVariables.shouldFire(r, false));
	}

	@Test
	public void capturesMatchWithSubstitution() {
		AliasData word = alias("^kk", "kill $1");
		assertEquals("goblin", AliasExpansion.captures(word, "kk goblin", "kk").get("1"));
		AliasData anchored = alias("^cast (.+)$", "c $1");
		assertEquals("fireball",
				AliasExpansion.captures(anchored, "cast fireball", "cast fireball").get("1"));
	}

	@Test
	public void parserRegistersOnEndAndBothLoadPathsShareIt() throws Exception {
		String parser = readJava("BTLib/src/com/resurrection/blowtorch2/lib/alias/AliasParser.java");
		assertTrue(parser.contains("setElementListener"));
		assertTrue(parser.contains("SetVariableResponderParser.registerListeners"));
		String listener = readJava(
				"BTLib/src/com/resurrection/blowtorch2/lib/alias/AliasElementListener.java");
		assertTrue(listener.contains("implements ElementListener"));
		assertTrue(listener.contains("getSetVariables().clear()"));
		assertTrue(listener.contains("callback.addAlias"));
		assertTrue("copy must happen in end(), not start()",
				listener.indexOf("public void end()")
						< listener.indexOf("callback.addAlias"));
		assertTrue(listener.indexOf("public void start(")
				< listener.indexOf("getSetVariables().clear()"));
		String main = readJava(
				"BTLib/src/com/resurrection/blowtorch2/lib/service/plugin/settings/ConnectionSetttingsParser.java");
		String plugin = readJava(
				"BTLib/src/com/resurrection/blowtorch2/lib/service/plugin/settings/PluginParser.java");
		assertTrue(main.contains("AliasParser.registerListeners"));
		assertTrue(plugin.contains("AliasParser.registerListeners"));
		String selection = readJava(
				"BTLib/src/com/resurrection/blowtorch2/lib/alias/BetterAliasSelectionDialog.java");
		assertTrue("Done rebuilds AliasData from scalars; the list must be copied through",
				selection.contains("setSetVariables"));
		String pluginJava = readJava(
				"BTLib/src/com/resurrection/blowtorch2/lib/service/plugin/Plugin.java");
		assertTrue(pluginJava.contains("dispatchAliasSetVariables"));
		assertTrue(pluginJava.contains("MESSAGE_SET_VARIABLE")
				|| pluginJava.contains("AliasSetVariables.dispatch"));
		String data = readJava("BTLib/src/com/resurrection/blowtorch2/lib/alias/AliasData.java");
		int write = data.indexOf("public void writeToParcel");
		int read = data.indexOf("private void readFromParcel");
		String writeBody = data.substring(write, data.indexOf("public String getPre"));
		assertTrue(writeBody.contains("o.writeInt(list.size())"));
		assertTrue(writeBody.contains("o.writeParcelable(r, flags)"));
		String readBody = data.substring(read, write);
		assertTrue(readBody.contains("p.dataAvail() > 0"));
		assertTrue(readBody.contains("p.readParcelable"));
	}

	private static String keyFor(AliasData a) {
		String key = a.getPre();
		if (key.startsWith("^")) {
			key = key.substring(1);
		}
		if (key.endsWith("$")) {
			key = key.substring(0, key.length() - 1);
		}
		return key;
	}

	private static PluginParser.NewItemCallback callback(final List<AliasData> got) {
		return new PluginParser.NewItemCallback() {
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
		};
	}

	private static String readJava(String relativeFromRepo) throws java.io.IOException {
		java.io.File[] candidates = new java.io.File[] {
				new java.io.File(relativeFromRepo),
				new java.io.File("../" + relativeFromRepo),
		};
		for (java.io.File f : candidates) {
			if (f.isFile()) {
				return new String(java.nio.file.Files.readAllBytes(f.toPath()),
						java.nio.charset.StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing " + relativeFromRepo + " from "
				+ new java.io.File(".").getAbsolutePath());
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
