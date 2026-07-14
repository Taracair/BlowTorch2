package com.resurrection.blowtorch2.lib.responder.toast;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;
import android.sax.StartElementListener;

public class ToastResponderParser {
	public static void registerListeners(Element root,Object obj,TriggerData current_trigger,TimerData current_timer) {
		Element toast = root.getChild(BasePluginParser.TAG_TOASTRESPONDER);
		toast.setStartElementListener(new ToastElementListener(obj,current_trigger,current_timer));
	}
	
	public static void saveToastResponderToXML(XmlSerializer out,ToastResponder r) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_TOASTRESPONDER);
		out.attribute("", BasePluginParser.ATTR_TOASTMESSAGE, r.getMessage());
		out.attribute("", BasePluginParser.ATTR_TOASTDELAY, new Integer(r.getDelay()).toString());
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("", BasePluginParser.TAG_TOASTRESPONDER);
	}
}
