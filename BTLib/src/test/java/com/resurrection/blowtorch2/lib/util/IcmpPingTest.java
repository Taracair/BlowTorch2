package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IcmpPingTest {

	@Test
	public void sanitizeKeepsDnsAndV4() {
		assertEquals("example.org", IcmpPing.sanitize(" example.org "));
		assertEquals("203.0.113.1", IcmpPing.sanitize("203.0.113.1"));
	}

	@Test
	public void sanitizeStripsV6Brackets() {
		assertEquals("2001:db8::1", IcmpPing.sanitize("[2001:db8::1]"));
	}

	@Test
	public void sanitizeRejectsFlagsAndShell() {
		assertNull(IcmpPing.sanitize("-c"));
		assertNull(IcmpPing.sanitize("example.org;id"));
		assertNull(IcmpPing.sanitize("example.org 1.1.1.1"));
		assertNull(IcmpPing.sanitize(""));
		assertNull(IcmpPing.sanitize(null));
	}

	@Test
	public void commandIsOneEchoNoShell() {
		assertArrayEquals(
				new String[] { IcmpPing.PING4, "-c", "1", "-W", "3", "example.org" },
				IcmpPing.command("example.org"));
		assertArrayEquals(
				new String[] { IcmpPing.PING6, "-c", "1", "-W", "3", "2001:db8::1" },
				IcmpPing.command("2001:db8::1"));
	}

	@Test
	public void parseRttFromIputilsReplyLine() {
		String out = "PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.\n"
				+ "64 bytes from 1.1.1.1: icmp_seq=1 ttl=54 time=44.2 ms\n"
				+ "\n"
				+ "--- 1.1.1.1 ping statistics ---\n"
				+ "1 packets transmitted, 1 received, 0% packet loss, time 0ms\n"
				+ "rtt min/avg/max/mdev = 44.288/44.288/44.288/0.000 ms\n";
		assertEquals("44.2", IcmpPing.parseRttMs(out));
		assertEquals("example.org: 44.2 ms",
				IcmpPing.report("example.org", out, Integer.valueOf(0), false));
	}

	@Test
	public void reportLossAndPermission() {
		assertEquals("example.org: no ICMP reply",
				IcmpPing.report("example.org",
						"1 packets transmitted, 0 received, 100% packet loss\n",
						Integer.valueOf(1), false));
		assertEquals("ICMP ping is not allowed on this phone.",
				IcmpPing.report("example.org", "socket: Permission denied\n",
						Integer.valueOf(2), false));
		assertEquals("example.org: timed out",
				IcmpPing.report("example.org", null, null, true));
		assertEquals("example.org: unknown host",
				IcmpPing.report("example.org", "ping: unknown host example.org\n",
						Integer.valueOf(2), false));
	}
}
