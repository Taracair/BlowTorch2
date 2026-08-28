package com.resurrection.blowtorch2.lib.service.function;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * {@code .sensor} — what hardware this phone has, and what to do with its readings.
 *
 * <pre>
 * .sensor                    what is set up on this phone
 * .sensor caps               which sensor provides each reading here
 * .sensor wave look          make a reading send a command
 * .sensor wave               what that reading does now
 * .sensor wave on|off
 * .sensor fire wave          try it now, without moving the phone
 * </pre>
 *
 * <p><b>Not button gestures.</b> Swipes and holds on the input bar and chrome
 * are configured in the button editor. This command is about the phone's own
 * hardware — proximity, motion, light, charging, headphones — as triggers,
 * conditions and timers already understand them.
 *
 * <p><b>Why {@code .sensor wave look} looks like an alias.</b> Setting an alias
 * is {@code .name text} and has been for years; pointing a sensor reading at a
 * command is the same kind of thing, so it is set the same way from the input
 * bar. What it writes is an ordinary trigger, so scripts, sounds, speech and
 * conditions sit alongside the command.
 *
 * <p><b>The trap this inherits.</b> {@code Connection.processCommand} looks up
 * the player's aliases <em>before</em> the built-in commands, so an alias named
 * {@code sensor} would hide this whole command with no message at all. Nothing
 * can be done about that here; it is why the manual says so out loud.
 */
public class SensorCommand extends SpecialCommand {

	public SensorCommand() {
		this.commandName = "sensor";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		if (arg.length() == 0 || arg.equalsIgnoreCase("list")) {
			c.sendDataToWindow(overview(c));
			return null;
		}
		String[] parts = arg.split("\\s+", 2);
		String head = parts[0].toLowerCase(Locale.US);
		String rest = parts.length > 1 ? parts[1].trim() : "";

		if (head.equals("caps") || head.equals("sensors")) {
			c.sendDataToWindow(GestureAvailability.report(c.getContext()));
			return null;
		}
		if (head.equals("help") || head.equals("?")) {
			c.sendDataToWindow(usage());
			return null;
		}
		if (head.equals("examples") || head.equals("why")) {
			c.sendDataToWindow(examples());
			return null;
		}
		if (head.equals("threshold") || head.equals("calibrate")) {
			String[] bits = rest.split("\\s+");
			if (bits.length >= 3 && bits[0].equalsIgnoreCase("light")) {
				try {
					com.resurrection.blowtorch2.lib.service.sensor.GestureTuning
							.setLightThresholds(c.getContext(), Float.parseFloat(bits[1]),
									Float.parseFloat(bits[2]));
					// Not refreshDeviceGestures: nothing about which gestures are
					// wanted has changed, so that call returns early and the new
					// number would sit in storage unused.
					c.retuneDeviceSensors();
					c.sendDataToWindow("\nDark is now at or under " + bits[1]
							+ " lux, bright at or over " + bits[2] + ".\n"
							+ "Kept with this phone, not with the world profile.\n");
				} catch (NumberFormatException bad) {
					c.sendDataToWindow(getErrorMessage("Sensor usage",
							"Two numbers are needed: .sensor threshold light 40 900"));
				}
				return null;
			}
			if (bits.length < 2 || !bits[0].equalsIgnoreCase("shake")) {
				c.sendDataToWindow("\nTwo things have thresholds, and both are easier to"
						+ " measure than to guess:\nOptions \u2192 Device \u2192"
						+ " Calibrate shake, and Calibrate light.\nBy hand:"
						+ " .sensor threshold shake 14.5  |  .sensor threshold light 40 900"
						+ "\nNow: " + String.format(Locale.US, "%.1f",
							com.resurrection.blowtorch2.lib.service.sensor.GestureTuning
								.shakeThreshold(c.getContext())) + " m/s2, dark under "
						+ String.format(Locale.US, "%.0f",
							com.resurrection.blowtorch2.lib.service.sensor.GestureTuning
								.darkBelow(c.getContext())) + " lux, bright over "
						+ String.format(Locale.US, "%.0f",
							com.resurrection.blowtorch2.lib.service.sensor.GestureTuning
								.brightAbove(c.getContext())) + " lux\n");
				return null;
			}
			try {
				float stored = com.resurrection.blowtorch2.lib.service.sensor.GestureTuning
						.setShakeThreshold(c.getContext(), Float.parseFloat(bits[1]));
				// Registration reads the value, so bounce the sensor rather than
				// leaving the old number live until something else happens.
				c.retuneDeviceSensors();
				c.sendDataToWindow(String.format(Locale.US,
						"\nShake threshold is now %.1f m/s2 on this phone.\n"
						+ "It stays with the phone, not with the world profile.\n", stored));
			} catch (NumberFormatException bad) {
				c.sendDataToWindow(getErrorMessage("Sensor usage",
						"\"" + bits[1] + "\" is not a number."));
			}
			return null;
		}
		if (head.equals("watch") || head.equals("state")) {
			if (rest.equalsIgnoreCase("on") || rest.equalsIgnoreCase("off")) {
				boolean on = rest.equalsIgnoreCase("on");
				c.setDeviceStateVariables(on);
				c.sendDataToWindow("\nDevice state variables are " + (on ? "on" : "off")
						+ ".\n" + (on ? "device.facing, device.screen,"
							+ " device.headphones, device.charging, device.battery and"
							+ " device.covered\nare now kept up to date. .sensor state"
							+ " shows them.\n"
							: "Conditions testing device.* are now false, so triggers"
							+ " gated on the phone\nwill not fire.\n"));
				return null;
			}
			c.sendDataToWindow(c.deviceStateReport());
			return null;
		}
		if (head.equals("fire") || head.equals("test")) {
			if (rest.length() == 0) {
				c.sendDataToWindow(getErrorMessage("Sensor usage",
						"Which reading? .sensor fire wave"));
				return null;
			}
			c.sendDataToWindow(c.fireDeviceGestureAndReport(
					rest.split("\\s+", 2)[0].toLowerCase(Locale.US)));
			return null;
		}

		Gesture g = GestureCatalog.byId(head);
		if (g == null) {
			c.sendDataToWindow(getErrorMessage("Sensor usage",
					"There is no sensor reading called \"" + head + "\".\n" + usage()));
			return null;
		}
		if (rest.length() == 0) {
			c.sendDataToWindow(describe(c, g));
			return null;
		}
		if (rest.equalsIgnoreCase("on") || rest.equalsIgnoreCase("off")) {
			return setEnabled(c, g, rest.equalsIgnoreCase("on"));
		}
		return setCommand(c, g, rest);
	}

	/**
	 * Point a gesture at a command.
	 *
	 * <p>Only the first "send a command" action is touched. A gesture the player
	 * has since given a script or a sound keeps them, and is told so — quietly
	 * throwing away work done in the editor is the kind of thing this project
	 * has already had to apologise for once.
	 */
	private Object setCommand(final Connection c, final Gesture g, final String command) {
		// More than one trigger can answer the same gesture, and all of them run.
		// Guessing which one the player meant would edit something they cannot
		// see from the input bar, so this stops and points at the screen that
		// shows all of them.
		if (countTriggers(c, g) > 1) {
			c.sendDataToWindow("\n" + countTriggers(c, g) + " triggers answer "
					+ g.getId() + ", and all of them run. Which one did you mean?\n"
					+ "Open Options \u2192 Device \u2192 Sensors to see them.\n");
			return null;
		}
		TriggerData existing = findTrigger(c, g);
		boolean created = false;
		TriggerData target;
		if (existing == null) {
			target = new TriggerData();
			target.setName(g.getId());
			target.setPattern(g.getPattern());
			target.setInterpretAsRegex(false);
			target.setEnabled(true);
			target.setSave(true);
			created = true;
		} else {
			target = existing.copy();
		}
		int others = 0;
		AckResponder ack = null;
		for (TriggerResponder r : target.getResponders()) {
			if (ack == null && r instanceof AckResponder) {
				ack = (AckResponder) r;
			} else {
				others++;
			}
		}
		if (ack == null) {
			ack = new AckResponder();
			// WINDOW_BOTH is "always": a gesture made with the app in the
			// background is exactly when sending a command matters most.
			ack.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
			target.getResponders().add(ack);
		}
		ack.setAckWith(command);

		if (created) {
			c.addTrigger(target);
		} else {
			c.updateTrigger(existing, target);
		}
		// No refresh call here: addTrigger and updateTrigger both rebuild the
		// trigger system, and that is where the sensor is picked up.
		c.saveMainSettings();

		StringBuilder out = new StringBuilder();
		out.append('\n').append(Colorizer.getBrightCyanColor());
		out.append('[').append(g.getId()).append(" => ").append(command).append(']');
		if (others > 0) {
			out.append(" (").append(others)
				.append(others == 1 ? " other action kept" : " other actions kept").append(')');
		}
		out.append(Colorizer.getWhiteColor()).append('\n');
		out.append(availabilityLine(c, g));
		return sendAndReturn(c, out.toString());
	}

	private Object setEnabled(final Connection c, final Gesture g, final boolean on) {
		TriggerData existing = findTrigger(c, g);
		if (existing == null) {
			c.sendDataToWindow("\nNothing is set up for " + g.getId()
					+ " yet. Give it something to do first:\n.sensor "
					+ g.getId() + " <command>\n");
			return null;
		}
		TriggerData updated = existing.copy();
		updated.setEnabled(on);
		c.updateTrigger(existing, updated);
		c.saveMainSettings();
		c.sendDataToWindow("\n" + g.getId() + " is " + (on ? "on" : "off") + ".\n");
		return null;
	}

	private String describe(final Connection c, final Gesture g) {
		TriggerData t = findTrigger(c, g);
		StringBuilder out = new StringBuilder();
		out.append('\n').append(g.getLabel()).append('\n');
		out.append("  ").append(g.getHelp()).append('\n');
		out.append(availabilityLine(c, g));
		if (t == null) {
			out.append("  nothing set up — .sensor ").append(g.getId())
				.append(" <command>\n");
			return out.toString();
		}
		int count = countTriggers(c, g);
		if (count > 1) {
			out.append("  ").append(count).append(" triggers answer this, and all of")
				.append(" them run.\n  Options \u2192 Device \u2192 Sensors shows them.\n");
		}
		out.append("  ").append(t.isEnabled() ? "on" : "off").append(", ")
			.append(describeActions(t)).append('\n');
		return out.toString();
	}

	private String overview(final Connection c) {
		StringBuilder out = new StringBuilder();
		out.append("\n--- sensors on this phone ---\n");
		for (Gesture g : GestureCatalog.all()) {
			GestureAvailability.Resolution r =
					GestureAvailability.resolve(c.getContext(), g);
			TriggerData t = findTrigger(c, g);
			out.append(String.format(Locale.US, "  %-6s %-10s %s%n",
					g.getId(),
					r.isAvailable() ? (t == null ? "unused"
							: (t.isEnabled() ? "on" : "off")) : "unavailable",
					t != null ? describeActions(t) : g.getLabel().toLowerCase(Locale.US)));
		}
		out.append("\n.sensor wave look     point a reading at a command\n");
		out.append(".sensor fire wave     try it without moving the phone\n");
		out.append(".sensor caps          which hardware does what on this phone\n");
		out.append("\nEach reading is an ordinary trigger, so it can also run a script,\n");
		out.append("play a sound, speak, or gate on a condition. The whole list with\n");
		out.append("a screen of its own is in Options \u2192 Device \u2192 Sensors.\n");
		return out.toString();
	}

	private String availabilityLine(final Connection c, final Gesture g) {
		GestureAvailability.Resolution r = GestureAvailability.resolve(c.getContext(), g);
		return "  sensor: " + r.describe() + "\n";
	}

	private String describeActions(final TriggerData t) {
		List<TriggerResponder> responders = t.getResponders();
		if (responders == null || responders.isEmpty()) {
			return "no actions";
		}
		String command = null;
		int others = 0;
		for (TriggerResponder r : responders) {
			if (command == null && r instanceof AckResponder) {
				command = ((AckResponder) r).getAckWith();
			} else {
				others++;
			}
		}
		StringBuilder out = new StringBuilder();
		if (command != null && command.length() > 0) {
			out.append("sends \"").append(command).append('"');
		} else {
			out.append("no command");
		}
		if (others > 0) {
			out.append(" + ").append(others)
				.append(others == 1 ? " other action" : " other actions");
		}
		return out.toString();
	}

	/** How many triggers answer this gesture. More than one is allowed. */
	private int countTriggers(final Connection c, final Gesture g) {
		HashMap<String, TriggerData> triggers = c.getTriggers();
		if (triggers == null) {
			return 0;
		}
		int found = 0;
		for (TriggerData t : triggers.values()) {
			if (t != null && !t.isInterpretAsRegex()
					&& g.getPattern().equals(t.getPattern())) {
				found++;
			}
		}
		return found;
	}

	/** The main-settings trigger for this gesture, or null. */
	private TriggerData findTrigger(final Connection c, final Gesture g) {
		HashMap<String, TriggerData> triggers = c.getTriggers();
		if (triggers == null) {
			return null;
		}
		for (TriggerData t : triggers.values()) {
			if (t != null && !t.isInterpretAsRegex()
					&& g.getPattern().equals(t.getPattern())) {
				return t;
			}
		}
		return null;
	}

	private Object sendAndReturn(final Connection c, final String message) {
		c.sendDataToWindow(message);
		return null;
	}

	/**
	 * What this is actually for.
	 *
	 * <p>Written because the feature is easy to laugh at — "so you walk around
	 * shaking your phone" — and the useful cases are nothing like that. Every one
	 * of these is a thing that goes wrong while playing a MUD on a phone in
	 * public, and every one is two taps to set up.
	 */
	private String examples() {
		StringBuilder out = new StringBuilder();
		out.append("\n--- what these are actually for ---\n\n");
		out.append("1. Your MUD stops shouting in public.\n");
		out.append("   Reading: headphones unplugged \u2192 run a script that turns\n");
		out.append("   speech off. The jack catches on a bag strap on the bus and the\n");
		out.append("   whole carriage does not hear your combat log.\n\n");
		out.append("2. Speech that only ever happens in your ears.\n");
		out.append("   Not a sensor trigger — a condition. On any trigger that speaks, add\n");
		out.append("   Conditions \u2192 The phone \u2192 \"Headphones are plugged in\".\n");
		out.append("   Now it is silent when they are not, without you remembering.\n\n");
		out.append("3. Someone talks to you and you put the phone down.\n");
		out.append("   .sensor facedown afk  and  .sensor faceup afk off\n");
		out.append("   You go AFK in the game by doing the thing you were doing anyway.\n\n");
		out.append("4. Alerts that know whether you are looking.\n");
		out.append("   Put \"Screen is off\" as a condition on your bell or notification\n");
		out.append("   action, and \"Screen is on\" on the quiet on-screen one. The same\n");
		out.append("   event reaches you the right way in both cases.\n\n");
		out.append("5. A panic button you do not have to find.\n");
		out.append("   .sensor cover flee   \u2014 hold a hand over the top of the screen.\n");
		out.append("   Quiet, one-handed, and it works without looking at the phone.\n\n");
		out.append("6. Nothing fires from inside a pocket.\n");
		out.append("   Options \u2192 Device keeps movement sensors off while the screen\n");
		out.append("   is off. For belt and braces, add the condition \"Nothing is over\n");
		out.append("   the screen\" to anything that sends a command.\n\n");
		out.append("7. The long session at a desk.\n");
		out.append("   Condition \"Phone is charging\" on your noisier alerts: they only\n");
		out.append("   speak up when you are plugged in and settled, not on the walk home.\n\n");
		out.append("8. The map when you turn the phone sideways.\n");
		out.append("   .sensor landscape .map open  and  .sensor portrait .map close\n");
		out.append("   The game window rebuilds on rotate, so the map may open a moment\n");
		out.append("   after the screen comes back. Off until you add the trigger.\n");
		return out.toString();
	}

	private String usage() {
		StringBuilder out = new StringBuilder();
		out.append("\n.sensor                 what this phone can measure\n");
		out.append(".sensor caps            which sensor provides each reading\n");
		out.append(".sensor wave look       make a reading send a command\n");
		out.append(".sensor wave            what that reading does now\n");
		out.append(".sensor wave on|off     without deleting it\n");
		out.append(".sensor fire wave       try it now, without moving the phone\n");
		out.append(".sensor examples        what people actually use these for\n");
		out.append(".sensor watch on|off    keep device.* up to date for conditions\n");
		out.append(".sensor threshold shake 14.5   how hard a shake has to be here\n");
		out.append("\nReadings: ");
		boolean first = true;
		for (Gesture g : GestureCatalog.all()) {
			if (!first) {
				out.append(", ");
			}
			out.append(g.getId());
			first = false;
		}
		out.append('\n');
		return out.toString();
	}
}
