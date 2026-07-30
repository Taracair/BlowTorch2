package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.MudstdFrame;
import com.resurrection.blowtorch2.lib.service.Processor;

/**
 * The player's half of the {@code mudstd.frame} package.
 *
 * <p>The specification has {@code frame.closed} with {@code reason: "user"} —
 * the player shut the frame — and until now BlowTorch could never send it. A
 * server could open a frame here and never learn the person reading it was
 * done. That is the missing half this command supplies.
 *
 * <p>It is a command rather than a close button because nothing draws a frame
 * yet: frame content is described in the main window. The event on the wire is
 * the one a close button would send, which is the half a server author has to
 * write against, and it can be replaced by a button later without the server
 * noticing.
 *
 * <p>{@code .window hide/show} is a different thing entirely — that is our own
 * extra text windows, not server-opened frames.
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
		sb.append("Close one with .frame close <id>. Their content is shown in this\n");
		sb.append("window labelled [frame <id>]; nothing is drawn in a frame yet.\n");
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

	private String helpText() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("Frames a server opens here (").append(MudstdFrame.MODULE).append("):\n");
		sb.append("  .frame               — what is open\n");
		sb.append("  .frame list          — the same\n");
		sb.append("  .frame close <id>    — close it and tell the server you did\n");
		sb.append("  .frame close all     — close every one\n");
		sb.append("Not the same as .window, which is BlowTorch's own extra text\n");
		sb.append("windows. A frame is asked for by the server.\n");
		return sb.toString();
	}
}
