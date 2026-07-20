package com.resurrection.blowtorch2.lib.launcher;

import java.util.ArrayList;

public class MudConnection {

	private String displayname;
	private String hostname;
	private String port;
	private String lastPlayed = "never";
	private boolean connected = false;
	/** Account slots (login/password/mail); primary used for GMCP Char.Login. */
	private ArrayList<ServerAccount> accounts = new ArrayList<ServerAccount>();

	public MudConnection copy() {
		MudConnection tmp = new MudConnection();

		tmp.displayname = this.displayname;
		tmp.hostname = this.hostname;
		tmp.port = this.port;
		tmp.lastPlayed = this.lastPlayed;
		tmp.accounts = new ArrayList<ServerAccount>();
		if (this.accounts != null) {
			for (ServerAccount account : this.accounts) {
				if (account != null) {
					tmp.accounts.add(account.copy());
				}
			}
		}

		return tmp;
	}

	public String getDisplayName() {
		return displayname;
	}

	public String getHostName() {
		return hostname;
	}

	public String getPortString() {
		return port;
	}

	public void setDisplayName(String in) {
		displayname = in;
	}

	public void setHostName(String in) {
		hostname = in;
	}

	public void setPortString(String in) {
		port = in;
	}

	public ArrayList<ServerAccount> getAccounts() {
		if (accounts == null) {
			accounts = new ArrayList<ServerAccount>();
		}
		return accounts;
	}

	public void setAccounts(ArrayList<ServerAccount> accounts) {
		this.accounts = accounts != null ? accounts : new ArrayList<ServerAccount>();
	}

	/** Ensure at least one editable slot exists for the connection editor UI. */
	public ServerAccount primaryAccount() {
		ArrayList<ServerAccount> list = getAccounts();
		if (list.isEmpty()) {
			list.add(new ServerAccount());
		}
		return list.get(0);
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof MudConnection)) return false;

		MudConnection test = (MudConnection) o;

		if (!test.getDisplayName().equals(this.getDisplayName())) return false;
		if (!test.getHostName().equals(this.getHostName())) return false;
		if (!test.getPortString().equals(this.getPortString())) return false;
		if (!test.getLastPlayed().equals(this.getLastPlayed())) return false;
		return true;
	}

	public void setLastPlayed(String lastPlayed) {
		this.lastPlayed = lastPlayed;
	}

	public String getLastPlayed() {
		return lastPlayed;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public boolean isConnected() {
		return connected;
	}
}
