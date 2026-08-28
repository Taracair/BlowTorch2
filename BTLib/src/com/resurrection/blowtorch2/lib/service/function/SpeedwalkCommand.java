package com.resurrection.blowtorch2.lib.service.function;

import java.util.HashMap;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.speedwalk.SpeedwalkExpand;

public class SpeedwalkCommand extends SpecialCommand {

	private HashMap<String, DirectionData> mDirections = null;
	private com.resurrection.blowtorch2.lib.service.Connection.Data mData = null;
	private final boolean reverse;

	public SpeedwalkCommand(HashMap<String, DirectionData> directions,
			com.resurrection.blowtorch2.lib.service.Connection.Data data) {
		this(directions, data, false);
	}

	public SpeedwalkCommand(HashMap<String, DirectionData> directions,
			com.resurrection.blowtorch2.lib.service.Connection.Data data, boolean reverse) {
		this.commandName = reverse ? "rev" : "run";
		this.reverse = reverse;
		mDirections = directions;
		mData = data;
	}

	public void setDirections(HashMap<String, DirectionData> directions) {
		mDirections = directions;
	}

	public Object execute(Object o, Connection c) {
		String str = (String) o;

		if (str.equals("") || str.equals(" ")) {
			c.sendDataToWindow(getErrorMessage(
					reverse ? "Speedwalk reverse (.rev) usage:" : "Speedwalk (run) special command usage:",
					usage(reverse)));
			return null;
		}

		SpeedwalkExpand.Result result = reverse
				? SpeedwalkExpand.reverse(str, mDirections)
				: SpeedwalkExpand.forward(str, mDirections);
		if (!result.ok) {
			if (result.missingLetter != null) {
				c.sendDataToWindow(getErrorMessage(
						"No reverse for '" + result.missingLetter + "' ("
								+ result.missingCommand + ")",
						"Set Reverse in ⋮ → Speedwalk Directions for that letter.\n"
								+ "Compass n↔s, e↔w, in↔out, enter↔leave works with Reverse blank.\n"
								+ "door / cave / custom exits need Reverse filled.\n"
								+ "Comma text stays as written: .rev 2n,open door,n sends s;open door;s;s — not close door."));
				return null;
			}
			int errlength = iCaret(result.errorIndex);
			StringBuffer tmpb = new StringBuffer();
			for (int a = 0; a < errlength; a++) {
				tmpb.append("-");
			}
			tmpb.append("^");
			c.sendDataToWindow((getErrorMessage("Invalid direction in command:",
					"." + commandName + " " + str + "\n"
							+ tmpb.toString() + "\n"
							+ "At location " + errlength + ", " + result.errorBit)));
			return null;
		}

		mData.setCmdString(result.cmd);
		mData.setVisString("." + commandName + " " + str);
		return mData;
	}

	private int iCaret(int errorIndex) {
		// ".run " / ".rev " in front of the string; same length for both names.
		return errorIndex + 1 + commandName.length() + 1;
	}

	public static String usage(boolean reverse) {
		if (reverse) {
			return ".rev directions\n"
					+ "Same letters as .run, walked backwards. Mapping is ⋮ → Speedwalk Directions.\n"
					+ "Each letter has Command (.run) and Reverse (.rev). Compass pairs fill in when Reverse is blank: n↔s, e↔w, u↔d, in↔out, enter↔leave, diagonals.\n"
					+ "Custom letters (door, cave, portal) have no compass opposite — type the reverse command in Reverse or .rev stops on that letter.\n"
					+ "Counts stay with the letter: .rev 3n2e sends w;w;s;s;s.\n"
					+ "Comma commands stay as written and move with the reversed path: .rev 2n,open door,n sends s;open door;s;s — not close door.\n"
					+ "Example: you walked .run 3n2e. .rev 3n2e walks it back.";
		}
		return ".run directions\n"
				+ "Direction letters are editable: ⋮ → Speedwalk Directions. Default mapping:\n"
				+ " n: north\n e: east\n s: south\n w: west\n u: up\n d: down\n h: northwest\n j: northeast\n k: southwest\n l: southeast\n"
				+ "Prefix a letter with a count. Commas insert a command; another comma resumes walking.\n"
				+ "Each letter also has Reverse, used by .rev. Compass n↔s / in↔out works with Reverse blank; door/cave need it filled.\n"
				+ "Example:\n"
				+ "\".run 3desw2n\", will send d;d;d;e;s;w;n;n to the server.\n"
				+ "\".run jlk3n3j\", will send se;nw;sw;n;n;n;se;se;se to the server.\n"
				+ "\".run 3ds,open door,3w\" will send d;d;d;s;open door;w;w;w to the server.\n"
				+ "\".rev 3n2e\" walks the reverse (see .rev).";
	}

	public class Data {
		public String cmdString;
		public String visString;
		public Data() {
			cmdString = "";
			visString = "";
		}
	}
}
