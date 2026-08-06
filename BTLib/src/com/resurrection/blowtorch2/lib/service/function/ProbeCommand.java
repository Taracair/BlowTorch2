package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

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
				+ "lines on this world, or do the lines arrive too cut up for that?\n"));
		return null;
	}
}
