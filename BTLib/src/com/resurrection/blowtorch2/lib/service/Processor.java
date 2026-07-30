/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.json.JSONObject;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/** Class implementation for the telnet state machine. */
public class Processor {
	/** Skippable bytes in the state machine for case 1. */
	private static final int SKIP_BYTES = 3;
	/** Telnet SUB payload byte count. */
	private static final int PAYLOAD_BYTES = 5;
	/** Handler object to dispatch results to. */
	private Handler mReportTo = null;	
	/** Negotiation sublayer object. */
	private OptionNegotiator mOptionHandler;
	/** Selected encoding to use. */
	private String mEncoding = null;
	/** Application context. */
	private Context mContext = null;
	/** Weather or not to display telnet debugging messages. */
	private boolean mDebugTelnet = false;
	/** When true, GMCP handshake/packets go to logcat (+ session log if on). */
	private boolean mLogGMCP = false;
	/** When true, echo GMCP IN/OUT lines into the game window. */
	private boolean mFeedGMCP = false;
	/** Optional toast when server sends a module not in Supports.Set (default off). */
	private boolean mSuggestGmcpModules = false;
	/** Optional profile label for session-log GMCP lines. */
	private String mLogProfile = "session";
	/** Holdover sequence buffer. Used when a telnet negotation spans a transmission boundary. */
	private byte[] mHoldover = null;
	/** GMCP Data holder object. */
	private GMCPData mGMCP = null;
	/** List of GMCP Triggers. */
	private HashMap<String, ArrayList<GMCPWatcher>> mGMCPTriggers = new HashMap<String, ArrayList<GMCPWatcher>>();
	/** GMCP Hello string (version filled from package versionName). */
	private String mGMCPHello = "core.hello {\"client\": \"BlowTorch\",\"version\": \"2.1.13\"}";
	/** Tracker for weather or not the use GMCP. */
	private Boolean mUseGMCP = false;
	/** GMCP Supports string. */
	private String mGMCPSupports = "core.supports.set [\"Char 1\", \"Room 1\", \"Core 1\", \"Char.Login 1\", \"Client.Media 1\"]";
	/** Native Client.Media player (null until first use / init). */
	private GmcpMediaPlayer mMediaPlayer = null;
	/** Native Char.Login handler. */
	private GmcpCharLogin mCharLogin = null;
	/** Module catalog / enabled / seen for this connection. */
	private final GmcpModuleRegistry mModuleRegistry = new GmcpModuleRegistry();
	/** Optional MSDP/MSSP stores (populated only when those protocols are enabled). */
	private final MudProtocolData mMudProtocols = new MudProtocolData();
	/** Profile display name for Char.Login credential lookup. */
	private String mDisplayName = "";
	/** Constructor.
	 * 
	 * @param useme reporting handler target.
	 * @param pEncoding selected encoding to use.
	 * @param pContext application content.
	 */
	public Processor(final Handler useme, final String pEncoding, final Context pContext) {
		mReportTo = useme;

		mContext = pContext;
		String ttype = ConfigurationLoader.getConfigurationValue("terminalTypeString", mContext);
		mOptionHandler = new OptionNegotiator(ttype);
		mGMCP = new GMCPData();
		setEncoding(pEncoding);
		rebuildGmcpHello();
	}

	/** Refresh Core.Hello version from the installed APK versionName. */
	private void rebuildGmcpHello() {
		String ver = "2.1.13";
		if (mContext != null) {
			try {
				ver = mContext.getPackageManager()
						.getPackageInfo(mContext.getPackageName(), 0).versionName;
			} catch (Exception ignored) {
			}
		}
		if (ver == null || ver.length() == 0) {
			ver = "2.1.13";
		}
		mGMCPHello = "core.hello {\"client\": \"BlowTorch\",\"version\": \"" + ver + "\"}";
	}

	public final String getGmcpHello() {
		return mGMCPHello;
	}

	/** Profile display name used to resolve Char.Login credentials from the launcher list. */
	public final void setDisplayName(final String displayName) {
		mDisplayName = displayName != null ? displayName : "";
		if (mCharLogin != null) {
			// Force reload on next Default if display changes.
			mCharLogin = null;
		}
	}

	public final void setLoginCredentials(final String account, final String password) {
		ensureCharLogin();
		mCharLogin.setCredentials(account, password);
	}

	/** Getter for mDebugTelnet.
	 * 
	 * @return mDebugTelnet
	 */
	public final boolean isDebugTelnet() {
		return mDebugTelnet;
	}

	/** Setter for mDebugTelnet.
	 * 
	 * @param debugTelnet value for mDebugTelnet
	 */
	public final void setDebugTelnet(final boolean debugTelnet) {
		mDebugTelnet = debugTelnet;
	}

	public final void setLogGMCP(final boolean logGMCP) {
		mLogGMCP = logGMCP;
	}

	public final void setFeedGMCP(final boolean feedGMCP) {
		mFeedGMCP = feedGMCP;
	}

	public final boolean isFeedGMCP() {
		return mFeedGMCP;
	}

	public final void setSuggestGmcpModules(final boolean suggest) {
		mSuggestGmcpModules = suggest;
	}

	public final boolean isLogGMCP() {
		return mLogGMCP;
	}

	public final void setLogProfile(final String profile) {
		mLogProfile = (profile == null || profile.length() == 0) ? "session" : profile;
	}

	/** Optional patterns that route inbound GMCP into extra text windows (suppress main feed). */
	private final java.util.ArrayList<String> mGmcpExtraRoutePatterns =
			new java.util.ArrayList<String>();

	private void logGmcp(final String direction, final String payload) {
		if (!mLogGMCP && !mDebugTelnet && !mFeedGMCP) {
			return;
		}
		String safe = payload == null ? "" : payload;
		// Never write cleartext Char.Login passwords to the app log / window.
		if (safe.toLowerCase(java.util.Locale.US).contains("char.login.credentials")) {
			safe = safe.replaceAll("(?i)(\"password\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
		}
		String line = direction + " " + safe;
		if (mLogGMCP || mDebugTelnet) {
			Log.i("GMCP", line);
			if (mContext != null && mLogGMCP) {
				// Its own file, not the error log: a trace of every packet used to
				// roll the crash history away under it. Logcat is fine for watching
				// live, but a player on a phone cannot read logcat, and the option
				// promised a file, so here is the file.
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger
						.logGmcpTrace(mContext, line);
				if (SessionLogger.isEnabled(mContext)) {
					SessionLogger.appendMarker(mContext, mLogProfile, "GMCP " + line);
				}
			}
		}
		if (mFeedGMCP && mReportTo != null) {
			// Inbound modules routed to an extra text window are not also dumped in main.
			if ("IN".equals(direction) && isGmcpRoutedToExtraWindow(safe)) {
				return;
			}
			String shown = safe;
			if (shown.length() > 360) {
				shown = shown.substring(0, 360) + "…";
			}
			String msg = "\n" + Colorizer.getTeloptStartColor()
					+ "[GMCP " + direction + "] " + shown
					+ Colorizer.getResetColor() + "\n";
			mReportTo.sendMessageDelayed(
					mReportTo.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, msg), 1);
		}
	}

	/**
	 * Patterns from extra-text slot {@code gmcp} lists. When a packet matches,
	 * {@link #mFeedGMCP} skips echoing it into the main mud window (the pane gets it).
	 */
	public final void setGmcpExtraRoutePatterns(final java.util.List<String> patterns) {
		mGmcpExtraRoutePatterns.clear();
		if (patterns == null) {
			return;
		}
		for (int i = 0; i < patterns.size(); i++) {
			String p = patterns.get(i);
			if (p != null) {
				String t = p.trim();
				if (t.length() > 0 && !mGmcpExtraRoutePatterns.contains(t)) {
					mGmcpExtraRoutePatterns.add(t);
				}
			}
		}
	}

	private boolean isGmcpRoutedToExtraWindow(final String payload) {
		if (mGmcpExtraRoutePatterns.isEmpty() || payload == null || payload.length() == 0) {
			return false;
		}
		String whole = payload.trim();
		int sp = whole.indexOf(' ');
		String module = sp < 0 ? whole : whole.substring(0, sp);
		if (module.length() == 0) {
			return false;
		}
		for (int i = 0; i < mGmcpExtraRoutePatterns.size(); i++) {
			if (com.resurrection.blowtorch2.lib.window.ExtraTextSlot.patternMatchesModule(
					mGmcpExtraRoutePatterns.get(i), module)) {
				return true;
			}
		}
		return false;
	}
	
	/** The main processing routine.
	 * 
	 * @param data The data to process.
	 * @return The processed data minus telnet data.
	 */
	public final byte[] rawProcess(final byte[] data) {
		if (data == null) {
			return null;
		}

		// Re-assemble incomplete IAC sequences that spanned packet boundaries.
		byte[] input = data;
		if (mHoldover != null) {
			ByteBuffer combined = ByteBuffer.allocate(mHoldover.length + data.length);
			combined.put(mHoldover);
			combined.put(data);
			mHoldover = null;
			input = combined.array();
		}

		if (input.length == 1 && input[0] == TC.IAC) {
			mHoldover = new byte[] { TC.IAC };
			return null;
		}

		ByteBuffer buff = ByteBuffer.allocate(input.length);
		ByteBuffer opbuf = ByteBuffer.allocate(input.length * 2);

		int count = 0; // count of the number of bytes in the buffer;
		for (int i = 0; i < input.length; i++) {
			switch (input[i]) {
			case TC.IAC:
				if (i + 1 >= input.length) {
					mHoldover = new byte[] { TC.IAC };
					return truncBuffer(buff, count);
				}
				if ((input[i + 1] >= TC.WILL && input[i + 1] <= TC.DONT)
						|| input[i + 1] == TC.SB) {
					if (input[i + 1] == TC.SB) {
						// Need at least IAC SB <option>
						if (i + 2 >= input.length) {
							mHoldover = Arrays.copyOfRange(input, i, input.length);
							return truncBuffer(buff, count);
						}
						// Scan for IAC SE, honoring escaped IAC IAC in the payload.
						boolean done = false;
						int j = i + SKIP_BYTES;
						while (j + 1 < input.length) {
							if (input[j] == TC.IAC) {
								if (input[j + 1] == TC.SE) {
									done = true;
									break;
								} else if (input[j + 1] == TC.IAC) {
									j += 2; // literal 0xFF in SB data
									continue;
								}
								// Unexpected IAC command inside SB — skip the IAC byte.
								j += 1;
								continue;
							}
							j++;
						}
						if (!done) {
							mHoldover = Arrays.copyOfRange(input, i, input.length);
							return truncBuffer(buff, count);
						}
						opbuf = ByteBuffer.allocate(j - (i + SKIP_BYTES) + PAYLOAD_BYTES);
						opbuf.put(TC.IAC);
						opbuf.put(input[i + 1]);
						opbuf.put(input[i + 2]);
						if (j - (i + SKIP_BYTES) > 0) {
							for (int q = i + SKIP_BYTES; q < j; q++) {
								opbuf.put(input[q]);
							}
						}
						opbuf.put(TC.IAC);
						opbuf.put(TC.SE);

						opbuf.rewind();
						boolean compress = dispatchSUB(opbuf.array());
						if (compress) {
							ByteBuffer b = ByteBuffer.allocate(input.length - (j + 2 - i));
							for (int z = j + 2; z < input.length; z++) {
								b.put(input[z]);
							}

							b.rewind();
							mReportTo.sendMessageAtFrontOfQueue(mReportTo
									.obtainMessage(
											Connection.MESSAGE_STARTCOMPRESS,
											b.array()));
							if (mDebugTelnet) {
								String message = "\n" + Colorizer.getTeloptStartColor() + "IN:[IAC SB COMPRESS2 IAC SE] -BEGIN COMPRESSION-" + Colorizer.getResetColor() + "\n";
								mReportTo.sendMessageDelayed(mReportTo.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message), 1);
							}
							return truncBuffer(buff, count);

						} else {
							// Advance past IAC SE (for-loop will i++).
							i = j + 1;
						}
					} else {
						// WILL/WONT/DO/DONT require the option byte.
						if (i + 2 >= input.length) {
							mHoldover = Arrays.copyOfRange(input, i, input.length);
							return truncBuffer(buff, count);
						}
						dispatchIAC(input[i + 1], input[i + 2]);
						i = i + 2;
					}
				} else {

					switch (input[i + 1]) {
					case TC.IAC:
						buff.put(input[i]); // keep one IAC and consume the extra.
						count++;
						i++;
						break;
					case TC.GOAHEAD:
					case TC.IP:
						// TODO: REAL IP HANDLING HERE, I THINK THIS INVOLVES
						// SETTING THE CURSOR BACK TO A PLACE OR SOMETHING
					case TC.BREAK:
					case TC.AO:
						// i think this one is more for us to send to the
						// server.
					case TC.EC:
						// TODO: REAL ERASE CHARACTER
					case TC.EL:
						// TODO: REAL ERASE LINE
					case TC.AYT:
						i++; // consume the byte.
						break;
					default:
						// everything else keep
						break;
					}
				}
				break;
			case TC.BELL:
				mReportTo.sendEmptyMessage(Connection.MESSAGE_BELLINC);
				break;
			case TC.CARRIAGE:
				//strip carriage returns
				break;
			default:
				buff.put(input[i]);
				count++;
				break;
			}

		}
		
		return truncBuffer(buff, count);
		
	}

	/** Copy the first {@code count} bytes out of {@code buff}. */
	private static byte[] truncBuffer(final ByteBuffer buff, final int count) {
		byte[] trunc = new byte[count];
		if (count > 0) {
			buff.rewind();
			buff.get(trunc, 0, count);
		}
		return trunc;
	}

	/** Telnet negotiation sequence.
	 * 
	 * @param action The action byte (WILL, WONT, DO, DONT)
	 * @param option The numeric indicator of the telnet negotiation type (TTYPE, GMCP, ECHO ...)
	 */
	public final void dispatchIAC(final byte action, final byte option) {
		
		byte[] resp = mOptionHandler.processCommand(TC.IAC, action, option);
		Message sb = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA, resp);
		if (resp.length > 2) {
			if (resp[2] == TC.NAWS) {
				//naws has started.
				disaptchNawsString();
			}
			
		}
		Bundle b = sb.getData();
		b.putByteArray("THE_DATA", resp);
		String message = null;
		if (mDebugTelnet) {
			message = Colorizer.getTeloptStartColor() + "IN:[" + TC.decodeIAC(new byte[]{TC.IAC, action, option}) + "]" + " ";
			message += Colorizer.getTeloptStartColor() + "OUT:[" + TC.decodeIAC(resp) + "]" + Colorizer.getResetColor() + "\n";
		}
		b.putString("DEBUG_MESSAGE", message);
		sb.setData(b);
		mReportTo.sendMessage(sb);
		
		if (action == TC.WILL && option == TC.GMCP) {
			logGmcp("NEG", "IAC WILL GMCP → " + (mUseGMCP ? "DO + hello/supports" : "DONT (use_gmcp off)"));
			//so we are responding accordingly, but we want to "initialize" the gmcp
			if (mUseGMCP) {
				initGMCP();
			}
		}

		if (action == TC.WILL && option == TC.CHARSET) {
			// Agree to CHARSET and prefer UTF-8, but do NOT send a client REQUEST.
			// Some MUDs (incl. eden-test) treat an unsolicited REQUEST poorly.
			setEncoding("UTF-8");
			mReportTo.sendMessage(mReportTo.obtainMessage(Connection.MESSAGE_CHARSET, "UTF-8"));
		}
		
	}
	
	/** Telnet subnegotiation handler. 
	 * 
	 * @param negotiation the subnegotiation sequence.
	 * @return I think the return value here means start compression. But that would be bad.
	 */
	public final boolean dispatchSUB(final byte[] negotiation) {
		byte[] sub = mOptionHandler.getSubnegotiationResponse(negotiation);

		if (sub == null) {
			return false;
		} 
		
		// special handling for the compression marker.
		byte[] compressresp = new byte[1];
		compressresp[0] = TC.COMPRESS2;

		if (sub[0] == compressresp[0]) {
			return true;
		} else if (sub[0] == TC.GMCP) {
			if (mDebugTelnet) {
				String message = "\n" + Colorizer.getTeloptStartColor() + "IN:[" + TC.decodeSUB(negotiation) + "]" + Colorizer.getResetColor() + "\n";
				mReportTo.sendMessageDelayed(mReportTo.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message), 1);
			}
			byte[] foo = new byte[negotiation.length - PAYLOAD_BYTES];
			ByteBuffer wrap = ByteBuffer.wrap(negotiation);
			wrap.rewind();
			wrap.position(SKIP_BYTES);
			wrap.get(foo, 0, negotiation.length - PAYLOAD_BYTES);
			try {
				String whole = new String(foo, "UTF-8").trim();
				logGmcp("IN", whole);
				int split = whole.indexOf(' ');
				String module;
				String data;
				if (split < 0) {
					module = whole;
					data = null;
				} else {
					module = whole.substring(0, split);
					data = whole.substring(split + 1).trim();
				}
				String suggested = mModuleRegistry.noteSeen(module);
				if (mSuggestGmcpModules && suggested != null && mReportTo != null) {
					mReportTo.sendMessageDelayed(mReportTo.obtainMessage(
							Connection.MESSAGE_PROCESSORWARNING,
							"GMCP seen (not enabled): " + suggested
									+ " — Options → Manage modules… or .gmcp enable "
									+ suggested), 1);
				}
				// A GMCP body is any JSON value, not always an object. This used to
				// call new JSONObject(data) on whatever arrived, so an array — the
				// shape our own core.supports.set uses — produced a red
				// "[GMCP ERR] parse failed" line and the packet was dropped.
				// GmcpBody decides the shape first, and has the tests.
				GmcpBody body = GmcpBody.of(data);
				switch (body.shape()) {
				case OBJECT:
					mGMCP.absorb(module, body.object());
					dispatchNativeGmcp(module, body.object());
					break;
				case ABSENT:
					dispatchNativeGmcp(module, new JSONObject());
					break;
				case ARRAY:
					// The GMCP table is a tree of named nodes with nowhere to put a
					// bare list, and no native handler takes one, so an array is
					// recorded where it means something and passed on as raw JSON
					// rather than absorbed. Lua GMCP triggers on a module that only
					// ever sends arrays therefore see exactly what they saw before:
					// getTable() found nothing then and finds nothing now.
					noteSupportsList(module, body);
					break;
				case SCALAR:
					// Legal JSON with nothing to store. Not worth an error line.
					break;
				default:
					Log.e("GMCP", "GMCP PARSING FOR: " + body.raw());
					Log.e("GMCP", "REASON: " + body.error());
					logGmcp("ERR", "parse failed for " + module + ": " + body.error());
					break;
				}
				dispatchGmcpExtraText(module, body.json());
				
				//TODO: THIS IS WHERE THE ACTUAL WORK IS DONE TO SEND MUD DATA.
				ArrayList<GMCPWatcher> list = mGMCPTriggers.get(module);
				if (list != null) {
					for (int i = 0; i < list.size(); i++) {
						GMCPWatcher tmp = list.get(i);
						HashMap<String, Object> tmpdata = mGMCP.getTable(module);
						Message gmsg = mReportTo.obtainMessage(Connection.MESSAGE_GMCPTRIGGERED, tmpdata);
						gmsg.getData().putString("TARGET", tmp.mPlugin);
						gmsg.getData().putString("CALLBACK", tmp.mCallback);
						mReportTo.sendMessage(gmsg);
					}
				}				
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			
			
			return false;
		} else if (sub[0] == TC.MSDP || sub[0] == TC.MSSP) {
			handleMsdpOrMssp(negotiation, sub[0]);
			return false;
		} else if (sub.length == 1 && sub[0] == TC.CHARSET) {
			// CHARSET ACCEPTED/REJECTED from server — apply pending encoding, no reply.
			applyPendingCharset();
			if (mDebugTelnet) {
				String message = "\n" + Colorizer.getTeloptStartColor() + "IN:[" + TC.decodeSUB(negotiation) + "]" + Colorizer.getResetColor() + "\n";
				mReportTo.sendMessageDelayed(mReportTo.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message), 1);
			}
			return false;
		} else {
			String message = null;
			if (mDebugTelnet) {
				message = Colorizer.getTeloptStartColor() + "IN:[" + TC.decodeSUB(negotiation) + "]" + " ";
				message += Colorizer.getTeloptStartColor() + "OUT:[" + TC.decodeSUB(sub) + "]" + Colorizer.getResetColor() + "\n";
			}
			Message sbm = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
			Bundle b = sbm.getData();
			b.putByteArray("THE_DATA", sub);
			b.putString("DEBUG_MESSAGE", message);
			sbm.setData(b);
			mReportTo.sendMessage(sbm);
			// Our ACCEPTED reply to a REQUEST also sets pending charset.
			if (sub.length > 3 && sub[2] == TC.CHARSET && sub[3] == TC.CHARSET_ACCEPTED) {
				applyPendingCharset();
			}
			return false;
		}
		
		
	}

	/** Notify Connection to switch display encoding after CHARSET negotiation. */
	private void applyPendingCharset() {
		String charset = mOptionHandler.consumePendingCharset();
		if (charset == null || charset.length() == 0) {
			return;
		}
		setEncoding(charset);
		mReportTo.sendMessage(mReportTo.obtainMessage(Connection.MESSAGE_CHARSET, charset));
	}

	/** Setter for mEncoding.
	 * 
	 * @param encoding Selected encoding.
	 */
	public final void setEncoding(final String encoding) {
		this.mEncoding = encoding;
	}

	/** Getter for mEncoding.
	 * 
	 * @return The currently selected encoding.
	 */
	public final String getEncoding() {
		return mEncoding;
	}

	/** Helper method for NAWS.
	 * 
	 * @param rows Rows in display.
	 * @param cols Columns in display.
	 */
	public final void setDisplayDimensions(final int rows, final int cols) {
		mOptionHandler.setColumns(cols);
		mOptionHandler.setRows(rows);
	}

	/** Helper method for naws. This may happen because the foreground window changed shape. */
	public final void disaptchNawsString() {
		byte[] nawsout = mOptionHandler.getNawsString();
		if (nawsout == null) {
			return;
		}
		Message sbm = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
		Bundle b = sbm.getData();
		b.putByteArray("THE_DATA", nawsout);
		
		String message = null;
		if (mDebugTelnet) {
			message = Colorizer.getTeloptStartColor() + "OUT:[" + TC.decodeSUB(nawsout) + "]" + Colorizer.getResetColor() + "\n";
		}
		b.putString("DEBUG_MESSAGE", message);
		sbm.setData(b);
		mReportTo.sendMessageDelayed(sbm, 2);
		return;
	}

	/** Reset method, this is called when the settings have been foreably reloaded. */
	public final void reset() {
		mOptionHandler.reset();
	}
	
	/** Helper method to get a GMCP module quickly.
	 * 
	 * @param str The module to get?
	 * @return The table of data?
	 */
	public final Object getGMCPValue(final String str) {
		return mGMCP.get(str);
	}
	
	/** Helper method to get a GMCP table for a given path.
	 * 
	 * @param path The module path, e.g. char.vitals.hp
	 * @return The mapping of objects representing the gmcp table at the desired path.
	 */
	public final HashMap<String, Object> getGMCPTable(final String path) {
		return mGMCP.getTable(path);
	}
	
	/** Utility method to initialize GMCP. */
	public final void initGMCP() {
		rebuildGmcpHello();
		ensureMediaPlayer();
		ensureCharLogin();
		mModuleRegistry.setLastSupportsSet(mGMCPSupports);
		try {
			byte[] hellob = getGMCPResponse(mGMCPHello);
			byte[] supportb = getGMCPResponse(mGMCPSupports);
			logGmcp("OUT", mGMCPHello);
			logGmcp("OUT", mGMCPSupports);
			
			String hello = Colorizer.getTeloptStartColor() + "OUT:[" + TC.decodeSUB(hellob) + "]" + Colorizer.getResetColor() + "\n";
			String supports = Colorizer.getTeloptStartColor() + "OUT:[" + TC.decodeSUB(supportb) + "]" + Colorizer.getResetColor() + "\n";
			
			Message hm = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
			Bundle bh = hm.getData();
			bh.putByteArray("THE_DATA", hellob);
			if (mDebugTelnet) {
				bh.putString("DEBUG_MESSAGE", hello);
			}
			hm.setData(bh);
			mReportTo.sendMessage(hm);
			
			Message sm = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
			Bundle bs = sm.getData();
			bs.putByteArray("THE_DATA", supportb);
			if (mDebugTelnet) {
				bs.putString("DEBUG_MESSAGE", supports);
			}
			sm.setData(bs);
			mReportTo.sendMessage(sm);

			// Inside the try on purpose: if Supports.Set never went out, there is
			// nothing for a frame announcement to follow.
			announceMudstdFrameSupport();

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/** The GMCP package name, spelled as the specification spells it. */
	private static final String MUDSTD_FRAME_MODULE = MudstdFrame.MODULE;

	/**
	 * What BlowTorch can host if a server asks. See {@link MudstdFrame}: only
	 * what actually works goes on the wire, which today is a floating frame
	 * carrying terminal content.
	 */
	private static final String MUDSTD_FRAME_SUPPORT = MudstdFrame.supportMessage();

	/**
	 * Volunteer our frame capabilities, immediately behind Core.Supports.Set so
	 * that it arrives on the same queue in that order.
	 *
	 * <p>Only when the module is switched on in Manage modules…. It is off by
	 * default: this answers frame.support, but nothing yet draws what frame.open
	 * would ask for, and claiming otherwise to every server would be a lie.
	 */
	private void announceMudstdFrameSupport() {
		if (mModuleRegistry == null || !mModuleRegistry.isEnabled(MUDSTD_FRAME_MODULE)) {
			return;
		}
		sendGmcpPacket(MUDSTD_FRAME_SUPPORT);
	}

	public final GmcpModuleRegistry getModuleRegistry() {
		return mModuleRegistry;
	}

	/** Re-send Hello + Supports without full reconnect. */
	public final void renegotiateGMCP() {
		if (!mUseGMCP) {
			return;
		}
		initGMCP();
	}

	/** Stop Client.Media audio without tearing down helpers (e.g. task removed). */
	public final void stopGmcpMedia() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stopAllImmediatePublic();
		}
	}

	/** Release Client.Media players (call on disconnect). */
	public final void releaseGmcpHelpers() {
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		if (mCharLogin != null) {
			mCharLogin.release();
			mCharLogin = null;
		}
		mModuleRegistry.clearSeen();
		mMudProtocols.clearMsdp();
		mMudProtocols.clearMssp();
	}

	/**
	 * Record a supports list a server sent us.
	 *
	 * <p>There is no standard "ask a server what it supports" call in GMCP, so
	 * this is the only way we ever learn: a server that mirrors the shape of our
	 * own {@code core.supports.set} back at us, as
	 * {@code Server.Supports.Set ["Core 1", …]}. Until the array shape was
	 * decoded at all, that list went in the bin with the packet.
	 *
	 * <p>Matched on the tail of the module name rather than one server's
	 * spelling, and it never enables anything: what we ask a server for stays the
	 * player's choice. It surfaces in {@code .gmcp ask}.
	 */
	private void noteSupportsList(final String module, final GmcpBody body) {
		if (mModuleRegistry == null || module == null) {
			return;
		}
		if (!module.toLowerCase(java.util.Locale.US).endsWith("supports.set")) {
			return;
		}
		ArrayList<String> tokens = body.asStringList();
		if (tokens.isEmpty()) {
			return;
		}
		mModuleRegistry.setServerSupports(tokens);
		logGmcp("INFO", module + " understood: " + tokens.size()
				+ " module(s) the server offers — see .gmcp ask");
	}

	private void dispatchNativeGmcp(final String module, final JSONObject body) {
		if (module == null) {
			return;
		}
		String lower = module.toLowerCase(java.util.Locale.US);
		if (lower.startsWith("client.media")) {
			ensureMediaPlayer();
			mMediaPlayer.handle(module, body);
		} else if (lower.startsWith("char.login")) {
			ensureCharLogin();
			mCharLogin.handle(module, body);
		} else if (lower.startsWith("room.")) {
			dispatchRoomGmcp(module, body);
		} else if (lower.startsWith(MudstdFrame.MODULE)) {
			dispatchFrameGmcp(lower, body);
		}
	}

	/**
	 * Answer the {@code mudstd.frame} package.
	 *
	 * <p>Only floating terminal frames can be hosted today. Everything else is
	 * turned down out loud, with {@code frame.closed reason=system}, because a
	 * server author writing the other half needs to see a definite answer rather
	 * than silence — and both sides can read the exchange in logs/gmcp.log.
	 */
	private void dispatchFrameGmcp(final String lowerModule, final JSONObject body) {
		if (!mModuleRegistry.isEnabled(MudstdFrame.MODULE)) {
			return;
		}
		String id = body != null ? body.optString("id", "") : "";
		if (lowerModule.endsWith(".open")) {
			String type = body != null ? body.optString("type", "") : "";
			String content = body != null ? body.optString("content", "") : "";
			String refusal = MudstdFrame.refusalFor(type, content);
			if (refusal != null) {
				logGmcp("INFO", "declining frame '" + id + "': " + refusal);
				sendGmcpPacket(MudstdFrame.closedEvent(id, MudstdFrame.REASON_SYSTEM));
				return;
			}
			mOpenFrames.add(id);
			String caveat = MudstdFrame.acceptedButNotDrawn(type, content);
			if (caveat != null) {
				// Say it in both places. The server author reads gmcp.log; the
				// player watching the screen should not think a window failed to
				// appear when in fact none was ever going to.
				logGmcp("INFO", "frame '" + id + "': " + caveat);
				noteToWindow("[frame " + id + "] " + caveat);
			}
			// Sizes are what the frame will actually be once it is drawn. Until
			// there is a window to measure, report the request rather than
			// invent numbers: sizeValue with sizeUnit "c" is in characters.
			int cols = "c".equalsIgnoreCase(body.optString("sizeUnit", ""))
					? body.optInt("sizeValue", 0) : 0;
			sendGmcpPacket(MudstdFrame.openedEvent(id, cols, 0, 0, 0));
		} else if (lowerModule.endsWith(".close")) {
			if (mOpenFrames.remove(id)) {
				sendGmcpPacket(MudstdFrame.closedEvent(id, MudstdFrame.REASON_SYSTEM));
			}
		} else if (lowerModule.endsWith(".image")) {
			if (!mOpenFrames.contains(id)) {
				logGmcp("INFO", "frame.image for unknown frame '" + id + "'");
				return;
			}
			// Described rather than drawn, and described precisely enough to be
			// worth something: whether it came as base64 or a url, and how big
			// it was. That is what tells the other side its payload survived the
			// trip intact.
			String summary = MudstdFrame.imageSummary(
					body != null ? body.optString("image", "") : "");
			logGmcp("INFO", "frame '" + id + "' image: " + summary);
			noteToWindow("[frame " + id + "] image received — " + summary);
		} else if (lowerModule.endsWith(".terminal")) {
			if (!mOpenFrames.contains(id)) {
				logGmcp("INFO", "frame.terminal for unknown frame '" + id + "'");
				return;
			}
			String ansi = body != null ? body.optString("ansi", "") : "";
			if (ansi.length() > 0 && mReportTo != null) {
				// No frame window exists yet, so it goes to the main window
				// labelled with its id. Visibly wrong placement is a better
				// answer than dropping the server's text on the floor while it
				// waits to see whether anything arrived.
				noteToWindow("[frame " + id + "] " + ansi);
			}
		}
	}

	/** Put a line in the main window without running it past the triggers. */
	private void noteToWindow(final String line) {
		if (mReportTo == null || line == null) {
			return;
		}
		mReportTo.sendMessage(mReportTo.obtainMessage(
				Connection.MESSAGE_LUANOTE, "\n" + line + "\n"));
	}

	/** Frame ids the server believes are open here. */
	private final java.util.Set<String> mOpenFrames =
			new java.util.LinkedHashSet<String>();

	/** Forward Room.* GMCP to Connection → MapperController. */
	private void dispatchRoomGmcp(final String module, final JSONObject body) {
		if (mReportTo == null) {
			return;
		}
		try {
			String json = body != null ? body.toString() : "{}";
			Message msg = mReportTo.obtainMessage(Connection.MESSAGE_MAPPER_ROOM, json);
			Bundle b = msg.getData();
			if (b == null) {
				b = new Bundle();
			}
			b.putString("MODULE", module);
			msg.setData(b);
			mReportTo.sendMessage(msg);
		} catch (Exception ignored) {
		}
	}

	/** Fan-out inbound GMCP into extra text slots that list matching modules. */
	private void dispatchGmcpExtraText(final String module, final String bodyJson) {
		if (mReportTo == null || module == null || module.length() == 0) {
			return;
		}
		try {
			String json = bodyJson != null ? bodyJson : "";
			Message msg = mReportTo.obtainMessage(Connection.MESSAGE_GMCP_EXTRA_TEXT, json);
			Bundle b = msg.getData();
			if (b == null) {
				b = new Bundle();
			}
			b.putString("MODULE", module);
			msg.setData(b);
			mReportTo.sendMessage(msg);
		} catch (Exception ignored) {
		}
	}

	private void ensureMediaPlayer() {
		if (mMediaPlayer == null && mContext != null) {
			mMediaPlayer = new GmcpMediaPlayer(mContext);
		}
	}

	private void ensureCharLogin() {
		if (mCharLogin == null) {
			mCharLogin = new GmcpCharLogin(mContext, mDisplayName, new GmcpCharLogin.Sender() {
				@Override
				public void sendGmcp(final String payload) {
					sendGmcpPacket(payload);
				}

				@Override
				public void notifyWindow(final String message) {
					if (mReportTo != null && message != null) {
						mReportTo.sendMessage(mReportTo.obtainMessage(
								Connection.MESSAGE_PROCESSORWARNING, message));
					}
				}
			});
		}
	}

	/** Queue a GMCP payload to the server (module + optional JSON). */
	public final void sendGmcpPacket(final String payload) {
		if (payload == null || payload.length() == 0 || mReportTo == null) {
			return;
		}
		try {
			byte[] bytes = getGMCPResponse(payload);
			logGmcp("OUT", payload);
			Message sm = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
			Bundle bs = sm.getData();
			bs.putByteArray("THE_DATA", bytes);
			if (mDebugTelnet) {
				bs.putString("DEBUG_MESSAGE",
						Colorizer.getTeloptStartColor() + "OUT:[" + TC.decodeSUB(bytes) + "]"
								+ Colorizer.getResetColor() + "\n");
			}
			sm.setData(bs);
			mReportTo.sendMessage(sm);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Send one MSDP command: {@code IAC SB MSDP MSDP_VAR <cmd> MSDP_VAL <arg> IAC SE}.
	 *
	 * <p>MSDP is the only one of the three optional protocols here that expects
	 * the client to talk back. We parsed whatever a server volunteered and could
	 * never ask for anything, and most servers volunteer nothing until asked, so
	 * in practice the option did nothing at all.
	 *
	 * @param command One of LIST, SEND, REPORT, UNREPORT, RESET.
	 * @param argument The variable or group name; sent as the value.
	 * @return true if the packet was queued.
	 */
	public final boolean sendMsdpCommand(final String command, final String argument) {
		if (command == null || command.length() == 0 || mReportTo == null) {
			return false;
		}
		if (!mOptionHandler.isUseMSDP()) {
			return false;
		}
		byte[] cmd = latin1(command);
		byte[] arg = latin1(argument == null ? "" : argument);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		out.write(TC.IAC);
		out.write(TC.SB);
		out.write(TC.MSDP);
		out.write(TC.MSDP_VAR);
		writeMsdpEscaped(out, cmd);
		out.write(TC.MSDP_VAL);
		writeMsdpEscaped(out, arg);
		out.write(TC.IAC);
		out.write(TC.SE);
		byte[] bytes = out.toByteArray();
		logGmcp("OUT", "MSDP " + command + " " + (argument == null ? "" : argument));
		Message sm = mReportTo.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
		Bundle bs = sm.getData();
		bs.putByteArray("THE_DATA", bytes);
		sm.setData(bs);
		mReportTo.sendMessage(sm);
		return true;
	}

	/** A literal 0xFF inside sub-negotiation data has to be doubled. */
	private static void writeMsdpEscaped(final java.io.ByteArrayOutputStream out,
			final byte[] data) {
		for (int i = 0; i < data.length; i++) {
			out.write(data[i]);
			if (data[i] == TC.IAC) {
				out.write(TC.IAC);
			}
		}
	}

	/** MSDP names are plain ASCII; encoding is not negotiable here. */
	private static byte[] latin1(final String s) {
		try {
			return s.getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			return s.getBytes();
		}
	}

	/** Helper method to respond to the GMCP negotiation sequence.
	 * 
	 * @param str The subnegotiation string.
	 * @return The response.
	 * @throws UnsupportedEncodingException Thrown if the selected encoding isn't supported.
	 */
	public final byte[] getGMCPResponse(final String str) throws UnsupportedEncodingException {
		// GMCP payloads are UTF-8 (inbound already decoded as UTF-8). ISO-8859-1
		// mangled non-Latin-1 passwords/account names (e.g. Polish diacritics).
		int iaccount = 0;
		byte[] tmp = str.getBytes("UTF-8");
		for (int i = 0; i < tmp.length; i++) {
			if (tmp[i] == TC.IAC) {
				iaccount++;
			}
		}

		byte[] resp = new byte[tmp.length + PAYLOAD_BYTES + iaccount];
		resp[0] = TC.IAC;
		resp[1] = TC.SB;
		resp[2] = TC.GMCP;
		resp[resp.length - 1] = TC.SE;
		resp[resp.length - 2] = TC.IAC;
		int j = SKIP_BYTES;
		for (int i = 0; i < tmp.length; i++) {
			resp[j] = tmp[i];
			if (tmp[i] == TC.IAC) {
				resp[j + 1] = TC.IAC;
				j++;
			}
			j++;
		}

		return resp;
	}

	/** Utility method to dump the current gmcp data to the log. */
	public final void dumpGMCP() {
		mGMCP.dumpCache();
	}

	/** Utility class representing a plugin wanting to execute a callback when a gmcp module changes. */
	public class GMCPWatcher {
		/** The plugin name. */
		private String mPlugin;
		/** The callback to execute. */
		private String mCallback;
		/** Constructor. 
		 * 
		 * @param plugin The plugin name.
		 * @param callback The callback name.
		 */
		public GMCPWatcher(final String plugin, final String callback) {
			this.mPlugin = plugin;
			this.mCallback = callback;
		}
		
		/** Getter for mPlugin. 
		 * 
		 * @return the value of mPlugin
		 */
		public final String getPlugin() {
			return mPlugin;
		}
		
		/** Getter for mCallback. 
		 * 
		 * @return value of mCallback
		 */
		public final String getCallback() {
			return mCallback;
		}
	}
	
	/** Adds a new gmcp watcher for a given module path.
	 * 
	 * @param module Module path, e.g. char.vitals.hp.
	 * @param plugin The target plugin that is watching.
	 * @param callback The callback function to execute when module has changed.
	 */
	public final void addWatcher(final String module, final String plugin, final String callback) {
		GMCPWatcher tmp = new GMCPWatcher(plugin, callback);
		
		ArrayList<GMCPWatcher> list = mGMCPTriggers.get(module);
		if (list == null) {
			ArrayList<GMCPWatcher> foo = new ArrayList<GMCPWatcher>();
			foo.add(tmp);
			mGMCPTriggers.put(module, foo);
		} else {
			list.add(tmp);
		}
		
	}

	/** Setter method for mUseGMCP. 
	 * 
	 * @param value the new value for mUseGMCP.
	 */
	public final void setUseGMCP(final Boolean value) {
		mUseGMCP = value;
		mOptionHandler.setUseGMCP(mUseGMCP);
	}

	public final void setUseMTTS(final boolean value) {
		mOptionHandler.setUseMTTS(value);
	}

	public final void setUseMSDP(final boolean value) {
		mOptionHandler.setUseMSDP(value);
		if (!value) {
			mMudProtocols.clearMsdp();
		}
	}

	public final void setUseMSSP(final boolean value) {
		mOptionHandler.setUseMSSP(value);
		if (!value) {
			mMudProtocols.clearMssp();
		}
	}

	public final MudProtocolData getMudProtocols() {
		return mMudProtocols;
	}

	/**
	 * Absorb MSDP/MSSP payload safely. If the matching option is off we should
	 * not receive these (we answered DONT), but ignore corrupt data anyway.
	 */
	private void handleMsdpOrMssp(final byte[] negotiation, final byte option) {
		try {
			if (mDebugTelnet && mReportTo != null) {
				String message = "\n" + Colorizer.getTeloptStartColor() + "IN:["
						+ TC.decodeSUB(negotiation) + "]" + Colorizer.getResetColor() + "\n";
				mReportTo.sendMessageDelayed(mReportTo.obtainMessage(
						Connection.MESSAGE_PROCESSORWARNING, message), 1);
			}
			if (negotiation.length <= PAYLOAD_BYTES) {
				return;
			}
			byte[] foo = new byte[negotiation.length - PAYLOAD_BYTES];
			ByteBuffer wrap = ByteBuffer.wrap(negotiation);
			wrap.rewind();
			wrap.position(SKIP_BYTES);
			wrap.get(foo, 0, foo.length);
			if (option == TC.MSDP) {
				if (!mOptionHandler.isUseMSDP()) {
					return;
				}
				mMudProtocols.absorbMsdp(foo);
				logGmcp("MSDP", mMudProtocols.msdpStatusLine());
			} else if (option == TC.MSSP) {
				if (!mOptionHandler.isUseMSSP()) {
					return;
				}
				mMudProtocols.absorbMssp(foo);
				logGmcp("MSSP", mMudProtocols.msspStatusLine());
			}
		} catch (Exception e) {
			Log.w("MudProto", "MSDP/MSSP handler failed (ignored)", e);
			if (mReportTo != null) {
				mReportTo.sendMessageDelayed(mReportTo.obtainMessage(
						Connection.MESSAGE_PROCESSORWARNING,
						"\n" + Colorizer.getRedColor()
								+ "MSDP/MSSP parse error — packet ignored"
								+ Colorizer.getWhiteColor() + "\n"), 1);
			}
		}
	}

	/** Setter method for mGMCPSupports.
	 * 
	 * @param value The new value for mGMCPSupports.
	 */
	public final void setGMCPSupports(final String value) {
		mModuleRegistry.setEnabledFromSupportsString(value);
		mGMCPSupports = "core.supports.set [" + mModuleRegistry.toSupportsString() + "]";
		mModuleRegistry.setLastSupportsSet(mGMCPSupports);
	}
}

/*
 * Straight from rfc 854 NAME CODE MEANING
 * 
 * SE 240 End of subnegotiation parameters. NOP 241 No operation. Data Mark 242
 * The data stream portion of a Synch. This should always be accompanied by a
 * TCP Urgent notification. Break 243 NVT character BRK. Interrupt Process 244
 * The function IP. Abort output 245 The function AO. Are You There 246 The
 * function AYT. Erase character 247 The function EC. Erase Line 248 The
 * function EL. Go ahead 249 The GA signal. SB 250 Indicates that what follows
 * is subnegotiation of the indicated option. WILL (option code) 251 Indicates
 * the desire to begin performing, or confirmation that you are now performing,
 * the indicated option. WON'T (option code) 252 Indicates the refusal to
 * perform, or continue performing, the indicated option. DO (option code) 253
 * Indicates the request that the other party perform, or confirmation that you
 * are expecting the other party to perform, the indicated option. DON'T (option
 * code) 254 Indicates the demand that the other party stop performing, or
 * confirmation that you are no longer expecting the other party to perform, the
 * indicated option. IAC 255 Data Byte 255.
 */
