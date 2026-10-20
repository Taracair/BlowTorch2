package com.resurrection.blowtorch2.lib.service.function;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.launcher.ServerAccount;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .login}, {@code .password} and {@code .accounts}: send the login or
 * password stored on this world's launcher entry. The value goes straight to
 * the socket — never through aliases, local echo, the session log or the mapper.
 */
public class CredentialCommand extends SpecialCommand {

	public static final int LOGIN = 0;
	public static final int PASSWORD = 1;
	public static final int LIST = 2;

	/** {@link #pick} result when no account matches the argument. */
	static final int NO_MATCH = -1;

	private final int mMode;

	public CredentialCommand(final int mode) {
		mMode = mode;
		this.commandName = mode == LOGIN ? "login" : mode == PASSWORD ? "password" : "accounts";
	}

	/**
	 * Blank is the first account, a number is that slot (1-based), anything else
	 * is a label compared ignoring case.
	 */
	static int pick(final List<ServerAccount> accounts, final String raw) {
		if (accounts == null || accounts.isEmpty()) {
			return NO_MATCH;
		}
		String arg = raw == null ? "" : raw.trim();
		if (arg.length() == 0) {
			return 0;
		}
		if (arg.matches("\\d{1,4}")) {
			int n = Integer.parseInt(arg);
			return n >= 1 && n <= accounts.size() ? n - 1 : NO_MATCH;
		}
		String want = arg.toLowerCase(Locale.ROOT);
		for (int i = 0; i < accounts.size(); i++) {
			ServerAccount a = accounts.get(i);
			if (a != null && a.getLabel().trim().toLowerCase(Locale.ROOT).equals(want)) {
				return i;
			}
		}
		return NO_MATCH;
	}

	/** {@code .login}/{@code .password} as a whole word, anywhere in a line. */
	private static final Pattern INLINE = Pattern.compile("(?<=^|\\s)\\.(login|password)(?=\\s|$)");

	/** A line with credentials put in: what goes to the server and what the window shows. */
	static final class Inline {
		String wire;
		String shown;
		/** "login" or "password" when that value is not stored; nothing is sent then. */
		String missing;
	}

	/**
	 * Substitute account 1 into {@code connect .login .password}. Null when no
	 * token sits past the start: a line that starts with the command and has none
	 * later is the command itself, with its {@code [n|label]} argument.
	 */
	static Inline substitute(final String line, final List<ServerAccount> accounts) {
		Matcher m = INLINE.matcher(line);
		boolean pastStart = false;
		while (m.find()) {
			if (m.start() > 0) {
				pastStart = true;
				break;
			}
		}
		if (!pastStart) {
			return null;
		}
		ServerAccount a = accounts == null || accounts.isEmpty() ? null : accounts.get(0);
		Inline out = new Inline();
		StringBuilder wire = new StringBuilder();
		StringBuilder shown = new StringBuilder();
		int at = 0;
		m.reset();
		while (m.find()) {
			boolean login = m.group(1).equals("login");
			String value = a == null ? "" : login ? a.getLogin() : a.getPassword();
			if (value.length() == 0) {
				out.missing = m.group(1);
				return out;
			}
			wire.append(line, at, m.start()).append(value);
			shown.append(line, at, m.start()).append(login ? value : "********");
			at = m.end();
		}
		wire.append(line, at, line.length());
		shown.append(line, at, line.length());
		out.wire = wire.toString();
		out.shown = shown.toString();
		return out;
	}

	/** @return true when the line had inline credentials and was dealt with here. */
	public static boolean handleInline(final String line, final Connection c) {
		Inline in = substitute(line, c.getLauncherAccounts());
		if (in == null) {
			return false;
		}
		if (in.missing != null) {
			c.sendDataToWindow(getErrorMessage("." + in.missing + ":",
					"Account 1 has no " + in.missing + " stored. Nothing was sent."));
		} else if (!c.sendCredentialToServer(in.wire)) {
			c.sendDataToWindow(getErrorMessage("Login/password:",
					"Not connected. Nothing was sent."));
		} else {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "[sent: " + in.shown + "]\n");
		}
		return true;
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		String arg = o == null ? "" : o.toString().trim();
		List<ServerAccount> accounts = c.getLauncherAccounts();
		if (mMode == LIST || arg.equalsIgnoreCase("help") || arg.equals("?")) {
			c.sendDataToWindow(list(accounts));
			return null;
		}
		int idx = pick(accounts, arg);
		if (idx == NO_MATCH) {
			c.sendDataToWindow(getErrorMessage("." + commandName + ":",
					accounts.isEmpty()
							? "This world has no account in the launcher.\n"
									+ "Long-press the world in the launcher → Edit to add one."
							: "No account \"" + arg + "\". Type .accounts to see them."));
			return null;
		}
		ServerAccount a = accounts.get(idx);
		String value = mMode == LOGIN ? a.getLogin() : a.getPassword();
		if (value.length() == 0) {
			// A bare CRLF at a password prompt would submit an empty password.
			c.sendDataToWindow(getErrorMessage("." + commandName + ":",
					"Account " + (idx + 1) + " has no " + commandName
							+ " stored. Nothing was sent."));
			return null;
		}
		if (!c.sendCredentialToServer(value)) {
			c.sendDataToWindow(getErrorMessage("." + commandName + ":",
					"Not connected. Nothing was sent."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ (mMode == LOGIN ? "[login sent: " + value + "]" : "[password sent]")
				+ "\n");
		return null;
	}

	private static String list(final List<ServerAccount> accounts) {
		StringBuilder b = new StringBuilder("\n" + Colorizer.getWhiteColor());
		if (accounts.isEmpty()) {
			b.append("This world has no account in the launcher.\n");
		} else {
			for (int i = 0; i < accounts.size(); i++) {
				ServerAccount a = accounts.get(i);
				b.append("  ").append(i + 1).append(". ");
				if (a.getLabel().length() > 0) {
					b.append(a.getLabel()).append(" — ");
				}
				b.append(a.getLogin().length() > 0 ? a.getLogin() : "(no login)");
				b.append(a.getPassword().length() > 0 ? "" : "  (no password)");
				b.append('\n');
			}
		}
		b.append("Usage: .login [n|label]   .password [n|label]\n"
				+ "No argument is account 1. Put them in an alias,\n"
				+ "e.g. signin → .login;.wait 2s;.password\n"
				+ "Edits in the launcher apply on the next connect.\n");
		return b.toString();
	}
}
