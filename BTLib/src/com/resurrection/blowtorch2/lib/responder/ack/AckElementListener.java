package com.resurrection.blowtorch2.lib.responder.ack;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.StartElementListener;

public class AckElementListener implements StartElementListener {

	//PluginSettings settings = null;
	//PluginParser.NewItemCallback callback = null;
	TriggerData current_trigger = null;
	TimerData current_timer = null;
	Object selector = null;
	
	public AckElementListener(Object selector,TriggerData current_trigger,TimerData current_timer) {
		//this.settings = settings;
		//this.callback = callback;
		this.selector = selector;
		this.current_timer = current_timer;
		this.current_trigger = current_trigger;
	}
	
	public void start(Attributes a) {
		AckResponder r = new AckResponder();
		r.setAckWith(a.getValue("", BasePluginParser.ATTR_ACKWITH));
		String fireType = a.getValue("",BasePluginParser.ATTR_FIRETYPE);
		if(fireType == null) fireType = "";
		//Log.e("PARSER","ACK TAG READ, FIRETYPE IS:" + fireType);
		if(fireType.equals(TriggerResponder.FIRE_WINDOW_OPEN)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(TriggerResponder.FIRE_WINDOW_CLOSED)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(TriggerResponder.FIRE_ALWAYS)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
		} else if (fireType.equals(TriggerResponder.FIRE_NEVER)) {
			r.setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
		}
		
		if(selector instanceof TriggerData) {
			current_trigger.getResponders().add(r.copy());
		} else if(selector instanceof TimerData) {
			current_timer.getResponders().add(r.copy());
		}
	
	}

}
