/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import android.os.Message;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/** GMCP settings, status, trigger loading, and message-handler bodies for a Connection. */
final class ConnectionGmcp {

	/** GMCP payload minimum size. */
	private static final int GMCP_PAYLOAD_SIZE = 5;

	/** 500 ms timeout, generic timeout or other. */
	private static final int FIVE_HUNDRED_MILLIS = 500;

	private final Connection host;

	ConnectionGmcp(final Connection host) {
		this.host = host;
	}

	/** Handle MESSAGE_GMCPTRIGGERED. */
	@SuppressWarnings("unchecked")
	void handleGmcpTriggered(final Message msg) {
		String plugin = msg.getData().getString("TARGET");
		String gcallback = msg.getData().getString("CALLBACK");
		HashMap<String, Object> gdata = (HashMap<String, Object>) msg.obj;
		Plugin gp = host.mPluginMap.get(plugin);
		gp.handleGMCPCallback(gcallback, gdata);
	}

	/** Handle MESSAGE_SENDGMCPDATA. */
	void handleSendGmcpData(final Message msg) {
		byte bIAC = TC.IAC;
		byte bSB = TC.SB;
		byte bSE = TC.SE;
		byte bGMCP = TC.GMCP;
		int size = ((String) msg.obj).length() + GMCP_PAYLOAD_SIZE;
		ByteBuffer fub = ByteBuffer.allocate(size);
		fub.put(bIAC).put(bSB).put(bGMCP);
		try {
			fub.put(((String) msg.obj).getBytes("ISO-8859-1"));
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}
		fub.put(bIAC).put(bSE);
		byte[] fubtmp = new byte[size];
		fub.rewind();
		fub.get(fubtmp);
		if (host.mProcessor != null && msg.obj instanceof String
				&& (host.mProcessor.isLogGMCP() || host.mProcessor.isDebugTelnet())) {
			BlowTorchLogger.logError(host.mService.getApplicationContext(), "GMCP", "OUT " + msg.obj);
		}
		if (host.mPump != null && host.mPump.isConnected()) {
			host.mPump.sendData(fubtmp);
		} else {
			host.mHandler.sendMessageDelayed(
					host.mHandler.obtainMessage(Connection.MESSAGE_SENDGMCPDATA, msg.obj),
					FIVE_HUNDRED_MILLIS);
		}
	}

	/** The gmcp trigger loading routine. This is pretty self explanatory, but it seeks out
	 * non-regex triggers that start witht he gmcp trigger char (default %) and tracks them 
	 * accordingly.
	 */
	void loadGMCPTriggers() {
		String gmcpChar = host.mSettings.getGMCPTriggerChar();
		for (int i = 0; i < host.mPlugins.size(); i++) {
			Plugin p = host.mPlugins.get(i);
			HashMap<String, TriggerData> triggers = p.getSettings().getTriggers();
			for (TriggerData t : triggers.values()) {
				if (!t.isInterpretAsRegex()) { //this actually means literal
					if (t.getPattern().startsWith(gmcpChar)) {
						//add it to the watch list, if it has a script responder
						for (TriggerResponder r : t.getResponders()) {
							if (r instanceof ScriptResponder) {
								ScriptResponder s = (ScriptResponder) r;
								String callback = s.getFunction();
								String module = t.getPattern().substring(1, t.getPattern().length());
								String name = p.getName();
								host.mProcessor.addWatcher(module, name, callback);
							}
						}
					}
				}
			}
		}
	}

	void applyGmcpLogSetting() {
		try {
			Object opt = host.mSettings.getSettings().getOptions().findOptionByKey("log_gmcp");
			boolean on = false;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
			if (host.mProcessor != null) {
				host.mProcessor.setLogGMCP(on);
			}
		} catch (Exception ignored) {
		}
		try {
			Object opt = host.mSettings.getSettings().getOptions().findOptionByKey("gmcp_feed");
			boolean on = false;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
			if (host.mProcessor != null) {
				host.mProcessor.setFeedGMCP(on);
			}
		} catch (Exception ignored) {
		}
		try {
			Object opt = host.mSettings.getSettings().getOptions().findOptionByKey("gmcp_suggest_modules");
			boolean on = false;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
			if (host.mProcessor != null) {
				host.mProcessor.setSuggestGmcpModules(on);
			}
		} catch (Exception ignored) {
		}
		host.applyMudProtocolFlags();
	}

	/** Implementation of the gmcp supports string setting handler. */
	void doSetGMCPSupports(final String value) {
		if (host.mProcessor != null) {
			host.mProcessor.setGMCPSupports(value);
		}
	}

	String getGmcpModuleStatus() {
		boolean use = false;
		try {
			BaseOption o = (BaseOption) host.mSettings.getSettings().getOptions().findOptionByKey("use_gmcp");
			use = o != null && o.getValue() instanceof Boolean && ((Boolean) o.getValue()).booleanValue();
		} catch (Exception ignored) {
		}
		String body;
		if (host.mProcessor != null) {
			body = host.mProcessor.getModuleRegistry().statusLine();
		} else {
			try {
				BaseOption s = (BaseOption) host.mSettings.getSettings().getOptions().findOptionByKey("gmcp_supports");
				GmcpModuleRegistry r = GmcpModuleRegistry.fromSupportsOption(
						s != null && s.getValue() != null ? s.getValue().toString() : GmcpModuleRegistry.DEFAULT_SUPPORTS);
				body = r.statusLine();
			} catch (Exception e) {
				body = "—";
			}
		}
		return (use ? "on" : "off") + " · " + body;
	}

	@SuppressWarnings("rawtypes")
	java.util.List getGmcpSeenModules() {
		if (host.mProcessor != null) {
			return host.mProcessor.getModuleRegistry().seenModules();
		}
		return new java.util.ArrayList<String>();
	}

	void renegotiateGmcp() {
		if (host.mProcessor != null) {
			host.mProcessor.renegotiateGMCP();
		}
	}

	void applyGmcpSupportsFromUi(final String supports, final boolean renegotiate) {
		host.updateStringSetting("gmcp_supports", supports != null ? supports : GmcpModuleRegistry.DEFAULT_SUPPORTS);
		if (renegotiate) {
			renegotiateGmcp();
		}
	}

	/** Implementation of the use gmcp settings handler. */
	void doSetUseGMCP(final Boolean value) {
		if (host.mProcessor != null) {
			host.mProcessor.setUseGMCP(value);
		}
	}

	void doSetLogGMCP(final Boolean value) {
		if (host.mProcessor != null) {
			host.mProcessor.setLogGMCP(value != null && value.booleanValue());
		}
	}

	void doSetGmcpFeed(final Boolean value) {
		if (host.mProcessor != null) {
			host.mProcessor.setFeedGMCP(value != null && value.booleanValue());
		}
	}

	void doSetGmcpSuggestModules(final Boolean value) {
		if (host.mProcessor != null) {
			host.mProcessor.setSuggestGmcpModules(value != null && value.booleanValue());
		}
	}
}
