package com.resurrection.blowtorch2.lib.responder.replace;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;
import android.sax.TextElementListener;
import android.util.Log;

public final class ReplaceParser {
	public static void registerListeners(Element root,TriggerData current_trigger) {
		Element r = root.getChild(BasePluginParser.TAG_REPLACERESPONDER);
		r.setTextElementListener(new ReplaceElementListener(current_trigger));
	}

	public static void saveReplaceResponderToXML(XmlSerializer out,
			ReplaceResponder r) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_REPLACERESPONDER);
		
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		if(r.getRetarget() != null) {
			out.attribute("", BasePluginParser.ATTR_RETARGET, r.getRetarget());
			//out.attribute("", BasePluginParser.ATTR_DESTINATION, r.getWindowTarget());
		}
		out.text(r.getWith());
		
		out.endTag("", BasePluginParser.TAG_REPLACERESPONDER);
		
	}
}
