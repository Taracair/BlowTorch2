package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .chat} — the left chat drawer.
 *
 * <pre>
 * .chat                 toggle
 * .chat open            open; already open stays open
 * .chat close | hide    close; already closed stays closed
 * .chat help
 * .chat &lt;thread&gt;        open the drawer on that thread
 * </pre>
 *
 * Bare {@code .chat} still calls {@code doOpenChatPanel}, which toggles.
 */
public class ChatCommand extends SpecialCommand {

	public static final int ACTION_OPEN = 1;
	public static final int ACTION_CLOSE = 2;
	public static final int ACTION_HELP = 3;
	public static final int ACTION_THREAD = 4;
	public static final int ACTION_TOGGLE = 5;

	public ChatCommand() {
		this.commandName = "chat";
	}

	@Override
	public Object execute(Object o, Connection c) {
		Parse p = parse(o == null ? "" : o.toString());
		if (p.action == ACTION_HELP) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + usage());
			return null;
		}
		if (p.action == ACTION_THREAD && p.threadId != null && p.threadId.length() > 0) {
			c.getService().doOpenChatThread(p.threadId);
			return null;
		}
		if (p.action == ACTION_CLOSE) {
			c.getService().doCloseChatPanel();
			return null;
		}
		if (p.action == ACTION_OPEN) {
			c.getService().doShowChatPanel();
			return null;
		}
		c.getService().doOpenChatPanel();
		return null;
	}

	public static String usage() {
		return "Chat drawer:\n"
				+ "  .chat                  toggle\n"
				+ "  .chat open             open; already open stays open\n"
				+ "  .chat close | hide     close; already closed stays closed\n"
				+ "  .chat <thread>         open that thread (id or title, case-insensitive)\n"
				+ "  .chat help\n"
				+ "Also: overflow ⋮ → Chat (always opens)\n"
				+ "⚙: tap My lines or Reply for the submenu. ? in that dialog.\n"
				+ "My lines: Ada, or Ada says; Ada asks (one form per line also works).\n"
				+ "Reply: tell Bob $text / ooc $text. tell $1 $text is the trigger form, not Send.\n"
				+ "Notify: Tells / Channels / Auction / Other (Android Settings).\n"
				+ "Re-tune: chat left the alerts channel (bell stays on alerts).\n";
	}

	public static Parse parse(String arg) {
		String s = arg == null ? "" : arg.trim();
		if (s.length() == 0) {
			return new Parse(ACTION_TOGGLE, null);
		}
		String lower = s.toLowerCase(Locale.US);
		if (lower.equals("help") || lower.equals("?")) {
			return new Parse(ACTION_HELP, null);
		}
		if (lower.equals("open")) {
			return new Parse(ACTION_OPEN, null);
		}
		if (lower.equals("toggle")) {
			return new Parse(ACTION_TOGGLE, null);
		}
		if (lower.equals("close") || lower.equals("hide")) {
			return new Parse(ACTION_CLOSE, null);
		}
		return new Parse(ACTION_THREAD, s);
	}

	public static final class Parse {
		public final int action;
		public final String threadId;

		Parse(int action, String threadId) {
			this.action = action;
			this.threadId = threadId;
		}
	}
}
