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

		if (arg.startsWith("bleed") || arg.startsWith("colourbleed")
				|| arg.startsWith("colorbleed")) {
			String rest = arg.startsWith("bleed")
					? arg.substring("bleed".length()).trim()
					: arg.startsWith("colourbleed")
							? arg.substring("colourbleed".length()).trim()
							: arg.substring("colorbleed".length()).trim();
			if (rest.equals("on")) {
				c.setColourBleedProbe(true);
				c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
						+ "Colour-bleed probe on. Play until the leak, then .probe bleed report."
						+ Colorizer.getWhiteColor()
						+ "\nLogcat tag BlowTorchBleed. Costs nothing when off.\n");
				return null;
			}
			if (rest.equals("off")) {
				c.setColourBleedProbe(false);
				c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
						+ "Colour-bleed probe off. The reading is kept; .probe bleed reset clears it."
						+ Colorizer.getWhiteColor() + "\n");
				return null;
			}
			if (rest.equals("reset")) {
				c.resetColourBleedProbe();
				c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
						+ "Colour-bleed probe cleared."
						+ Colorizer.getWhiteColor() + "\n");
				return null;
			}
			if (rest.equals("report") || rest.equals("status") || rest.length() == 0) {
				c.sendDataToWindow(c.colourBleedProbeReport());
				return null;
			}
			c.sendDataToWindow(getErrorMessage("Probe usage",
					".probe bleed on     — record colour-trigger restores\n"
					+ ".probe bleed off    — stop; the reading is kept\n"
					+ ".probe bleed report — dump the reading here (also session log)\n"
					+ ".probe bleed reset  — clear the reading"));
			return null;
		}

		if (arg.equals("truecolor") || arg.equals("colour") || arg.equals("color")
				|| arg.equals("colours") || arg.equals("colors")) {
			c.sendDataToWindow(truecolorSample());
			return null;
		}

		if (arg.equals("osc8") || arg.equals("osc-8") || arg.equals("hyperlink")) {
			c.sendDataToWindow(osc8Sample());
			return null;
		}

		if (arg.equals("protocols") || arg.equals("protocol")) {
			c.sendDataToWindow(ProtocolSurveyCommand.report(c));
			return null;
		}

		if (arg.equals("mxp")) {
			c.sendBytesToWindow(mxpSampleBytes());
			return null;
		}

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
			if (rest.startsWith("light")) {
				String tail = rest.substring("light".length()).trim();
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
				c.sendDataToWindow(SensorProbe.startLightRun(c, seconds));
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
					+ ".probe sensors shake 10 — sample movement for 10 seconds\n"
					+ ".probe sensors light 10 — how bright it is here, in lux"));
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
				+ ".probe bleed on    — record colour-trigger restores (logcat BlowTorchBleed)\n"
				+ ".probe bleed report — dump that reading here\n\n"
				+ "This answers one question: can a trigger pattern span several\n"
				+ "lines on this world, or do the lines arrive too cut up for that?\n\n"
				+ ".probe sensors          — what sensors this phone has\n"
				+ ".probe sensors shake 10 — sample movement for 10 seconds\n"
				+ ".probe sensors light 10 — how bright the room is, in lux\n\n"
				+ "Those two answer a different question: which sensor readings\n"
				+ "this device could support, and how hard a shake has to be here.\n\n"
				+ ".probe truecolor   — dump a 24-bit RGB sample into this window\n"
				+ "                    (also .probe color). Does not wait for a MUD.\n"
				+ ".probe osc8        — dump OSC 8 hyperlink samples into this window\n"
				+ "                    (tap the marked words). Does not wait for a MUD.\n"
				+ ".probe mxp         — dump MXP SEND/colour samples into this window\n"
				+ "                    (tap the marked words). Does not wait for a MUD.\n"
				+ ".probe protocols   — same as .protocols (what was offered vs on)\n"));
		return null;
	}

	/**
	 * A known 24-bit gradient plus a 256-colour strip, so the phone can show
	 * whether {@code CSI 38;2;r;g;b} draws as a smooth ramp.
	 */
	static String truecolorSample() {
		String reset = Colorizer.getResetColor();
		StringBuilder out = new StringBuilder();
		out.append("\n").append(Colorizer.getBrightCyanColor())
				.append("Truecolor probe (CSI 38;2;r;g;b). Smooth ramp = 24-bit; bands = fallback.")
				.append(reset).append("\n");
		out.append("Red→yellow: ");
		for (int i = 0; i <= 24; i++) {
			int g = (i * 255) / 24;
			out.append(csiTruecolor(255, g, 0)).append("█");
		}
		out.append(reset).append("\n");
		out.append("Green→cyan: ");
		for (int i = 0; i <= 24; i++) {
			int b = (i * 255) / 24;
			out.append(csiTruecolor(0, 255, b)).append("█");
		}
		out.append(reset).append("\n");
		out.append("Blue→magenta: ");
		for (int i = 0; i <= 24; i++) {
			int r = (i * 255) / 24;
			out.append(csiTruecolor(r, 0, 255)).append("█");
		}
		out.append(reset).append("\n");
		out.append("256-colour cube (38;5) for comparison: ");
		for (int n = 16; n <= 51; n++) {
			out.append("\u001B[38;5;").append(n).append("m█");
		}
		out.append(reset).append("\n");
		out.append("Orange 38;2;255;128;0: ")
				.append(csiTruecolor(255, 128, 0))
				.append("this sentence")
				.append(reset).append("\n");
		return out.toString();
	}

	private static String csiTruecolor(final int r, final int g, final int b) {
		return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
	}

	/**
	 * Known OSC 8 samples: BEL, ST, display≠URI, a close, and a rejected scheme.
	 */
	static String osc8Sample() {
		final String esc = "\u001B";
		final String bel = "\u0007";
		final String st = esc + "\\";
		final String reset = Colorizer.getResetColor();
		StringBuilder out = new StringBuilder();
		out.append("\n").append(Colorizer.getBrightCyanColor())
				.append("OSC 8 probe. Tap the marked words. Display text need not be the URL.")
				.append(reset).append("\n");
		out.append("1) display ≠ URL: ")
				.append(esc).append("]8;;https://example.com/real-path").append(bel)
				.append("click here")
				.append(esc).append("]8;;").append(bel)
				.append("\n");
		out.append("2) URL as text: ")
				.append(esc).append("]8;;https://example.org/").append(bel)
				.append("https://example.org/")
				.append(esc).append("]8;;").append(bel)
				.append("\n");
		out.append("3) ST terminator: ")
				.append(esc).append("]8;;https://example.net/st").append(st)
				.append("ST-terminated")
				.append(esc).append("]8;;").append(st)
				.append("\n");
		out.append("4) mailto: ")
				.append(esc).append("]8;;mailto:nobody@example.com").append(bel)
				.append("mail nobody")
				.append(esc).append("]8;;").append(bel)
				.append("\n");
		out.append("5) javascript: must stay plain: ")
				.append(esc).append("]8;;javascript:alert(1)").append(bel)
				.append("plain")
				.append(esc).append("]8;;").append(bel)
				.append("\n");
		out.append("6) send: (StickMUD / Mudlet command): ")
				.append(esc).append("]8;;send:look").append(bel)
				.append("LOOK")
				.append(esc).append("]8;;").append(bel)
				.append("\n");
		out.append("7) prompt: fills the input bar: ")
				.append(esc).append("]8;;prompt:cast%20fireball").append(bel)
				.append("Cast Fireball")
				.append(esc).append("]8;;").append(bel)
				.append("\n");
		out.append("Turn off: .osc8 off    Sample world: launcher \"OSC 8 links (local test)\"\n");
		return out.toString();
	}

	/**
	 * Temple-style MXP sample, already run through {@code MxpEngine} so it does
	 * not need a MUD or a handshake. Tap the SEND words.
	 */
	static byte[] mxpSampleBytes() {
		com.resurrection.blowtorch2.lib.service.mxp.MxpEngine e =
				new com.resurrection.blowtorch2.lib.service.mxp.MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(6);
		String markup = mxpSampleMarkup();
		byte[] body;
		try {
			body = e.process(markup.getBytes("UTF-8"));
		} catch (java.io.UnsupportedEncodingException ex) {
			body = e.process(markup.getBytes());
		}
		String head = "\n" + Colorizer.getBrightCyanColor()
				+ "MXP probe. Tap the marked words. Display text need not be the command."
				+ Colorizer.getResetColor() + "\n";
		byte[] prefix;
		try {
			prefix = head.getBytes("UTF-8");
		} catch (java.io.UnsupportedEncodingException ex) {
			prefix = head.getBytes();
		}
		byte[] out = new byte[prefix.length + body.length];
		System.arraycopy(prefix, 0, out, 0, prefix.length);
		System.arraycopy(body, 0, out, prefix.length, body.length);
		return out;
	}

	static String mxpSampleMarkup() {
		return "<!ELEMENT Ex '<SEND>'>"
				+ "<!ELEMENT Item '<SEND href=\"buy &text;\">'>"
				+ "<COLOR red><B>The Main Temple</B></COLOR>\n"
				+ "A <I>lovely</I> <SEND href=\"drink fountain\">fountain</SEND> stands here.\n"
				+ "Exits: <Ex>N</Ex>, <Ex>S</Ex>, <Ex>E</Ex>, <Ex>W</Ex>\n"
				+ "Shop: <Item>bread</Item>  <Item>water</Item>\n"
				+ "<SEND href=\"look|get all\" hint=\"click|Look|Get all\">a sack</SEND>\n"
				+ "<SEND href=\"say hello\" prompt>prompt send</SEND>\n"
				+ "<SEND href=\"old\" expire=\"Exits\">stale exit</SEND> "
				+ "<EXPIRE Exits>(expired)\n"
				+ "Turn off: .mxp off\n";
	}
}
