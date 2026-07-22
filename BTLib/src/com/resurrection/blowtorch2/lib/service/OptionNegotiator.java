/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import android.util.Log;

/** Helper class to the Processor. This object keeps track of the negotiation responses. */
public class OptionNegotiator {
	/** The default number of columns for NAWS (interim until UI reports live size). */
	private static final int DEFAULT_COLS = 40;
	/** The default number of rows for NAWS (~phone-friendly terminal height). */
	private static final int DEFAULT_ROWS = 20;
	/** IAC WILL. */
	private static final byte IAC_WILL = (byte) 0xFB; //251
	/** IAC WONT. */
	private static final byte IAC_WONT = (byte) 0xFC; //252
	/** IAC DO. */
	private static final byte IAC_DO = (byte) 0xFD; //253
	/** IAC DONT. */
	private static final byte IAC_DONT = (byte) 0xFE; //254
	/** MCCP 2 compressiong marker. */
	private static final byte COMPRESS2 = (byte) 0x56; //86
	/** GMCP marker. */
	private static final byte GMCP = (byte) 201;
	/** Suppress goahead marker. */
	private static final byte SUPPRESS_GOAHEAD = (byte) 0x03;
	/** NAWS marker. */
	private static final byte NAWS_TYPE = (byte) 0x1F; //31 -- NAWS, negotiate window size
	/** TELNET negotiation size. */
	private static final int NEGOTIATION_SIZE = 3;
	/** Size of the NAWS string. */
	private static final int NAWS_STRING_SIZE = 9;
	/** LSB mask for an int. */
	private static final int LSB_MASK = 0x000000FF;
	/** Second LSB mask for an int. */
	private static final int SLSB_MASK = 0x0000FF00;
	/** TTYPE data location. */
	private static final int TTYPE_DATA_LOCATION = 3;
	/**
	 * MTTS bitvector: ANSI (1) + UTF-8 (4) + 256 colors (8) = 13.
	 * @see <a href="https://mudstandards.org/mud/mtts">MUD Terminal Type Standard</a>
	 */
	private static final int MTTS_BITS = 1 | 4 | 8;
	/** Tracker for the configured number of columns for NAWS. */
	private int mColumns = DEFAULT_COLS;
	/** Tracker for the configured number of rows for NAWS. */
	private int mRows = DEFAULT_ROWS;
	/** Tracker for if naws has been negotiatated. */
	private boolean mIsNAWS = false;
	/** The termtype array of strings that will be iterated through for TTYPE negotiation. */
	private String[] mTermTypes = null;
	/** The termtype negotiation attempt number. */
	private int mTermTypeAttempt = 0;
	/** Selected termtype. */
	private String mTermType = null;
	/** Tracker for if naws data is current and sent to the server. */
	private boolean mDoneNAWS = false;
	/** Tracker for if GMCP should be negotiated. */
	private Boolean mUseGMCP = false;
	/** When true, third TTYPE reply is MTTS &lt;bits&gt;. */
	private boolean mUseMTTS = false;
	/** When true, answer DO to IAC WILL MSDP. */
	private boolean mUseMSDP = false;
	/** When true, answer DO to IAC WILL MSSP. */
	private boolean mUseMSSP = false;
	/** Encoding selected via CHARSET subnegotiation; consumed by Processor. */
	private String mPendingCharset = null;
	
	
	/** Constructor.
	 * 
	 * @param ttype The package level configurable termtype option.
	 */
	public OptionNegotiator(final String ttype) {
		mTermType = ttype;
		rebuildTermTypes();
	}

	private void rebuildTermTypes() {
		String primary = (mTermType != null && mTermType.length() > 0) ? mTermType : "BlowTorch";
		// Always use the MTTS three-reply cycle (name → ANSI → MTTS <bits>).
		// Use MTTS? selects full capability bits (13) vs ANSI-only (1).
		final int bits = mUseMTTS ? MTTS_BITS : 1;
		mTermTypes = new String[] {
				primary,
				"ANSI",
				"MTTS " + bits
		};
		mTermTypeAttempt = 0;
	}
	
	/** The top level telnet processing routine.
	 * 
	 * @param first I believe this will always be IAC
	 * @param second The action byte, WILL, WONT, DO, DONT
	 * @param third The actual negotiation type, TTYPE, NAWS, GMCP, MCCP2, etc.
	 * @return The response to the given telnet negotiation.
	 */
	public final byte[] processCommand(final byte first, final byte second, final byte third) {
	    	
			
			
			//byte SB = (byte)0xFA; //250 - subnegotiation start
			//byte SE = (byte)0xF0; //240 - subnegotiation start
			
			
			//final byte COMPRESS1 = (byte)0x55; //85
			//final byte ATCP_CUSTOM = (byte)0xC8; //200 -- ATCP protocol, http://www.ironrealms.com/rapture/manual/files/FeatATCP-txt.html
			//final byte AARD_CUSTOM = (byte)0x66; //102 -- Aardwolf custom, http://www.aardwolf.com/blog/2008/07/10/telnet-negotiation-control-mud-client-interaction/
			//final byte TERM_TYPE = (byte)0x18; //24

	    	byte[] ret = new byte[NEGOTIATION_SIZE];
	    	
	    	//first byte should always be 255.
	    	if (first != TC.IAC) {
	    		return null;
	    	}
	    	
	    	byte response = 0x00;
	    	
	    	if (second == IAC_WILL) {
	    		switch(third) {
	    		case COMPRESS2:
	    			response = IAC_DO;
	    			break;
	    		case SUPPRESS_GOAHEAD:
	    			response = IAC_DO;
	    			break;
	    		case GMCP:
	    			if (mUseGMCP) {
	    				Log.e("GMCP", "IAC WILL GMP RECIEVED, RESPONDING DO");
	    				response = IAC_DO;
	    			} else {
	    				response = IAC_DONT;
	    			}
	    			break;
	    		case TC.MSDP:
	    			response = mUseMSDP ? IAC_DO : IAC_DONT;
	    			break;
	    		case TC.MSSP:
	    			response = mUseMSSP ? IAC_DO : IAC_DONT;
	    			break;
	    		case TC.CHARSET:
	    			response = IAC_DO;
	    			break;
	    		default:
	    			response = IAC_DONT;
	    		}
	    	}
	    	
	    	if (second == IAC_DO) {
	    		switch(third) {
	    		case COMPRESS2:
	    			response = IAC_WONT;
	    			break;
	    		case NAWS_TYPE:
	    			response = IAC_WILL;
	    			mIsNAWS = true;
	    			mDoneNAWS = false;
	    			break; 
	    		case TC.TERM:
	    			response = IAC_WILL;
	    			break;
	    		case TC.CHARSET:
	    			response = IAC_WILL;
	    			break;
	    		default:
	    			response = IAC_WONT;
	    		}
	    	}
	    	
	    	if (second == IAC_WONT) {
	    		response = IAC_DONT;
	    	}
	    	
	    	if (second == IAC_DONT) {
	    		response = IAC_WONT;
	    	}
	    		
	    	//construct return value
	    	ret[0] = first;
	    	ret[1] = response;
	    	ret[2] = third;
	    	
	    	//byte[] additionalcmd = getCommandSubneg(ret[1],ret[2]);
	    	
	    	/*if(additionalcmd != null) {
	    		//append subnegotiation onto stream.
	    		ByteBuffer buf = ByteBuffer.allocate(ret.length + additionalcmd.length);
	    		buf.put(ret,0,ret.length);
	    		buf.put(additionalcmd,0,additionalcmd.length);
	    		byte[] altret = buf.array();
	    		return altret;
	    	}*/
	    	
	    	return ret;
	    	
	    	
	    }

	/** Processing routine for telnet subnegotiations.
	 * 
	 * @param sequence The whole telnet subnegotiation sequence.
	 * @return The response to <b>sequence</b>
	 */
	public final byte[] getSubnegotiationResponse(final byte[] sequence) {
    	//first some asserts
    	if (sequence[0] != TC.IAC || sequence[1] != TC.SB || sequence[sequence.length - 2] != TC.IAC || sequence[sequence.length - 1] != TC.SE) {
    		//return null, not a valid suboption negotiation starting sequence.
    		return null;
    	}
    	
    	byte[] responsedata = null;
    	//Integer sw = new Integer((char)0xFF & sequence[2]); //fetch out the option number
    	switch(sequence[2]) {
    	case TC.TERM:
    		//get terminal response.
    		//String termtype = "UNKNOWN";
    		
    		
    		String termtype = mTermTypes[mTermTypeAttempt];
    		//Log.e("PROCESSOR","Sending terminal type: " + termtype);
    		try {
				responsedata = termtype.getBytes("ISO-8859-1");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
    		ByteBuffer buf = ByteBuffer.allocate(sequence.length + responsedata.length);
    		buf.put(sequence, 0, sequence.length - TTYPE_DATA_LOCATION);
    		buf.put((byte) 0x00); //fix the response to IS
    		buf.put(responsedata, 0 , responsedata.length);
    		buf.put(sequence, sequence.length - 2, 2);
    		
    		// Advance until the last entry, then keep repeating it (RFC1091 / MTTS cycle end).
    		if (mTermTypeAttempt < mTermTypes.length - 1) {
    			mTermTypeAttempt++;
    		}
    		return buf.array();
    		
    		//break;
    	case COMPRESS2:
    		//holy shit we have the compressor subnegotiation sequence start
    		//construct special return value to notify handler to switch to compression
    		//Log.e("PROCESSOR","COMPRESS2 ENCOUNTERED");
    		
    		byte[] compressstart = new byte[1];
    		compressstart[0] = TC.COMPRESS2;
    		return compressstart;
    	case GMCP:
    		return new byte[] {TC.GMCP};
    		//break;
    	case TC.MSDP:
    		return new byte[] { TC.MSDP };
    	case TC.MSSP:
    		return new byte[] { TC.MSSP };
    	case TC.CHARSET:
    		return buildCharsetSubnegotiationResponse(sequence);
    	default:
    	}
    	
    	
    	
    	
    	return null;
	}
	
	/** Build a CHARSET subnegotiation reply and stash the Java encoding name if accepted. */
	private byte[] buildCharsetSubnegotiationResponse(final byte[] sequence) {
		// IAC SB CHARSET <cmd> ... IAC SE  — need at least 6 bytes
		if (sequence.length < 6) {
			return charsetRejected();
		}
		byte cmd = sequence[3];
		try {
			if (cmd == TC.CHARSET_REQUEST) {
				String chosenWire = pickCharsetFromRequest(sequence);
				if (chosenWire == null) {
					return charsetRejected();
				}
				mPendingCharset = mapCharsetToJava(chosenWire);
				byte[] nameBytes = chosenWire.getBytes("US-ASCII");
				ByteBuffer buf = ByteBuffer.allocate(6 + nameBytes.length);
				buf.put(TC.IAC);
				buf.put(TC.SB);
				buf.put(TC.CHARSET);
				buf.put(TC.CHARSET_ACCEPTED);
				buf.put(nameBytes);
				buf.put(TC.IAC);
				buf.put(TC.SE);
				return buf.array();
			}
			if (cmd == TC.CHARSET_ACCEPTED) {
				// Server accepted our REQUEST — apply the charset they echoed.
				int nameLen = sequence.length - 6;
				if (nameLen > 0) {
					String name = new String(sequence, 4, nameLen, "US-ASCII").trim();
					mPendingCharset = mapCharsetToJava(name);
				}
				// Marker only — no reply to send.
				return new byte[] { TC.CHARSET };
			}
			if (cmd == TC.CHARSET_REJECTED) {
				return new byte[] { TC.CHARSET };
			}
		} catch (UnsupportedEncodingException e) {
			return charsetRejected();
		}
		return charsetRejected();
	}

	/** Parse RFC 2066 REQUEST payload: REQUEST <sep><name>(<sep><name>)* */
	private static String pickCharsetFromRequest(final byte[] sequence) {
		if (sequence.length < 7) {
			return null;
		}
		// Bytes 4..len-3 are separator + charset list (before IAC SE).
		int start = 4;
		int end = sequence.length - 2;
		if (start >= end) {
			return null;
		}
		byte sep = sequence[start];
		java.util.ArrayList<String> names = new java.util.ArrayList<String>();
		StringBuilder cur = new StringBuilder();
		for (int i = start + 1; i < end; i++) {
			byte b = sequence[i];
			if (b == sep) {
				if (cur.length() > 0) {
					names.add(cur.toString());
					cur.setLength(0);
				}
			} else if (b == TC.IAC && i + 1 < end && sequence[i + 1] == TC.IAC) {
				cur.append((char) (TC.IAC & 0xFF));
				i++;
			} else {
				cur.append((char) (b & 0xFF));
			}
		}
		if (cur.length() > 0) {
			names.add(cur.toString());
		}
		// Prefer UTF-8, then ISO-8859-1 / ASCII.
		String fallback = null;
		for (String n : names) {
			String javaName = mapCharsetToJava(n);
			if (javaName == null) {
				continue;
			}
			if ("UTF-8".equals(javaName)) {
				return normalizeWireName(n, javaName);
			}
			if (fallback == null) {
				fallback = normalizeWireName(n, javaName);
			}
		}
		return fallback;
	}

	private static String normalizeWireName(final String wire, final String javaName) {
		if ("UTF-8".equals(javaName)) {
			return "UTF-8";
		}
		if ("ISO-8859-1".equals(javaName)) {
			return "ISO-8859-1";
		}
		return wire;
	}

	/** Map a CHARSET wire name to a Java Charset name, or null if unsupported. */
	static String mapCharsetToJava(final String raw) {
		if (raw == null) {
			return null;
		}
		String n = raw.trim();
		if (n.length() == 0) {
			return null;
		}
		String u = n.toUpperCase(java.util.Locale.US);
		if (u.equals("UTF-8") || u.equals("UTF8") || u.equals("UTF_8")) {
			return "UTF-8";
		}
		if (u.equals("ISO-8859-1") || u.equals("ISO8859-1") || u.equals("ISO_8859_1")
				|| u.equals("LATIN1") || u.equals("LATIN-1") || u.equals("US-ASCII")
				|| u.equals("ASCII")) {
			return "ISO-8859-1";
		}
		// Accept any charset Java knows.
		try {
			if (java.nio.charset.Charset.isSupported(n)) {
				return java.nio.charset.Charset.forName(n).name();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static byte[] charsetRejected() {
		return new byte[] {
				TC.IAC, TC.SB, TC.CHARSET, TC.CHARSET_REJECTED, TC.IAC, TC.SE
		};
	}

	/** Build a client-initiated CHARSET REQUEST for UTF-8. */
	public final byte[] getCharsetRequestUtf8() {
		try {
			byte[] name = "UTF-8".getBytes("US-ASCII");
			// IAC SB CHARSET REQUEST <sep> <name> IAC SE  => 7 + name.length
			ByteBuffer buf = ByteBuffer.allocate(7 + name.length);
			buf.put(TC.IAC);
			buf.put(TC.SB);
			buf.put(TC.CHARSET);
			buf.put(TC.CHARSET_REQUEST);
			buf.put((byte) ';');
			buf.put(name);
			buf.put(TC.IAC);
			buf.put(TC.SE);
			return buf.array();
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	/** Return and clear a charset accepted via subnegotiation, or null. */
	public final String consumePendingCharset() {
		String pending = mPendingCharset;
		mPendingCharset = null;
		return pending;
	}

	/** Method to set the number of columns for NAWS.
	 * 
	 * @param columns The columns to use for NAWS.
	 */
	public final void setColumns(final int columns) {
		if (columns < 1) { return; }
		if (this.mColumns != columns) {
			mDoneNAWS = false;
		}
		this.mColumns = columns;
		
	}

	/** Getter method for the NAWS Column data.
	 * 
	 * @return The number of columns.
	 */
	public final int getColumns() {
		return mColumns;
	}

	/** Method to set the number of rows for NAWS.
	 * 
	 * @param rows The rows to report for NAWS.
	 */
	public final void setRows(final int rows) {
		if (rows < 1) { return; }
		if (this.mRows != rows) {
			mDoneNAWS = false;
		}
		this.mRows = rows;
		
	}

	/** Getter method for NAWS rows.
	 * 
	 * @return Number of rows that NAWS is reporting.
	 */
	public final int getRows() {
		return mRows;
	}
	
	/** Utility method to get the current NAWS String. Used for debugging telnet data.
	 * 
	 * @return The NAWS String that will be sent to the server (or already sent, not sure).
	 */
	public final byte[] getNawsString() {
		if (!mIsNAWS) { return null; }
		if (mDoneNAWS) { return null; }
		//Log.e("OPT","WHO LET THE NAWS OUT");
		ByteBuffer buf = ByteBuffer.allocate(NAWS_STRING_SIZE);
		buf.put(TC.IAC); //IAC
		buf.put(TC.SB); //SB
		buf.put(TC.NAWS); //NAWS
		//buf.put((byte)0x00); //IS
		// RFC 1073: 16-bit width/height, network byte order (high byte first).
		byte highCol = (byte) ((mColumns >> 8) & 0xFF);
		byte lowCol = (byte) (mColumns & 0xFF);
		buf.put(highCol); //columns, high byte
		buf.put(lowCol); //columns, low byte
		
		byte highRow = (byte) ((mRows >> 8) & 0xFF);
		byte lowRow = (byte) (mRows & 0xFF);
		buf.put(highRow); //lines, high byte
		buf.put(lowRow); //lines, low byte
		
		buf.put(TC.IAC); //IAC
		buf.put(TC.SE); //SE
		
		buf.rewind();
		byte[] suboption = buf.array();
		
		mDoneNAWS = true; //only send naws once per valid session.
		//send the data back.
		return suboption;
	}

	/** Reset method, this is just to let the processing routines know that the TTYPE stack is reset to the beginning. */
	public final void reset() {
		mTermTypeAttempt = 0;
	}

	/** Setter method for mUseGMCP.
	 * 
	 * @param useGMCP Weather or not to negotiate GMCP.
	 */
	public final void setUseGMCP(final Boolean useGMCP) {
		mUseGMCP  = useGMCP;
	}

	public final void setUseMTTS(final boolean useMTTS) {
		if (mUseMTTS == useMTTS) {
			return;
		}
		mUseMTTS = useMTTS;
		rebuildTermTypes();
	}

	public final boolean isUseMTTS() {
		return mUseMTTS;
	}

	public final void setUseMSDP(final boolean useMSDP) {
		mUseMSDP = useMSDP;
	}

	public final void setUseMSSP(final boolean useMSSP) {
		mUseMSSP = useMSSP;
	}

	public final boolean isUseMSDP() {
		return mUseMSDP;
	}

	public final boolean isUseMSSP() {
		return mUseMSSP;
	}
}
