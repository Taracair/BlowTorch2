package com.resurrection.blowtorch2.lib.trigger;

import java.io.IOException;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponderParser;
import com.resurrection.blowtorch2.lib.responder.color.ColorActionParser;
import com.resurrection.blowtorch2.lib.responder.gag.GagActionParser;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponderParser;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceParser;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponderParser;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponderParser;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponderParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionParser;

import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.StartElementListener;

public final class TriggerParser {
	public static void registerListeners(Element root, PluginParser.NewItemCallback callback, TriggerData current_trigger, TimerData current_timer) {
		Element trigger = root.getChild(BasePluginParser.TAG_TRIGGER);
		TriggerElementListener listener = new TriggerElementListener(callback, current_trigger);

		trigger.setElementListener(listener);

		ConditionParser.registerListeners(trigger, current_trigger);
		AckResponderParser.registerListeners(trigger, current_trigger, current_timer, current_trigger);
		ToastResponderParser.registerListeners(trigger, current_trigger, current_trigger, current_timer);
		NotificationResponderParser.registerListeners(trigger, current_trigger, current_trigger, current_timer);
		ScriptResponderParser.registerListeners(trigger, current_trigger, current_trigger, current_timer);
		ReplaceParser.registerListeners(trigger, current_trigger);
		ColorActionParser.registerListeners(trigger, current_trigger);
		GagActionParser.registerListeners(trigger, current_trigger);
		SetVariableResponderParser.registerListeners(trigger, current_trigger, current_timer, current_trigger);
		
	}
	
	public static void saveTriggerToXML(XmlSerializer out,TriggerData trigger) throws IllegalArgumentException, IllegalStateException, IOException {
		if(trigger.isSave()) {
			out.startTag("", BasePluginParser.TAG_TRIGGER);
			out.attribute("", BasePluginParser.ATTR_TRIGGERTITLE, trigger.getName());
			out.attribute("", BasePluginParser.ATTR_TRIGGERPATTERN, trigger.getPattern());
			if(trigger.isInterpretAsRegex()) {
				out.attribute("", "regexp", trigger.isInterpretAsRegex() ? "true" : "false");
			}
			if(trigger.isFireOnce()) {
				out.attribute("", BasePluginParser.ATTR_TRIGGERONCE, trigger.isFireOnce() ? "true" : "false");
			}
			if(trigger.isHidden())  out.attribute("", BasePluginParser.ATTR_TRIGGERHIDDEN, "true");
			if(!trigger.isEnabled()) {
				out.attribute("", BasePluginParser.ATTR_TRIGGERENEABLED, trigger.isEnabled() ? "true" : "false");
			}
			if(trigger.getSequence() != TriggerData.DEFAULT_SEQUENCE) {
				out.attribute("", BasePluginParser.ATTR_SEQUENCE, Integer.toString(trigger.getSequence()));
			}
			if(!trigger.getGroup().equals(TriggerData.DEFAULT_GROUP)) out.attribute("", BasePluginParser.ATTR_GROUP, trigger.getGroup());
			
			//if(trigger.isKeepEvaluating()) {
				//out.attribute("", BasePluginParser.ATTR_KEEPEVALUATING, trigger.isKeepEvaluating() ? "true" : "false");
			//}
			
			ConditionParser.saveConditionsToXML(out, trigger);
			for(TriggerResponder r : trigger.getResponders()){
				r.saveResponderToXML(out);
			}
			//OutputResponders(out,trigger.getResponders());
			out.endTag("", BasePluginParser.TAG_TRIGGER);
		}
	}
	
	
}
