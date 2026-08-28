package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .chat} — open the left chat drawer (UI process via
 * {@code StellarService.doOpenChatPanel}).
 *
 * <pre>
 * .chat / .chat open
 * .chat close | hide
 * .chat help
 * .chat &lt;thread&gt;   open the drawer on that thread
 * </pre>
 *
 * There is no close binder method. {@code .chat close} posts the same open
 * callback; the panel treats that message as a toggle.
 */
public class ChatCommand extends SpecialCommand {

	public static final int ACTION_OPEN = 1;
	public static final int ACTION_CLOSE = 2;
	public static final int ACTION_HELP = 3;
	public static final int ACTION_THREAD = 4;

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
		c.getService().doOpenChatPanel();
		return null;
	}

	public static String usage() {
		return "Chat drawer:\n"
				+ "  .chat / .chat open     open (toggles if already open)\n"
				+ "  .chat close | hide     same toggle; or tap ✕ / the dim area\n"
				+ "  .chat <thread>         open that thread (id or title, case-insensitive)\n"
				+ "  .chat help\n"
				+ "Also: overflow ⋮ → Chat\n"
				+ "⚙ My lines: type Alice (name after ]:). See .help chat.\n";
	}

	public static Parse parse(String arg) {
		String s = arg == null ? "" : arg.trim();
		if (s.length() == 0) {
			return new Parse(ACTION_OPEN, null);
		}
		String lower = s.toLowerCase(Locale.US);
		if (lower.equals("help") || lower.equals("?")) {
			return new Parse(ACTION_HELP, null);
		}
		if (lower.equals("open") || lower.equals("toggle")) {
			return new Parse(ACTION_OPEN, null);
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
