package com.resurrection.blowtorch2.lib.window;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.Selection;
//import android.util.Log;
//import android.util.Log;

import com.resurrection.blowtorch2.lib.window.TextTree.Line;

public class TextTree {
	
	/**
	 * Default finder string (bare domains on, built-in TLDs only). Prefer
	 * {@link UrlLinkPatterns#buildFinderString(boolean, String)} when settings matter.
	 */
	public static final String urlFinderString =
			UrlLinkPatterns.buildFinderString(true, "");

	private Pattern urlPattern = UrlLinkPatterns.defaultPattern();
	private Matcher urlMatcher = urlPattern.matcher("");

	/** Strip trailing punctuation often glued to URLs in prose. */
	public static String trimUrlJunk(final String raw) {
		if (raw == null || raw.length() == 0) {
			return raw;
		}
		String url = raw;
		while (url.length() > 0) {
			char c = url.charAt(url.length() - 1);
			if (".,;:!?)]}>'\"".indexOf(c) >= 0) {
				url = url.substring(0, url.length() - 1);
			} else {
				break;
			}
		}
		return url;
	}

	/** First URL-like substring in {@code text} using the default pattern, or null. */
	public static String extractUrl(final String text) {
		return extractUrl(text, UrlLinkPatterns.defaultPattern());
	}

	/** First URL-like substring in {@code text} using {@code pattern}, or null. */
	public static String extractUrl(final String text, final Pattern pattern) {
		if (text == null || text.length() < 4 || pattern == null) {
			return null;
		}
		Matcher m = pattern.matcher(text);
		if (!m.find()) {
			return null;
		}
		String found = m.group(1) != null ? m.group(1) : m.group();
		return trimUrlJunk(found);
	}

	/**
	 * Turn a matched link into something {@link android.net.Uri#parse} can open.
	 * Adds {@code http://} when the scheme is missing (www. / bare domain).
	 */
	public static String normalizeUrl(final String matched) {
		String url = trimUrlJunk(matched);
		if (url == null || url.length() == 0) {
			return null;
		}
		String lower = url.toLowerCase(Locale.US);
		if (lower.startsWith("http://") || lower.startsWith("https://")
				|| lower.startsWith("mailto:")) {
			return url;
		}
		if (!url.contains("://")) {
			url = "http://" + url;
		}
		return url;
	}

	/**
	 * Replace the URL finder for this tree (e.g. after hyperlink settings change).
	 * Does not re-scan existing {@link Text} units — only text added after this
	 * call uses the new pattern.
	 */
	public void setUrlPattern(final Pattern pattern) {
		if (pattern == null) {
			return;
		}
		this.urlPattern = pattern;
		this.urlMatcher = pattern.matcher("");
	}

	/** Apply bare-domain / extras settings via {@link UrlLinkPatterns#build}. */
	public void setUrlLinkSettings(final boolean bareEnabled, final String extraTldsCsv) {
		setUrlPattern(UrlLinkPatterns.build(bareEnabled, extraTldsCsv));
	}

	public Pattern getUrlPattern() {
		return urlPattern;
	}
	
	public static final int MESSAGE_ADDTEXT = 0;
	Pattern colordata = Pattern.compile("\\x1B\\x5B.+m");
	Matcher colormatch = colordata.matcher("");
	
	private int modCount;
	
	public boolean debugLineAdd = false;
	//Pattern newlinelookup = Pattern.compile("\n");
	//Pattern tab = Pattern.compile(new String(new byte[]{0x09}));
	
	//public Handler addTextHandler = null;
	private boolean linkify = true;
	/** Open OSC 8 href; connection thread only. Null means closed. */
	private String osc8Href;
	/** MXP expire group for the open OSC 8 span. */
	private String osc8Expire;
	/** Stamp href onto new text only when this is on. Parsing still runs. */
	private boolean osc8Enabled = true;
	/** Null when the window option is off. Same thread as {@link #addBytesImpl}. */
	private RepeatedLineDimmer repeatedLineDimmer;
	private int dimRepeatedWindow = RepeatedLineDimmer.DEFAULT_WINDOW;
	
	/**
	 * Colour the server is in at the parse point of <em>this</em> tree.
	 * Not static: an extra-text window parsing a gagged colour-trigger line
	 * must not change what the main window's next line restores to.
	 */
	private LinkedList<Integer> bleedColor = new LinkedList<Integer>();
	
	private int MAX_LINES = 2000;
	/**
	 * How many lines the player may ask for.
	 *
	 * <p>Raised from 8000 once {@link #maxBytes} existed. A line count on its own
	 * bounds nothing: measured (TextTreeFootprintTest), a line costs 1.5 KB of
	 * heap as ordinary prose and 85 KB as a 2000-character unwrapped one, so the
	 * same 2000-line cap is 3 MB in one world and 163 MB in another. The byte
	 * budget is what keeps the heap bounded; the line cap is now only the
	 * player's preference within it.
	 */
	public static final int ABSOLUTE_MAX_LINES = 20000;
	public static final int MIN_LINES = 100;

	/** Floor 100, ceiling {@link #ABSOLUTE_MAX_LINES}. Used on XML parse/dump too. */
	public static int clampMaxLines(int maxLines) {
		if (maxLines < MIN_LINES) {
			return MIN_LINES;
		}
		if (maxLines > ABSOLUTE_MAX_LINES) {
			return ABSOLUTE_MAX_LINES;
		}
		return maxLines;
	}

	/**
	 * Raw bytes of text to keep, or 0 for no limit. Pruned together with
	 * {@link #MAX_LINES} — whichever runs out first.
	 *
	 * <p>Off by default because not every tree is scrollback: {@code Connection}'s
	 * {@code mWorking} / {@code mFinished} are parse buffers that are drained
	 * whole by {@code dumpToBytes(false)}, and dropping their oldest lines would
	 * lose text that was never displayed. Only window buffers set a budget
	 * ({@code WindowToken.BUFFER_BYTE_BUDGET}).
	 */
	private int maxBytes = 0;
	
	private String encoding = "UTF-8";
	
	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	private int breakAt = 43;
	private boolean wordWrap = true;
	
	private int brokenLineCount = 0;
	
	private int totalbytes = 0;
	private boolean cullExtraneous = true;
	
	
	public int getBrokenLineCount() {
		return brokenLineCount;
	}

	public void setBrokenLineCount(int brokenLineCount) {
		this.brokenLineCount = brokenLineCount;
	}

	public TextTree() {
		//simpleMode = pMode;
		mLines = new LinkedList<Line>();
		//LinkedList<Unit> list = new LinkedList<Unit>();
		//addTextHandler = new AddTextHandler();
		bleedColor.add(37);
		bleedColor.add(0);
	}
	
	public void addString(String str) {
		try {
			this.addBytesImplSimple(str.getBytes(encoding));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public byte[] dumpToBytes(boolean keep) {
		// Colour units already carry their CSI in bin. OSC 8 hrefs do not —
		// they were parsed out of the stream — so they have to be written
		// back here or the UI's addBytesImpl never sees them (mWorking is
		// drained, then this byte stream is what the window parses).
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, totalbytes));
		String dumpHref = null;
		String dumpExpire = null;
		ListIterator<Line> i = mLines.listIterator(mLines.size());
		while(i.hasPrevious()) {
			Line l = i.previous();
			if (l.receivedAt > 0L) {
				byte[] stamp = LineStamp.marker(l.receivedAt);
				out.write(stamp, 0, stamp.length);
			}
			Iterator<Unit> iu = l.getData().iterator();
			while(iu.hasNext()) {
				Unit u = iu.next();
				switch(u.type) {
				case WHITESPACE:
				case TEXT:
					dumpHref = emitOsc8Transition(out, dumpHref, dumpExpire,
							((Text)u).getHref(), ((Text)u).getExpireGroup());
					dumpExpire = ((Text)u).getExpireGroup();
					if (dumpHref == null) {
						dumpExpire = null;
					}
					byte[] tbin = ((Text)u).bin;
					if (tbin != null && tbin.length > 0) {
						out.write(tbin, 0, tbin.length);
					}
					break;
				case COLOR:
					byte[] cbin = ((Color)u).bin;
					if (cbin != null && cbin.length > 0) {
						out.write(cbin, 0, cbin.length);
					}
					break;
				case NEWLINE:
					out.write(NEWLINE);
					break;
				case TAB:
					out.write(TAB);
					break;
				default:
					break;
				}
			}
		}
		emitOsc8Transition(out, dumpHref, dumpExpire, null, null);
		byte[] ret = out.toByteArray();
		if(!keep) empty();
		return ret;
	}

	/**
	 * Write an OSC 8 open/close so {@link #dumpToBytes} round-trips through
	 * {@link #addBytesImpl}. Each dump is self-contained (closed at the end)
	 * so the UI parser does not leak an href into the next chunk.
	 */
	private String emitOsc8Transition(final ByteArrayOutputStream out,
			final String current, final String currentExpire,
			final String next, final String nextExpire) {
		boolean same = current == null ? next == null : current.equals(next);
		if (same) {
			boolean expSame = currentExpire == null ? nextExpire == null
					: currentExpire.equals(nextExpire);
			if (expSame) {
				return current;
			}
		}
		if (current != null) {
			writeOsc8Close(out);
		}
		if (next != null) {
			writeOsc8Open(out, next, nextExpire);
		}
		return next;
	}

	private void writeOsc8Open(final ByteArrayOutputStream out, final String uri,
			final String expireGroup) {
		out.write(ESC);
		out.write(']');
		out.write('8');
		out.write(';');
		String id = com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.expireId(expireGroup);
		if (id != null) {
			try {
				byte[] idb = ("id=" + id).getBytes(encoding);
				out.write(idb, 0, idb.length);
			} catch (UnsupportedEncodingException e) {
				byte[] idb = ("id=" + id).getBytes();
				out.write(idb, 0, idb.length);
			}
		}
		out.write(';');
		try {
			byte[] ub = uri.getBytes(encoding);
			out.write(ub, 0, ub.length);
		} catch (UnsupportedEncodingException e) {
			byte[] ub = uri.getBytes();
			out.write(ub, 0, ub.length);
		}
		out.write(0x07);
	}

	private static void writeOsc8Close(final ByteArrayOutputStream out) {
		out.write(ESC);
		out.write(']');
		out.write('8');
		out.write(';');
		out.write(';');
		out.write(0x07);
	}

	public void empty() {
		clearLines();
		osc8Href = null;
		osc8Expire = null;
	}

	/**
	 * Drop the lines but keep OSC 8 parser state. {@code mWorking} uses this
	 * after handing lines to {@code mFinished}: a link can open in one TCP
	 * chunk and close in the next. {@link #empty()} is the full wipe (clear
	 * screen, replay).
	 */
	public void drainLines() {
		clearLines();
	}

	private void clearLines() {
		mLines.clear();
		this.totalbytes = 0;
		this.brokenLineCount=0;
		appendLast = false;
	}
	
	
	public LinkedList<Line> getLines() {
		return mLines;
	}

	public void setLines(LinkedList<Line> mLines) {
		this.mLines = mLines;
	}

	/**
	 * Split an SGR byte sequence ({@code ESC [ … m}) into the integer ops
	 * {@link com.resurrection.blowtorch2.lib.window.Window} walks.
	 *
	 * <p>{@code ;} separates parameters; {@code :} separates subparameters
	 * (ITU T.416 / ISO-8613-6). Both {@code CSI 38;5;n m} and {@code CSI 38:5:n m}
	 * become {@code 38, 5, n}. Truecolor {@code CSI 38:2::r:g:b m} drops the
	 * empty colorspace slot and becomes {@code 38, 2, r, g, b}, the same shape
	 * as the xterm semicolon form the draw path already understands.
	 */
	private LinkedList<Integer> getOperationsFromBytes(byte[] in) {
		ArrayList<ArrayList<Integer>> params = new ArrayList<ArrayList<Integer>>();
		ArrayList<Integer> current = new ArrayList<Integer>();
		ArrayList<Integer> digits = new ArrayList<Integer>();
		boolean sawDigit = false;
		for (int i = 0; i < in.length; i++) {
			byte b = in[i];
			if (b >= b0 && b <= b9) {
				digits.add(Integer.valueOf(getAsciiNumber(b)));
				sawDigit = true;
			} else if (b == COLON) {
				current.add(sawDigit ? Integer.valueOf(digitsToInt(digits)) : null);
				digits.clear();
				sawDigit = false;
			} else if (b == SEMI || b == m) {
				current.add(sawDigit ? Integer.valueOf(digitsToInt(digits)) : null);
				digits.clear();
				sawDigit = false;
				params.add(current);
				current = new ArrayList<Integer>();
				if (b == m) {
					break;
				}
			}
		}
		LinkedList<Integer> tmp = expandSgrParams(params);
		bleedColor = tmp;
		return tmp;
	}

	private static int digitsToInt(ArrayList<Integer> digits) {
		int v = 0;
		for (int i = 0; i < digits.size(); i++) {
			v = v * 10 + digits.get(i).intValue();
		}
		return v;
	}

	private static int sgrInt(Integer value) {
		return value == null ? 0 : value.intValue();
	}

	/**
	 * Flatten semicolon parameters / colon subparameters into the linear op
	 * list Window's register machine consumes.
	 */
	private static LinkedList<Integer> expandSgrParams(ArrayList<ArrayList<Integer>> params) {
		LinkedList<Integer> out = new LinkedList<Integer>();
		for (int p = 0; p < params.size(); p++) {
			ArrayList<Integer> group = params.get(p);
			if (group.isEmpty()) {
				out.addLast(Integer.valueOf(0));
				continue;
			}
			int code = sgrInt(group.get(0));
			if ((code == 38 || code == 48) && group.size() > 1) {
				int mode = sgrInt(group.get(1));
				if (mode == 5) {
					int idx = group.size() > 2 ? sgrInt(group.get(2)) : 0;
					out.addLast(Integer.valueOf(code));
					out.addLast(Integer.valueOf(5));
					out.addLast(Integer.valueOf(idx));
					continue;
				}
				if (mode == 2) {
					int i = 2;
					while (i < group.size() && group.get(i) == null) {
						i++;
					}
					// T.416: optional colorspace id before R,G,B.
					if (group.size() - i >= 4) {
						i++;
					}
					int r = i < group.size() ? sgrInt(group.get(i)) : 0;
					int g = i + 1 < group.size() ? sgrInt(group.get(i + 1)) : 0;
					int b = i + 2 < group.size() ? sgrInt(group.get(i + 2)) : 0;
					out.addLast(Integer.valueOf(code));
					out.addLast(Integer.valueOf(2));
					out.addLast(Integer.valueOf(r));
					out.addLast(Integer.valueOf(g));
					out.addLast(Integer.valueOf(b));
					continue;
				}
			}
			for (int s = 0; s < group.size(); s++) {
				out.addLast(Integer.valueOf(sgrInt(group.get(s))));
			}
		}
		return out;
	}
	
	private static int getAsciiNumber(byte b) {
		switch(b) {
		case b0:
			return 0;
		case b1:
			return 1;
		case b2:
			return 2;
		case b3:
			return 3;
		case b4:
			return 4;
		case b5:
			return 5;
		case b6:
			return 6;
		case b7:
			return 7;
		case b8:
			return 8;
		case b9:
			return 9;
		default:
			return 0;
			
		}
	}
	
	private final byte TAB = (byte)0x09;
	private final static byte ESC = (byte)0x1B;
	/** Abort a runaway OSC/DCS that never sees BEL/ST (broken hyperlink closers). */
	private static final int MAX_OSC_PAYLOAD_BYTES = 4096;
	private final static byte BRACKET = (byte)0x5B;
	private final byte NEWLINE = (byte)0x0A;
	//private final byte CARRIAGE = (byte)0x0D;
	private final static byte m = (byte)0x6D;
	private final static byte SEMI = (byte)0x3B;
	/** ITU T.416 / ISO-8613-6 SGR subparameter separator. */
	private final static byte COLON = (byte)0x3A;
	
	private final static byte b0 = (byte)0x30;
	private final static byte b1 = (byte)0x31;
	private final static byte b2 = (byte)0x32;
	private final static byte b3 = (byte)0x33;
	private final static byte b4 = (byte)0x34;
	private final static byte b5 = (byte)0x35;
	private final static byte b6 = (byte)0x36;
	private final static byte b7 = (byte)0x37;
	private final static byte b8 = (byte)0x38;
	private final static byte b9 = (byte)0x39;
	
	//more ansi escape sequences.
	private final byte A = (byte)0x41;
	private final byte B = (byte)0x42;
	private final byte C = (byte)0x43;
	private final byte D = (byte)0x44;
	private final byte E = (byte)0x45;
	private final byte F = (byte)0x46;
	private final byte G = (byte)0x47;
	private final byte H = (byte)0x48;
	private final byte J = (byte)0x4A;
	private final byte K = (byte)0x4B;
	private final byte S = (byte)0x53;
	private final byte T = (byte)0x54;
	private final byte f = (byte)0x66;
	private final byte s = (byte)0x73;
	private final byte u = (byte)0x75;
	private final byte n = (byte)0x6E;
	
	public void addBytesImplSimple(byte[] data) {
		//int startcount = this.getBrokenLineCount();
		ByteBuffer sb = ByteBuffer.allocate(data.length);
		for(int i=0;i<data.length;i++) {
			if(data[i] == NEWLINE) {
				int size = sb.position();
				byte[] buf = new byte[size];
				sb.rewind();
				sb.get(buf,0,size);
				
				sb.clear();
				
				try {
					Text u = new Text(buf);
					Line l = new Line();
					l.getData().addLast(u);
					l.getData().addLast(new NewLine());
					addLine(l);
				} catch (UnsupportedEncodingException e) {
					
					e.printStackTrace();
				}
				
				
			} else {
				sb.put(data[i]);
			}
		}
		
		if(sb.position() > 0) {
			int size = sb.position();
			byte[] buf = new byte[size];
			sb.rewind();
			sb.get(buf,0,size);
			
			sb.clear();
			
			try {
				Text u = new Text(buf);
				Line l = new Line();
				l.getData().addLast(u);
				addLine(l);
			} catch (UnsupportedEncodingException e) {
				
				e.printStackTrace();
			}
		}
		
		this.prune();
	}
	
	static enum RUN {
		WHITESPACE,
		TEXT,
		NEW
	};
	boolean appendLast = false; //for marking when the addtext call has ended with a newline or not.
	private byte[] holdover = null;
	LinkedList<Integer> prev_color = null;
	Color lastColor = null;
	byte[] strag = null;
	public int addBytesImpl(byte[] data) throws UnsupportedEncodingException {
		//if(simpleMode) {
		//	addBytesImplSimple(data);
		//}
		//this actually shouldn't be too hard to do with just a for loop.
		//STATE init = STATE.TEXT;
		int projected = totalbytes + data.length;
		//Log.e("TREE","ADDING: " + data.length + " bytes, buffer has " + totalbytes + " total bytes. " + projected + " projected.");
		//LinkedList<Line> lines = new LinkedList<Line>();
		
		int startcount = this.getBrokenLineCount();
		int linesadded = 0;
		Line tmp = null;
		
		if(holdover != null) {
			//Log.e("TREE","HOLDOVER SEQUENCE:" + new String(holdover,"ISO-8859-1"));
			ByteBuffer b = ByteBuffer.allocate(holdover.length + data.length);
			b.put(holdover,0,holdover.length);
			b.put(data,0,data.length);
			b.rewind();
			data = b.array();
			holdover = null;
		}
		
		// A line is finished when the newline that closed it is its last unit, and
		// every path that closes one puts the NewLine there last. Asking the last
		// unit directly is therefore the whole question.
		//
		// This used to scan the line for the last Text or NewLine, which answered
		// nothing at all when the newest line held neither -- and then left
		// appendLast holding whatever the previous call had worked out, since it
		// is a field. A chunk ending "text\n\033[0m" leaves exactly that: the
		// newline closes the line, and the colour reset opens a fresh one carrying
		// a single Color unit. With a stale false that unit was orphaned as a
		// blank line, and the text belonging to it began another line below.
		appendLast = false;
		if(mLines.size() > 0) {
			LinkedList<Unit> newest = mLines.get(0).getData();
			// Empty is only ever an artefact -- a blank line from the server is a
			// line holding a NewLine -- so continue into it rather than leave it.
			appendLast = newest.isEmpty() || !(newest.getLast() instanceof NewLine);
		}
		
		LinkedList<Unit> ldata = null;
		Line reopened = null;
		if(appendLast) { //yay appendLast is over. now just look at the last line of the buffer, parse through it and find if the last text in it (not color) was a newline.
			//if(mLines.size() > 0) {
				tmp = mLines.remove(0); //dont worry kids, it'll be appended back.
				totalbytes -= tmp.bytes; //this will be added back too, this is just to avoid memory leaking
				ldata = tmp.getData();
				brokenLineCount -= tmp.breaks + 1;
				reopened = tmp;
				//Log.e("TREE",">>>>>>>>>>>>>>APPENDING TO: " + deColorLine(tmp));
			//}
		} //else {
			//tmp = new Line();
		//}

		tmp = new Line();
		tmp.serverColorAtStart = bleedColor;

		if(ldata != null) {
			tmp = new Line();
			tmp.setData(ldata);
			// Carrying on with a line the last chunk left open: the line object is
			// new, the line is not, so its start colour and any trigger colour
			// still running on it come with the data.
			tmp.serverColorAtStart = reopened.serverColorAtStart;
			tmp.triggerColorOpen = reopened.triggerColorOpen;
			tmp.triggerColorRestore = reopened.triggerColorRestore;
			tmp.receivedAt = reopened.receivedAt;
			//Log.e("TREE","DATA STRIP OUT:" + deColorLine(tmp));
		}

		ByteBuffer sb = ByteBuffer.allocate(data.length);
		ByteBuffer cb = ByteBuffer.allocate(data.length);
		RUN runtype = RUN.NEW;
		
		//boolean endOnNewLine = false;
		for(int i=0;i<data.length;i++) {
			//Log.e("TREE","DATA PROCESSING LOOP: " + deColorLine(tmp));
			switch(data[i]) {
			case ESC:
				// Flush pending text before handling an escape sequence.
				if(sb.position() > 0) {
					int size = sb.position();
					strag = new byte[size];
					sb.rewind();
					sb.get(strag,0,size);
					sb.rewind();
					switch(runtype) {
					case WHITESPACE:
						tmp.getData().addLast(stampOsc8(new WhiteSpace(strag)));
						break;
					case TEXT:
						tmp.getData().addLast(stampOsc8(new Text(strag)));
						break;
					default:
						break;
					}
					runtype = RUN.NEW;
				}

				if( (i+1) >= data.length) {
					holdover = new byte[]{ ESC };
					// Guarded like the end of the method: an escape arriving right
					// after a newline leaves tmp empty, and adding it put a blank
					// line in the buffer that the server never sent.
					if(tmp.getData().size() > 0) {
						addLine(tmp);
						linesadded += tmp.breaks + 1;
					}
					return this.getBrokenLineCount() - startcount;
				}

				byte intro = data[i+1];

				// CSI: ESC [ ... final (0x40-0x7E). Color 'm' is applied; others skipped.
				if(intro == BRACKET) {
					cb.rewind();
					cb.put(data[i]);
					cb.put(data[i+1]);

					if( (i+2) >= data.length) {
						int tmpsize = cb.position();
						holdover = new byte[tmpsize];
						cb.rewind();
						cb.get(holdover,0,tmpsize);
						if(tmp.getData().size() > 0) {
							addLine(tmp);
							linesadded += tmp.breaks + 1;
						}
						return this.getBrokenLineCount() - startcount;
					}

					boolean done = false;
					for(int j=i+2;j<data.length;j++) {
						byte b = data[j];
						if(b == m) {
							done = true;
							cb.put(m);
							int cmdsize = cb.position();
							byte[] cmd = new byte[cmdsize];
							cb.rewind();
							cb.get(cmd,0,cmdsize);

							Color c = new Color(cmd);
							if(lastColor == null) {
								lastColor = c;
								tmp.getData().addLast(c);
							} else if(lastColor.equals(c)) {
								if(this.isCullExtraneous()) {
									//do nothing
								} else {
									tmp.getData().addLast(c);
								}
							} else {
								tmp.getData().addLast(c);
								lastColor = c;
							}

							cb.rewind();
							i = j;
							break;
						}
						// Any CSI final byte (@ through ~) terminates the sequence.
						int ub = b & 0xFF;
						if(ub >= 0x40 && ub <= 0x7E) {
							done = true;
							cb.rewind();
							i = j;
							break;
						}
						cb.put(b);
					}
					if(!done) {
						int mtmpsz = cb.position();
						holdover = new byte[mtmpsz];
						cb.rewind();
						cb.get(holdover,0,mtmpsz);
						if(tmp.getData().size() > 0) {
							addLine(tmp);
							linesadded += tmp.breaks + 1;
						}
						return this.getBrokenLineCount() - startcount;
					}
					break;
				}

				// OSC (ESC ]), DCS (ESC P), APC (ESC _), PM (ESC ^): skip until BEL or ST (ESC \).
				if(intro == (byte)0x5D || intro == (byte)0x50
						|| intro == (byte)0x5F || intro == (byte)0x5E) {
					boolean done = false;
					// i moves onto the terminator below, so where the payload
					// began has to be kept before that happens.
					final int oscPayloadStart = i + 2;
					int payloadEnd = -1;
					for(int j = oscPayloadStart; j < data.length; j++) {
						if(data[j] == 0x07) { // BEL
							done = true;
							payloadEnd = j;
							i = j;
							break;
						}
						if(data[j] == ESC && (j + 1) < data.length && data[j + 1] == (byte)0x5C) {
							done = true;
							payloadEnd = j;
							i = j + 1;
							break;
						}
						// Darkwind (and some other MUDs) close OSC 8 hyperlinks as
						// ESC ]8;;…ESC ]8;;) — a second ESC ] with no BEL/ST. Without
						// this, the open sequence never ends and every later byte is
						// held as OSC holdover: the screen freezes after the first
						// link while GMCP and the session log keep going. Measured
						// on newbiehist 12 Aug 2026.
						if(data[j] == ESC && (j + 1) < data.length) {
							byte next = data[j + 1];
							if(next == (byte)0x5D || next == (byte)0x50
									|| next == (byte)0x5F || next == (byte)0x5E) {
								done = true;
								payloadEnd = j;
								i = j - 1; // re-process this ESC as a new sequence
								break;
							}
						}
						// A bare newline inside OSC is not legal for OSC 8; abort so
						// a missing terminator cannot swallow the rest of the session.
						if(data[j] == NEWLINE) {
							done = true;
							payloadEnd = j;
							i = j - 1;
							break;
						}
						if((j - oscPayloadStart) >= MAX_OSC_PAYLOAD_BYTES) {
							done = true;
							payloadEnd = j;
							i = j - 1;
							break;
						}
					}
					// One OSC sequence is ours: the marker that puts a picture into
					// the text. Every other one is still skipped exactly as before.
					// Riding on OSC is what makes an unrecognised marker disappear
					// rather than print — see InlineImageMarker.
					if(done && intro == (byte)0x5D && payloadEnd > oscPayloadStart) {
						byte[] payload = new byte[payloadEnd - oscPayloadStart];
						System.arraycopy(data, oscPayloadStart, payload, 0, payload.length);
						String payloadStr = new String(payload, encoding);
						com.resurrection.blowtorch2.lib.service.InlineImageMarker.Parsed m =
								com.resurrection.blowtorch2.lib.service.InlineImageMarker.parse(
										payloadStr);
						if(m != null) {
							tmp.setInlineImage(m.key, m.lines);
						}
						Long stamp = LineStamp.parse(payloadStr);
						if(stamp != null && tmp.getReceivedAt() == 0L) {
							tmp.setReceivedAt(stamp.longValue());
						}
						OscEight.Result osc = OscEight.parse(payloadStr);
						if(osc != null) {
							if(osc.uri != null && com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isExpireCommand(osc.uri)) {
								expireMxpLinks(com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.expireGroup(osc.uri));
								osc8Href = null;
								osc8Expire = null;
								Text expireMarker = new Text("");
								expireMarker.setHref(osc.uri);
								tmp.getData().addLast(expireMarker);
							} else if(osc.isClose()) {
								osc8Href = null;
								osc8Expire = null;
							} else if(osc8Enabled || com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isMxpHref(osc.uri)) {
								osc8Href = osc.uri;
								osc8Expire = com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.groupFromExpireId(osc.id);
							} else {
								// Recognised OSC 8 we are not showing (send:/prompt:
								// with the option off). A new open still ends the
								// previous span; leaving mxp-send stamped would
								// bleed it onto the send: display text.
								osc8Href = null;
								osc8Expire = null;
							}
						}
					}
					if(!done) {
						int len = data.length - i;
						holdover = new byte[len];
						System.arraycopy(data, i, holdover, 0, len);
						if(tmp.getData().size() > 0) {
							addLine(tmp);
							linesadded += tmp.breaks + 1;
						}
						return this.getBrokenLineCount() - startcount;
					}
					break;
				}

				// Two-byte ESC sequences (ESC 7/8/c/D/E/H/M/…): consume and ignore.
				i = i + 1;
				break;
			case TAB:
				//make new tab node.
				tmp.getData().addLast(new Tab());
				break;
			case NEWLINE:
				//Log.e("TREE","START APPEND DUE TO NEWLINE:"  + deColorLine(tmp));
				//Log.e("TREE","NEWLINE ADDING: " +sb.toString());
				//linesadded += 1;
				if(sb.position() > 0) {
					int nsize = sb.position();
					byte[] txtdata = new byte[nsize];
					sb.rewind();
					sb.get(txtdata,0,nsize);
					//Log.e("TREE","APPEND TO LINE:"  + deColorLine(tmp));
					switch(runtype) {
					case WHITESPACE:
						tmp.getData().addLast(stampOsc8(new WhiteSpace(txtdata)));
						break;
					case TEXT:
						tmp.getData().addLast(stampOsc8(new Text(txtdata)));
						break;
					default:
						break;
					}
					runtype = RUN.NEW;
					sb.rewind();
				}
				//append the line as we do.
				NewLine nl = new NewLine();
				tmp.getData().addLast(nl);
				// The line is closed here and nowhere else, so this is where the
				// colour the server is in at its end is known.
				tmp.serverColorAtEnd = bleedColor;
				//Log.e("TREE","APPEND DUE TO NEWLINE:"  + deColorLine(tmp));
				addLine(tmp);
				linesadded += tmp.breaks + 1;
				tmp = new Line();
				tmp.serverColorAtStart = bleedColor;
				break;
			default:
				//put it in the buffer.
				if(Character.isWhitespace(data[i])) {
					//start whitespace run
					//Log.e("BYTE","FOUND WHITESPACE");
					switch(runtype) {
					case TEXT:
						int len = sb.position();
						byte[] cap = new byte[len];
						sb.rewind();
						sb.get(cap,0,len);
						tmp.mData.addLast(stampOsc8(new Text(cap)));
						
						runtype = RUN.WHITESPACE;
						sb.rewind();
						break;
					case NEW:
						runtype = RUN.WHITESPACE;
						break;
					default:
						break;
					}
				} else {
					switch(runtype) {
					case WHITESPACE:
						int len = sb.position();
						byte[] cap = new byte[len];
						sb.rewind();
						sb.get(cap,0,len);
						//Log.e("BYTE","ADDING WHITESPACE RUN");
						tmp.mData.addLast(stampOsc8(new WhiteSpace(cap)));
						runtype = RUN.TEXT;
						sb.rewind();
						break;
					case NEW:
						runtype = RUN.TEXT;
						break;
					default:
						break;
					}
				}
				sb.put(data[i]);
				//Log.e("TREE","BUFFER NOW:"+sb.toString()+"|");
				//endOnNewLine = false;
				
				break;
			}
		}
		//Log.e("TREE","BUFFER CONTAINS:" +sb.toString() + "||||");
		
		if(sb.position() > 0) {
			int fsize = sb.position();
			byte[] tmpb = new byte[fsize];
			sb.rewind();
			sb.get(tmpb,0,fsize);
			// Incomplete UTF-8 at the end of a TCP chunk must be held — otherwise
			// █ (E2 96 88) split across packets becomes three U+FFFD cells and maps skew.
			int incomplete = ("UTF-8".equalsIgnoreCase(encoding)
					|| "UTF8".equalsIgnoreCase(encoding))
					? utf8IncompleteTailLength(tmpb) : 0;
			if (incomplete > 0 && incomplete < fsize) {
				byte[] complete = new byte[fsize - incomplete];
				System.arraycopy(tmpb, 0, complete, 0, complete.length);
				holdover = new byte[incomplete];
				System.arraycopy(tmpb, complete.length, holdover, 0, incomplete);
				tmpb = complete;
			} else if (incomplete > 0) {
				holdover = tmpb;
				tmpb = null;
			}
			if (tmpb != null && tmpb.length > 0) {
				tmp.getData().addLast(stampOsc8(new Text(tmpb)));
			}
			sb.rewind();
		}
		
		if(tmp.getData().size() > 0) {
			addLine(tmp);
			linesadded += tmp.breaks + 1;
		}
		
		//if(debugLineAdd) {
		//	Log.e("TREE","ADDED " + linesadded + " LINES TO TREE");
		//}
		
		int endcount = this.getBrokenLineCount();
		prune();
		
		return endcount - startcount;
	}
	
	/**
	 * Bytes at the end of {@code buf} that form an incomplete UTF-8 sequence, or 0.
	 * Used so multi-byte glyphs (e.g. U+2588 █) are not decoded as � across packet boundaries.
	 */
	private static int utf8IncompleteTailLength(final byte[] buf) {
		if (buf == null || buf.length == 0) {
			return 0;
		}
		// Only relevant when decoding as UTF-8 (default for modern MUDs).
		final int len = buf.length;
		int i = len - 1;
		if ((buf[i] & 0x80) == 0) {
			return 0; // trailing ASCII — complete
		}
		while (i > 0 && (buf[i] & 0xC0) == 0x80) {
			i--;
		}
		final int lead = buf[i] & 0xFF;
		final int expected;
		if ((lead & 0xE0) == 0xC0) {
			expected = 2;
		} else if ((lead & 0xF0) == 0xE0) {
			expected = 3;
		} else if ((lead & 0xF8) == 0xF0) {
			expected = 4;
		} else {
			return 0;
		}
		final int have = len - i;
		if (have > 0 && have < expected) {
			return have;
		}
		return 0;
	}

	public void prune() {
		while(mLines.size() > MAX_LINES) {
			//Log.e("TREE","TRIMMING BUFFER");
			dropOldestLine();
		}
		// Never down to nothing: one line stays whatever its size, so a single
		// enormous line is shown rather than silently swallowed.
		while(maxBytes > 0 && totalbytes > maxBytes && mLines.size() > 1) {
			dropOldestLine();
		}
	}

	/** mLines is newest-first, so the oldest line is the last one. */
	private void dropOldestLine() {
		Line del = mLines.removeLast();
		brokenLineCount -= (1 + del.breaks);
		totalbytes -= del.bytes;
	}

	/**
	 * Cap the text kept, in raw bytes.
	 *
	 * @param bytes budget, or 0 to keep every line the line cap allows.
	 */
	public void setMaxBytes(int bytes) {
		maxBytes = bytes < 0 ? 0 : bytes;
		prune();
	}

	public int getMaxBytes() {
		return maxBytes;
	}

	/** Raw bytes currently held — what {@link #setMaxBytes} is measured against. */
	public int getTotalBytes() {
		return totalbytes;
	}
	
	/*private class AddTextHandler extends Handler {
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MESSAGE_ADDTEXT:
				try {
					addBytesImpl((byte[])msg.obj);
				} catch (UnsupportedEncodingException e) {
					
					e.printStackTrace();
				}
				break;
			default:
				break;
			}
		}
	}*/
	
	public void updateMetrics() {
		brokenLineCount = 0;
		totalbytes = 0;
		ListIterator<Line> iterator = mLines.listIterator(mLines.size());
		while(iterator.hasPrevious()) {
			Line l = iterator.previous();
			l.updateData();
			totalbytes += l.bytes;
			brokenLineCount += l.breaks + 1;
		}
	}
	
	private void addLine(Line l) {
		if (l.receivedAt == 0L) {
			l.receivedAt = System.currentTimeMillis();
		}
		l.updateData();
		brokenLineCount += l.breaks + 1;
		totalbytes += l.bytes;
		markDimRepeatedIfFinished(l);
		//Log.e("TREE","A:" + deColorLine(l));
		mLines.add(0,l);
	}

	/**
	 * A line is finished when its last unit is a NewLine. Incomplete chunks
	 * (holdover, TCP split) are added without one and must not be remembered.
	 */
	private void markDimRepeatedIfFinished(final Line l) {
		if (repeatedLineDimmer == null || l == null) {
			return;
		}
		final LinkedList<Unit> data = l.getData();
		if (data == null || data.isEmpty() || !(data.getLast() instanceof NewLine)) {
			return;
		}
		l.setDimRepeated(repeatedLineDimmer.rememberAndShouldDim(deColorLine(l).toString()));
	}

	/** Enable or disable line-level dim memory. Off forgets the recent lines. */
	public void setDimRepeatedLines(final boolean enabled) {
		if (enabled) {
			if (repeatedLineDimmer == null) {
				repeatedLineDimmer = new RepeatedLineDimmer(dimRepeatedWindow);
			}
		} else {
			repeatedLineDimmer = null;
		}
	}

	/** How many recent long lines stay in memory. Applied even while dimming is off. */
	public void setDimRepeatedWindow(final int n) {
		dimRepeatedWindow = RepeatedLineDimmer.clampWindow(n);
		if (repeatedLineDimmer != null) {
			repeatedLineDimmer.setWindowSize(dimRepeatedWindow);
		}
	}
	
	LinkedList<Line> mLines;
	Line pStart;
	Line pSend;
	
	public class Line {
		//protected int totalchars;
		protected int charcount;
		protected int breaks;
		protected int bytes;
		//protected int viswidth;
		private ListIterator<Unit> theIterator = null;
		
		public int getBreaks() {
			return breaks;
		}

		public void setBreaks(int breaks) {
			this.breaks = breaks;
		}

		protected LinkedList<Unit> mData;

		/** A picture drawn over this line and the ones under it, or null.
		 *
		 * <p>Deliberately a field on the line rather than a new kind of
		 * {@code Unit}. A Unit would have to be understood by every
		 * {@code switch(u.type)} in the parser and the draw loop, and would be
		 * asked how many characters wide it is — a question a picture has no
		 * answer to. As a field it is invisible to all of that: the line stays
		 * an ordinary empty line, so wrapping, selection, tap targets and the
		 * scroll arithmetic keep working on it without knowing it is there.
		 *
		 * <p>Every line is still the same height. The picture covers whole
		 * lines; it never makes one taller.
		 */
		private String inlineImageKey;
		/** How many lines the picture covers, this one included. */
		private int inlineImageLines;
		/**
		 * True when this finished line matched a recent long line. A field, not a
		 * Unit — same reason as {@link #inlineImageKey}: the draw loop already
		 * walks units, and a flag does not change width, wrap or selection.
		 */
		private boolean dimRepeated;
		/**
		 * When this line arrived, millis since epoch, or 0 if never stamped.
		 * A field, not a Unit: same reason as {@link #inlineImageKey}.
		 */
		private long receivedAt;

		public Line() {
			mData = new LinkedList<Unit>();
			breaks =0;
		}

		/** Mark this line as the top of a picture. See {@code InlineImageMarker}. */
		public void setInlineImage(final String key, final int lines) {
			this.inlineImageKey = key;
			this.inlineImageLines = lines;
		}

		/** The picture's key in the UI image store, or null when there is none. */
		public String getInlineImageKey() {
			return inlineImageKey;
		}

		public int getInlineImageLines() {
			return inlineImageLines;
		}

		public void setDimRepeated(final boolean dimRepeated) {
			this.dimRepeated = dimRepeated;
		}

		public boolean isDimRepeated() {
			return dimRepeated;
		}

		public void setReceivedAt(final long receivedAt) {
			this.receivedAt = receivedAt;
		}

		public long getReceivedAt() {
			return receivedAt;
		}
		
		public void updateData() {
			this.breaks = 0;
			this.charcount = 0;
			this.bytes = 0;
			//this.viswidth = 0;
			
			//boolean broken = false;
			//boolean visfound = false;
			//Break lastBreak = null;
			//int backlogvis = 0;
			//int tmpvis = 0;
			
			int charsinline = 0; //tracker for how many characters are in the line
			//int nonWhiteSpaceRun = 0; //tracker for how many characters have accumulated without whitespace
			boolean whiteSpaceFound = false;
			
			theIterator = mData.listIterator(0);
			stripBreaks();
			//Counter counter = new Counter();
			while(theIterator.hasPrevious()) {
				theIterator.previous();
			}
			
			while(theIterator.hasNext()) {
			//while()
			
				Unit u = theIterator.next();
				
				switch(u.type) {
				case WHITESPACE:
					if(wordWrap) whiteSpaceFound = true;
				case TEXT:
					charsinline += ((Text)u).charcount;
					this.bytes += ((Text)u).bytecount;
					this.charcount += u.charcount;
					break;
				case TAB:
				case NEWLINE:
				case COLOR:
					this.bytes += u.reportSize();
					break;
				case BREAK:
					theIterator.remove();
					this.breaks -= 1;
					break;	
				}
				//check if it is whitespace
//				if(u instanceof WhiteSpace) {
//					if(wordWrap) {
//						whiteSpaceFound = true;
//					}
//					//this.charcount += u.charcount;
//				}
//				
//				if(u instanceof Text) {
//					//update charsinline
//					charsinline += ((Text)u).charcount;
//					this.bytes += ((Text)u).charcount;
//					this.charcount += u.charcount;
//				}
//				
//				if(u instanceof Tab || u instanceof NewLine || u instanceof Color) {
//					this.bytes += u.reportSize();
//				}
//				if(u instanceof Break) {
//					theIterator.remove();
//					this.breaks -= 1;
//				}
				
				if(charsinline > breakAt) {
					int amount = charsinline - breakAt;
					if(wordWrap) {
						if(whiteSpaceFound && !segmentLooksLikeAnsiMap()) {
							//find the nearest whitespace and break.
							boolean found = false;
							//i.previous(); //advance back because we are on the right hand side of the unit that broke.
							while(!found && theIterator.hasPrevious()) {
								Unit tmp = theIterator.previous();
								if(tmp instanceof WhiteSpace) {
									theIterator.next(); //get on the right side of the unit.
									Break b = new Break();
									theIterator.add(b);
									this.breaks += 1;
									found = true;
									
								} 
								
							}
							
							whiteSpaceFound = false;
							charsinline = 0;
						
						} else {
							// Hard break: no whitespace yet, or this segment is an ANSI/Unicode map
							// (Block Elements) — soft-wrapping at colored spaces shreds the grid.
							int pos = u.charcount - (u.charcount-amount);
							pos += 1;
							pos -= 1;
							breakAt(theIterator,u,pos,u.charcount);
							charsinline = 0;
							whiteSpaceFound = false;
						}
						
					//if the number of non whitespace characters is < breakAt, then we should go back and search for the whitespace
					//else, break in the middle.
					} else {
						//just break in the middle as we are not word wrapping
						//charsinline = breakAt(theIterator,u,amount,u.charcount);
						breakAt(theIterator,u,amount,u.charcount);
						charsinline = 0;
					}
				}
				
			}
			
			
			
			while(theIterator.hasPrevious()) {
				theIterator.previous();
			}
			
			//if we are here, then we should work backward through the list requesting sizes
			//this.bytes = 0;
			//while(i.hasPrevious()) {
			//	Unit tmp = i.previous();
			//	this.bytes += tmp.reportSize();
			//}
			
			//Log.e("TREE",this.bytes + ":"+deColorLine(this));
			
		}
		
		/**
		 * True when the current unbroken segment contains Unicode Block Elements.
		 * Soft-wrapping those lines at spaces destroys Eden-style ANSI maps.
		 */
		private boolean segmentLooksLikeAnsiMap() {
			int idx = theIterator.nextIndex();
			ListIterator<Unit> scan = mData.listIterator(idx);
			int checked = 0;
			while (scan.hasPrevious() && checked < 64) {
				Unit tmp = scan.previous();
				if (tmp instanceof Break || tmp instanceof NewLine) {
					break;
				}
				if (tmp instanceof Text) {
					String s = ((Text) tmp).getString();
					if (s != null) {
						for (int i = 0; i < s.length(); ) {
							int cp = s.codePointAt(i);
							// Block Elements, Braille, Symbols for Legacy Computing (sextants etc.)
							if ((cp >= 0x2580 && cp <= 0x259F)
									|| (cp >= 0x2800 && cp <= 0x28FF)
									|| (cp >= 0x1FB00 && cp <= 0x1FBFF)) {
								return true;
							}
							i += Character.charCount(cp);
						}
					}
				}
				checked++;
			}
			return false;
		}

		public ListIterator<Unit> getIterator() {
			return theIterator;
		}
		
		public void resetIterator() {
			while(theIterator.hasPrevious()) {
				theIterator.previous();
			}
		}
		
		public void stripBreaks() {
			//Iterator<Unit> stripper = mData.iterator();
			while(theIterator.hasPrevious()) {
				theIterator.previous();
			}
			while(theIterator.hasNext()) {
				Unit tmp = theIterator.next();
				if(tmp instanceof Break) {
					theIterator.remove();
				}
			}
		}

		public final Text newText(String str) {
			return new Text(str);
		}
		
		/*public final Color newColor(int c) { //constructs a new xterm256 color.
			//byte[] x = new byte[6];
			//x[0] = ESC;
			//x[1] = BRACKET;
			//x[2] = 38
		}*/
		
		public Color newColor(int color)
		{
			Color c = new Color();
			c.triggerPaint = true;
			c.operations.add(38);
			c.operations.add(5);
			c.operations.add(color);
			c.bytecount = 1 + 1 + 2 + 1 + 1 + 1 + (Integer.toString(color)).length() + 1;
			//           ESC  [  38   ;   5   ;   color data, can be up to 3           m
			String foo = null;
			try {
				foo = new String(new byte[]{ESC},encoding) + "[38;5;"+color+"m";
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				c.bin = foo.getBytes(encoding);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return c;
		}
		
		public Color newBackgroundColor(int color)
		{
			Color c = new Color();
			c.triggerPaint = true;
			c.operations.add(48);
			c.operations.add(5);
			c.operations.add(color);
			c.bytecount = 1 + 1 + 2 + 1 + 1 + 1 + (Integer.toString(color)).length() + 1;
			//           ESC  [  38   ;   5   ;   color data, can be up to 3           m
			String foo = null;
			try {
				foo = new String(new byte[]{ESC},encoding) + "[48;5;"+color+"m";
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				c.bin = foo.getBytes(encoding);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return c;
		}
		
		private int breakAt(ListIterator<Unit> i, Unit u, int amount, int length) {
			int charsinline;
			boolean removed;
			if(amount == 0) {
				i.add(new Break());
				//advance so we don't process this for the original break checking.
				if(i.hasNext()) {
					i.next(); //advance the cursor so we don't go through and delete existing breaks.
				}
				breaks += 1;
				charsinline = 0;
				removed = true;
			} else {
				int start = length - amount;
				int end = length - (length-amount);
				
				try {
				Text orig = (Text) u;
				String first = orig.data.substring(0, start);
				String second = orig.data.substring(start,start+end);
				Text firstPart = new Text(first);
				Text secondPart = new Text(second);
				if (orig.getHref() != null) {
					firstPart.setHref(orig.getHref());
					secondPart.setHref(orig.getHref());
					firstPart.setExpireGroup(orig.getExpireGroup());
					secondPart.setExpireGroup(orig.getExpireGroup());
				} else if (orig.isLink()) {
					firstPart.setLink(true);
					secondPart.setLink(true);
				}
				i.set(firstPart);
				i.add(new Break());
				i.add(secondPart);
				} catch (StringIndexOutOfBoundsException e) { 
					throw e;
				}
				
				//length = end;
				breaks += 1;
				
				
				removed = true;
				
				charsinline = end;
			}
			
			if(removed) {
				i.previous(); //queue the next pass to start with the unbroken end
				//Iterator<Unit> ja = this.mData.iterator();
				//StringBuilder b = new StringBuilder();
				//while(ja.hasNext()) {
				//	Unit tz = ja.next();
				//	if(tz instanceof Text) {
				//		b.append(((Text)tz).getString());
				//	}
				//	if(tz instanceof Break) {
				//		b.append("|");
				//	}
				//}
				//Log.e("TREE","BROKE LINE: " + b.toString());
				//break;
			}
			return charsinline;
		}

		/**
		 * The colour the server was in when this line started, and when it
		 * ended -- {@code null} on a line that is still open. Snapshots, taken
		 * while the bytes are parsed and therefore before any trigger has run,
		 * so they hold what the server said and never what a trigger painted.
		 *
		 * <p>{@code bleedColor} answers the same question for the stream as a
		 * whole, but only at the point parsing has reached: asking it while
		 * colouring the first line of a chunk gives the colour of its last one.
		 */
		private LinkedList<Integer> serverColorAtStart;
		private LinkedList<Integer> serverColorAtEnd;
		/** A colour trigger's colour is still running at the end of this line. */
		private boolean triggerColorOpen;
		/**
		 * Colour to go back to when that open trigger colour is closed. Copied
		 * onto {@code TriggerColorState} before this line is drained: the next
		 * dispatch's continuation has inherited the trigger colour as bleed, so
		 * {@link #serverColorAtEnd} is the wrong answer there.
		 */
		private LinkedList<Integer> triggerColorRestore;

		public LinkedList<Integer> getServerColorAtStart() {
			return serverColorAtStart;
		}

		public LinkedList<Integer> getServerColorAtEnd() {
			return serverColorAtEnd;
		}

		public boolean isTriggerColorOpen() {
			return triggerColorOpen;
		}

		public void setTriggerColorOpen(boolean open) {
			this.triggerColorOpen = open;
		}

		public void setTriggerColorRestore(final List<Integer> ops) {
			if (ops == null) {
				this.triggerColorRestore = null;
			} else {
				this.triggerColorRestore = new LinkedList<Integer>(ops);
			}
		}

		public LinkedList<Integer> getTriggerColorRestore() {
			return triggerColorRestore;
		}

		public LinkedList<Unit> getData() {
			return mData;
		}

		public void setData(LinkedList<Unit> l) {
			mData = l;
			//need to parse this to make sure we report the correct data.
			charcount = 0;
			//totalchars = 0;
			breaks = 0;
			for(Unit u : mData) {
				if(u instanceof Text) {
					charcount += u.charcount;
					///totalchars += u.charcount;
				}
				if(u instanceof Color) {
					//totalchars += u.charcount;
				}
			}
			
			theIterator = null;
			theIterator = mData.listIterator(0);
		}
		
		
		
	}
	
	public enum UNIT_TYPE {
		BLAND,
		TEXT,
		WHITESPACE,
		TAB,
		NEWLINE,
		COLOR,
		BREAK,
	}
	
	public class Unit {
		protected int charcount;
		protected int bytecount;
		//protected int bytecount;
		
		public Unit() { charcount = 0; bytecount=0; }
		//	charcount = 0;
		//}
		public UNIT_TYPE type = UNIT_TYPE.BLAND;
		
		//public Unit copy() { return null;}
		public int reportSize() { return 0; } //raw units have no size.
		
	}
	
	
	public class Text extends Unit {
		protected String data;
		protected byte[] bin;
		private boolean link = false;
		/** OSC 8 target, or null. Distinct from regex-linkify (display may not be the URL). */
		private String href;
		/** MXP EXPIRE group, or null. */
		private String expireGroup;
		
		//public Text copy() {
		//	return null;
		//}
		public Text() {
			data = "";
			charcount = 0;
			bytecount = 0;
			bin = new byte[0];
			this.type = UNIT_TYPE.TEXT;
		}
		
		public Text(String input) {
			
			if(linkify && (input.length() > 4)) {
				urlMatcher.reset(input);
				if(urlMatcher.find()) {
					this.link = true;
				}
			}
			
			data = input;
			// Columns = Unicode code points (█ is one cell), not UTF-16 units / bytes.
			this.charcount = data.codePointCount(0, data.length());
			try {
				bin = data.getBytes(encoding);
				this.bytecount = bin.length;
			} catch (UnsupportedEncodingException e) {
				
				e.printStackTrace();
			}
			
			this.type = UNIT_TYPE.TEXT;
		}
		
		public Text(byte[] in) throws UnsupportedEncodingException {
			bin = in;
			data = new String(in,encoding);
			if(linkify && in.length > 4) {
				urlMatcher.reset(data);
				if(urlMatcher.find()) {
					this.link = true;
				}
			}
			this.charcount = data.codePointCount(0, data.length());
			bytecount = bin.length;
			this.type = UNIT_TYPE.TEXT;
		}

		public String getString() {
			return data;
		}
		
		public byte[] getBytes() {
			return bin;
		}
		
		public int reportSize() {
		
			return bin.length;
			
		}

		public void setLink(boolean link) {
			this.link = link;
		}

		public boolean isLink() {
			return link;
		}

		public void setHref(final String href) {
			this.href = href;
		}

		public String getHref() {
			return href;
		}

		public void setExpireGroup(final String expireGroup) {
			this.expireGroup = expireGroup;
		}

		public String getExpireGroup() {
			return expireGroup;
		}
		
		//public Text copy() {
			
			
		//}
		
		
	}
	
	
	
	private class Tab extends Unit {
		//protected String data;
		
		public Tab() {
			//data = new String(new byte[]{0x09});
			this.charcount = 1;
			this.bytecount = 1;
			this.type = UNIT_TYPE.TAB;
		}
		
		public int reportSize() {
			return 1;
		}
		
	}
	public class NewLine extends Unit {
		protected String data;
		
		public NewLine() {
			data = new String("\n");
			this.charcount = 1;
			this.bytecount = 1;
			this.type = UNIT_TYPE.NEWLINE;
		}
		
		public int reportSize() {
			return 1;
		}
	}
	
	/*public class Counter extends Unit {
		public int count = 0;
		public Counter() {
			
		}
		
		public int reportSize() {
			return 0;
		}
	}*/
	
	
	public class Color extends Unit {
		protected byte[] bin;
		//protected String data;
		ArrayList<Integer> operations;
		//ListIterator<Integer> it;

		/**
		 * Draw-path memo of the ANSI register machine. A fling re-visits the same
		 * Color units sixty times a second; without this each visit re-parses the
		 * ops and calls Paint.setColor. Keyed on the register fingerprint
		 * <em>before</em> the unit — the result depends on prior state, so a
		 * cache that ignored it would paint the wrong colour after a scroll that
		 * changed the bleed.
		 */
		boolean drawCacheValid;
		int drawCacheBeforeFp;
		int drawCacheFg;
		int drawCacheBg;
		Integer drawCacheSelectedColor;
		Integer drawCacheSelectedBackground;
		Integer drawCacheSelectedBright;
		boolean drawCacheXterm256FG;
		boolean drawCacheXterm256BG;
		boolean drawCacheTrueColorFG;
		boolean drawCacheTrueColorBG;
		/**
		 * Inserted by a colour trigger. Replace uses this to tell a MUD
		 * background from a trigger background. A later colour trigger on the
		 * same line treats these units as the colour still in effect, so a
		 * word trigger restores to a channel colour instead of the raw MUD
		 * colour underneath.
		 */
		boolean triggerPaint;
		
		public Color() {
			//data = "[0m";
			//this.charcount = data.length();
			operations = new ArrayList<Integer>();
			//operations.add(new Integer(0));
			this.type = UNIT_TYPE.COLOR;
		}
		
		public void setOperations(ArrayList<Integer> ops) {
			this.operations = ops;
			drawCacheValid = false;
		}
		//public Color(String input) {
			//data = input;
			//this.charcount = data.length();
		//	computeOperations(input);
			//try {
				//bytecount = data.getBytes(encoding).length;
			//} catch (UnsupportedEncodingException e) {
			//	
		//		e.printStackTrace();
		//	}
		//}
		
		/*public Color(String input,LinkedList<Integer> ops) {
			data = input;
			this.charcount = data.length();
			operations = new ArrayList<Integer>(ops); //will need to track this for actual memory usage.
			try {
				bytecount = data.getBytes(encoding).length;
			} catch (UnsupportedEncodingException e) {
		
				e.printStackTrace();
			}
		}*/
		
		public Color(byte[] input) {
			bin = input;
			bytecount = input.length;
			operations = new ArrayList<Integer>(getOperationsFromBytes(input));
			//it = operations.listIterator();
			/*try {
				data = new String(bin,encoding);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}*/
			this.type = UNIT_TYPE.COLOR;
			
		}
		
		//public String getData() {
		//	return data;
		//}
		
		public void computeOperations(String input) {
			//
		}

		public ArrayList<Integer> getOperations() {
			return operations;
		}

		public boolean isTriggerPaint() {
			return triggerPaint;
		}
		
		public boolean equals(Object o) {
			if(o == this) return true;
			if(!(o instanceof Color)) return false;
			Color c = (Color)o;
			if(c.bin.length != this.bin.length) {
				return false;
			}
			for(int i=0;i<this.bin.length;i++) {
				if(c.bin[i] != this.bin[i]) {
					return false;
				}
			}
			
			return true;
		}
		
		public int reportSize() {
			return bin.length;
		}
	}
	
	public class Break extends Unit {
		public int viswidth = 0;
		public Break() {
			this.type = UNIT_TYPE.BREAK;
		}
		public int reportSize() {
			return 0;
		}
		
	}
	
	public class WhiteSpace extends Text {
		//whitespace is esentially text.
		public WhiteSpace() {
			super();
			this.type = UNIT_TYPE.WHITESPACE;
		}
		
		public WhiteSpace(String pIn) {
			super(pIn);
			this.type = UNIT_TYPE.WHITESPACE;
		}
		
		public WhiteSpace(byte[] pIn) throws UnsupportedEncodingException {
			super(pIn);
			this.type = UNIT_TYPE.WHITESPACE;
		}
		
		public String getString() {
			return data;
		}
		
		public byte[] getBytes() {
			return bin;
		}
		
		public int reportSize() {
			return super.reportSize();
		}
	}
	
	public String getLastTwenty(boolean showcolor) {
		StringBuffer buf = new StringBuffer();
		Iterator<Line> i = mLines.iterator();
		int j = 0;
		while(j < 20) {
			if(i.hasNext()) {
				buf.insert(0,j + ":" + deColorLine((Line)i.next()));
				//buf.insert(0,"\n");
				//
			}
			j++;
		}
		return buf.toString();
	}
	
	private static StringBuffer stripColor = new StringBuffer();
	public static StringBuffer deColorLine(Line line) {
		stripColor.setLength(0);
		for(Unit u : line.getData()) {
			if(u instanceof Text) {
				stripColor.append(((Text)u).data);
			}
			
			//if(u instanceof NewLine) {
				//stripColor.append("\n");
			//}
			
		}
		
		return stripColor;
	}

	public void setMaxLines(int maxLines) {
		MAX_LINES = clampMaxLines(maxLines);
		prune();
	}

	public int getMaxLines() {
		return MAX_LINES;
	}

	/** Plain-text dump of scrollback (newest last), for search/export. */
	public String dumpPlainText() {
		StringBuilder out = new StringBuilder();
		// mLines is newest-first in this tree; reverse for chronological dump.
		java.util.ListIterator<Line> it = mLines.listIterator(mLines.size());
		while (it.hasPrevious()) {
			out.append(deColorLine(it.previous()));
			out.append('\n');
		}
		return out.toString();
	}

	public void setLineBreakAt(Integer i) {
		// Same wrap width → updateTree is a pure no-op on content (Line.updateData
		// recomputes breaks from raw text + breakAt + wordWrap). Skip the O(lines)
		// walk. Without this, a height-only resize (extra-text / frame top-drawer
		// drag) rebuilt the whole buffer on every ACTION_MOVE: Window.onSizeChanged
		// → calculateCharacterFeatures → setLineBreaks(0), and rows come from
		// width, not height. Same guard pattern as setWordWrap.
		if (i != null && i.intValue() == breakAt) {
			return;
		}
		breakAt = i;
		updateTree();
	}
	
	private void updateTree() {
		brokenLineCount = 0;
		totalbytes = 0;
		for(Line l : mLines) {
			l.updateData();
			totalbytes += l.bytes;
			brokenLineCount += (1 + l.breaks);
		}
	}

	public void setWordWrap(boolean wordWrap) {
		boolean doupdate = false;
		if(wordWrap != this.wordWrap) doupdate = true;
		this.wordWrap = wordWrap;
		if(doupdate) {
			updateTree();
		}
	}

	public boolean isWordWrap() {
		return wordWrap;
	}

	public void setCullExtraneous(boolean cullExtraneous) {
		this.cullExtraneous = cullExtraneous;
	}

	public boolean isCullExtraneous() {
		return cullExtraneous;
	}

	public void setLinkify(boolean linkify) {
		this.linkify = linkify;
	}

	public boolean isLinkify() {
		return linkify;
	}

	public void setOsc8Links(final boolean enabled) {
		this.osc8Enabled = enabled;
		if (!enabled) {
			if (osc8Href != null && !com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isMxpHref(osc8Href)) {
				osc8Href = null;
				osc8Expire = null;
			}
		}
	}

	public boolean isOsc8Links() {
		return osc8Enabled;
	}

	private Text stampOsc8(final Text t) {
		if (t != null && osc8Href != null) {
			boolean mxp = com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isMxpHref(osc8Href);
			if (osc8Enabled || mxp) {
				t.setHref(osc8Href);
				t.setExpireGroup(osc8Expire);
			}
		}
		return t;
	}

	/**
	 * Deactivate MXP SEND/A links tagged with this expire group. Text stays;
	 * taps do nothing. Walks this tree only — the UI copy sees the same OSC
	 * command in the byte stream.
	 */
	public void expireMxpLinks(final String group) {
		boolean all = group == null || group.length() == 0;
		ListIterator<Line> li = mLines.listIterator(mLines.size());
		while (li.hasPrevious()) {
			Line line = li.previous();
			for (Unit u : line.getData()) {
				if (u instanceof Text) {
					Text t = (Text) u;
					if (all) {
						if (t.getExpireGroup() != null) {
							t.setHref(null);
							t.setExpireGroup(null);
						}
					} else if (group.equals(t.getExpireGroup())) {
						t.setHref(null);
						t.setExpireGroup(null);
					}
				}
			}
		}
	}

	public void setBleedColor(Color c) {
		bleedColor = new LinkedList<Integer>(c.getOperations());
	}

	public Color getBleedColor() {
		return makeColor(bleedColor);
	}

	/**
	 * The colour to go back to after a trigger's colour, given what the server
	 * had said. A colour trigger paints a background as well as a foreground, so
	 * a restore that only names a foreground leaves the trigger's background
	 * running. When the server's own colour says nothing about the background,
	 * 49 (default background) is added to say it.
	 */
	public Color makeRestoreColor(List<Integer> operations) {
		boolean background = false;
		for(int i=0;i<operations.size();i++) {
			int op = operations.get(i).intValue();
			// 38/48 introduce an xterm-256 (…;5;n) or truecolor (…;2;r;g;b)
			// colour: the numbers that follow are its value, not codes of
			// their own, and skipping them is what keeps a foreground like
			// 38;5;45 from reading as background 45.
			if(op == XTERM_FG_INTRO || op == XTERM_BG_INTRO) {
				if(op == XTERM_BG_INTRO) {
					background = true;
				}
				if(i+1 < operations.size() && operations.get(i+1).intValue() == XTERM_256_MODE) {
					i += 2;
				} else if(i+1 < operations.size() && operations.get(i+1).intValue() == TRUECOLOR_MODE) {
					i += 4;
				}
			} else if(op == ZERO_CODE || (op >= BACKGROUND_FIRST && op <= BACKGROUND_DEFAULT)) {
				// A reset returns the background to default as surely as 49 does.
				background = true;
			}
		}
		if(background) {
			return makeColor(operations);
		}
		ArrayList<Integer> withDefault = new ArrayList<Integer>(operations);
		withDefault.add(Integer.valueOf(BACKGROUND_DEFAULT));
		return makeColor(withDefault);
	}

	private static final int XTERM_FG_INTRO = 38;
	private static final int XTERM_BG_INTRO = 48;
	private static final int XTERM_256_MODE = 5;
	private static final int TRUECOLOR_MODE = 2;
	private static final int ZERO_CODE = 0;
	private static final int BACKGROUND_FIRST = 40;
	private static final int BACKGROUND_DEFAULT = 49;

	/**
	 * A colour unit for an operation list, with the escape sequence that writes
	 * it built as well -- {@code dumpToBytes} sends {@code bin}, so a unit
	 * without one is a colour that exists in the buffer and never in the stream.
	 */
	public Color makeColor(List<Integer> operations) {
		Color c = new Color();
		c.setOperations(new ArrayList<Integer>(operations));
		StringBuffer b = new StringBuffer();
		try {
			b.append(new String(new byte[]{ESC},encoding));
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		b.append("[");
		Iterator<Integer> it = c.getOperations().iterator();
		while(it.hasNext()) {
			Integer i = it.next();
			b.append(i);
			if(it.hasNext()) {
				b.append(";");
			} else {
				b.append("m");
			}
			
			
		}
		try {
			c.bin = b.toString().getBytes(encoding);
			c.bytecount = c.bin.length;
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		b.setLength(0);
		b = null;
		//c.bin = (ESC + "[" )
		
		return c;
	}

	public void appendLine(Line line) {
		addLine(line);
		
	}

	public Selection getSelectionForPoint(int line, int column) {
		//this is neat.
		//get an iterator.
		Line data = null;
		ListIterator<Line> lineIterator = mLines.listIterator();
		boolean done = false;
		int working = 0;
		int subline = 0;
		//if(line == 0) {
		while(lineIterator.hasNext() && !done) {
			
			Line l = lineIterator.next();
			
			
			if(l.breaks > 0) {
				subline = l.breaks;
				
				for(int i=0;i<l.breaks;i++) {
					if(i != 0) {subline = subline - 1;};
					if(working == line) {
						data = l;
						done = true;
						i=l.breaks;
					}
					working += 1;
					
				}
				
				if(!done) {
					subline = 0;
					if(working == line){
						data = l;
						done = true;
					}
					working += 1;
				}
				
				
			} else {
				
				if(working == line) {
					data = l;
					done = true;
				}
				working += 1;
			}
		}
		
		if(!done) {
			//no text there.
			return null;
		}
		
		
		if(data.bytes == 0) {
			return null;
		}
		//i.getIterator().r
		ListIterator<Unit> i = data.getData().listIterator();
		
		//advance to the subline.
		done = false;
		working = 0;
		if(subline > 0) {
			while(i.hasNext() && !done) {
				Unit u = i.next();
				if(u instanceof Break) {
					working += 1;
					if(working == subline) {
						done = true;
					}
				}
			}
		}
		
		done = false;
		working = 0;
		while(i.hasNext() && !done) {
			Unit u = i.next();
			if(u instanceof WhiteSpace) {
				working += u.bytecount;
				if(working >= column) {
					//get the text on either side.
					boolean subdone = false;
					int startline,startcol,endline,endcol;
					Unit prevUnit = null;
					int unitsback = 1;
					i.previous();
					startline = line;
					startcol = working - u.bytecount;
					endline = line;
					//StringBuilder output = new StringBuilder();
					while(!subdone && i.hasPrevious()) {
						Unit subu = i.previous();
						if(subu instanceof Text) {
							prevUnit = subu;
							subdone = true;
							//output.append(((Text)subu).getString());
							startcol = startcol - ((Text)subu).bytecount;
						}
						unitsback += 1;
					}
					endcol = startcol;
					for(int j=0;j<unitsback;j++) {
						Unit tmp = i.next();
						if(tmp instanceof Text) {
							//output.append(((Text)tmp).getString());
							endcol += ((Text)u).bytecount;
						}
					}					
					subdone = false;
					while(!subdone && i.hasNext()) {
						Unit tmp = i.next();
						if(tmp instanceof Text) {
							//output.append(((Text)tmp).getString());
							endcol += ((Text)u).bytecount;
							SelectionCursor start = new SelectionCursor(startline,startcol);
							SelectionCursor end = new SelectionCursor(endline, endcol);
							Selection selection = new Selection(start, end);
							return selection;
						}
					}
					
				}
			} else if(u instanceof Text) {
				working += u.bytecount;
				if(working >= column) {
					int startline,startcol,endline,endcol;
					startline = line;
					endline = line;
					startcol = working - u.bytecount;
					//find the next whitespace.
					int units_back = 1;
					i.previous();
					boolean subdone = false;
					while(!subdone && i.hasPrevious()) {
						Unit tmp = i.previous();
						if(tmp instanceof WhiteSpace) {
							subdone = true;
						} else if(tmp instanceof Text) {
							startcol = startcol - ((Text)tmp).bytecount;
						}
						units_back += 1;
					}
					endcol = startcol-1;
					i.next();
					for(int j=0;j<units_back-1;j++) {
						Unit tmp = i.next();
						if(tmp instanceof Text) {
							endcol += ((Text)tmp).bytecount;
						}
					}
					subdone = false;
					while(!subdone && i.hasNext()) {
						Unit tmp = i.next();
						if(tmp instanceof WhiteSpace) {
							subdone = true;
						} else if(tmp instanceof Text) {
							endcol += ((Text)tmp).bytecount;
						}
					}
					
					SelectionCursor start = new SelectionCursor(startline,startcol);
					SelectionCursor end = new SelectionCursor(endline, endcol);
					Selection selection = new Selection(start, end);
					return selection;
					//return ((Text)u).getString();
				}
			}
		}
		
		return null;
	}
	
	public String getTextSection(Selection selection) {
		
		int startline,startcol,endline,endcol;
		
		if(selection.end.line > selection.start.line) {
			
			startline = selection.end.line;
			startcol = selection.end.column;
			endline = selection.start.line;
			endcol = selection.start.column;
		} else {
			startline = selection.start.line;
			startcol = selection.start.column;
			endline = selection.end.line;
			endcol = selection.end.column;
		}
		
		StringBuilder builder = new StringBuilder();
		
		boolean startfound = false;
		ListIterator<Line> lineIterator = mLines.listIterator();
		Line firstdata = null;
		//boolean done = false;
		int working = 0;
		int subline = 0;
		int workingLine = 0;
		//if(line == 0) {
		while(lineIterator.hasNext() && !startfound) {
			
			Line l = lineIterator.next();
			
			
			if(l.breaks > 0) {
				subline = l.breaks;
				
				for(int i=0;i<l.breaks;i++) {
					if(i != 0) {subline = subline - 1;};
					if(working == startline) {
						firstdata = l;
						startfound = true;
						i=l.breaks;
					}
					working += 1;
					
				}
				
				if(!startfound) {
					subline = 0;
					if(working == startline){
						firstdata = l;
						startfound = true;
					}
					working += 1;
				}
				
				
			} else {
				
				if(working == startline) {
					firstdata = l;
					startfound = true;
				}
				working += 1;
			}
		}
		workingLine = startline;
		if(!startfound) {
			//no text there.
			return null;
		}
		
		lineIterator.previous();
		
		boolean done = false;
		ListIterator<Unit> li = firstdata.getData().listIterator();
		working = 0;
		if(subline > 0) {
			while(li.hasNext() && !done) {
				Unit u = li.next();
				if(u instanceof Break) {
					working += 1;
					if(working == subline) {
						done = true;
					}
				}
			}
			

		}
		
		done = false;
		working = 0;
		while(!done && li.hasNext()) {
			Unit u = li.next();
			if(u instanceof Text) {
				working += ((Text)u).bytecount;
				if(working >= startcol) {
					
					builder.append(((Text)u).getString().substring(((Text)u).bytecount-(working-startcol), ((Text)u).bytecount));
					
					done = true;
					
				}
			}
		}
		
		if(startline == endline || startline - endline < (firstdata.breaks+1)) {
			done = false;
			while(li.hasNext() && !done) {
				Unit u = li.next();
				done = false;
				if(u instanceof Text) {
					working += ((Text)u).bytecount;
					if(workingLine == endline && working > endcol) {
						if(((Text)u).bytecount == 1) {
							builder.append(((Text)u).getString());
						} else {
							builder.append(((Text)u).getString().substring(0,endcol-(working-((Text)u).bytecount-1)));
						}
						done = true;
					} else {
						builder.append(((Text)u).getString());
					}
				}
				if(u instanceof Break) {
					working = 0;
					workingLine -= 1;
				}
				
			}
			return builder.toString();
		} else {
			done = false;
			while(li.hasNext()) {
				Unit u = li.next();
				if(u instanceof Text) {
					builder.append(((Text)u).getString());
				} else if(u instanceof Break) {
					working = 0;
					workingLine -=1;
				}
				
			}
			builder.append("\n");
			done = false;
			while(!done && lineIterator.hasPrevious()) {
				Line tmp = lineIterator.previous();
				workingLine -= 1 + tmp.breaks;
				if(workingLine <= endline) {
					workingLine = workingLine + tmp.getBreaks();
					Iterator<Unit> slow = tmp.getData().iterator();
					boolean enddone = false;
					working = 0;
					while(!enddone && slow.hasNext()) {
						Unit u = slow.next();
						if(u instanceof Text) {
							working += ((Text)u).bytecount;
							if(workingLine == endline && working > endcol) {
								if(((Text)u).bytecount == 1) {
									builder.append((((Text)u).getString()));
								} else{
									builder.append(((Text)u).getString().substring(0,endcol-(working-((Text)u).bytecount-1)));
								}
								enddone = true;
								done = true;
							} else {
								builder.append(((Text)u).getString());
							}
						} if(u instanceof Break) {
							working = 0;
							workingLine -= 1;
						}
					}
					done = true;
				} else {
					Iterator<Unit> fast = tmp.getData().iterator();
					while(fast.hasNext()) {
						Unit u = fast.next();
						if(u instanceof Text) {
							builder.append(((Text)u).getString());
						}
					}
					builder.append("\n");
				}
			}
			return builder.toString();
		}
		
		
		
		//return null;
	}
	
	public int getModCount() {
		return modCount;
	}

	public void setModCount(int modCount) {
		this.modCount = modCount;
	}

	public class SelectionCursor {
		public int line,column;
		
		public SelectionCursor(int line,int column) {
			this.line = line;
			this.column = column;
		}
	}
	
	public class Selection {
		SelectionCursor start,end;
		
		public Selection(SelectionCursor start,SelectionCursor end) {
			this.start = start;
			this.end = end;
		}
		
	}
	
	/*public Line makeLine(String str) {
		Line l = new Line();
		LinkedList<Unit> tmp = new LinkedList<Unit>();
		Text t = l.newText(str);
		tmp.add(t);
		l.setData(tmp);
		return l;
	}*/
}
