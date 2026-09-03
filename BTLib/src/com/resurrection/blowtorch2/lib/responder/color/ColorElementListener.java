package com.resurrection.blowtorch2.lib.responder.color;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.StartElementListener;
import android.sax.TextElementListener;

public class ColorElementListener implements TextElementListener{

	//PluginSettings settings = null;
	TriggerData current_trigger = null;
	//TimerData current_timer = null;
	//Object selector = null;
	
	public ColorElementListener(TriggerData current_trigger) {
		//this.settings = settings;
		//this.selector = selector;
		//this.current_timer = current_timer;
		this.current_trigger = current_trigger;
	}
	
	public void start(Attributes a) {
		ColorAction tmp = new ColorAction();
		tmp.setPaint(TriggerColorPaint.fromXml(
				a.getValue("", "text"),
				a.getValue("", "textMode"),
				a.getValue("", "background"),
				a.getValue("", "backgroundMode"),
				xmlTrue(a, "bold"),
				xmlTrue(a, "faint"),
				xmlTrue(a, "italic"),
				xmlTrue(a, "underline"),
				xmlTrue(a, "reverse"),
				xmlTrue(a, "strike")));
		current_trigger.getResponders().add(tmp.copy());
	}

	private static boolean xmlTrue(Attributes a, String name) {
		String v = a.getValue("", name);
		return v != null && ("true".equalsIgnoreCase(v) || "1".equals(v));
	}

	public void end(String body) {
		
	
	}

}
