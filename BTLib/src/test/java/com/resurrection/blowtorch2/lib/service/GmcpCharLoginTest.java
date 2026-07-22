package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

/**
 * Eden / GMCP Auth: after Char.Login.Default the client must reply with Credentials
 * or empty Credentials {} — silent skip hangs the server password wait.
 */
public class GmcpCharLoginTest {

	private final List<String> sent = new ArrayList<String>();
	private GmcpCharLogin login;

	@Before
	public void setUp() {
		sent.clear();
		login = new GmcpCharLogin(null, "eden-test", new GmcpCharLogin.Sender() {
			@Override
			public void sendGmcp(final String payload) {
				sent.add(payload);
			}

			@Override
			public void notifyWindow(final String message) {
			}
		});
	}

	@Test
	public void noCredentials_sendsEmptyCredentialsObject() throws Exception {
		login.setCredentials("", "");
		login.handle("Char.Login.Default",
				new JSONObject("{\"type\":[\"password-credentials\"]}"));
		assertEquals(1, sent.size());
		assertEquals("Char.Login.Credentials {}", sent.get(0));
	}

	@Test
	public void oauthOnly_sendsEmptyCredentialsObject() throws Exception {
		login.setCredentials("user", "pass");
		login.handle("Char.Login.Default",
				new JSONObject("{\"type\":[\"oauth\"],\"location\":\"https://example.com\"}"));
		assertEquals(1, sent.size());
		assertEquals("Char.Login.Credentials {}", sent.get(0));
	}
}
