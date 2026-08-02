package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionLeaf;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;

import android.os.Parcel;
import android.sax.Element;
import android.sax.RootElement;
import android.util.Xml;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Conditions must survive UI↔:stellar parcel and profile XML the same way
 * responders already do. Empty groups after round-trip made triggers always fire.
 */
@RunWith(AndroidJUnit4.class)
public class TriggerConditionPersistenceTest {

	@Test
	public void triggerConditionsSurviveParcelRoundTrip() {
		TriggerData original = new TriggerData();
		original.setName("_pager");
		original.setPattern("From your wristpad");
		original.setInterpretAsRegex(true);
		ToastResponder toast = new ToastResponder();
		toast.setMessage("Test");
		original.getResponders().add(toast);
		original.getConditions().getChildren().add(
				new ConditionLeaf(ConditionType.TRIGGER_ENABLED, "_cerb", "", ""));
		original.getConditions().getChildren().add(
				new ConditionLeaf(ConditionType.ALIAS_ENABLED, "kk", "", ""));

		Parcel parcel = Parcel.obtain();
		try {
			original.writeToParcel(parcel, 0);
			parcel.setDataPosition(0);
			TriggerData restored = TriggerData.CREATOR.createFromParcel(parcel);
			assertEquals("_pager", restored.getName());
			assertEquals(1, restored.getResponders().size());
			assertEquals(TriggerResponder.RESPONDER_TYPE.TOAST,
					restored.getResponders().get(0).getType());
			assertEquals(2, restored.getConditions().getChildren().size());
			ConditionLeaf t = restored.getConditions().getChildren().get(0);
			assertEquals(ConditionType.TRIGGER_ENABLED, t.getType());
			assertEquals("_cerb", t.getName());
			ConditionLeaf a = restored.getConditions().getChildren().get(1);
			assertEquals(ConditionType.ALIAS_ENABLED, a.getType());
			assertEquals("kk", a.getName());
		} finally {
			parcel.recycle();
		}
	}

	@Test
	public void triggerConditionsSurviveXmlRoundTrip() throws IOException, SAXException {
		TriggerData original = new TriggerData();
		original.setName("_pager");
		original.setPattern("From your wristpad");
		original.setInterpretAsRegex(true);
		original.setSave(true);
		ToastResponder toast = new ToastResponder();
		toast.setMessage("Test");
		original.getResponders().add(toast);
		original.getConditions().getChildren().add(
				new ConditionLeaf(ConditionType.TRIGGER_ENABLED, "_cerb", "", ""));
		original.getConditions().getChildren().add(
				new ConditionLeaf(ConditionType.ALIAS_DISABLED, "travel_home", "mapper", ""));

		String saved = serializeTrigger(original);
		assertTrue(saved.contains("<conditions"));
		assertTrue(saved.contains("triggerEnabled"));
		assertTrue(saved.contains("name=\"_cerb\""));
		assertTrue(saved.contains("aliasDisabled"));
		assertTrue(saved.contains("plugin=\"mapper\""));

		PluginSettings loaded = parseTriggers(saved);
		TriggerData again = loaded.getTriggers().get("_pager");
		assertNotNull(again);
		assertFalse(again.getConditions().isEmpty());
		assertEquals(2, again.getConditions().getChildren().size());
		assertEquals(ConditionType.TRIGGER_ENABLED,
				again.getConditions().getChildren().get(0).getType());
		assertEquals("_cerb", again.getConditions().getChildren().get(0).getName());
		assertEquals(ConditionType.ALIAS_DISABLED,
				again.getConditions().getChildren().get(1).getType());
		assertEquals("travel_home", again.getConditions().getChildren().get(1).getName());
		assertEquals("mapper", again.getConditions().getChildren().get(1).getPlugin());
	}

	private static PluginSettings parseTriggers(String xml) throws IOException, SAXException {
		final PluginSettings settings = new PluginSettings();
		final TriggerData currentTrigger = new TriggerData();
		final TimerData currentTimer = new TimerData();

		PluginParser.NewItemCallback callback = new PluginParser.NewItemCallback() {
			@Override
			public void addAlias(String key, AliasData a) {
			}

			@Override
			public void addTrigger(String key, TriggerData t) {
				settings.getTriggers().put(key, t);
			}

			@Override
			public void addTimer(String key, TimerData t) {
			}

			@Override
			public void addScript(String name, String body, boolean execute) {
			}

			@Override
			public void addWindow(String name, WindowToken w) {
			}
		};

		RootElement root = new RootElement("blowtorch");
		Element triggers = root.getChild("triggers");
		TriggerParser.registerListeners(triggers, callback, currentTrigger, currentTimer);
		Xml.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), Xml.Encoding.UTF_8,
				root.getContentHandler());
		return settings;
	}

	private static String serializeTrigger(TriggerData trigger) throws IOException {
		java.io.StringWriter writer = new java.io.StringWriter();
		org.xmlpull.v1.XmlSerializer out = android.util.Xml.newSerializer();
		out.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
		out.setOutput(writer);
		out.startDocument("UTF-8", true);
		out.startTag("", "blowtorch");
		out.attribute("", "xmlversion", "2");
		out.startTag("", "triggers");
		TriggerParser.saveTriggerToXML(out, trigger);
		out.endTag("", "triggers");
		out.endTag("", "blowtorch");
		out.endDocument();
		return writer.toString();
	}
}
