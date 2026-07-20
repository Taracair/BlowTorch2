package com.resurrection.blowtorch2.lib.launcher;

/**
 * Informational login slot for a launcher server entry.
 * Used for GMCP Char.Login password-credentials when the server offers it.
 * Stored on-device with the server list (currently plaintext).
 */
public class ServerAccount {

	private String label = "";
	private String login = "";
	private String password = "";
	private String mail = "";

	public ServerAccount copy() {
		ServerAccount tmp = new ServerAccount();
		tmp.label = this.label;
		tmp.login = this.login;
		tmp.password = this.password;
		tmp.mail = this.mail;
		return tmp;
	}

	public String getLabel() {
		return label == null ? "" : label;
	}

	public void setLabel(String label) {
		this.label = label == null ? "" : label;
	}

	public String getLogin() {
		return login == null ? "" : login;
	}

	public void setLogin(String login) {
		this.login = login == null ? "" : login;
	}

	public String getPassword() {
		return password == null ? "" : password;
	}

	public void setPassword(String password) {
		this.password = password == null ? "" : password;
	}

	public String getMail() {
		return mail == null ? "" : mail;
	}

	public void setMail(String mail) {
		this.mail = mail == null ? "" : mail;
	}

	public boolean isEmpty() {
		return getLabel().length() == 0
				&& getLogin().length() == 0
				&& getPassword().length() == 0
				&& getMail().length() == 0;
	}
}
