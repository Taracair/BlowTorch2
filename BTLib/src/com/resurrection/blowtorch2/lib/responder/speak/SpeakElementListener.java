package com.resurrection.blowtorch2.lib.responder.speak;

import org.xml.sax.Attributes;

import android.sax.StartElementListener;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

public class SpeakElementListener implements StartElementListener {

	TriggerData current_trigger = null;
	TimerData current_timer = null;
	Object selector = null;

	public SpeakElementListener(Object selector, TriggerData current_trigger,
			TimerData current_timer) {
		this.current_trigger = current_trigger;
		this.selector = selector;
		this.current_timer = current_timer;
	}

	public void start(Attributes attributes) {
		SpeakResponder r = new SpeakResponder();
		r.setMessage(attributes.getValue("", BasePluginParser.ATTR_SPEAKMESSAGE));
		r.setInterrupt(Boolean.parseBoolean(
				attributes.getValue("", BasePluginParser.ATTR_SPEAKINTERRUPT)));
		String warn = attributes.getValue("", BasePluginParser.ATTR_SOUNDWARN);
		r.setWarnWhenSilent(warn == null || !warn.equalsIgnoreCase("false"));
		String fireType = attributes.getValue("", BasePluginParser.ATTR_FIRETYPE);
		if (fireType == null) {
			fireType = "";
		}
		if (fireType.equals(TriggerResponder.FIRE_WINDOW_OPEN)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(TriggerResponder.FIRE_WINDOW_CLOSED)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(TriggerResponder.FIRE_NEVER)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_NEVER);
		} else {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
		}

		if (selector instanceof TriggerData) {
			current_trigger.getResponders().add(r.copy());
		} else if (selector instanceof TimerData) {
			current_timer.getResponders().add(r.copy());
		}
	}
}
