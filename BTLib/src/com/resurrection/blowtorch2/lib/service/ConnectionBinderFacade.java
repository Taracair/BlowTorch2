/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
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
	public void registerCallback(final IConnectionBinderCallback c, final String host, final int port, final String display)
			throws RemoteException {
		if (c != null) {
			service.mCallbacks.register(c);

			if (!service.mConnections.containsKey(display)) {
				this.setConnectionData(host, port, display);
			} else {
				service.mConnectionClutch = display;
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
		Connection active = service.mConnections.get(service.mConnectionClutch);
		// Only skip when the socket is actually up. A zombie Looper after a failed
		// connect used to make isAlive()==true and block all further startups.
		if (active != null && active.isConnected()) {
			android.util.Log.i("BlowTorch", "initXfer skipped — already connected");
			return;
		}
		service.mHandler.sendEmptyMessage(StellarService.MESSAGE_STARTUP);
	}

	@Override
	public void endXfer() throws RemoteException {
		//doStartup();
		Connection c = service.mConnections.get(service.mConnectionClutch);
		c.sendDataToWindow("\n" + Colorizer.getRedColor() + "Connection terminated by user." + Colorizer.getWhiteColor() + "\n\n");
		c.killNetThreads(true);
		service.mConnections.get(service.mConnectionClutch).doDisconnect(true);
	}

	@Override
	public boolean isConnected() throws RemoteException {
		if (service.mConnections.size() < 1) {
			return false;
		}
		return service.mConnections.get(service.mConnectionClutch).isConnected();
	}

	@Override
	public void sendData(final byte[] seq) throws RemoteException {
		Handler handler = service.mConnections.get(service.mConnectionClutch).getHandler();
		handler.sendMessage(handler.obtainMessage(Connection.MESSAGE_SENDDATA_BYTES, seq));
	}

	@Override
	public void saveSettings() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		if (c == null && service.mConnections.size() == 1) {
			c = service.mConnections.values().iterator().next();
		}
		if (c == null) {
			return;
		}
		c.saveMainSettings();
	}

	@Override
	public void setConnectionData(final String host, final int port, final String display)
			throws RemoteException {
		Message msg = service.mHandler.obtainMessage(StellarService.MESSAGE_NEWCONENCTION);
		Bundle b = msg.getData();
		b.putString("DISPLAY", display);
		b.putString("HOST", host);
		b.putInt("PORT", port);
		msg.setData(b);
		service.mHandler.sendMessage(msg);
		
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getSystemCommands() throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getSystemCommands();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getAliases() throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getAliases();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setAliases(final Map map) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setAliases((HashMap<String, AliasData>) map);
	}

	@Override
	public void loadSettingsFromPath(final String path) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).startLoadSettingsSequence(path);
	}

	@Override
	public void exportSettingsToPath(final String path) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).exportSettings(path);
	}

	@Override
	public void resetSettings() throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).resetSettings();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getTriggerData() throws RemoteException {
		HashMap<String, TriggerData> triggers = service.mConnections.get(service.mConnectionClutch).getTriggers();
		
		return triggers;
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginTriggerData(final String id) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getPluginTriggers(id);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getDirectionData() throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getDirectionData();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setDirectionData(final Map data) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setDirectionData((HashMap<String, DirectionData>) data);
	}

	@Override
	public void newTrigger(final TriggerData data) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).addTrigger(data);
	}

	@Override
	public void updateTrigger(final TriggerData from, final TriggerData to)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateTrigger(from, to);
		
	}

	@Override
	public void deleteTrigger(final String which) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deleteTrigger(which);
	}

	@Override
	public TriggerData getTrigger(final String pattern) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getTrigger(pattern);
	}

	@Override
	public boolean isKeepLast() throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).isKeepLast();
	}

	@Override
	public void setDisplayDimensions(final int rows, final int cols)
			throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
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
		service.mConnections.get(connection).doReconnect();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getTimers() throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getTimers();
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginTimers(final String plugin) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getPluginTimers(plugin);
	}

	@Override
	public TimerData getTimer(final String ordinal) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getTimer(ordinal);
	}

	@Override
	public void startTimer(final String ordinal) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).playTimer(ordinal);
	}

	@Override
	public void pauseTimer(final String ordinal) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).pauseTimer(ordinal);
	}

	@Override
	public void stopTimer(final String ordinal) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).stopTimer(ordinal);
	}
	
	@Override
	public void startPluginTimer(final String plugin, final String ordinal) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).playPluginTimer(plugin, ordinal);
	}

	@Override
	public void pausePluginTimer(final String plugin, final String ordinal) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).pausePluginTimer(plugin, ordinal);
	}

	@Override
	public void stopPluginTimer(final String plugin, final String ordinal) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).stopPluginTimer(plugin, ordinal);
	}

	@Override
	public void updateTimer(final TimerData old, final TimerData newtimer)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateTimer(old, newtimer);
	}

	@Override
	public void addTimer(final TimerData newtimer) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).addTimer(newtimer);
	}

	@Override
	public void removeTimer(final TimerData deltimer) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deleteTimer(deltimer.getName());
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
		return (String) ((EncodingOption) service.mConnections.get(service.mConnectionClutch).getSettings().findOptionByKey("encoding")).getValue();
	}

	@Override
	public String getConnectedTo() throws RemoteException {
		return service.mConnectionClutch;
	}
	
	@Override
	public boolean isFullScreen() throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).isFullScren();
	}

	@Override
	public String getConnectionDurationText(final String display) throws RemoteException {
		return service.getConnectionDurationText(display);
	}
	
	@Override
	public void setTriggerEnabled(final boolean enabled, final String key)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setTriggerEnabled(enabled, key);
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

	@Override
	public WindowToken[] getWindowTokens() throws RemoteException {
		if (service.mConnections == null || service.mConnections.size() == 0) { return null; }
		return service.mConnections.get(service.mConnectionClutch).getWindows();
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
		return service.mConnections.get(service.mConnectionClutch).getScript(plugin, name);
	}

	@Override
	public void reloadSettings() throws RemoteException {
		service.mHandler.sendEmptyMessage(StellarService.MESSAGE_RELOADSETTINGS);
		
	}

	@Override
	public void pluginXcallS(final String plugin, final String function, final String str)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).pluginXcallS(plugin, function, str);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginList() throws RemoteException {
		
		Connection c = service.mConnections.get(service.mConnectionClutch);
		HashMap<String, String> list = new HashMap<String, String>();
		
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
		Connection c = service.mConnections.get(service.mConnectionClutch);
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
		service.mConnections.get(service.mConnectionClutch).newPluginTrigger(selectedPlugin, data);
	}

	@Override
	public void updatePluginTrigger(final String selectedPlugin,
			final TriggerData from, final TriggerData to) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updatePluginTrigger(selectedPlugin, from, to);
	}

	@Override
	public TriggerData getPluginTrigger(final String selectedPlugin, final String pattern)
			throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getPluginTrigger(selectedPlugin, pattern);
	}

	@Override
	public void setPluginTriggerEnabled(final String selectedPlugin,
			final boolean enabled, final String key) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setPluginTriggerEnabled(selectedPlugin, enabled, key);
	}

	@Override
	public void deletePluginTrigger(final String selectedPlugin, final String which)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deletePluginTrigger(selectedPlugin, which);
	}

	@Override
	public AliasData getAlias(final String key) throws RemoteException {
		
		return service.mConnections.get(service.mConnectionClutch).getAlias(key);
	}

	@Override
	public AliasData getPluginAlias(final String plugin, final String key)
			throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getPluginAlias(plugin, key);
	}

//		@SuppressWarnings("rawtypes")
//		public Map getAliases(final String currentPlugin) throws RemoteException {
//			
//			return service.mConnections.get(service.mConnectionClutch).getAliases();
//		}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Map getPluginAliases(final String currentPlugin) {
		return service.mConnections.get(service.mConnectionClutch).getPluginAliases(currentPlugin);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void setPluginAliases(final String plugin, final Map map)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setPluginAliases(plugin, (HashMap<String, AliasData>) map);
	}

	@Override
	public void deleteAlias(final String key) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deleteAlias(key);
	}

	@Override
	public void deletePluginAlias(final String plugin, final String key)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deletePluginAlias(plugin, key);
	}

	@Override
	public void setAliasEnabled(final boolean enabled, final String key)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setAliasEnabled(enabled, key);
		
	}

	@Override
	public void setPluginAliasEnabled(final String plugin, final boolean enabled,
			final String key) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).setPluginAliasEnabled(plugin, enabled, key);
	}

	@Override
	public TimerData getPluginTimer(final String plugin, final String name) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getPluginTimer(plugin, name);
	}

	@Override
	public void deleteTimer(final String name) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deleteTimer(name);
	}

	@Override
	public void deletePluginTimer(final String plugin, final String name)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deletePluginTimer(plugin, name);
	}

	@Override
	public void updatePluginTimer(final String plugin, final TimerData old,
			final TimerData newtimer) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updatePluginTimer(plugin, old, newtimer);
	}

	@Override
	public void addPluginTimer(final String plugin, final TimerData newtimer)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).addPluginTimer(plugin, newtimer);
	}

	@Override
	public SettingsGroup getSettings() throws RemoteException {
		if (service.mConnections.size() == 0) { return null; }
		Connection c = service.mConnections.get(service.mConnectionClutch);
		if (c == null) { return null; }
		return c.getSettings();
	}

	@Override
	public SettingsGroup getPluginSettings(final String plugin)
			throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).getPluginSettings(plugin);
	}

	@Override
	public void updateBooleanSetting(final String key, final boolean value)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateBooleanSetting(key, value);
	}

	@Override
	public void updatePluginBooleanSetting(final String plugin, final String key,
			final boolean value) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updatePluginBooleanSetting(plugin, key, value);
	}

	@Override
	public void updateIntegerSetting(final String key, final int value)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateIntegerSetting(key, value);
	}

	@Override
	public void updatePluginIntegerSetting(final String plugin, final String key,
			final int value) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updatePluginIntegerSetting(plugin, key, value);
	}

	@Override
	public void updateFloatSetting(final String key, final float value)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateFloatSetting(key, value);
	}

	@Override
	public void updatePluginFloatSetting(final String plugin, final String key,
			final float value) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updatePluginFloatSetting(plugin, key, value);
	}

	@Override
	public void updateStringSetting(final String key, final String value)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateStringSetting(key, value);
	}

	@Override
	public void updatePluginStringSetting(final String plugin, final String key,
			final String value) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updatePluginStringSetting(plugin, key, value);
	}

	@Override
	public void updateWindowBufferMaxValue(final String plugin, final String window,
			final int amount) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateWindowBufferMaxValue(plugin, window, amount);
	}
	
	@Override
	public void closeConnection(final String display) {
		Connection c = service.mConnections.get(display);
		if (c != null) {
			c.shutdown();
		
			service.mConnections.remove(display);
		}
	}
	
	@Override
	public void windowShowing(final boolean show) {
		service.mWindowShowing = show;
	}

	@Override
	public void dispatchLuaError(final String message) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).dispatchLuaError(message);
	}
	
	@Override
	public void addLink(final String path) {
		service.mConnections.get(service.mConnectionClutch).addLink(path);
	}

	@Override
	public void deletePlugin(final String plugin) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).deletePlugin(plugin);
	}

	@Override
	public boolean setPluginEnabled(final String plugin, final boolean enabled)
			throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).setPluginEnabled(plugin, enabled);
	}

	@Override
	public boolean isPluginEnabled(final String plugin) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).isPluginEnabled(plugin);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getPluginsWithAliases() {
		ArrayList<String> list = new ArrayList<String>();
		Connection c = service.mConnections.get(service.mConnectionClutch);
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
		Connection c = service.mConnections.get(service.mConnectionClutch);
		for (Plugin p : c.getPlugins()) {
			if (p.getSettings().getTimers().size() > 0) {
				list.add(p.getName());
			}
		}
		return list;
	}

	@Override
	public boolean isLinkLoaded(final String link) throws RemoteException {
		boolean retval = service.mConnections.get(service.mConnectionClutch).isLinkLoaded(link);
		return retval;
	}

	@Override
	public String getPluginPath(final String plugin) throws RemoteException {
		String path = service.mConnections.get(service.mConnectionClutch).getPluginPath(plugin);
		if (path == null) { path = ""; }
		return path;
	}

	@Override
	public void dispatchLuaText(final String str) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).dispatchLuaText(str);
	}

	@Override
	public void callPluginFunction(final String plugin, final String function)
			throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).callPluginFunction(plugin, function);
	}

	@Override
	public boolean isPluginInstalled(final String desired) throws RemoteException {
		return service.mConnections.get(service.mConnectionClutch).isPluginInstalled(desired);
	}

	@Override
	public void setShowRegexWarning(boolean state) throws RemoteException {
		service.mConnections.get(service.mConnectionClutch).updateBooleanSetting("show_regex_warning", state);
	}

	@Override
	public String getPluginOption(String plugin, String key)
			throws RemoteException {
		//service.mConnections.get(service.mConnectionClutch).getPluginOptionValue(plugin,key);
		
		return service.mConnections.get(service.mConnectionClutch).getPluginOptionValue(plugin,key);
	}

	@Override
	public String getGmcpModuleStatus() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		return c != null ? c.getGmcpModuleStatus() : "off";
	}

	@Override
	@SuppressWarnings("rawtypes")
	public java.util.List getGmcpSeenModules() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		return c != null ? c.getGmcpSeenModules() : new java.util.ArrayList<String>();
	}

	@Override
	public void renegotiateGmcp() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		if (c != null) {
			c.renegotiateGmcp();
		}
	}

	@Override
	public String getMcpStatusHint() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		return c != null ? c.getMcpStatusHint() : "off";
	}

	@Override
	@SuppressWarnings("rawtypes")
	public java.util.List getMcpSeenPackages() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		return c != null ? c.getMcpSeenPackages() : new java.util.ArrayList<String>();
	}

	@Override
	public void renegotiateMcp() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		if (c != null && c.getMcpEngine() != null) {
			c.getMcpEngine().renegotiate();
		}
	}

	@Override
	public void sendMcpSimpleEditSet(String reference, String type, String content)
			throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		if (c != null) {
			c.sendMcpSimpleEditSet(reference, type, content);
		}
	}

	@Override
	public String getMapperSnapshotJson() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		return c != null ? c.getMapperSnapshotJson() : "";
	}

	@Override
	public void requestMapperUi(int action) throws RemoteException {
		service.notifyMapperUi(action);
	}

	@Override
	public void requestMapperUiArg(int action, String arg) throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		if (c != null) {
			c.setMapperUiArg(arg);
		}
		service.notifyMapperUi(action);
	}

	@Override
	public String takeMapperUiArg() throws RemoteException {
		Connection c = service.mConnections.get(service.mConnectionClutch);
		return c != null ? c.takeMapperUiArg() : null;
	}

}
