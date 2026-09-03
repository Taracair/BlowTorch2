package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.MudstdFrame;
import com.resurrection.blowtorch2.lib.service.Processor;

/**
 * Player half of {@code mudstd.frame}: send {@code frame.closed}. Not
 * {@code .window hide} (extra text windows). Also closes frames with no window.
 *
 * <pre>
 * .frame
 * .frame list
 * .frame close &lt;id&gt;
 * .frame close all
 * </pre>
 */
public class FrameCommand extends SpecialCommand {

	public FrameCommand() {
		this.commandName = "frame";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		if (arg.equalsIgnoreCase("help") || arg.equals("?")) {
			c.sendDataToWindow(helpText());
			return null;
		}
		String[] parts = arg.split("\\s+", 2);
		String sub = parts[0].toLowerCase(Locale.US);
		String rest = parts.length > 1 ? parts[1].trim() : "";
		if (arg.length() == 0 || sub.equals("list") || sub.equals("ls")) {
			return doList(c);
		}
		if (sub.equals("close") || sub.equals("shut")) {
			return doClose(c, rest);
		}
		if (sub.equals("reopen") || sub.equals("open")) {
			return doReopen(c, rest);
		}
		c.sendDataToWindow(getErrorMessage("Frame usage",
				"Unknown subcommand '" + sub + "'.\n" + helpText()));
		return null;
	}

	private Object doList(Connection c) {
		Processor p = c.getProcessor();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		if (p == null) {
			sb.append("Frames: not connected.\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		ArrayList<String> open = p.getOpenFrames();
		if (open.isEmpty()) {
			sb.append("No frames open.\n");
			sb.append("A frame is a window the server asks for (");
			sb.append(MudstdFrame.MODULE).append("). ");
			sb.append("Off unless enabled: Options → Manage modules….\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		sb.append("Frames the server has open here (").append(open.size()).append("):\n");
		for (String id : open) {
			sb.append("  ").append(id).append("\n");
		}
		sb.append("Close one with .frame close <id>, or with the × on the frame\n");
		sb.append("itself. A picture is drawn in a floating separate window, or in\n");
		sb.append("the game text — Options → GMCP → Pictures the server sends. Text\n");
		sb.append("content still appears in this window labelled [frame <id>].\n");
		ArrayList<String> closed = p.getClosedFrames();
		if (!closed.isEmpty()) {
			sb.append("Closed here but still being fed by the server: ");
			for (int i = 0; i < closed.size(); i++) {
				sb.append(i > 0 ? ", " : "").append(closed.get(i));
			}
			sb.append("\n.frame reopen <id> puts one back.\n");
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doClose(Connection c, String rest) {
		Processor p = c.getProcessor();
		if (p == null) {
			c.sendDataToWindow(getErrorMessage("Frame close", "Not connected."));
			return null;
		}
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Frame close",
					"Which frame? .frame list shows what is open."));
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		if (rest.equalsIgnoreCase("all")) {
			ArrayList<String> open = p.getOpenFrames();
			if (open.isEmpty()) {
				sb.append("No frames open.\n");
				c.sendDataToWindow(sb.toString());
				return null;
			}
			for (String id : open) {
				p.closeFrameByUser(id);
			}
			sb.append("Closed ").append(open.size());
			sb.append(open.size() == 1 ? " frame" : " frames");
			sb.append(", and told the server you did it.\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		// The id is taken exactly as typed. Server frame ids are case-sensitive
		// strings the server chose, and one of the cases already tested here
		// contains a double quote.
		if (p.closeFrameByUser(rest)) {
			sb.append("Closed frame '").append(rest);
			sb.append("' — sent ").append(MudstdFrame.MODULE);
			sb.append(".closed with reason \"user\".\n");
		} else {
			sb.append("No frame '").append(rest).append("' is open. ");
			sb.append("Ids are case-sensitive; .frame list shows them.\n");
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	/**
	 * Put back a frame the player closed while the server kept feeding it.
	 *
	 * <p>Not in the specification, and it does not need to be: the client closing
	 * a frame is a message to the server, and a server that carries on sending
	 * pictures has not acted on it. eden does exactly that. Without this the only
	 * way back to a frame closed by mistake is to reconnect.
	 */
	private Object doReopen(Connection c, String rest) {
		Processor p = c.getProcessor();
		if (p == null) {
			c.sendDataToWindow(getErrorMessage("Frame reopen", "Not connected."));
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		ArrayList<String> closed = p.getClosedFrames();
		if (rest.length() == 0) {
			if (closed.isEmpty()) {
				sb.append("No frame here is waiting to be reopened.\n");
				sb.append("Only a frame you closed yourself can come back, and only\n");
				sb.append("while the server still believes it is open.\n");
			} else {
				sb.append("Which frame? These can come back:\n");
				for (String id : closed) {
					sb.append("  ").append(id).append("\n");
				}
			}
			c.sendDataToWindow(sb.toString());
			return null;
		}
		if (rest.equalsIgnoreCase("all")) {
			if (closed.isEmpty()) {
				sb.append("No frame here is waiting to be reopened.\n");
				c.sendDataToWindow(sb.toString());
				return null;
			}
			int back = 0;
			for (String id : closed) {
				if (p.reopenFrameByUser(id)) {
					back++;
				}
			}
			sb.append("Reopened ").append(back);
			sb.append(back == 1 ? " frame" : " frames");
			sb.append(", and told the server each one is open again.\n");
			c.sendDataToWindow(sb.toString());
			return null;
		}
		if (p.reopenFrameByUser(rest)) {
			sb.append("Reopened frame '").append(rest);
			sb.append("' — sent ").append(MudstdFrame.MODULE).append(".opened again.\n");
			sb.append("The last picture the server sent for it is already there.\n");
		} else {
			sb.append("No closed frame '").append(rest).append("' to reopen. ");
			sb.append("Ids are case-sensitive;\n.frame reopen on its own lists what can come "
					+ "back.\n");
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private String helpText() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("Frames a server opens here (").append(MudstdFrame.MODULE).append("):\n");
		sb.append("  .frame               — what is open\n");
		sb.append("  .frame list          — the same\n");
		sb.append("  .frame close <id>    — close it and tell the server you did\n");
		sb.append("  .frame close all     — close every one\n");
		sb.append("  .frame reopen <id>   — put back one you closed\n");
		sb.append("  .frame reopen        — what can come back\n");
		sb.append("Not the same as .window, which is BlowTorch's own extra text\n");
		sb.append("windows. A frame is asked for by the server.\n");
		return sb.toString();
	}
}
