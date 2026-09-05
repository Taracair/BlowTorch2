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
import com.resurrection.blowtorch2.lib.service.mxp.MxpEngine;
import com.resurrection.blowtorch2.lib.service.mxp.MxpSound;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
	/**
	 * Toast when the server sends a module not in Supports.Set, and once on
	 * connect for a supports list it advertises. On by default since 2.2.0 —
	 * keep this in step with {@code gmcp_suggest_modules} in
	 * ConnectionSettingsPlugin and with what ConnectionSetttingsParser persists.
	 */
	private boolean mSuggestGmcpModules = true;
	/** Optional profile label for session-log GMCP lines. */
	private String mLogProfile = "session";
	/** Holdover sequence buffer. Used when a telnet negotation spans a transmission boundary. */
	private byte[] mHoldover = null;
	/** Bare CR → newline; CRLF across packets stays one LF. */
	private final CrToNewline mCrToNewline = new CrToNewline();
	/** GMCP Data holder object. */
	private GMCPData mGMCP = null;
	/** List of GMCP Triggers. */
	private HashMap<String, ArrayList<GMCPWatcher>> mGMCPTriggers = new HashMap<String, ArrayList<GMCPWatcher>>();
	/** GMCP Hello string (version filled from package versionName). */
	private String mGMCPHello = "core.hello {\"client\": \"BlowTorch\",\"version\": \"2.4.3\"}";
	/** Tracker for weather or not the use GMCP. */
	private Boolean mUseGMCP = false;
	/**
	 * True after this Processor saw {@code ESC ] 8 ;} in telnet-cleared inbound
	 * bytes. MXP-synthesized OSC and {@code .probe osc8} do not go through here.
	 */
	private boolean mSawOsc8 = false;
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
	/** MXP 1.0 stream filter (option 91). */
	private final MxpEngine mMxp = new MxpEngine();
	/** Logcat handshake notes when Options → Log MXP? is on. */
	private boolean mLogMxp;
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
		mMxp.setClient("BlowTorch", packageVersion());
	}

	private String packageVersion() {
		String ver = "2.4.3";
		if (mContext != null) {
			try {
				ver = mContext.getPackageManager()
						.getPackageInfo(mContext.getPackageName(), 0).versionName;
			} catch (Exception ignored) {
			}
		}
		if (ver == null || ver.length() == 0) {
			ver = "2.4.3";
		}
		return ver;
	}

	/** Refresh Core.Hello version from the installed APK versionName. */
	private void rebuildGmcpHello() {
		String ver = "2.4.3";
		if (mContext != null) {
			try {
				ver = mContext.getPackageManager()
						.getPackageInfo(mContext.getPackageName(), 0).versionName;
			} catch (Exception ignored) {
			}
		}
		if (ver == null || ver.length() == 0) {
			ver = "2.4.3";
		}
		mGMCPHello = "core.hello {\"client\": \"BlowTorch\",\"version\": \"" + ver + "\"}";
	}

	public final String getGmcpHello() {
		return mGMCPHello;
	}

	public final OptionNegotiator getOptionHandler() {
		return mOptionHandler;
	}

	/**
	 * Scan telnet-cleared inbound bytes for an OSC 8 open ({@code ESC ] 8 ;})
	 * before MXP rewriting. Once true, stays true for this Processor.
	 */
	public final void noteInboundOsc8(final byte[] raw) {
		if (!mSawOsc8 && com.resurrection.blowtorch2.lib.window.OscEight.containsOpen(raw)) {
			mSawOsc8 = true;
		}
	}

	public final boolean sawOsc8() {
		return mSawOsc8;
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
					return finishText(buff, count);
				}
				if ((input[i + 1] >= TC.WILL && input[i + 1] <= TC.DONT)
						|| input[i + 1] == TC.SB) {
					if (input[i + 1] == TC.SB) {
						// Need at least IAC SB <option>
						if (i + 2 >= input.length) {
							mHoldover = Arrays.copyOfRange(input, i, input.length);
							return finishText(buff, count);
						}
						int j = findSubnegotiationEnd(input, i);
						if (j < 0) {
							mHoldover = Arrays.copyOfRange(input, i, input.length);
							return finishText(buff, count);
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
							mReportTo.sendMessageAtFrontOfQueue(mReportTo
									.obtainMessage(
											Connection.MESSAGE_STARTCOMPRESS,
											remainderAfterSubnegotiation(input, j)));
							if (mDebugTelnet) {
								String message = "\n" + Colorizer.getTeloptStartColor() + "IN:[IAC SB COMPRESS2 IAC SE] -BEGIN COMPRESSION-" + Colorizer.getResetColor() + "\n";
								mReportTo.sendMessageDelayed(mReportTo.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message), 1);
							}
							return finishText(buff, count);

						} else {
							// Advance past IAC SE (for-loop will i++).
							i = j + 1;
						}
					} else {
						// WILL/WONT/DO/DONT require the option byte.
						if (i + 2 >= input.length) {
							mHoldover = Arrays.copyOfRange(input, i, input.length);
							return finishText(buff, count);
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
					case TC.EOR:
					case TC.GOAHEAD:
						// GA and EOR both mark the end of a prompt, and a prompt
						// is the one line that never gets a newline. Connection
						// holds an unfinished trailing line back so that a gag
						// cannot cut a line in half; without this the prompt
						// would sit there until the 150 ms timer let it go. The
						// message lands behind the chunk being processed, so the
						// prompt bytes are already held by the time it is read.
						//
						// Worlds that send neither lose nothing: the timer is
						// still what releases their prompts.
						mReportTo.sendEmptyMessage(
								Connection.MESSAGE_FLUSH_LINE_HOLDOVER);
						i++;
						break;
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
			default:
				buff.put(input[i]);
				count++;
				break;
			}

		}
		
		return finishText(buff, count);
		
	}

	/** Find the {@code IAC SE} that closes the subnegotiation opened at {@code start}.
	 *
	 * <p>Pure so it can be tested with {@link #remainderAfterSubnegotiation}: together
	 * they decide which bytes reach the MCCP Inflater, and a wrong index there is a
	 * screenful of binary rather than a wrong character.
	 *
	 * @param input The packet being parsed.
	 * @param start Index of the {@code IAC} of {@code IAC SB}.
	 * @return Index of the {@code IAC} that opens the closing {@code IAC SE}, or -1
	 *         when the subnegotiation is not complete in this packet.
	 */
	static int findSubnegotiationEnd(final byte[] input, final int start) {
		int j = start + SKIP_BYTES;
		while (j + 1 < input.length) {
			if (input[j] == TC.IAC) {
				if (input[j + 1] == TC.SE) {
					return j;
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
		return -1;
	}

	/** Everything after an {@code IAC SE} that ends a subnegotiation.
	 *
	 * <p>For MCCP2 this is the first slice of the zlib stream, so it has to be
	 * byte-exact: the Inflater is fed it verbatim. The old code allocated
	 * {@code input.length - (j + 2 - i)} while writing {@code input.length - (j + 2)}
	 * bytes, so {@code b.array()} carried {@code i} trailing zeros — {@code i} being
	 * the offset of the marker inside the packet. Achaea always sends a short
	 * {@code IAC WONT …} ahead of {@code IAC SB MCCP2 IAC SE} in the same packet
	 * (measured: i = 3), so the Inflater hit those zeros as a bogus stored-block
	 * header and threw on the very first chunk. Servers that put the marker at
	 * offset 0 had i = 0 and never showed the bug.
	 *
	 * @param input The packet being parsed.
	 * @param j Index of the {@code IAC} that starts the closing {@code IAC SE}.
	 * @return The bytes following {@code IAC SE}, possibly empty, never null.
	 */
	static byte[] remainderAfterSubnegotiation(final byte[] input, final int j) {
		int start = j + 2;
		if (start >= input.length) {
			return new byte[0];
		}
		return Arrays.copyOfRange(input, start, input.length);
	}

	/** Copy the first {@code count} bytes out of {@code buff}, then turn bare CR into LF. */
	private byte[] finishText(final ByteBuffer buff, final int count) {
		return mCrToNewline.apply(truncBuffer(buff, count));
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

		if (option == TC.MXP && (action == TC.WILL || action == TC.DO)
				&& mOptionHandler.isUseMXP()) {
			mMxp.setActive(true);
			if (mLogMxp) {
				android.util.Log.i("BlowTorch.MXP", "active after IAC "
						+ (action == TC.WILL ? "WILL" : "DO") + " MXP");
			}
		}

		if (option == TC.ECHO && (action == TC.WILL || action == TC.WONT)) {
			// Ask the negotiator rather than re-deriving it here: it has already
			// answered this command, and one source of truth cannot drift from the
			// other. Local echo is off exactly while the server holds ECHO.
			//
			// Run the UI update now when we are already on the Connection looper.
			// rawProcess is called from that handler's dispatch(), and a normal
			// sendMessage would sit *behind* the current turn — so the prompt
			// text from this same packet reached the input bar before the mask
			// flipped. On a live world that meant: nickname still dotted (WONT
			// arrived after "What is your name?"), password still clear (WILL
			// arrived after the password prompt). Measured 11 Aug 2026.
			Message echoMsg = mReportTo.obtainMessage(
					Connection.MESSAGE_LOCALECHO, mOptionHandler.isServerEcho() ? 0 : 1, 0);
			if (Looper.myLooper() == mReportTo.getLooper()) {
				mReportTo.dispatchMessage(echoMsg);
			} else {
				mReportTo.sendMessage(echoMsg);
			}
		}

		if (action == TC.WILL && option == TC.CHARSET) {
			// Agree to CHARSET and prefer UTF-8, but do NOT send a client REQUEST.
			// Some MUDs treat an unsolicited REQUEST poorly.
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
		} else if (sub[0] == TC.MXP) {
			mMxp.setActive(true);
			if (mLogMxp) {
				android.util.Log.i("BlowTorch.MXP", "active after IAC SB MXP");
			}
			if (mDebugTelnet && mReportTo != null) {
				String message = "\n" + Colorizer.getTeloptStartColor() + "IN:["
						+ TC.decodeSUB(negotiation) + "] MXP start"
						+ Colorizer.getResetColor() + "\n";
				mReportTo.sendMessageDelayed(mReportTo.obtainMessage(
						Connection.MESSAGE_PROCESSORWARNING, message), 1);
			}
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
		mMxp.setEncoding(encoding);
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
		// Frames belong to the connection that opened them. Keeping the ids would
		// mean .frame list showing frames from a session that is over, and a close
		// event for a frame the new server never opened. The windows go with them,
		// for the same reason.
		if (!mOpenFrames.isEmpty()) {
			sendFrameEvent(FrameEvent.clear());
		}
		mOpenFrames.clear();
		mClosedFrames.clear();
		mClosedFrameNoted.clear();
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
		suggestUnaskedModules();
	}

	/**
	 * Tell the player once about modules a server offers that we never ask for.
	 *
	 * <p>One aggregated line, not one per module: this server offers fifteen and
	 * six of them are new, and six separate notices on connect would be a wall
	 * rather than a hint. Same option as the per-packet suggestion
	 * ("Suggest modules when seen?"), and it never enables anything.
	 */
	private void suggestUnaskedModules() {
		if (!mSuggestGmcpModules || mReportTo == null) {
			return;
		}
		ArrayList<String> unasked = mModuleRegistry.unaskedServerSupports();
		if (unasked.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("GMCP: the server offers ").append(unasked.size());
		sb.append(unasked.size() == 1 ? " module" : " modules");
		sb.append(" you do not ask for — ");
		for (int i = 0; i < unasked.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(unasked.get(i));
		}
		sb.append("\nOptions → Manage modules… or .gmcp enable <name>");
		mReportTo.sendMessageDelayed(mReportTo.obtainMessage(
				Connection.MESSAGE_PROCESSORWARNING, sb.toString()), 1);
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
	 * <p>Image frames get a window of their own in the UI process; terminal
	 * frames are still text in the main window, labelled with the frame id.
	 * Anything outside the specification's vocabulary is turned down out loud,
	 * with {@code frame.closed reason=system}, because a server author writing
	 * the other half needs a definite answer rather than silence — and both
	 * sides can read the exchange in logs/gmcp.log.
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
			// Sizes are what the frame will actually be once it is drawn. The
			// pixel size is not known here — the window that answers it lives in
			// the other process — so the request is echoed and the UI corrects it
			// with frame.resized once it has measured itself.
			//
			// Two spellings are read. The package page calls the field sizeValue;
			// a live world sends size (measured 30 July). Reading
			// both costs nothing and means the server author is not debugging a
			// width of zero that came from a field name.
			int cols = 0;
			if (body != null && "c".equalsIgnoreCase(body.optString("sizeUnit", ""))) {
				cols = body.optInt("sizeValue", body.optInt("size", 0));
			}
			OpenFrame frame = new OpenFrame();
			frame.label = body != null ? body.optString("label", "") : "";
			frame.type = type;
			frame.content = content;
			frame.sizeChars = cols;
			mOpenFrames.put(id, frame);
			// The server opening it settles any argument about a frame the player
			// closed earlier: this is a new frame with that id, not the old one.
			mClosedFrames.remove(id);
			mClosedFrameNoted.remove(id);

			String caveat = MudstdFrame.acceptedButNotDrawn(type, content);
			if (caveat != null) {
				// Say it in both places. The server author reads gmcp.log; the
				// player watching the screen should not think a window failed to
				// appear when in fact none was ever going to.
				logGmcp("INFO", "frame '" + id + "': " + caveat);
				noteToWindow("[frame " + id + "] " + caveat
						+ "\n[frame " + id + "] .frame close " + id + " to shut it");
			}
			if (MudstdFrame.needsFrameWindow(content) && !mFrameImageInText) {
				sendFrameEvent(FrameEvent.open(id, frame.label, type, content, cols));
			}
			sendGmcpPacket(MudstdFrame.openedEvent(id, cols, 0, 0, 0));
		} else if (lowerModule.endsWith(".close")) {
			OpenFrame gone = mOpenFrames.remove(id);
			if (gone != null) {
				sendFrameEvent(FrameEvent.close(id));
				sendGmcpPacket(MudstdFrame.closedEvent(id, MudstdFrame.REASON_SYSTEM));
			}
		} else if (lowerModule.endsWith(".image")) {
			String raw = body != null ? body.optString("image", "") : "";
			OpenFrame frame = mOpenFrames.get(id);
			if (frame == null) {
				noteImageForClosedFrame(id, raw);
				return;
			}
			// Logged as a description rather than echoed: a base64 map is tens of
			// kilobytes and the useful facts are the carrier and the size. That is
			// also what tells the other side its payload survived the trip. A URL is
			// not summarised: the IN line above already carries it in full, and eden
			// sends eight of these per step between two tiles, so the second copy is
			// half of a log a server author has to read.
			if (!MudstdFrame.isUrlCarrier(raw)) {
				logGmcp("INFO", "frame '" + id + "' image: " + MudstdFrame.imageSummary(raw));
			}
			FrameEvent event = FrameEvent.image(id, raw);
			if (event.isOversizedPayload()) {
				// Sending this across the binder would risk taking an unrelated
				// call down with it, so it is refused where the size is known and
				// the refusal says which limit it broke.
				String tooBig = "image is " + raw.length() + " characters; the limit for an "
						+ "inline payload is " + FrameEvent.MAX_BASE64_CHARS
						+ ". Send a URL instead.";
				logGmcp("INFO", "frame '" + id + "': " + tooBig);
				noteToWindow("[frame " + id + "] " + tooBig);
				return;
			}
			frame.image = raw;
			if (mFrameImageInText) {
				placeImageInText(id, raw);
			} else {
				sendFrameEvent(event);
			}
		} else if (lowerModule.endsWith(".terminal")) {
			if (!mOpenFrames.containsKey(id)) {
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

	/** True when pictures go in the game text rather than in a window. */
	private boolean mFrameImageInText;

	/** How many lines of game text a picture covers. */
	private int mFrameImageLines = InlineImageMarker.DEFAULT_LINES;

	/** Makes each in-text picture's key unique — see {@link InlineImageMarker#keyFor}. */
	private int mInlineImageCounter;

	/**
	 * Options → GMCP → "Pictures the server sends".
	 *
	 * <p>The windows follow the setting immediately, which they did not before.
	 * A frame opened while pictures went into the text had no window, and turning
	 * the setting back never built one — every later {@code frame.image} was
	 * dropped by the UI as an image for a frame it did not have, and the only way
	 * back was to reconnect. Switching the other way left a window on screen that
	 * the pictures had stopped going to.
	 */
	public final void setFrameImageInText(final boolean inText) {
		boolean changed = mFrameImageInText != inText;
		mFrameImageInText = inText;
		if (!changed) {
			return;
		}
		if (inText) {
			// The windows go; the frames stay open as far as the server is
			// concerned, so no frame.closed is sent. Nothing was closed — the
			// pictures are going somewhere else now.
			for (java.util.Map.Entry<String, OpenFrame> entry : mOpenFrames.entrySet()) {
				if (MudstdFrame.needsFrameWindow(entry.getValue().content)) {
					sendFrameEvent(FrameEvent.close(entry.getKey()));
				}
			}
			return;
		}
		// The same events a rebuilt activity is given, for the same reason.
		ArrayList<FrameEvent> replay = describeOpenFrames();
		for (int i = 0; i < replay.size(); i++) {
			sendFrameEvent(replay.get(i));
		}
	}

	/** Options → GMCP → "Picture height in the text (lines)". */
	public final void setFrameImageLines(final int lines) {
		mFrameImageLines = InlineImageMarker.clampLines(lines);
	}

	/**
	 * Print a picture into the game text where it arrived.
	 *
	 * <p>Two things go out and they are deliberately not one thing. The marker
	 * goes down the text pipe, so it lands in the scrollback in its proper place
	 * among the room descriptions. The picture itself goes to the UI's image
	 * store as an ordinary frame event, because the text pipe is the wrong place
	 * for tens of kilobytes of base64 and because a URL written into the text
	 * would be found by the client's own link detection and underlined.
	 *
	 * <p>They can arrive in either order and it does not matter: the marker
	 * draws nothing until the store has the picture, and the store tells the
	 * window to repaint when it does.
	 */
	private void placeImageInText(final String frameId, final String spec) {
		mInlineImageCounter++;
		String key = InlineImageMarker.keyFor(frameId, mInlineImageCounter);
		sendFrameEvent(FrameEvent.inline(key, spec));
		noteRawToWindow(InlineImageMarker.encode(key, mFrameImageLines));
	}

	/**
	 * Put bytes in the main window verbatim.
	 *
	 * <p>Unlike {@link #noteToWindow} this adds no newlines of its own: the
	 * marker's layout is exact, and an extra line either side would move the
	 * picture off the space reserved for it.
	 */
	private void noteRawToWindow(final String raw) {
		if (mReportTo == null || raw == null || raw.length() == 0) {
			return;
		}
		mReportTo.sendMessage(mReportTo.obtainMessage(Connection.MESSAGE_LUANOTE, raw));
	}

	/**
	 * Hand one frame event to {@link Connection}, which forwards it to the UI.
	 *
	 * <p>A batch of one. Batching happens on the queue in {@code Connection},
	 * not here: this is called from the connection thread as packets arrive, and
	 * holding a packet back to see whether another follows it would mean
	 * deciding how long to wait.
	 */
	private void sendFrameEvent(final FrameEvent event) {
		if (mReportTo == null || event == null) {
			return;
		}
		ArrayList<FrameEvent> one = new ArrayList<FrameEvent>(1);
		one.add(event);
		mReportTo.sendMessage(mReportTo.obtainMessage(
				Connection.MESSAGE_FRAME_EVENT, FrameEvent.toJson(one)));
	}

	/** Put a line in the main window without running it past the triggers. */
	private void noteToWindow(final String line) {
		if (mReportTo == null || line == null) {
			return;
		}
		mReportTo.sendMessage(mReportTo.obtainMessage(
				Connection.MESSAGE_LUANOTE, "\n" + line + "\n"));
	}

	/**
	 * What the server believes is open here, in the order it opened, with
	 * everything needed to put it back on screen.
	 *
	 * <p>This used to be a set of ids, which was enough while a frame was only
	 * ever described in words. A window has to be rebuilt after the activity is
	 * destroyed, so the label, the shape and the last image stay too.
	 */
	private final java.util.LinkedHashMap<String, OpenFrame> mOpenFrames =
			new java.util.LinkedHashMap<String, OpenFrame>();

	/** One open frame, as much of it as we were told. */
	private static final class OpenFrame {
		String label = "";
		String type = "";
		String content = "";
		int sizeChars;
		/** The last {@code image} field the server sent, raw. May be empty. */
		String image = "";
	}

	/** Frame ids the server believes are open, in the order they opened. */
	public final ArrayList<String> getOpenFrames() {
		return new ArrayList<String>(mOpenFrames.keySet());
	}

	/**
	 * Every open frame as the events that would create it from nothing.
	 *
	 * <p>An {@code open} followed by its {@code image}, in the order they
	 * opened, which is exactly what a UI that has just been rebuilt needs to
	 * catch up.
	 */
	public final ArrayList<FrameEvent> describeOpenFrames() {
		ArrayList<FrameEvent> out = new ArrayList<FrameEvent>();
		if (mFrameImageInText) {
			// Pictures went into the scrollback, and the scrollback is replayed
			// by its own machinery. Rebuilding windows here would put a window
			// on screen that the player never had.
			return out;
		}
		for (java.util.Map.Entry<String, OpenFrame> entry : mOpenFrames.entrySet()) {
			OpenFrame f = entry.getValue();
			if (!MudstdFrame.needsFrameWindow(f.content)) {
				continue;
			}
			out.add(FrameEvent.open(entry.getKey(), f.label, f.type, f.content, f.sizeChars));
			if (f.image.length() > 0) {
				out.add(FrameEvent.image(entry.getKey(), f.image));
			}
		}
		return out;
	}

	/**
	 * The player closing a frame, which is what {@code reason: "user"} is for.
	 *
	 * <p>The specification has always had it and we never sent it: a server could
	 * open a frame here and never learn that the person reading it was done with
	 * it. Nothing draws a frame yet, so the way a player says so is
	 * {@code .frame close <id>} — but the event on the wire is the same one a
	 * close button would send, and that is the half the server has to write.
	 *
	 * @param id The frame id, exactly as the server spelled it.
	 * @return true if that frame was open; false leaves the wire silent, because
	 *         telling a server a frame closed twice is worse than saying nothing.
	 */
	public final boolean closeFrameByUser(final String id) {
		OpenFrame closed = id == null ? null : mOpenFrames.remove(id);
		if (closed == null) {
			return false;
		}
		rememberClosed(id, closed);
		// The window goes too, whichever end the close came from: .frame close
		// leaves nothing on screen, and a close button does not leave the id in
		// the list behind it.
		sendFrameEvent(FrameEvent.close(id));
		sendGmcpPacket(MudstdFrame.closedEvent(id, MudstdFrame.REASON_USER));
		return true;
	}

	/**
	 * Frames the player closed, kept in case they want them back.
	 *
	 * <p>Small on purpose. This is not a history — it is the handful of frames a
	 * server is probably still feeding, so {@code .frame reopen} has a label, a
	 * shape and a picture to work from rather than guesses.
	 */
	private final java.util.LinkedHashMap<String, OpenFrame> mClosedFrames =
			new java.util.LinkedHashMap<String, OpenFrame>();

	private static final int MAX_REMEMBERED_CLOSED_FRAMES = 8;

	/** Ceiling on ids we remember having complained about. See the note below. */
	private static final int MAX_NOTED_FRAME_IDS = 64;

	/** Frame ids the player has already been told are closed but still arriving. */
	private final java.util.HashSet<String> mClosedFrameNoted = new java.util.HashSet<String>();

	private void rememberClosed(final String id, final OpenFrame frame) {
		mClosedFrames.remove(id);
		mClosedFrames.put(id, frame);
		while (mClosedFrames.size() > MAX_REMEMBERED_CLOSED_FRAMES) {
			String oldest = mClosedFrames.keySet().iterator().next();
			mClosedFrames.remove(oldest);
			mClosedFrameNoted.remove(oldest);
		}
	}

	/**
	 * A picture for a frame that is not open here.
	 *
	 * <p>eden keeps sending {@code frame.image} for a frame it was told the player
	 * closed — eight per step between two tiles — and each one used to be a line
	 * in {@code gmcp.log} saying it had been dropped, which buried everything
	 * else. Now the drop is said once per frame, in the window as well as the log,
	 * and it says how to get the frame back: the player closed it, so quietly
	 * putting it on screen again would be overruling them, and staying silent left
	 * them with no way back except reconnecting.
	 *
	 * <p>The newest picture is kept while the frame is closed, so a reopen shows
	 * where the character is standing now rather than where they were when they
	 * shut it.
	 */
	private void noteImageForClosedFrame(final String id, final String raw) {
		OpenFrame closed = mClosedFrames.get(id);
		if (closed == null) {
			// Never open here at all. That is the server's mistake, not a choice
			// the player made, so it is logged as before — once per id, up to a
			// bound: the ids are the server's to invent, and remembering every one
			// it ever got wrong is a leak with a remote input for a size.
			if (mClosedFrameNoted.size() >= MAX_NOTED_FRAME_IDS) {
				return;
			}
			if (mClosedFrameNoted.add(id)) {
				logGmcp("INFO", "frame.image for unknown frame '" + id
						+ "'; further images for it are dropped without a line each");
			}
			return;
		}
		// Kept only if it could actually cross the binder on a reopen. The open path
		// refuses an oversized payload before storing it and this one has to as
		// well: FrameEvent's limit exists because a transaction past it does not
		// fail politely, it takes an unrelated call down with it. Dropping the
		// picture leaves a reopened frame saying no picture has arrived, which is
		// true of every picture that could be shown.
		FrameEvent candidate = FrameEvent.image(id, raw);
		closed.image = candidate.isOversizedPayload() ? "" : candidate.getImage();
		if (!mClosedFrameNoted.add(id)) {
			return;
		}
		logGmcp("INFO", "frame '" + id + "' is closed here and the server is still sending "
				+ "images; dropping them. .frame reopen " + id + " brings it back");
		noteToWindow("[frame " + id + "] you closed this frame; the server is still sending "
				+ "pictures for it.\n[frame " + id + "] .frame reopen " + id
				+ " brings it back.");
	}

	/**
	 * Put back a frame the player closed while the server still believes in it.
	 *
	 * <p>{@code frame.opened} goes out again, which is the honest thing to send: as
	 * far as the server is concerned this frame is open, and the pixel size it was
	 * last told belonged to a window that no longer exists.
	 *
	 * @return false when nothing here remembers that id.
	 */
	public final boolean reopenFrameByUser(final String id) {
		OpenFrame frame = id == null ? null : mClosedFrames.remove(id);
		if (frame == null) {
			return false;
		}
		mClosedFrameNoted.remove(id);
		mOpenFrames.put(id, frame);
		if (MudstdFrame.needsFrameWindow(frame.content) && !mFrameImageInText) {
			sendFrameEvent(FrameEvent.open(id, frame.label, frame.type, frame.content,
					frame.sizeChars));
			if (frame.image.length() > 0) {
				sendFrameEvent(FrameEvent.image(id, frame.image));
			}
		}
		sendGmcpPacket(MudstdFrame.openedEvent(id, frame.sizeChars, 0, 0, 0));
		return true;
	}

	/** Frame ids the player closed that the server has not been told to stop. */
	public final ArrayList<String> getClosedFrames() {
		return new ArrayList<String>(mClosedFrames.keySet());
	}

	/**
	 * The window measured itself; tell the server how big the frame really is.
	 *
	 * <p>{@code frame.opened} goes out when the frame is accepted, before any
	 * window exists, so its pixel size is zero. This is the correction, and it
	 * is what {@code frame.resized} is for.
	 *
	 * @param id The frame id, exactly as the server spelled it.
	 * @param widthPx Measured width; {@code heightPx} likewise.
	 * @return false when that frame is not open, leaving the wire silent.
	 */
	public final boolean reportFrameSize(final String id, final int widthPx, final int heightPx) {
		OpenFrame frame = id == null ? null : mOpenFrames.get(id);
		if (frame == null) {
			return false;
		}
		sendGmcpPacket(MudstdFrame.resizedEvent(id, frame.sizeChars, 0, widthPx, heightPx));
		return true;
	}

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

	/** MXP SOUND/MUSIC — same player as Client.Media. */
	public final void playMxpSound(final MxpSound.Request req) {
		ensureMediaPlayer();
		if (mMediaPlayer != null) {
			mMediaPlayer.playMxp(req);
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

	public final boolean isUseGMCP() {
		return mUseGMCP != null && mUseGMCP.booleanValue();
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

	public final void setUseMCCP(final boolean value) {
		mOptionHandler.setUseMCCP(value);
	}

	public final void setUseMXP(final boolean value) {
		mOptionHandler.setUseMXP(value);
		mMxp.setEnabled(value);
		if (!value) {
			mMxp.reset();
		}
	}

	public final void setLogMXP(final boolean value) {
		mLogMxp = value;
	}

	public final boolean isUseMXP() {
		return mOptionHandler.isUseMXP();
	}

	public final MxpEngine getMxp() {
		return mMxp;
	}

	/** Strip/interpret MXP in the telnet-cleared stream. No-op when Use MXP is off. */
	public final byte[] filterMxp(final byte[] raw) {
		if (raw == null || !mOptionHandler.isUseMXP()) {
			return raw;
		}
		return mMxp.process(raw);
	}

	public final boolean isUseMCCP() {
		return mOptionHandler.isUseMCCP();
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
