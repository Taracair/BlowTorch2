package com.resurrection.blowtorch2.lib.responder.setvariable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.CaptureSubstitution;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;
import com.resurrection.blowtorch2.lib.trigger.TriggerEditorDialog;
import com.resurrection.blowtorch2.lib.trigger.condition.SessionVariableStore;

/**
 * Set Variable: today's replace when {@code mode} is missing, then add / append
 * / unset / persist sidecar. Arithmetic runs after {@code $1} substitution.
 */
public class SetVariableResponderTest {

	@Test
	public void missingModeAndPersistAreSetAndSessionOnly() {
		SetVariableResponder r = new SetVariableResponder();
		MapAttributes a = new MapAttributes();
		a.put(BasePluginParser.ATTR_NAME, "fighting");
		a.put(BasePluginParser.ATTR_CONDITION_VALUE, "1");
		a.put(BasePluginParser.ATTR_FIRETYPE, "always");
		SetVariableResponderParser.applyFromAttributes(r, a);
		assertEquals("fighting", r.getVariableName());
		assertEquals("1", r.getVariableValue());
		assertEquals(SetVariableApply.MODE_SET, r.getMode());
		assertFalse(r.isPersist());
		assertEquals(FIRE_WHEN.WINDOW_BOTH, r.getFireType());
	}

	@Test
	public void unknownOrEmptyModeIsSet() {
		SetVariableResponder nope = new SetVariableResponder();
		MapAttributes a = new MapAttributes();
		a.put(BasePluginParser.ATTR_NAME, "kills");
		a.put("mode", "nope");
		SetVariableResponderParser.applyFromAttributes(nope, a);
		assertEquals(SetVariableApply.MODE_SET, nope.getMode());

		SetVariableResponder empty = new SetVariableResponder();
		MapAttributes b = new MapAttributes();
		b.put(BasePluginParser.ATTR_NAME, "kills");
		b.put("mode", "");
		SetVariableResponderParser.applyFromAttributes(empty, b);
		assertEquals(SetVariableApply.MODE_SET, empty.getMode());
	}

	@Test
	public void defaultSaveOmitsModeAndPersist() throws Exception {
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("fighting");
		r.setVariableValue("1");
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		r.saveResponderToXML(out);
		String xml = out.toString();
		assertTrue(xml.contains("<setVariable"));
		assertTrue(xml.contains("name=\"fighting\""));
		assertTrue(xml.contains("value=\"1\""));
		assertTrue(xml.contains("fireWhen="));
		assertFalse(xml.contains("mode="));
		assertFalse(xml.contains("persist="));
	}

	@Test
	public void persistTrueAndAddModeAreWritten() throws Exception {
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("kills");
		r.setVariableValue("1");
		r.setMode(SetVariableApply.MODE_ADD);
		r.setPersist(true);
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		r.saveResponderToXML(out);
		String xml = out.toString();
		assertTrue(xml.contains("mode=\"add\""));
		assertTrue(xml.contains("persist=\"true\""));
	}

	@Test
	public void replaceDoesNotAdd() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("kills", "5");
		assertEquals("17", SetVariableApply.applyToStore(store, "kills", "17",
				SetVariableApply.MODE_SET));
		assertEquals("17", store.get("kills"));
	}

	@Test
	public void emptyKeyIsNoOpForEveryMode() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("kills", "5");
		assertNull(SetVariableApply.applyToStore(store, "", "1", SetVariableApply.MODE_ADD));
		assertNull(SetVariableApply.applyToStore(store, "", "1", SetVariableApply.MODE_SET));
		assertEquals("5", store.get("kills"));
		SetVariableResponder r = new SetVariableResponder();
		r.setVariableName("");
		r.setVariableValue("1");
		assertFalse(r.doResponse(null, null, 0, null, null, 0, 0, "", null, "", "",
				0, 0, true, null, null, null, "", ""));
	}

	@Test
	public void dollarOneInSetValueIsACapture() {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "goblin");
		assertEquals("goblin", CaptureSubstitution.apply("$1", map));
		SessionVariableStore store = new SessionVariableStore();
		assertEquals("goblin", SetVariableApply.applyToStore(store, "target",
				CaptureSubstitution.apply("$1", map), SetVariableApply.MODE_SET));
		assertEquals("goblin", store.get("target"));
	}

	@Test
	public void copyAndEqualsMatchTodaysSet() {
		SetVariableResponder a = new SetVariableResponder();
		a.setVariableName("fighting");
		a.setVariableValue("1");
		SetVariableResponder b = a.copy();
		assertEquals(a, b);
		assertNotSame(a, b);
		assertEquals("fighting=1", TriggerEditorDialog.actionSummary(a));
		b.setMode(SetVariableApply.MODE_ADD);
		assertFalse(a.equals(b));
		assertEquals("fighting +1", TriggerEditorDialog.actionSummary(b));
	}

	@Test
	public void addSubtractStartAtZeroAndWipeDecimals() {
		SessionVariableStore store = new SessionVariableStore();
		assertEquals("1", SetVariableApply.applyToStore(store, "kills", "1",
				SetVariableApply.MODE_ADD));
		assertEquals("2", SetVariableApply.applyToStore(store, "kills", "1",
				SetVariableApply.MODE_ADD));
		store.set("hp", "47.5");
		assertEquals("1", SetVariableApply.applyToStore(store, "hp", "1",
				SetVariableApply.MODE_ADD));
		assertEquals("0", SetVariableApply.applyToStore(store, "kills", "2",
				SetVariableApply.MODE_SUBTRACT));
		assertEquals("0", SetVariableApply.applyToStore(store, "kills", "nope",
				SetVariableApply.MODE_ADD));
		assertEquals("3", SetVariableApply.applyToStore(store, "padded", " 3 ",
				SetVariableApply.MODE_ADD));
	}

	@Test
	public void dollarOneThenPlusIsNotAnExpression() {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "3");
		String operand = CaptureSubstitution.apply("$1+1", map);
		assertEquals("3+1", operand);
		SessionVariableStore store = new SessionVariableStore();
		store.set("kills", "10");
		assertEquals("10", SetVariableApply.applyToStore(store, "kills", operand,
				SetVariableApply.MODE_ADD));
	}

	@Test
	public void appendConcatenatesIncludingALeadingComma() {
		SessionVariableStore store = new SessionVariableStore();
		assertEquals(", Bob", SetVariableApply.applyToStore(store, "seen", ", Bob",
				SetVariableApply.MODE_APPEND));
		assertEquals(", Bob, Ann", SetVariableApply.applyToStore(store, "seen", ", Ann",
				SetVariableApply.MODE_APPEND));
	}

	@Test
	public void unsetRemovesTheName() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("fighting", "1");
		assertNull(SetVariableApply.applyToStore(store, "fighting", "",
				SetVariableApply.MODE_UNSET));
		assertFalse(store.exists("fighting"));
	}

	@Test
	public void persistAttributeTrueAndOne() {
		SetVariableResponder r = new SetVariableResponder();
		MapAttributes a = new MapAttributes();
		a.put("persist", "true");
		SetVariableResponderParser.applyFromAttributes(r, a);
		assertTrue(r.isPersist());
		SetVariableResponder r2 = new SetVariableResponder();
		MapAttributes b = new MapAttributes();
		b.put("persist", "1");
		SetVariableResponderParser.applyFromAttributes(r2, b);
		assertTrue(r2.isPersist());
		SetVariableResponder r3 = new SetVariableResponder();
		MapAttributes c = new MapAttributes();
		c.put("persist", "yes");
		SetVariableResponderParser.applyFromAttributes(r3, c);
		assertFalse(r3.isPersist());
	}

	@Test
	public void sidecarOmitsSessionOnlyAndDeviceNames() {
		SessionVariableSidecar side = new SessionVariableSidecar();
		side.remember("kills", "17");
		side.remember("device.battery", "74");
		side.remember("fighting", null);
		Map<String, String> snap = side.snapshot();
		assertEquals("17", snap.get("kills"));
		assertFalse(snap.containsKey("device.battery"));
		assertFalse(snap.containsKey("fighting"));

		LinkedHashMap<String, String> encoded = new LinkedHashMap<String, String>();
		encoded.put("kills", "17");
		encoded.put("device.headphones", "yes");
		encoded.put("session", "1");
		String json = SessionVariableSidecar.toJson(encoded);
		assertTrue(json.contains("kills"));
		assertFalse(json.contains("device.headphones"));
		LinkedHashMap<String, String> loaded = new LinkedHashMap<String, String>();
		SessionVariableSidecar.mergeFromJson(loaded,
				"{\"kills\":\"17\",\"device.battery\":\"12\",\"seen\":\", Bob\"}");
		assertEquals("17", loaded.get("kills"));
		assertEquals(", Bob", loaded.get("seen"));
		assertFalse(loaded.containsKey("device.battery"));
	}

	@Test
	public void restoreIntoSkipsDeviceKeysAlreadyInTheMap() {
		SessionVariableSidecar side = new SessionVariableSidecar();
		side.remember("kills", "17");
		SessionVariableStore store = new SessionVariableStore();
		side.restoreInto(store);
		assertEquals("17", store.get("kills"));
		assertEquals("worlda.vars.json",
				SessionVariableSidecar.fileNameForDisplay("world-a"));
	}

	@Test
	public void timerListenerCallsApplyFromAttributes() throws Exception {
		String src = readJava("BTLib/src/com/resurrection/blowtorch2/lib/timer/TimerResponderListeners.java");
		assertTrue("timers have a second XML parser; mode/persist must go through applyFromAttributes",
				src.contains("SetVariableResponderParser")
						&& src.contains("applyFromAttributes"));
	}

	@Test
	public void parcelWritesModeThenPersistAfterFireType() throws Exception {
		String src = readJava(
				"BTLib/src/com/resurrection/blowtorch2/lib/responder/setvariable/SetVariableResponder.java");
		int write = src.indexOf("public void writeToParcel");
		int read = src.indexOf("public void readFromParcel");
		assertTrue(write > 0 && read > 0);
		String writeBody = src.substring(write, src.indexOf("public String getVariableName"));
		assertTrue(writeBody.contains("out.writeString(variableName)"));
		assertTrue(writeBody.contains("out.writeString(variableValue)"));
		assertTrue(writeBody.contains("out.writeString(getFireType().getString())"));
		assertTrue(writeBody.contains("out.writeString(SetVariableApply.normalizeMode(mode))"));
		assertTrue(writeBody.contains("out.writeInt(persist ? 1 : 0)"));
		String readBody = src.substring(read, write);
		assertTrue(readBody.contains("setMode(in.readString())"));
		assertTrue(readBody.contains("setPersist(in.readInt() != 0)"));
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
