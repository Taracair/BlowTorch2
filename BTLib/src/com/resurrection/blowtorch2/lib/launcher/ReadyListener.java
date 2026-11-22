package com.resurrection.blowtorch2.lib.launcher;

public interface ReadyListener {
	/** @return false to leave the new-connection dialog open. */
	public boolean ready(MudConnection newData);
	public void modify(MudConnection old, MudConnection newData);
}
