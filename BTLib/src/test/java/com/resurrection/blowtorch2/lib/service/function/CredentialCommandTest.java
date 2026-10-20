package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.launcher.ServerAccount;

public class CredentialCommandTest {

	private static ServerAccount account(final String label, final String login) {
		ServerAccount a = new ServerAccount();
		a.setLabel(label);
		a.setLogin(login);
		a.setPassword("secret");
		return a;
	}

	private static List<ServerAccount> two() {
		List<ServerAccount> list = new ArrayList<ServerAccount>();
		list.add(account("Main", "kamil"));
		list.add(account("Alt", "kamil2"));
		return list;
	}

	@Test
	public void blankPicksTheFirstAccount() {
		assertEquals(0, CredentialCommand.pick(two(), ""));
		assertEquals(0, CredentialCommand.pick(two(), null));
	}

	@Test
	public void numberIsOneBasedSlot() {
		assertEquals(1, CredentialCommand.pick(two(), "2"));
		assertEquals(CredentialCommand.NO_MATCH, CredentialCommand.pick(two(), "3"));
		assertEquals(CredentialCommand.NO_MATCH, CredentialCommand.pick(two(), "0"));
	}

	@Test
	public void labelIgnoresCase() {
		assertEquals(1, CredentialCommand.pick(two(), " alt "));
		assertEquals(CredentialCommand.NO_MATCH, CredentialCommand.pick(two(), "kamil2"));
	}

	@Test
	public void noAccountsNeverMatches() {
		assertEquals(CredentialCommand.NO_MATCH,
				CredentialCommand.pick(new ArrayList<ServerAccount>(), ""));
	}

	@Test
	public void inlineTokensAreReplacedAndPasswordMasked() {
		CredentialCommand.Inline in = CredentialCommand.substitute("connect .login .password", two());
		assertEquals("connect kamil secret", in.wire);
		assertEquals("connect kamil ********", in.shown);
		in = CredentialCommand.substitute(".login .password", two());
		assertEquals("kamil secret", in.wire);
	}

	@Test
	public void commandAtStartWithoutLaterTokenIsNotInline() {
		assertNull(CredentialCommand.substitute(".password 2", two()));
		assertNull(CredentialCommand.substitute("say my.password and ..password", two()));
	}

	@Test
	public void missingValueSendsNothing() {
		List<ServerAccount> list = two();
		list.get(0).setPassword("");
		CredentialCommand.Inline in = CredentialCommand.substitute("connect .login .password", list);
		assertEquals("password", in.missing);
		assertNull(in.wire);
		assertEquals("login", CredentialCommand.substitute("x .login",
				new java.util.ArrayList<ServerAccount>()).missing);
	}
}
