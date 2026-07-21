package com.resurrection.blowtorch2.lib.responder.setvariable;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.StartElementListener;

public class SetVariableElementListener implements StartElementListener {

	TriggerData currentTrigger;
	TimerData currentTimer;
	Object selector;

	public SetVariableElementListener(Object selector, TriggerData currentTrigger,
			TimerData currentTimer) {
		this.selector = selector;
		this.currentTimer = currentTimer;
		this.currentTrigger = currentTrigger;
	}

	public void start(Attributes a) {
		SetVariableResponder r = new SetVariableResponder();
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

		if (selector instanceof TriggerData) {
			currentTrigger.getResponders().add(r.copy());
		} else if (selector instanceof TimerData) {
			currentTimer.getResponders().add(r.copy());
		}
	}
}
