package com.resurrection.blowtorch2.lib.service.function;

import java.util.LinkedHashMap;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.speedwalk.SpeedwalkExpand;

/**
 * {@code .walk 1ne1climb vine2s} and {@code .walk back [<a|b> ...] path}: a
 * speedwalk where every step is a count and a command sent as written, so any
 * command can be a step without a Speedwalk Directions letter.
 */
public class WalkCommand extends SpecialCommand {

	private final Connection.Data mData;

	public WalkCommand(final Connection.Data data) {
		this.commandName = "walk";
		mData = data;
	}

	public Object execute(Object o, Connection c) {
		String str = o == null ? "" : ((String) o).trim();
		if (str.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Walk (.walk) usage:", usage()));
			return null;
		}
		boolean back = false;
		String lower = str.toLowerCase(java.util.Locale.US);
		if (lower.equals("back") || lower.startsWith("back ")) {
			back = true;
			str = str.substring(4).trim();
		}
		Map<String, String> pairs = new LinkedHashMap<String, String>();
		while (back && str.startsWith("<")) {
			int close = str.indexOf('>');
			int bar = str.indexOf('|');
			if (close < 0 || bar < 0 || bar > close) {
				c.sendDataToWindow(getErrorMessage("Bad pair in .walk back:",
						"Write a pair as <climb vine|down>: the command, a bar, the way back."));
				return null;
			}
			String a = str.substring(1, bar).trim();
			String b = str.substring(bar + 1, close).trim();
			if (a.length() == 0 || b.length() == 0) {
				c.sendDataToWindow(getErrorMessage("Bad pair in .walk back:",
						"Both sides of <" + a + "|" + b + "> need a command."));
				return null;
			}
			pairs.put(a, b);
			str = str.substring(close + 1).trim();
		}
		if (str.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Walk (.walk) usage:", usage()));
			return null;
		}
		if (!back && str.startsWith("<")) {
			c.sendDataToWindow(getErrorMessage("Pairs go after back:",
					".walk back <climb vine|down> 1nw1climb vine2s"));
			return null;
		}
		// Read per call: Speedwalk Directions can change while connected.
		SpeedwalkExpand.Result result = SpeedwalkExpand.walk(str, back, pairs,
				c.getDirectionData());
		if (!result.ok) {
			c.sendDataToWindow(getErrorMessage("Each step of .walk is a count and a command:",
					".walk " + (back ? "back " : "") + str + "\n"
							+ "Near \"" + result.errorBit + "\" (position " + (result.errorIndex + 1) + ").\n"
							+ "Every command needs a count in front, 1 to 99: 1ne1climb vine2s.\n"
							+ "A command cannot contain a digit; use .run with commas for that."));
			return null;
		}
		mData.setCmdString(result.cmd);
		mData.setVisString(".walk " + o);
		return mData;
	}

	public static String usage() {
		return ".walk <count><command><count><command>…\n"
				+ "Every command has a count in front and is sent as written:\n"
				+ "\".walk 1ne1sw2e3nw\" sends ne;sw;e;e;nw;nw;nw.\n"
				+ "\".walk 1nw1climb vine2s\" sends nw;climb vine;s;s.\n"
				+ "A command runs to the next digit, so it cannot contain one.\n"
				+ ".walk back <path> walks it in reverse: \".walk back 1nw1climb vine2s\" sends n;n;climb vine;se.\n"
				+ "The way back is the Reverse of the Speedwalk Directions row with that Command,\n"
				+ "else the compass opposite (n↔s, ne↔sw, up↔down, in↔out), else the command as written.\n"
				+ "Name a way back on the spot with <command|way back> before the path:\n"
				+ "\".walk back <climb vine|down> 1nw1climb vine2s\" sends n;n;down;se.\n"
				+ ".run and .rev with direction letters work as before.";
	}
}
