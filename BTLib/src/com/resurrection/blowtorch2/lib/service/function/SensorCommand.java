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
 * {@code .sensor} — what the phone can feel, and what it should do about it.
 *
 * <pre>
 * .sensor                    what this phone can do and what is set up
 * .sensor caps               which sensor provides each gesture here
 * .sensor wave look          make a gesture send a command
 * .sensor wave               what that gesture does now
 * .sensor wave on|off
 * .sensor fire wave          do it now, without moving the phone
 * </pre>
 *
 * <p><b>Why {@code .sensor wave look} looks like an alias.</b> Setting an alias
 * is {@code .name text} and has been for years; a gesture is the same kind of
 * thing — a name with something it does — so it is set the same way, from the
 * input bar, without opening an editor. What it writes is an ordinary trigger,
 * so anything the editor can add later (a script, a sound, speech, a condition)
 * sits alongside the command rather than replacing this.
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

	/** Same command under the word half the world would reach for. */
	public static final String ALIAS_NAME = "gesture";

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
		if (head.equals("fire") || head.equals("test")) {
			if (rest.length() == 0) {
				c.sendDataToWindow(getErrorMessage("Sensor usage",
						"Which gesture? .sensor fire wave"));
				return null;
			}
			c.sendDataToWindow(c.fireDeviceGestureAndReport(
					rest.split("\\s+", 2)[0].toLowerCase(Locale.US)));
			return null;
		}

		Gesture g = GestureCatalog.byId(head);
		if (g == null) {
			c.sendDataToWindow(getErrorMessage("Sensor usage",
					"There is no gesture called \"" + head + "\".\n" + usage()));
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
		c.saveMainSettings();
		// A gesture nobody had a trigger for was not being listened for. Now
		// there is one, so the sensor has to be picked up.
		c.refreshDeviceGestures();

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
		c.refreshDeviceGestures();
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
		out.append("  ").append(t.isEnabled() ? "on" : "off").append(", ")
			.append(describeActions(t)).append('\n');
		return out.toString();
	}

	private String overview(final Connection c) {
		StringBuilder out = new StringBuilder();
		out.append("\n--- gestures ---\n");
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
		out.append("\n.sensor wave look     point a gesture at a command\n");
		out.append(".sensor fire wave     try it without moving the phone\n");
		out.append(".sensor caps          which sensor does what on this phone\n");
		out.append("\nA gesture is an ordinary trigger: open it in the Triggers editor\n");
		out.append("to add a script, a sound, speech or a condition.\n");
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

	private String usage() {
		StringBuilder out = new StringBuilder();
		out.append("\n.sensor                 what this phone can feel\n");
		out.append(".sensor caps            which sensor provides each gesture\n");
		out.append(".sensor wave look       make a gesture send a command\n");
		out.append(".sensor wave            what that gesture does now\n");
		out.append(".sensor wave on|off     without deleting it\n");
		out.append(".sensor fire wave       do it now, without moving the phone\n");
		out.append("\nGestures: ");
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
