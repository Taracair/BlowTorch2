package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.util.TriggerSounds;

/**
 * {@code .sound} — which volume a trigger's sound uses, and whether to say so
 * when nobody can hear it.
 *
 * <p>Exists because of a measured failure rather than a guess: the first build
 * played on the notification stream, the maintainer's ringer was muted, and the
 * result was a feature that did nothing with nothing anywhere saying why. A
 * game does not belong on the ringer's volume, and if it is put there anyway
 * the app should say so.
 */
public class SoundCommand extends SpecialCommand {

	public static final String STREAM_KEY = "trigger_sound_stream";
	public static final String WARN_KEY = "trigger_sound_warn_silent";

	public SoundCommand() {
		this.commandName = "sound";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.startsWith("stream")) {
			return setStream(arg.substring("stream".length()).trim(), c);
		}
		if (arg.startsWith("warn")) {
			return setWarn(arg.substring("warn".length()).trim(), c);
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nTrigger sounds play on the "
					+ describeStream(stream(c)) + " volume"
					+ (stream(c) == TriggerSounds.STREAM_NOTIFICATION
						? " — which follows the ringer, so silencing the phone"
							+ " silences your triggers" : "")
					+ ".\nWhen that volume is off you are "
					+ (warnOn(c) ? "told" : "not told")
					+ ".\nUse .sound stream media|notification|alarm,"
					+ " .sound warn on|off\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Sound usage:",
				".sound stream media        — the game and video volume (default)\n"
				+ ".sound stream notification — follows the ringer; silent phone,\n"
				+ "                             silent triggers\n"
				+ ".sound stream alarm        — loudest, usually survives Do Not Disturb\n"
				+ ".sound warn on|off         — say when that volume is turned off\n"
				+ ".sound                     — what it is set to now\n"));
		return null;
	}

	private Object setStream(String arg, Connection c) {
		int picked;
		if (arg.equals("media") || arg.equals("music") || arg.equals("game")) {
			picked = TriggerSounds.STREAM_MEDIA;
		} else if (arg.equals("notification") || arg.equals("notify")
				|| arg.equals("ringer")) {
			picked = TriggerSounds.STREAM_NOTIFICATION;
		} else if (arg.equals("alarm")) {
			picked = TriggerSounds.STREAM_ALARM;
		} else {
			c.sendDataToWindow(getErrorMessage("Sound usage:",
					".sound stream media|notification|alarm\n"));
			return null;
		}
		c.updateIntegerSetting(STREAM_KEY, picked);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Trigger sounds now play on the " + describeStream(picked)
				+ " volume."
				+ (picked == TriggerSounds.STREAM_NOTIFICATION
					? " Remember that this one follows the ringer: with the phone"
						+ " silenced your triggers are silent too." : "")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object setWarn(String arg, Connection c) {
		if (!arg.equals("on") && !arg.equals("off")) {
			c.sendDataToWindow(getErrorMessage("Sound usage:", "Use on or off.\n"));
			return null;
		}
		boolean on = arg.equals("on");
		c.updateBooleanSetting(WARN_KEY, on);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ (on
					? "You will be told when a trigger plays a sound into a volume"
						+ " that is turned off. At most once every half minute."
					: "No more warnings about a silent volume.")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private static String describeStream(int stream) {
		if (stream == TriggerSounds.STREAM_NOTIFICATION) {
			return "notification";
		}
		if (stream == TriggerSounds.STREAM_ALARM) {
			return "alarm";
		}
		return "media";
	}

	private static int stream(Connection c) {
		BaseOption o = findOption(c, STREAM_KEY);
		if (o instanceof ListOption && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return TriggerSounds.DEFAULT_STREAM;
	}

	private static boolean warnOn(Connection c) {
		BaseOption o = findOption(c, WARN_KEY);
		if (o instanceof BooleanOption && o.getValue() instanceof Boolean) {
			return ((Boolean) o.getValue()).booleanValue();
		}
		return true;
	}

	private static BaseOption findOption(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		Object o = c.getSettings().findOptionByKey(key);
		return o instanceof BaseOption ? (BaseOption) o : null;
	}
}
