package com.offsetnull.bt.timer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.offsetnull.bt.alias.AliasData;
import com.offsetnull.bt.responder.TriggerResponder;
import com.offsetnull.bt.responder.ack.AckResponder;
import com.offsetnull.bt.service.WindowToken;
import com.offsetnull.bt.service.plugin.settings.PluginParser;
import com.offsetnull.bt.service.plugin.settings.PluginSettings;
import com.offsetnull.bt.trigger.TriggerData;

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

@RunWith(AndroidJUnit4.class)
public class TimerAckPersistenceTest {

	@Test
	public void timerAckSurvivesParcelRoundTrip() {
		TimerData original = new TimerData();
		original.setName("t1");
		original.setSeconds(30);
		AckResponder ack = new AckResponder();
		ack.setAckWith("say hi");
		original.getResponders().add(ack);

		Parcel parcel = Parcel.obtain();
		try {
			original.writeToParcel(parcel, 0);
			parcel.setDataPosition(0);
			TimerData restored = TimerData.CREATOR.createFromParcel(parcel);
			assertEquals(1, restored.getResponders().size());
			assertEquals(TriggerResponder.RESPONDER_TYPE.ACK, restored.getResponders().get(0).getType());
			assertEquals("say hi", ((AckResponder) restored.getResponders().get(0)).getAckWith());
		} finally {
			parcel.recycle();
		}
	}

	@Test
	public void timerAckSurvivesXmlRoundTrip() throws IOException, SAXException {
		String xml = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
				+ "<blowtorch xmlversion=\"2\">"
				+ "<timers>"
				+ "<timer name=\"t1\" seconds=\"30\" repeat=\"true\" playing=\"false\">"
				+ "<ack with=\"say hi\" fireWhen=\"always\"/>"
				+ "</timer>"
				+ "</timers>"
				+ "</blowtorch>";

		PluginSettings settings = parseTimers(xml);
		TimerData loaded = settings.getTimers().get("t1");
		assertNotNull(loaded);
		assertEquals(1, loaded.getResponders().size());
		assertEquals("say hi", ((AckResponder) loaded.getResponders().get(0)).getAckWith());

		String saved = serializeTimer(loaded);
		assertTrue("saved XML must contain ack responder", saved.contains("with=\"say hi\""));

		PluginSettings reloaded = parseTimers(saved);
		TimerData again = reloaded.getTimers().get("t1");
		assertNotNull(again);
		assertEquals(1, again.getResponders().size());
		assertEquals("say hi", ((AckResponder) again.getResponders().get(0)).getAckWith());
	}

	private static PluginSettings parseTimers(String xml) throws IOException, SAXException {
		final PluginSettings settings = new PluginSettings();
		final TimerData currentTimer = new TimerData();
		final TriggerData currentTrigger = new TriggerData();

		PluginParser.NewItemCallback callback = new PluginParser.NewItemCallback() {
			@Override
			public void addAlias(String key, AliasData a) {
			}

			@Override
			public void addTrigger(String key, TriggerData t) {
			}

			@Override
			public void addTimer(String key, TimerData t) {
				settings.getTimers().put(key, t);
			}

			@Override
			public void addScript(String name, String body, boolean execute) {
			}

			@Override
			public void addWindow(String name, WindowToken w) {
			}
		};

		RootElement root = new RootElement("blowtorch");
		Element timers = root.getChild("timers");
		TimerParser.registerListeners(timers, callback, currentTrigger, currentTimer);
		Xml.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), Xml.Encoding.UTF_8, root.getContentHandler());
		return settings;
	}

	private static String serializeTimer(TimerData timer) throws IOException {
		java.io.StringWriter writer = new java.io.StringWriter();
		org.xmlpull.v1.XmlSerializer out = android.util.Xml.newSerializer();
		out.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
		out.setOutput(writer);
		out.startDocument("UTF-8", true);
		out.startTag("", "blowtorch");
		out.attribute("", "xmlversion", "2");
		out.startTag("", "timers");
		TimerParser.saveTimerToXML(out, timer);
		out.endTag("", "timers");
		out.endTag("", "blowtorch");
		out.endDocument();
		return writer.toString();
	}
}
