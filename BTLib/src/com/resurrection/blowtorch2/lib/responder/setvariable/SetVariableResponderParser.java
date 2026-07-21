package com.resurrection.blowtorch2.lib.responder.setvariable;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public final class SetVariableResponderParser {

	private SetVariableResponderParser() {
	}

	public static void registerListeners(Element root, Object obj, TimerData currentTimer,
			TriggerData currentTrigger) {
		Element el = root.getChild(BasePluginParser.TAG_SETVARIABLE);
		el.setStartElementListener(new SetVariableElementListener(obj, currentTrigger, currentTimer));
	}

	public static void saveResponderToXML(XmlSerializer out, SetVariableResponder r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_SETVARIABLE);
		out.attribute("", BasePluginParser.ATTR_NAME, r.getVariableName());
		out.attribute("", BasePluginParser.ATTR_CONDITION_VALUE, r.getVariableValue());
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("", BasePluginParser.TAG_SETVARIABLE);
	}
}
