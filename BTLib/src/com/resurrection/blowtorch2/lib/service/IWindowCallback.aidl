/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/**
 * Service -> UI. Every method must stay one-way.
 *
 * <p>A synchronous binder transaction into a *frozen* process is a kill. The
 * cached-app freezer suspends the UI process about two minutes after it goes
 * to the background; the next non-oneway call from :stellar makes the kernel
 * report "Sync transaction while frozen" and activity manager kills the
 * target. Measured 31 July 2026: am_freeze 14:34:18, am_kill 14:34:20,
 * two seconds apart. That kill is what redrew the whole screen — and
 * sometimes showed a splash — whenever the player came back to a backgrounded
 * game.
 *
 * <p>`oneway` on the interface is the barrier, not a style choice. Every
 * implementation only posts to a Handler and returns, so nothing here ever
 * wanted a reply; CLAUDE.md's "service -> UI calls are queued" described that
 * Handler post and hid the fact that the binder call itself blocked. Two
 * methods used to force a reply: getName(), now the RemoteCallbackList cookie
 * registered in Connection.registerWindowCallback, and isWindowShowing(),
 * which nothing called — the service reads its own StellarService
 * mWindowShowing instead.
 *
 * <p>A method with a return value, an `out` or an `inout` parameter will not
 * compile here. That is the point.
 */
oneway interface IWindowCallback {
	void rawDataIncoming(in byte[] raw);
	void resetWithRawDataIncoming(in byte[] raw);
	void redraw();
	void shutdown();
	void xcallS(String function,String str);
	void xcallB(String function,in byte[] raw);
	void clearText();
	void updateSetting(String key,String value);
	void setEncoding(String value);
	void setLocalEcho(boolean enabled);
	/**
	 * The tappable-word rules are out of date: read them again.
	 *
	 * The frame around a tappable word is drawn in the UI process, from its own
	 * copy of the triggers. Anything that changes what a trigger matches --
	 * including editing an alias a trigger's pattern names, from the editor or
	 * from `.name newtext` in the input bar -- happens over here, and the UI
	 * had no way of hearing about it.
	 */
	void tapRulesChanged();
}
