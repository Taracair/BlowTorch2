package com.resurrection.blowtorch2.lib.timer;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;

import android.sax.Element;
import android.sax.StartElementListener;

import java.math.BigInteger;

/**
 * Timer-specific XML listeners. Responders are always attached to the active
 * {@link TimerData} being parsed, without relying on instanceof selector checks
 * shared with trigger parsing.
 */
public final class TimerResponderListeners {
	private TimerResponderListeners() {
	}

	public static void register(Element timer, final TimerData currentTimer) {
		timer.getChild(BasePluginParser.TAG_ACKRESPONDER).setStartElementListener(new StartElementListener() {
			@Override
			public void start(Attributes a) {
				AckResponder r = new AckResponder();
				r.setAckWith(a.getValue("", BasePluginParser.ATTR_ACKWITH));
				r.setFireType(parseFireType(a.getValue("", BasePluginParser.ATTR_FIRETYPE)));
				currentTimer.getResponders().add(r.copy());
			}
		});

		timer.getChild(BasePluginParser.TAG_TOASTRESPONDER).setStartElementListener(new StartElementListener() {
			@Override
			public void start(Attributes a) {
				ToastResponder r = new ToastResponder();
				r.setMessage(a.getValue("", BasePluginParser.ATTR_TOASTMESSAGE));
				String delayStr = a.getValue("", BasePluginParser.ATTR_TOASTDELAY);
				r.setDelay((delayStr == null) ? 2 : Integer.parseInt(delayStr));
				r.setFireType(parseFireType(a.getValue("", BasePluginParser.ATTR_FIRETYPE)));
				currentTimer.getResponders().add(r.copy());
			}
		});

		timer.getChild(BasePluginParser.TAG_NOTIFICATIONRESPONDER).setStartElementListener(new StartElementListener() {
			@Override
			public void start(Attributes a) {
				NotificationResponder r = new NotificationResponder();
				r.setMessage(a.getValue("", BasePluginParser.ATTR_NOTIFICATIONMESSAGE));
				r.setTitle(a.getValue("", BasePluginParser.ATTR_NOTIFICATIONTITLE));
				r.setFireType(parseFireType(a.getValue("", BasePluginParser.ATTR_FIRETYPE)));

				String spawnnew = a.getValue("", BasePluginParser.ATTR_NEWNOTIFICATION);
				r.setSpawnNewNotification("true".equals(spawnnew));

				String useongoing = a.getValue("", BasePluginParser.ATTR_USEONGOING);
				r.setUseOnGoingNotification("true".equals(useongoing));

				String usedefaultlight = a.getValue("", BasePluginParser.ATTR_USEDEFAULTLIGHT);
				if (usedefaultlight == null) {
					usedefaultlight = "false";
				}
				if ("true".equals(usedefaultlight)) {
					r.setUseDefaultLight(true);
					String color = a.getValue("", BasePluginParser.ATTR_LIGHTCOLOR);
					r.setColorToUse((color == null) ? 0xFFFF0000 : new BigInteger(color, 16).intValue());
				} else {
					r.setUseDefaultLight(false);
				}

				String usedefaultvibrate = a.getValue("", BasePluginParser.ATTR_USEDEFAULTVIBRATE);
				if (usedefaultvibrate == null) {
					usedefaultvibrate = "false";
				}
				if ("true".equals(usedefaultvibrate)) {
					r.setUseDefaultVibrate(true);
					String vibrate = a.getValue("", BasePluginParser.ATTR_VIBRATELENGTH);
					r.setVibrateLength((vibrate == null) ? 0 : Integer.parseInt(vibrate));
				} else {
					r.setUseDefaultVibrate(false);
				}

				String usedefaultsound = a.getValue("", BasePluginParser.ATTR_USEDEFAULTSOUND);
				if (usedefaultsound == null) {
					usedefaultsound = "false";
				}
				if ("true".equals(usedefaultsound)) {
					r.setUseDefaultSound(true);
					r.setSoundPath(a.getValue("", BasePluginParser.ATTR_SOUNDPATH));
				} else {
					r.setUseDefaultSound(false);
				}

				currentTimer.getResponders().add(r.copy());
			}
		});

		timer.getChild(BasePluginParser.TAG_SCRIPTRESPONDER).setStartElementListener(new StartElementListener() {
			@Override
			public void start(Attributes a) {
				ScriptResponder r = new ScriptResponder();
				r.setFunction(a.getValue("", BasePluginParser.ATTR_FUNCTION));
				r.setFireType(parseFireType(a.getValue("", BasePluginParser.ATTR_FIRETYPE)));
				currentTimer.getResponders().add(r.copy());
			}
		});
	}

	private static FIRE_WHEN parseFireType(String fireType) {
		if (fireType == null) {
			fireType = "";
		}
		if (fireType.equals(TriggerResponder.FIRE_WINDOW_OPEN)) {
			return FIRE_WHEN.WINDOW_OPEN;
		}
		if (fireType.equals(TriggerResponder.FIRE_WINDOW_CLOSED)) {
			return FIRE_WHEN.WINDOW_CLOSED;
		}
		if (fireType.equals(TriggerResponder.FIRE_ALWAYS)) {
			return FIRE_WHEN.WINDOW_BOTH;
		}
		if (fireType.equals(TriggerResponder.FIRE_NEVER)) {
			return FIRE_WHEN.WINDOW_NEVER;
		}
		return FIRE_WHEN.WINDOW_BOTH;
	}
}
