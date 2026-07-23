/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

interface IConnectionBinderCallback {
	boolean isWindowShowing();
	void dataIncoming(inout byte[] seq);
	void processedDataIncoming(CharSequence seq);
	void htmlDataIncoming(String html);
	void rawDataIncoming(inout byte[] raw);
	void rawBufferIncoming(inout byte[] incoming);
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
	int getPort();
	String getHost();
	String getDisplay();
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
}
