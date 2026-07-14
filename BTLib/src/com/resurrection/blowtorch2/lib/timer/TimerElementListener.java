package com.resurrection.blowtorch2.lib.timer;

import java.util.ArrayList;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;

import android.sax.Element;
import android.sax.ElementListener;
import android.sax.EndElementListener;
import android.sax.StartElementListener;

public class TimerElementListener implements ElementListener {

	PluginParser.NewItemCallback callback = null;
	TimerData current_timer = null;

	public TimerElementListener(PluginParser.NewItemCallback callback, TimerData current_timer) {
		this.callback = callback;
		this.current_timer = current_timer;
	}

	public void start(Attributes a) {
		current_timer.setName((a.getValue("", BasePluginParser.ATTR_TIMERNAME) == null) ? "" : a.getValue("", BasePluginParser.ATTR_TIMERNAME));
		current_timer.setOrdinal((a.getValue("", BasePluginParser.ATTR_ORDINAL) == null) ? 0 : Integer.parseInt(a.getValue("", BasePluginParser.ATTR_ORDINAL)));
		current_timer.setSeconds((a.getValue("", BasePluginParser.ATTR_SECONDS) == null) ? 30 : Integer.parseInt(a.getValue("", BasePluginParser.ATTR_SECONDS)));
		current_timer.setRemainingTime(current_timer.getSeconds());
		current_timer.setRepeat((a.getValue("", BasePluginParser.ATTR_REPEAT) == null) ? false : a.getValue("", BasePluginParser.ATTR_REPEAT).equals("true"));
		current_timer.setPlaying((a.getValue("", BasePluginParser.ATTR_PLAYING) == null) ? false : a.getValue("", BasePluginParser.ATTR_PLAYING).equals("true"));
		current_timer.setResponders(new ArrayList<TriggerResponder>());
	}

	public void end() {
		callback.addTimer(current_timer.getName(), current_timer.copy());
	}

	/** HyperSAXParser-compatible split listeners for nested child responder tags. */
	public static void register(Element timer, PluginParser.NewItemCallback callback, TimerData current_timer) {
		final TimerElementListener listener = new TimerElementListener(callback, current_timer);
		timer.setStartElementListener(new StartElementListener() {
			@Override
			public void start(Attributes attributes) {
				listener.start(attributes);
			}
		});
		timer.setEndElementListener(new EndElementListener() {
			@Override
			public void end() {
				listener.end();
			}
		});
		TimerResponderListeners.register(timer, current_timer);
	}
}
