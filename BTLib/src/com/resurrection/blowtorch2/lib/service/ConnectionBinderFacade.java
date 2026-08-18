/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.service.plugin.settings.EncodingOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * AIDL bind target for {@link StellarService}. Holds a reference to the service
 * and proxies foreground-process calls to the active {@link Connection}.
 */
class ConnectionBinderFacade extends IConnectionBinder.Stub {

	private final StellarService service;

	ConnectionBinderFacade(final StellarService service) {
		this.service = service;
	}


	@Override
	public void registerCallback(final IConnectionBinderCallback c, final String host, final int port,
			final boolean useTls, final String display)
			throws RemoteException {
		if (c != null) {
			service.mCallbacks.register(c);

			if (!service.mConnections.containsKey(display)) {
				this.setConnectionData(host, port, useTls, display);
			} else {
				service.mConnectionClutch = display;
				// UI process came back onto a live Connection (recents kill, or
				// launcher reopen). The previous IWindowCallbacks are corpses in
				// RemoteCallbackList — Keep-in-background works because dirtyExit
				// unregisters them first. Wipe them here so the new Windows own
				// the map before loadWindowSettings rebuilds them.
				Connection existing = service.mConnections.get(display);
				if (existing != null) {
					existing.purgeAllWindowCallbacks();
				}
				c.loadWindowSettings();
			}
		}
	}

	@Override
	public void unregisterCallback(final IConnectionBinderCallback c)
			throws RemoteException {
		if (c !=  null) {
			service.mCallbacks.unregister(c);
		}
	}
	
	@Override
	public void registerLauncherCallback(final ILauncherCallback c) {
		if (c != null) {
			service.mLauncherCallbacks.register(c);
		}
	}
	
	@Override
	public void unregisterLauncherCallback(final ILauncherCallback c) {
		if (c != null) {
			service.mLauncherCallbacks.unregister(c);
		}
	}

	@Override
	public void initXfer() throws RemoteException {
		Connection existing = active();
		// Only skip when the socket is actually up. A zombie Looper after a failed
		// connect used to make isAlive()==true and block all further startups.
		if (existing != null && existing.isConnected()) {
			android.util.Log.i("BlowTorch", "initXfer skipped — already connected");
			return;
		}
		service.mHandler.sendEmptyMessage(StellarService.MESSAGE_STARTUP);
	}

	@Override
	public void endXfer() throws RemoteException {
		//doStartup();
		Connection c = active();
		if (c == null) {
			return;
		}
		c.sendDataToWindow("\n" + Colorizer.getRedColor() + "Connection terminated by user." + Colorizer.getWhiteColor() + "\n\n");
		c.killNetThreads(true);
		c.doDisconnect(true);
	}

	@Override
	public boolean isConnected() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.isConnected();
	}

	@Override
	public void sendData(final byte[] seq) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		Handler handler = c.getHandler();
		if (handler == null) {
			return;
		}
		handler.sendMessage(handler.obtainMessage(Connection.MESSAGE_SENDDATA_BYTES, seq));
	}

	@Override
	public void saveSettings() throws RemoteException {
		Connection c = active();
		if (c == null && service.mConnections != null && service.mConnections.size() == 1) {
			c = service.mConnections.values().iterator().next();
		}
		if (c == null) {
			return;
		}
		c.saveMainSettings();
	}

	@Override
	public void setConnectionData(final String host, final int port, final boolean useTls,
			final String display)
			throws RemoteException {
		Message msg = service.mHandler.obtainMessage(StellarService.MESSAGE_NEWCONENCTION);
		Bundle b = msg.getData();
		b.putString("DISPLAY", display);
		b.putString("HOST", host);
		b.putInt("PORT", port);
		b.putBoolean("TLS", useTls);
		msg.setData(b);
		service.mHandler.sendMessage(msg);
		
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getSystemCommands() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return Collections.emptyList();
		}
		return c.getSystemCommands();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getAliases() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getAliases();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setAliases(final Map map) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setAliases((HashMap<String, AliasData>) map);
	}

	@Override
	public void loadSettingsFromPath(final String path) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.startLoadSettingsSequence(path);
	}

	@Override
	public void exportSettingsToPath(final String path) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.exportSettings(path);
	}

	@Override
	public void resetSettings() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.resetSettings();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getTriggerData() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getTriggers();
	}
	
	@Override
	public java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>
			getTapRules() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new java.util.ArrayList<
					com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>();
		}
		return c.getTapRules();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginTriggerData(final String id) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getPluginTriggers(id);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getDirectionData() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getDirectionData();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setDirectionData(final Map data) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setDirectionData((HashMap<String, DirectionData>) data);
	}

	@Override
	public void newTrigger(final TriggerData data) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.addTrigger(data);
	}

	@Override
	public void updateTrigger(final TriggerData from, final TriggerData to)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateTrigger(from, to);
		
	}

	@Override
	public void deleteTrigger(final String which) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deleteTrigger(which);
	}

	@Override
	public TriggerData getTrigger(final String pattern) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getTrigger(pattern);
	}

	@Override
	public boolean isKeepLast() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.isKeepLast();
	}

	@Override
	public void setDisplayDimensions(final int rows, final int cols)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.applyLiveDisplayDimensions(rows, cols);
	}

	@Override
	public void reconnect(final String str) throws RemoteException {
		String connection = str;
		if (str == null || str.equals("")) {
			connection = service.mConnectionClutch;
		}
		if (connection == null || service.mConnections == null) {
			return;
		}
		Connection c = service.mConnections.get(connection);
		if (c == null) {
			return;
		}
		c.doReconnect();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getTimers() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getTimers();
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginTimers(final String plugin) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getPluginTimers(plugin);
	}

	@Override
	public TimerData getTimer(final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getTimer(ordinal);
	}

	@Override
	public void startTimer(final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.playTimer(ordinal);
	}

	@Override
	public void pauseTimer(final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.pauseTimer(ordinal);
	}

	@Override
	public void stopTimer(final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.stopTimer(ordinal);
	}
	
	@Override
	public void startPluginTimer(final String plugin, final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.playPluginTimer(plugin, ordinal);
	}

	@Override
	public void pausePluginTimer(final String plugin, final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.pausePluginTimer(plugin, ordinal);
	}

	@Override
	public void stopPluginTimer(final String plugin, final String ordinal) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.stopPluginTimer(plugin, ordinal);
	}

	@Override
	public void updateTimer(final TimerData old, final TimerData newtimer)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateTimer(old, newtimer);
	}

	@Override
	public void addTimer(final TimerData newtimer) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.addTimer(newtimer);
	}

	@Override
	public void removeTimer(final TimerData deltimer) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deleteTimer(deltimer.getName());
	}

	@Override
	public int getNextTimerOrdinal() throws RemoteException {
		return 0;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getTimerProgressWad() throws RemoteException {
		return null;
	}

	@Override
	public String getEncoding() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return (String) ((EncodingOption) c.getSettings().findOptionByKey("encoding")).getValue();
	}

	@Override
	public String getConnectedTo() throws RemoteException {
		return service.mConnectionClutch;
	}
	
	@Override
	public boolean isFullScreen() throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.isFullScren();
	}

	@Override
	public String getConnectionDurationText(final String display) throws RemoteException {
		return service.getConnectionDurationText(display);
	}
	
	@Override
	public void setTriggerEnabled(final boolean enabled, final String key)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setTriggerEnabled(enabled, key);
	}

	@Override
	public void setButtonSetLocked(final boolean locked, final String key)
			throws RemoteException {
		
	}

	@Override
	public boolean isButtonSetLocked(final String key) throws RemoteException {
		return false;
	}

	@Override
	public boolean isButtonSetLockedMoveButtons(final String key)
			throws RemoteException {
		return false;
	}

	@Override
	public boolean isButtonSetLockedNewButtons(final String key)
			throws RemoteException {
		return false;
	}

	@Override
	public boolean isButtonSetLockedEditButtons(final String key)
			throws RemoteException {
		return false;
	}

	@Override
	public void startNewConnection(final String host, final int port, final String display)
			throws RemoteException {
	}

	@Override
	public void switchTo(final String display) throws RemoteException {
		service.mHandler.sendMessage(service.mHandler.obtainMessage(StellarService.MESSAGE_SWITCH, display));
	}

	@Override
	public boolean isConnectedTo(final String display) throws RemoteException {
		return service.mConnections.keySet().contains(display);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getConnections() throws RemoteException {
		List<String> tmp = new ArrayList<String>();
		for (String key : service.mConnections.keySet()) {
			tmp.add(key);
		}
		return tmp;
	}

	/**
	 * The connection the clutch names, or null when it names none.
	 *
	 * <p>The size check most methods here start with answers "are there any
	 * connections", which is not the same question: with two worlds open the map
	 * is full while the clutch can still miss it — during a switch, or after a
	 * connection is dropped. A miss used to reach {@code .someMethod()} on null,
	 * and <b>an exception thrown out of a binder method is re-thrown in the
	 * calling process</b>, so a null here killed the UI. Ask through this.
	 */
	private Connection active() {
		if (service.mConnections == null || service.mConnectionClutch == null) {
			return null;
		}
		return service.mConnections.get(service.mConnectionClutch);
	}

	@Override
	public WindowToken[] getWindowTokens() throws RemoteException {
		// Callers already handle null: this returned it whenever no connection
		// existed at all. MainWindow.findWindowToken is the one that crashed.
		Connection c = active();
		return c == null ? null : c.getWindows();
	}

	@Override
	public void registerWindowCallback(final String displayName, final String name, final IWindowCallback callback)
			throws RemoteException {
		Connection c = service.mConnections.get(displayName);
		if (c != null) {
			c.registerWindowCallback(name, callback);
		} 
	}

	@Override
	public void unregisterWindowCallback(final String name,
			final IWindowCallback callback) throws RemoteException {
		Connection c = service.mConnections.get(name);
		if (c != null) {
			c.unregisterWindowCallback(callback);
		}
	}

	@Override
	public String getScript(final String plugin, final String name)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return "";
		}
		return c.getScript(plugin, name);
	}

	@Override
	public void reloadSettings() throws RemoteException {
		service.mHandler.sendEmptyMessage(StellarService.MESSAGE_RELOADSETTINGS);
		
	}

	@Override
	public void pluginXcallS(final String plugin, final String function, final String str)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.pluginXcallS(plugin, function, str);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginList() throws RemoteException {
		
		Connection c = active();
		HashMap<String, String> list = new HashMap<String, String>();
		if (c == null) {
			return list;
		}
		
		for (Plugin p : c.getPlugins()) {
			String info = "";
			info += p.getTriggerCount() + " T, ";
			info += p.getAliasCount() + " A, ";
			info += p.getTimerCount() + " C, ";
			info += p.getScriptCount() + " S, ";
			info += p.getStorageType();
			list.put(p.getName(), info);
		}

		// Surface dangling settings links (e.g. missing alarm plugin) so they can be deleted.
		HashMap<String, String> failed = c.getFailedLinks();
		if (failed != null) {
			for (Map.Entry<String, String> entry : failed.entrySet()) {
				String link = entry.getKey();
				if (list.containsKey(link)) {
					continue;
				}
				list.put(link, "MISSING: " + entry.getValue());
			}
		}
		
		return list;
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public List getPluginsWithTriggers() {
		ArrayList<String> list = new ArrayList<String>();
		Connection c = active();
		if (c == null) {
			return list;
		}
		for (Plugin p : c.getPlugins()) {
			if (p.getSettings().getTriggers().size() > 0) {
				list.add(p.getName());
			}
		}
		return list;
	}

	@Override
	public void newPluginTrigger(final String selectedPlugin, final TriggerData data)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.newPluginTrigger(selectedPlugin, data);
	}

	@Override
	public void updatePluginTrigger(final String selectedPlugin,
			final TriggerData from, final TriggerData to) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updatePluginTrigger(selectedPlugin, from, to);
	}

	@Override
	public TriggerData getPluginTrigger(final String selectedPlugin, final String pattern)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getPluginTrigger(selectedPlugin, pattern);
	}

	@Override
	public void setPluginTriggerEnabled(final String selectedPlugin,
			final boolean enabled, final String key) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setPluginTriggerEnabled(selectedPlugin, enabled, key);
	}

	@Override
	public void deletePluginTrigger(final String selectedPlugin, final String which)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deletePluginTrigger(selectedPlugin, which);
	}

	@Override
	public AliasData getAlias(final String key) throws RemoteException {
		
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getAlias(key);
	}

	@Override
	public AliasData getPluginAlias(final String plugin, final String key)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getPluginAlias(plugin, key);
	}

//		@SuppressWarnings("rawtypes")
//		public Map getAliases(final String currentPlugin) throws RemoteException {
//			
//			return service.mConnections.get(service.mConnectionClutch).getAliases();
//		}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginAliases(final String currentPlugin) {
		Connection c = active();
		if (c == null) {
			return new HashMap();
		}
		return c.getPluginAliases(currentPlugin);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setPluginAliases(final String plugin, final Map map)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setPluginAliases(plugin, (HashMap<String, AliasData>) map);
	}

	@Override
	public void deleteAlias(final String key) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deleteAlias(key);
	}

	@Override
	public void deletePluginAlias(final String plugin, final String key)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deletePluginAlias(plugin, key);
	}

	@Override
	public void setAliasEnabled(final boolean enabled, final String key)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setAliasEnabled(enabled, key);
		
	}

	@Override
	public void setPluginAliasEnabled(final String plugin, final boolean enabled,
			final String key) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.setPluginAliasEnabled(plugin, enabled, key);
	}

	@Override
	public TimerData getPluginTimer(final String plugin, final String name) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getPluginTimer(plugin, name);
	}

	@Override
	public void deleteTimer(final String name) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deleteTimer(name);
	}

	@Override
	public void deletePluginTimer(final String plugin, final String name)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deletePluginTimer(plugin, name);
	}

	@Override
	public void updatePluginTimer(final String plugin, final TimerData old,
			final TimerData newtimer) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updatePluginTimer(plugin, old, newtimer);
	}

	@Override
	public void addPluginTimer(final String plugin, final TimerData newtimer)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.addPluginTimer(plugin, newtimer);
	}

	@Override
	public SettingsGroup getSettings() throws RemoteException {
		if (service.mConnections.size() == 0) { return null; }
		Connection c = active();
		if (c == null) { return null; }
		return c.getSettings();
	}

	@Override
	public SettingsGroup getPluginSettings(final String plugin)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return null;
		}
		return c.getPluginSettings(plugin);
	}

	@Override
	public void updateBooleanSetting(final String key, final boolean value)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateBooleanSetting(key, value);
	}

	@Override
	public void updatePluginBooleanSetting(final String plugin, final String key,
			final boolean value) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updatePluginBooleanSetting(plugin, key, value);
	}

	@Override
	public void updateIntegerSetting(final String key, final int value)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateIntegerSetting(key, value);
	}

	@Override
	public void updatePluginIntegerSetting(final String plugin, final String key,
			final int value) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updatePluginIntegerSetting(plugin, key, value);
	}

	@Override
	public void updateFloatSetting(final String key, final float value)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateFloatSetting(key, value);
	}

	@Override
	public void updatePluginFloatSetting(final String plugin, final String key,
			final float value) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updatePluginFloatSetting(plugin, key, value);
	}

	@Override
	public void updateStringSetting(final String key, final String value)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateStringSetting(key, value);
	}

	@Override
	public void updatePluginStringSetting(final String plugin, final String key,
			final String value) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updatePluginStringSetting(plugin, key, value);
	}

	@Override
	public void updateWindowBufferMaxValue(final String plugin, final String window,
			final int amount) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateWindowBufferMaxValue(plugin, window, amount);
	}
	
	@Override
	public void closeConnection(final String display) {
		Connection c = service.mConnections.get(display);
		if (c != null) {
			c.shutdown();

			service.mConnections.remove(display);
			// The launcher shows this server as connected and had no way to find
			// out otherwise: only a dropped connection and the duration ticker
			// ever broadcast. Now that the caller no longer waits for this
			// method, saying so is the only thing that turns the row grey while
			// the list is on screen.
			service.notifyLauncherListChanged();
		}
	}
	
	@Override
	public void windowShowing(final boolean show) {
		service.setWindowShowing(show);
	}

	@Override
	public void setPlayerTyping(final boolean typing) {
		// Straight to the engine's static state: no connection is needed to know
		// that a command is being composed, and this arrives while the player is
		// typing, which is exactly when nothing should be doing work.
		com.resurrection.blowtorch2.lib.util.SpeechEngine.setPlayerTyping(typing);
	}

	@Override
	public void dispatchLuaError(final String message) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.dispatchLuaError(message);
	}
	
	@Override
	public void addLink(final String path) {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.addLink(path);
	}

	@Override
	public void deletePlugin(final String plugin) throws RemoteException {
		// Return value deliberately dropped here: the refusal is reported to the
		// player by the service, and the AIDL signature is void.
		Connection c = active();
		if (c == null) {
			return;
		}
		c.deletePlugin(plugin);
	}

	@Override
	public boolean setPluginEnabled(final String plugin, final boolean enabled)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.setPluginEnabled(plugin, enabled);
	}

	@Override
	public boolean isPluginEnabled(final String plugin) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.isPluginEnabled(plugin);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getPluginsWithAliases() {
		ArrayList<String> list = new ArrayList<String>();
		Connection c = active();
		if (c == null) {
			return list;
		}
		for (Plugin p : c.getPlugins()) {
			if (p.getSettings().getAliases().size() > 0) {
				list.add(p.getName());
			}
		}
		return list;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getPluginsWithTimers() throws RemoteException {
		ArrayList<String> list = new ArrayList<String>();
		Connection c = active();
		if (c == null) {
			return list;
		}
		for (Plugin p : c.getPlugins()) {
			if (p.getSettings().getTimers().size() > 0) {
				list.add(p.getName());
			}
		}
		return list;
	}

	@Override
	public boolean isLinkLoaded(final String link) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.isLinkLoaded(link);
	}

	@Override
	public String getPluginPath(final String plugin) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return "";
		}
		String path = c.getPluginPath(plugin);
		if (path == null) { path = ""; }
		return path;
	}

	@Override
	public void dispatchLuaText(final String str) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.dispatchLuaText(str);
	}

	@Override
	public void callPluginFunction(final String plugin, final String function)
			throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.callPluginFunction(plugin, function);
	}

	@Override
	public boolean isPluginInstalled(final String desired) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return false;
		}
		return c.isPluginInstalled(desired);
	}

	@Override
	public void setShowRegexWarning(boolean state) throws RemoteException {
		Connection c = active();
		if (c == null) {
			return;
		}
		c.updateBooleanSetting("show_regex_warning", state);
	}

	@Override
	public String getPluginOption(String plugin, String key)
			throws RemoteException {
		//service.mConnections.get(service.mConnectionClutch).getPluginOptionValue(plugin,key);
		
		Connection c = active();
		if (c == null) {
			return "";
		}
		return c.getPluginOptionValue(plugin,key);
	}

	@Override
	public String getGmcpModuleStatus() throws RemoteException {
		Connection c = active();
		return c != null ? c.getGmcpModuleStatus() : "off";
	}

	@Override
	@SuppressWarnings("rawtypes")
	public java.util.List getGmcpSeenModules() throws RemoteException {
		Connection c = active();
		return c != null ? c.getGmcpSeenModules() : new java.util.ArrayList<String>();
	}

	@Override
	public void renegotiateGmcp() throws RemoteException {
		Connection c = active();
		if (c != null) {
			c.renegotiateGmcp();
		}
	}

	@Override
	public String getMcpStatusHint() throws RemoteException {
		Connection c = active();
		return c != null ? c.getMcpStatusHint() : "off";
	}

	@Override
	@SuppressWarnings("rawtypes")
	public java.util.List getMcpSeenPackages() throws RemoteException {
		Connection c = active();
		return c != null ? c.getMcpSeenPackages() : new java.util.ArrayList<String>();
	}

	@Override
	public void renegotiateMcp() throws RemoteException {
		Connection c = active();
		if (c != null && c.getMcpEngine() != null) {
			c.getMcpEngine().renegotiate();
		}
	}

	@Override
	public void sendMcpSimpleEditSet(String reference, String type, String content)
			throws RemoteException {
		Connection c = active();
		if (c != null) {
			c.sendMcpSimpleEditSet(reference, type, content);
		}
	}

	@Override
	public String getMapperSnapshotJson() throws RemoteException {
		Connection c = active();
		return c != null ? c.getMapperSnapshotJson() : "";
	}

	@Override
	public void requestMapperUi(int action) throws RemoteException {
		service.notifyMapperUi(action);
	}

	@Override
	public void requestMapperUiArg(int action, String arg) throws RemoteException {
		Connection c = active();
		if (c != null) {
			c.setMapperUiArg(arg);
		}
		service.notifyMapperUi(action);
	}

	@Override
	public String takeMapperUiArg() throws RemoteException {
		Connection c = active();
		return c != null ? c.takeMapperUiArg() : null;
	}

	@Override
	public String takeFrameEvents() throws RemoteException {
		Connection c = active();
		return c != null ? c.takeFrameEvents() : "[]";
	}

	@Override
	public String getOpenFramesJson() throws RemoteException {
		Connection c = active();
		return c != null ? c.getOpenFramesJson() : "[]";
	}

	@Override
	public boolean closeFrameByUser(String id) throws RemoteException {
		Connection c = active();
		return c != null && c.closeFrameByUser(id);
	}

	@Override
	public void reportFrameSize(String id, int widthPx, int heightPx) throws RemoteException {
		Connection c = active();
		if (c != null) {
			c.reportFrameSize(id, widthPx, heightPx);
		}
	}

	@Override
	public String getGaugeWidgetsJson() throws RemoteException {
		Connection c = active();
		return c != null ? c.getGaugeWidgetsJson() : "[]";
	}

	@Override
	public String getGaugeWidgetValuesJson() throws RemoteException {
		Connection c = active();
		return c != null ? c.getGaugeWidgetValuesJson() : "[]";
	}

}
