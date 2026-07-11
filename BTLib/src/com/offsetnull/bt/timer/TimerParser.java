package com.offsetnull.bt.timer;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.offsetnull.bt.responder.TriggerResponder;
import com.offsetnull.bt.service.plugin.settings.BasePluginParser;
import com.offsetnull.bt.service.plugin.settings.PluginParser;
import com.offsetnull.bt.trigger.TriggerData;

import android.sax.Element;

public final class TimerParser {
	public static void registerListeners(Element root, PluginParser.NewItemCallback callback, TriggerData current_trigger, TimerData current_timer) {
		Element timer = root.getChild(BasePluginParser.TAG_TIMER);
		TimerElementListener.register(timer, callback, current_timer);
	}

	public static void saveTimerToXML(XmlSerializer out, TimerData timer) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_TIMER);
		out.attribute("", BasePluginParser.ATTR_TIMERNAME, timer.getName());
		out.attribute("", BasePluginParser.ATTR_SECONDS, timer.getSeconds().toString());
		out.attribute("", BasePluginParser.ATTR_REPEAT, (timer.isRepeat()) ? "true" : "false");
		out.attribute("", BasePluginParser.ATTR_PLAYING, (timer.isPlaying()) ? "true" : "false");
		for (TriggerResponder r : timer.getResponders()) {
			r.saveResponderToXML(out);
		}
		out.endTag("", BasePluginParser.TAG_TIMER);
	}
}
