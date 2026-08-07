package com.resurrection.blowtorch2.lib.responder.speak;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public class SpeakResponderParser {

	public static void registerListeners(Element root, Object obj,
			TriggerData current_trigger, TimerData current_timer) {
		Element speak = root.getChild(BasePluginParser.TAG_SPEAKRESPONDER);
		speak.setStartElementListener(
				new SpeakElementListener(obj, current_trigger, current_timer));
	}

	public static void saveSpeakResponderToXML(XmlSerializer out, SpeakResponder r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_SPEAKRESPONDER);
		out.attribute("", BasePluginParser.ATTR_SPEAKMESSAGE, r.getMessage());
		out.attribute("", BasePluginParser.ATTR_SPEAKINTERRUPT,
				Boolean.toString(r.getInterrupt()));
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("", BasePluginParser.TAG_SPEAKRESPONDER);
	}
}
