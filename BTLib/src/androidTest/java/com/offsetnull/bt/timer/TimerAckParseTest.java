package com.offsetnull.bt.timer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

import android.sax.Element;
import android.sax.RootElement;
import android.util.Xml;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class TimerAckParseTest {

	@Test
	public void timerAckResponderIsLoadedFromXml() throws IOException, SAXException {
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

		String xml = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
				+ "<blowtorch xmlversion=\"2\">"
				+ "<timers>"
				+ "<timer name=\"t1\" seconds=\"10\" repeat=\"true\" playing=\"false\">"
				+ "<ack with=\"say hi\" fireWhen=\"always\"/>"
				+ "</timer>"
				+ "</timers>"
				+ "</blowtorch>";

		Xml.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), Xml.Encoding.UTF_8, root.getContentHandler());

		TimerData loaded = settings.getTimers().get("t1");
		assertNotNull(loaded);
		assertEquals(1, loaded.getResponders().size());
		assertEquals(TriggerResponder.RESPONDER_TYPE.ACK, loaded.getResponders().get(0).getType());
		assertEquals("say hi", ((AckResponder) loaded.getResponders().get(0)).getAckWith());
	}
}
