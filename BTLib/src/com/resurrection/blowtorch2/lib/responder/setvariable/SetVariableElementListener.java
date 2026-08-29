package com.resurrection.blowtorch2.lib.responder.setvariable;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.alias.AliasData;
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
		SetVariableResponderParser.applyFromAttributes(r, a);

		if (selector instanceof TriggerData) {
			currentTrigger.getResponders().add(r.copy());
		} else if (selector instanceof TimerData) {
			currentTimer.getResponders().add(r.copy());
		} else if (selector instanceof AliasData) {
			((AliasData) selector).getSetVariables().add(r.copy());
		}
	}
}
