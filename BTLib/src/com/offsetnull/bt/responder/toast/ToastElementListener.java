package com.offsetnull.bt.responder.toast;

import org.xml.sax.Attributes;

import android.sax.StartElementListener;

import com.offsetnull.bt.responder.TriggerResponder;
import com.offsetnull.bt.service.plugin.settings.BasePluginParser;
import com.offsetnull.bt.service.plugin.settings.PluginSettings;
import com.offsetnull.bt.timer.TimerData;
import com.offsetnull.bt.trigger.TriggerData;

public class ToastElementListener implements StartElementListener {
	//PluginSettings settings = null;
	TriggerData current_trigger = null;
	TimerData current_timer = null;
	Object selector = null;
	
	public ToastElementListener(Object selector,TriggerData current_trigger,TimerData current_timer) {
		//this.settings = settings;
		this.current_trigger = current_trigger;
		this.selector = selector;
		this.current_timer = current_timer;
	}

	public void start(Attributes attributes) {
		ToastResponder r = new ToastResponder();
		r.setMessage(attributes.getValue("", BasePluginParser.ATTR_TOASTMESSAGE));
		String delayStr = attributes.getValue("", BasePluginParser.ATTR_TOASTDELAY);
		r.setDelay((delayStr == null) ? 2 : Integer.parseInt(delayStr));
		String fireType = attributes.getValue("", BasePluginParser.ATTR_FIRETYPE);
		if (fireType == null) fireType = "";
		if (fireType.equals(TriggerResponder.FIRE_WINDOW_OPEN)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(TriggerResponder.FIRE_WINDOW_CLOSED)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(TriggerResponder.FIRE_ALWAYS)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
		} else if (fireType.equals(TriggerResponder.FIRE_NEVER)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_NEVER);
		} else {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
		}

		if (selector instanceof TriggerData) {
			current_trigger.getResponders().add(r.copy());
		}
		if (selector instanceof TimerData) {
			current_timer.getResponders().add(r.copy());
		}
	}
}
