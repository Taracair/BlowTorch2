package com.resurrection.blowtorch2.lib.timer;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionParser;

import android.sax.Element;

public final class TimerParser {
	public static void registerListeners(Element root, PluginParser.NewItemCallback callback, TriggerData current_trigger, TimerData current_timer) {
		Element timer = root.getChild(BasePluginParser.TAG_TIMER);
		TimerElementListener.register(timer, callback, current_timer);
		ConditionParser.registerListeners(timer, current_timer);
	}

	public static void saveTimerToXML(XmlSerializer out, TimerData timer) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_TIMER);
		out.attribute("", BasePluginParser.ATTR_TIMERNAME, timer.getName());
		out.attribute("", BasePluginParser.ATTR_SECONDS, timer.getSeconds().toString());
		out.attribute("", BasePluginParser.ATTR_REPEAT, (timer.isRepeat()) ? "true" : "false");
		out.attribute("", BasePluginParser.ATTR_PLAYING, (timer.isPlaying()) ? "true" : "false");
		if (timer.getGroup() != null
				&& !TimerData.DEFAULT_GROUP.equals(timer.getGroup())) {
			out.attribute("", BasePluginParser.ATTR_GROUP, timer.getGroup());
		}
		ConditionParser.saveConditionsToXML(out, timer);
		for (TriggerResponder r : timer.getResponders()) {
			r.saveResponderToXML(out);
		}
		out.endTag("", BasePluginParser.TAG_TIMER);
	}
}
