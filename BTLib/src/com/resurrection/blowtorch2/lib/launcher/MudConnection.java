package com.resurrection.blowtorch2.lib.launcher;

import java.util.ArrayList;

public class MudConnection {

	private String displayname;
	private String hostname;
	private String port;
	private String lastPlayed = "never";
	/** Optional subtitle shown under the title in the launcher list. */
	private String description = "";
	/** When true (or host is offline), opening skips TCP connect. */
	private boolean offline = false;
	/**
	 * Connect over TLS. Per world, because it is a property of the port: a MUD
	 * that offers both usually puts them on different ones, and the same host
	 * can therefore be two entries with different answers here.
	 */
	private boolean useTls = false;
	/** Starred in the launcher list. Default false; absent XML attribute means false. */
	private boolean favorite = false;
	private boolean connected = false;
	/** Account slots (login/password/mail); primary used for GMCP Char.Login. */
	private ArrayList<ServerAccount> accounts = new ArrayList<ServerAccount>();

	public MudConnection copy() {
		MudConnection tmp = new MudConnection();

		tmp.displayname = this.displayname;
		tmp.hostname = this.hostname;
		tmp.port = this.port;
		tmp.lastPlayed = this.lastPlayed;
		tmp.description = this.description;
		tmp.offline = this.offline;
		tmp.useTls = this.useTls;
		tmp.favorite = this.favorite;
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

	public void setDescription(String description) {
		this.description = description != null ? description : "";
	}

	public String getDescription() {
		return description != null ? description : "";
	}

	public void setOffline(boolean offline) {
		this.offline = offline;
	}

	public boolean isOffline() {
		return offline || BuiltinTutorial.isTutorialHost(hostname);
	}

	public void setUseTls(boolean useTls) {
		this.useTls = useTls;
	}

	/** An offline world connects to nothing, so TLS never applies to one. */
	public boolean isUseTls() {
		return useTls && !isOffline();
	}

	public void setFavorite(boolean favorite) {
		this.favorite = favorite;
	}

	public boolean isFavorite() {
		return favorite;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public boolean isConnected() {
		return connected;
	}
}
