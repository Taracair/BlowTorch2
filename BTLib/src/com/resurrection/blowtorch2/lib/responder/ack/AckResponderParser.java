package com.resurrection.blowtorch2.lib.responder.ack;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;
import android.sax.StartElementListener;

public final class AckResponderParser {
	public static void registerListeners(Element root,Object obj,TimerData current_timer,TriggerData current_trigger) {
		Element ack = root.getChild(BasePluginParser.TAG_ACKRESPONDER);
		ack.setStartElementListener(new AckElementListener(obj,current_trigger,current_timer));
	}
	
	public static void saveResponderToXML(XmlSerializer out,AckResponder r) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_ACKRESPONDER);
		out.attribute("", BasePluginParser.ATTR_ACKWITH, r.getAckWith());
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("",BasePluginParser.TAG_ACKRESPONDER);
	}
}
