package com.resurrection.blowtorch2.lib.responder.setvariable;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public final class SetVariableResponderParser {

	static final String ATTR_MODE = "mode";
	static final String ATTR_PERSIST = "persist";

	private SetVariableResponderParser() {
	}

	public static void registerListeners(Element root, Object obj, TimerData currentTimer,
			TriggerData currentTrigger) {
		Element el = root.getChild(BasePluginParser.TAG_SETVARIABLE);
		el.setStartElementListener(new SetVariableElementListener(obj, currentTrigger, currentTimer));
	}

	public static void applyFromAttributes(SetVariableResponder r, Attributes a) {
		if (r == null || a == null) {
			return;
		}
		String name = a.getValue("", BasePluginParser.ATTR_NAME);
		r.setVariableName(name != null ? name : "");
		String value = a.getValue("", BasePluginParser.ATTR_CONDITION_VALUE);
		if (value == null) {
			value = a.getValue("", "value");
		}
		r.setVariableValue(value != null ? value : "");
		String fireType = a.getValue("", BasePluginParser.ATTR_FIRETYPE);
		if (fireType == null) {
			fireType = "";
		}
		if (fireType.equals(TriggerResponder.FIRE_WINDOW_OPEN)) {
			r.setFireType(FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(TriggerResponder.FIRE_WINDOW_CLOSED)) {
			r.setFireType(FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(TriggerResponder.FIRE_ALWAYS)) {
			r.setFireType(FIRE_WHEN.WINDOW_BOTH);
		} else if (fireType.equals(TriggerResponder.FIRE_NEVER)) {
			r.setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			r.setFireType(FIRE_WHEN.WINDOW_BOTH);
		}
		r.setMode(a.getValue("", ATTR_MODE));
		r.setPersist(SetVariableApply.parsePersist(a.getValue("", ATTR_PERSIST)));
	}

	public static void saveResponderToXML(XmlSerializer out, SetVariableResponder r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_SETVARIABLE);
		out.attribute("", BasePluginParser.ATTR_NAME, r.getVariableName());
		out.attribute("", BasePluginParser.ATTR_CONDITION_VALUE, r.getVariableValue());
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		if (!SetVariableApply.MODE_SET.equals(r.getMode())) {
			out.attribute("", ATTR_MODE, r.getMode());
		}
		if (r.isPersist()) {
			out.attribute("", ATTR_PERSIST, "true");
		}
		out.endTag("", BasePluginParser.TAG_SETVARIABLE);
	}
}
