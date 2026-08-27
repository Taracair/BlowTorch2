/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/**
 * Service -> MainWindow. One-way for the reason spelled out on
 * {@link IWindowCallback}: a synchronous transaction into the frozen UI
 * process is a kill, and this interface carries the busiest traffic there is
 * — mapperUi/frameUi/extraTextUi fire on every room change.
 *
 * <p>The `inout byte[]` parameters became `in`: nothing on the far side ever
 * wrote back into those arrays (every implementation posts the array to a
 * Handler and returns), but `inout` forces a reply and so cannot be one-way.
 *
 * <p>Four methods with return values were removed rather than kept
 * synchronous — isWindowShowing(), getPort(), getHost() and getDisplay() had
 * no caller anywhere in the service. The service already knows host, port and
 * display from the connection it owns, and reads its own mWindowShowing.
 */
oneway interface IConnectionBinderCallback {
	void dataIncoming(in byte[] seq);
	void processedDataIncoming(CharSequence seq);
	void htmlDataIncoming(String html);
	void rawDataIncoming(in byte[] raw);
	void rawBufferIncoming(in byte[] incoming);
	void loadSettings();
	void displayXMLError(String error);
	void displaySaveError(String error);
	void displayPluginSaveError(String plugin, String error);
	void executeColorDebug(int arg);
	void invokeDirtyExit();
	void showMessage(String message,boolean longtime);
	void showDialog(String message);
	/** Open a URL from MCP dns-com-awns-displayurl. */
	void launchUrl(String url);
	/** MCP simpleedit content editor. */
	void showMcpSimpleEdit(String reference, String title, String type, String content);
	void doVisualBell();
	void setScreenMode(boolean fullscreen);
	void showKeyBoard(String txt,boolean popup,boolean add,boolean flush,boolean clear,boolean close);
	/**
	 * Drop one word into the input bar at the caret, spacing it against what is
	 * already there. Separate from showKeyBoard(add) because only the UI process
	 * can see the current text, and that is what decides whether a space is
	 * needed.
	 */
	void inputBarInsertWord(String word);
	/** Drop text at the caret exactly as given — no automatic spacing. */
	void inputBarInsertLiteral(String text);
	/**
	 * Open the Options screen. The dialog belongs to the UI process, so the
	 * service can only ask; a UI that is not there simply does not answer,
	 * which is the same as the ⋮ menu not being reachable.
	 */
	void openOptions();
	/**
	 * Incoming text, for the word completer's vocabulary only. Sent solely while
	 * completion is switched on, so a player not using it pays nothing: the main
	 * window's text does not travel this way, it lives in the buffer the UI
	 * adopts.
	 *
	 * @param display which connection produced this text — the UI ignores traffic
	 *        that is not the world it is currently showing.
	 */
	void vocabularyText(String display, String text);
	/**
	 * Forget every word learned so far. Sent when a connection starts, because
	 * the vocabulary lives in the UI process for the life of that process and
	 * would otherwise offer the last world's mob names in the next one.
	 *
	 * @param display which connection is resetting — ignored when it is not the
	 *        world on screen, so connecting a second world cannot wipe the first.
	 */
	void vocabularyReset(String display);
	/**
	 * Apply a surgical {@code .suggest forget|unpair|weight} to the live bag.
	 *
	 * <p>The UI holds command knowledge; a file snapshot can be ten seconds
	 * behind. The service therefore sends the edit here instead of writing the
	 * file itself. Empty {@code spec} is ignored.
	 *
	 * @param display which connection asked — ignored when it is not the world
	 *        on screen, same filter as {@link #vocabularyReset}.
	 * @param spec {@code forget word}, {@code unpair verb target}, or
	 *        {@code weight verb target n}.
	 */
	void vocabularyForget(String display, String spec);
	/**
	 * Take the n-th completion currently on the strip, counting from 1 — what
	 * {@code .complete 3} does. Sent rather than answered, so a super button over
	 * the keyboard can pick one without the finger ever reaching the strip.
	 *
	 * @param display which connection asked — only the foreground world's pick
	 *        reaches the strip.
	 */
	void pickCompletion(String display, int index);
	/**
	 * The world's prompt — the line the holdover released because nothing ever
	 * finishes it. Sent instead of drawing it in the game window while the
	 * prompt bar is on.
	 *
	 * @param display which connection produced the prompt.
	 */
	void promptLine(String display, String text);
	void inputBarSelectAll();
	void inputBarCopy();
	void inputBarPaste();
	void inputBarCut();
	void inputBarCursorToStart();
	void inputBarCursorToEnd();
	/** Move caret by one character: negative = back, positive = forward. */
	void inputBarCursorStep(int delta);
	/** Move caret by line: negative = up, positive = down. */
	void inputBarCursorVertical(int delta);
	/**
	 * Show/hide/toggle the Edit tools strip above the input row.
	 * mode: 0=toggle, 1=on, 2=off (see StellarService.INPUT_EDIT_TOOLS_*).
	 */
	void inputBarEditTools(int mode);
	/** Open in-game scrollback search; empty query opens the bar for typing. */
	void openScrollbackSearch(String query);
	/** nav: -1 prev, 1 next, 0 close */
	void scrollbackSearchNav(int nav);
	void doDisconnectNotice(String display);
	void doLineBreak(int i);
	void reloadButtons(String setName);
	void clearAllButtons();
	void updateMaxVitals(int hp, int mana, int moves);
	void updateVitals(int hp,int mana,int moves);
	void updateEnemy(int hp);
	void updateVitals2(int hp,int mp,int maxhp, int maxmana,int enemy);
	void luaOmg(int stateIndex);
	void updateTriggerDebugString(String str);
	void switchTo(String connection);
	void reloadBuffer();
	void loadWindowSettings();
	void markWindowsDirty();
	void markSettingsDirty();
	void setKeepLast(boolean keep);
	void setGrowInputBar(boolean grow);
	void setOrientation(int orientation);
	void setKeepScreenOn(boolean value);
	void setUseFullscreenEditor(boolean value);
	void setUseSuggestions(boolean value);
	void setCompatibilityMode(boolean value);
	void setRegexWarning(boolean value);
	/** Mapper overlay: 1=open, 2=close, 3=toggle, 4=refresh snapshot. */
	void mapperUi(int action);
	/** Extra text window overlays changed (action typically Connection.MESSAGE_EXTRA_TEXT_CHANGED). */
	void extraTextUi(int action);
	/** mudstd.frame events are waiting; collect them with takeFrameEvents(). */
	void frameUi(int action);
	/** Gauge widget config changed; UI pulls getGaugeWidgetsJson(). */
	void gaugeWidgetUi(int action);
	/**
	 * Live gauge amounts. {@code display} is the connection that produced them —
	 * ignore when it is not the world on screen (same filter as vocabularyText).
	 * Compact JSON: [{"id":"hp","v":80,"m":100}].
	 */
	void gaugeWidgetValues(String display, String json);
	/**
	 * Buzz on the UI process. The service process is often treated as
	 * background and the OS drops a vibrate from there. Appended: do not
	 * insert this above existing methods.
	 */
	void doVibrateBell(int durationMs, int amplitude);
}
