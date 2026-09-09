package com.resurrection.blowtorch2.lib.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One ICMP echo via {@code /system/bin/ping -c 1}. Measured as the app uid
 * (run-as) on GrapheneOS; the app's {@code untrusted_app} domain was not
 * separately confirmed.
 */
public final class IcmpPing {

	static final String PING4 = "/system/bin/ping";
	static final String PING6 = "/system/bin/ping6";
	static final int WAIT_SECONDS = 3;
	private static final long PROCESS_LIMIT_SECONDS = 8L;
	private static final int OUTPUT_LIMIT = 8192;
	private static final int HOST_MAX = 253;

	private static final Pattern TIME_MS = Pattern.compile(
			"time[=:]\\s*(\\d+(?:\\.\\d+)?)\\s*ms", Pattern.CASE_INSENSITIVE);
	private static final Charset PING_CHARSET = Charset.forName("UTF-8");

	private IcmpPing() {
	}

	public static String sanitize(String raw) {
		if (raw == null) {
			return null;
		}
		String host = raw.trim();
		if (host.startsWith("[") && host.endsWith("]") && host.length() >= 2) {
			host = host.substring(1, host.length() - 1).trim();
		}
		if (host.length() == 0 || host.length() > HOST_MAX) {
			return null;
		}
		if (host.charAt(0) == '-') {
			return null;
		}
		for (int i = 0; i < host.length(); i++) {
			char c = host.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
					|| (c >= '0' && c <= '9') || c == '.' || c == '-' || c == ':'
					|| c == '_';
			if (!ok) {
				return null;
			}
		}
		return host;
	}

	public static String[] command(String host) {
		String bin = host.indexOf(':') >= 0 ? PING6 : PING4;
		return new String[] { bin, "-c", "1", "-W", Integer.toString(WAIT_SECONDS), host };
	}

	public static String parseRttMs(String output) {
		if (output == null) {
			return null;
		}
		Matcher m = TIME_MS.matcher(output);
		return m.find() ? m.group(1) : null;
	}

	public static String report(String host, String output, Integer exitCode,
			boolean timedOut) {
		if (timedOut) {
			return host + ": timed out";
		}
		String text = output == null ? "" : output;
		String lower = text.toLowerCase(Locale.US);
		if (lower.contains("permission denied")
				|| lower.contains("operation not permitted")) {
			return "ICMP ping is not allowed on this phone.";
		}
		String rtt = parseRttMs(text);
		if (rtt != null) {
			return host + ": " + rtt + " ms";
		}
		if (lower.contains("unknown host") || lower.contains("name or service")
				|| lower.contains("temporary failure in name resolution")) {
			return host + ": unknown host";
		}
		if (exitCode != null && exitCode.intValue() == 0) {
			return host + ": no ICMP time in ping output";
		}
		return host + ": no ICMP reply";
	}

	public static String run(String host) {
		String[] cmd = command(host);
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process p;
		try {
			p = pb.start();
		} catch (IOException e) {
			return "Cannot run ping on this phone.";
		}
		try {
			boolean finished = p.waitFor(PROCESS_LIMIT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				p.destroy();
				p.waitFor(1, TimeUnit.SECONDS);
				if (p.isAlive()) {
					p.destroyForcibly();
				}
				return report(host, null, null, true);
			}
			String out = readLimited(p.getInputStream(), OUTPUT_LIMIT);
			return report(host, out, Integer.valueOf(p.exitValue()), false);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			p.destroy();
			return host + ": interrupted";
		} catch (IOException e) {
			p.destroy();
			return host + ": no ICMP reply";
		}
	}

	static String readLimited(InputStream in, int max) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] chunk = new byte[256];
		int n;
		while (buf.size() < max && (n = in.read(chunk)) != -1) {
			int take = Math.min(n, max - buf.size());
			buf.write(chunk, 0, take);
		}
		return new String(buf.toByteArray(), PING_CHARSET);
	}
}
