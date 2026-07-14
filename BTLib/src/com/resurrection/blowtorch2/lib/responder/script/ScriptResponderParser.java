package com.resurrection.blowtorch2.lib.responder.script;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;
import android.sax.StartElementListener;

public class ScriptResponderParser {
	public static void registerListeners(Element root,Object obj,TriggerData current_trigger,TimerData current_timer) {
		Element script = root.getChild(BasePluginParser.TAG_SCRIPTRESPONDER);
		script.setStartElementListener(new ScriptElementListener(obj,current_trigger,current_timer));
	}
	
	public static void saveScriptResponderToXML(XmlSerializer out,ScriptResponder r) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_SCRIPTRESPONDER);
		out.attribute("", BasePluginParser.ATTR_FUNCTION, r.getFunction());
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("", BasePluginParser.TAG_SCRIPTRESPONDER);
	}
}
