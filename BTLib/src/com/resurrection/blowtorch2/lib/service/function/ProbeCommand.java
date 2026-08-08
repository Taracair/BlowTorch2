package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.sensor.SensorProbe;

/**
 * {@code .probe} — turn on the chunk measurement, read it, clear it.
 *
 * <p>A measurement the player runs, not instrumentation left in the code: off
 * unless asked for, and it prints its answer into the game window rather than
 * into a log nobody will fetch off a phone. That is also why it can live in a
 * release build without tripping the "no instrumentation in tracked code"
 * guard — there is nothing here to revert later.
 */
public class ProbeCommand extends SpecialCommand {

	public ProbeCommand() {
		this.commandName = "probe";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);

		// What this phone's sensors are, and what they deliver to the service
		// process. Both halves are unknowable from the code: sensor hardware
		// differs by model, and responders do not run in the UI process.
		if (arg.startsWith("sensors")) {
			String rest = arg.substring("sensors".length()).trim();
			if (rest.length() == 0 || rest.equals("list")) {
				c.sendDataToWindow(SensorProbe.inventory(c.getContext()));
				return null;
			}
			if (rest.equals("state")) {
				c.sendDataToWindow(c.deviceStateReport());
				return null;
			}
			if (rest.startsWith("shake") || rest.startsWith("motion")) {
				String tail = rest.startsWith("shake")
						? rest.substring("shake".length()).trim()
						: rest.substring("motion".length()).trim();
				int seconds = 10;
				if (tail.length() > 0) {
					try {
						seconds = Integer.parseInt(tail);
					} catch (NumberFormatException bad) {
						c.sendDataToWindow(getErrorMessage("Probe usage",
								"\"" + tail + "\" is not a number of seconds."));
						return null;
					}
				}
				c.sendDataToWindow(SensorProbe.startMotionRun(c, seconds));
				return null;
			}
			c.sendDataToWindow(getErrorMessage("Probe usage",
					".probe sensors          — what this device has\n"
					+ ".probe sensors state    — the device.* variables right now\n"
					+ ".probe sensors shake 10 — sample movement for 10 seconds"));
			return null;
		}

		// "lines" is the only probe there is so far; accept it as a prefix so
		// ".probe lines on" reads the way it is documented, and ".probe on"
		// works too rather than being a silent usage error.
		if (arg.startsWith("lines")) {
			arg = arg.substring("lines".length()).trim();
		}

		if (arg.equals("on")) {
			c.setChunkProbe(true);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Chunk probe on. Play normally, then .probe report."
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.equals("off")) {
			c.setChunkProbe(false);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Chunk probe off. The reading is kept; .probe reset clears it."
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.equals("reset")) {
			c.resetChunkProbe();
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Chunk probe cleared." + Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.equals("report") || arg.equals("status") || arg.length() == 0) {
			c.sendDataToWindow(c.chunkProbeReport());
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Probe special command usage:",
				".probe lines on    — start measuring how text arrives\n"
				+ ".probe lines off   — stop; the reading is kept\n"
				+ ".probe report      — show the reading (also plain .probe)\n"
				+ ".probe reset       — clear the reading\n\n"
				+ "This answers one question: can a trigger pattern span several\n"
				+ "lines on this world, or do the lines arrive too cut up for that?\n\n"
				+ ".probe sensors          — what sensors this phone has\n"
				+ ".probe sensors shake 10 — sample movement for 10 seconds\n\n"
				+ "Those two answer a different question: which gestures this\n"
				+ "device could support, and how hard a shake has to be here.\n"));
		return null;
	}
}
