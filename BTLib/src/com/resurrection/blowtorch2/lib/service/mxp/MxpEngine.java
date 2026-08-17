package com.resurrection.blowtorch2.lib.service.mxp;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Streaming MXP 1.0 filter. Telnet is already stripped. Tags become SGR and
 * OSC 8 ({@link MxpLinks}); CSI {@code z} sets mode and is not forwarded.
 *
 * <p>Parsing starts after {@link #setActive(boolean) setActive(true)} or the
 * first {@code ESC [ n z}. Disabled engines copy input unchanged.
 */
public final class MxpEngine {

	public static final String MXP_SPEC_VERSION = "1.0";
	public static final String DEFAULT_CLIENT = "BlowTorch";

	private static final byte ESC = 0x1B;
	private static final byte BEL = 0x07;
	private static final int MAX_TAG = 4096;
	private static final int MAX_ENTITY = 80;
	private static final int MAX_CSI = 96;
	private static final int MAX_CUSTOM_DEPTH = 8;

	public enum Mode {
		OPEN, SECURE, LOCKED
	}

	public interface Listener {
		void sendToMud(String text);

		void expire(String group);

		void setVariable(String name, String value);

		void destOutput(String window, byte[] data);

		void playSound(String fname, int v, int l, int p, String type, String url);

		void onFlag(String flag, String text);
	}

	public static class CollectingListener implements Listener {
		public final ArrayList<String> mudReplies = new ArrayList<String>();
		public final ArrayList<String> expires = new ArrayList<String>();
		public final HashMap<String, String> variables = new HashMap<String, String>();
		public final ArrayList<String> flags = new ArrayList<String>();
		public final HashMap<String, ByteArrayOutputStream> dests =
				new HashMap<String, ByteArrayOutputStream>();

		@Override
		public void sendToMud(final String text) {
			mudReplies.add(text);
		}

		@Override
		public void expire(final String group) {
			expires.add(group);
		}

		@Override
		public void setVariable(final String name, final String value) {
			variables.put(name, value);
		}

		@Override
		public void destOutput(final String window, final byte[] data) {
			if (window == null || data == null) {
				return;
			}
			ByteArrayOutputStream buf = dests.get(window);
			if (buf == null) {
				buf = new ByteArrayOutputStream();
				dests.put(window, buf);
			}
			buf.write(data, 0, data.length);
		}

		@Override
		public void playSound(final String fname, final int v, final int l, final int p,
				final String type, final String url) {
		}

		@Override
		public void onFlag(final String flag, final String text) {
			flags.add(flag + "=" + text);
		}
	}

	private Listener listener = new CollectingListener();
	private boolean enabled = true;
	private boolean active;
	private Mode mode = Mode.OPEN;
	private Mode defaultMode = Mode.OPEN;
	private boolean tempSecure;
	private boolean eatNextNewline;
	private boolean inParagraph;
	private byte[] holdover;
	private String encoding = "UTF-8";
	private String clientName = DEFAULT_CLIENT;
	private String clientVersion = "2.3.0";
	private final MxpEntities entities = new MxpEntities();
	private final HashMap<String, ElementDef> elements = new HashMap<String, ElementDef>();
	private final HashMap<Integer, LineTag> lineTags = new HashMap<Integer, LineTag>();
	private final ArrayList<OpenElem> stack = new ArrayList<OpenElem>();
	private ByteArrayOutputStream out = new ByteArrayOutputStream();
	private ByteArrayOutputStream destBuf;
	private String destName;
	private int collectKind; // 0 none, 1 tag, 2 entity, 3 csi, 4 comment, 5 osc
	private final StringBuilder collect = new StringBuilder();
	private boolean csiStarted;
	private char tagQuote;
	private int customDepth;

	public void setListener(final Listener listener) {
		this.listener = listener == null ? new CollectingListener() : listener;
	}

	public Listener getListener() {
		return listener;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
		if (!enabled) {
			active = false;
			holdover = null;
			collectKind = 0;
			collect.setLength(0);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setActive(final boolean active) {
		this.active = active;
		if (active) {
			mode = defaultMode;
		}
	}

	public boolean isActive() {
		return active;
	}

	public Mode getMode() {
		return mode;
	}

	public void setEncoding(final String encoding) {
		if (encoding != null && encoding.length() > 0) {
			this.encoding = encoding;
		}
	}

	public void setClient(final String name, final String version) {
		if (name != null && name.length() > 0) {
			clientName = name;
		}
		if (version != null && version.length() > 0) {
			clientVersion = version;
		}
	}

	public void reset() {
		active = false;
		mode = Mode.OPEN;
		defaultMode = Mode.OPEN;
		tempSecure = false;
		eatNextNewline = false;
		inParagraph = false;
		holdover = null;
		collectKind = 0;
		collect.setLength(0);
		stack.clear();
		elements.clear();
		entities.reset();
		lineTags.clear();
		destBuf = null;
		destName = null;
		customDepth = 0;
		out = new ByteArrayOutputStream();
	}

	public byte[] process(final byte[] data) {
		if (data == null) {
			return null;
		}
		byte[] input = data;
		if (holdover != null) {
			byte[] combined = new byte[holdover.length + data.length];
			System.arraycopy(holdover, 0, combined, 0, holdover.length);
			System.arraycopy(data, 0, combined, holdover.length, data.length);
			holdover = null;
			input = combined;
		}
		if (!enabled) {
			return input;
		}
		out = new ByteArrayOutputStream(input.length + 16);
		int i = 0;
		while (i < input.length) {
			byte b = input[i];
			if (collectKind == 5) {
				emit(b);
				if (b == BEL) {
					collectKind = 0;
				} else if (b == ESC && i + 1 < input.length && (input[i + 1] & 0xFF) == '\\') {
					emit(input[i + 1]);
					i++;
					collectKind = 0;
				}
				i++;
				continue;
			}
			if (collectKind == 4) {
				collect.append((char) (b & 0xFF));
				if (b == '\n' || b == ESC) {
					collectKind = 0;
					collect.setLength(0);
					if (b == '\n') {
						handleNewline();
					} else {
						i = handleEsc(input, i);
						continue;
					}
					i++;
					continue;
				}
				if (collect.length() >= 3
						&& collect.substring(collect.length() - 3).equals("-->")) {
					collectKind = 0;
					collect.setLength(0);
				} else if (collect.length() > MAX_TAG) {
					collectKind = 0;
					collect.setLength(0);
				}
				i++;
				continue;
			}
			if (collectKind == 1) {
				char ch = (char) (b & 0xFF);
				if (b == '\n' || b == ESC) {
					emit((byte) '<');
					emitAscii(collect.toString());
					collectKind = 0;
					collect.setLength(0);
					tagQuote = 0;
					if (b == '\n') {
						handleNewline();
						i++;
						continue;
					}
					i = handleEsc(input, i);
					continue;
				}
				if (tagQuote != 0) {
					collect.append(ch);
					if (ch == tagQuote) {
						tagQuote = 0;
					}
					i++;
					continue;
				}
				if (ch == '\'' || ch == '"') {
					tagQuote = ch;
					collect.append(ch);
					i++;
					continue;
				}
				if (b == '>') {
					String inner = collect.toString();
					collectKind = 0;
					collect.setLength(0);
					tagQuote = 0;
					handleTagString(inner);
					i++;
					continue;
				}
				if (collect.length() >= MAX_TAG) {
					emit((byte) '<');
					emitAscii(collect.toString());
					collectKind = 0;
					collect.setLength(0);
					tagQuote = 0;
					i++;
					continue;
				}
				collect.append(ch);
				i++;
				continue;
			}
			if (collectKind == 2) {
				if (b == '\n' || b == ESC) {
					emitAscii("&");
					emitAscii(collect.toString());
					collectKind = 0;
					collect.setLength(0);
					if (b == '\n') {
						handleNewline();
						i++;
						continue;
					}
					i = handleEsc(input, i);
					continue;
				}
				if (b == ';') {
					String name = collect.toString();
					collectKind = 0;
					collect.setLength(0);
					expandEntity(name);
					i++;
					continue;
				}
				if (collect.length() >= MAX_ENTITY || !entityChar(collect.length(), b)) {
					emitAscii("&");
					emitAscii(collect.toString());
					emit(b);
					collectKind = 0;
					collect.setLength(0);
					i++;
					continue;
				}
				collect.append((char) (b & 0xFF));
				i++;
				continue;
			}
			if (collectKind == 3) {
				if (!csiStarted) {
					if (b == '[') {
						csiStarted = true;
						collect.append((char) '[');
						i++;
						continue;
					}
					if (b == ']') {
						emit(ESC);
						emit(b);
						collectKind = 5;
						collect.setLength(0);
						i++;
						continue;
					}
					emit(ESC);
					emit(b);
					collectKind = 0;
					collect.setLength(0);
					i++;
					continue;
				}
				int ub = b & 0xFF;
				if (ub >= 0x40 && ub <= 0x7E) {
					String params = collect.length() > 1 ? collect.substring(1) : "";
					collectKind = 0;
					collect.setLength(0);
					csiStarted = false;
					if (ub == 'z') {
						handleModeParams(params);
						i++;
						continue;
					}
					emit(ESC);
					emitAscii("[");
					emitAscii(params);
					emit(b);
					i++;
					continue;
				}
				if (collect.length() >= MAX_CSI || b == '\n') {
					emit(ESC);
					emitAscii(collect.toString());
					collectKind = 0;
					collect.setLength(0);
					csiStarted = false;
					continue;
				}
				collect.append((char) (b & 0xFF));
				i++;
				continue;
			}
			if (b == ESC) {
				if (i + 1 >= input.length) {
					holdover = new byte[] { ESC };
					flushDest();
					return out.toByteArray();
				}
				i = handleEsc(input, i);
				continue;
			}
			if (b == '\n') {
				handleNewline();
				i++;
				continue;
			}
			if (active && mode != Mode.LOCKED && b == (byte) '<') {
				if (i + 3 < input.length && input[i + 1] == '!' && input[i + 2] == '-'
						&& input[i + 3] == '-') {
					if (!secureOk()) {
						emit(b);
						i++;
						continue;
					}
					collectKind = 4;
					collect.setLength(0);
					collect.append("!--");
					i += 4;
					continue;
				}
				collectKind = 1;
				collect.setLength(0);
				tagQuote = 0;
				i++;
				continue;
			}
			if (active && mode != Mode.LOCKED && b == (byte) '&') {
				collectKind = 2;
				collect.setLength(0);
				i++;
				continue;
			}
			emit(b);
			i++;
		}
		if (collectKind != 0) {
			byte[] prefix;
			if (collectKind == 1) {
				prefix = joinHold((byte) '<', collect);
			} else if (collectKind == 2) {
				prefix = joinHold((byte) '&', collect);
			} else if (collectKind == 3) {
				prefix = joinHold(ESC, collect);
			} else if (collectKind == 4) {
				prefix = joinHold((byte) '<', collect);
			} else {
				prefix = new byte[] { ESC, ']' };
			}
			holdover = prefix;
			collectKind = 0;
			collect.setLength(0);
			csiStarted = false;
		}
		flushDest();
		return out.toByteArray();
	}

	private int handleEsc(final byte[] input, final int i) {
		if (i + 1 >= input.length) {
			holdover = new byte[] { ESC };
			return input.length;
		}
		byte next = input[i + 1];
		if (next == '[') {
			collectKind = 3;
			csiStarted = true;
			collect.setLength(0);
			collect.append('[');
			return i + 2;
		}
		if (next == ']') {
			emit(ESC);
			emit(next);
			collectKind = 5;
			return i + 2;
		}
		emit(ESC);
		emit(next);
		return i + 2;
	}

	private void handleModeParams(final String params) {
		int n = 0;
		try {
			String p = params;
			if (p.startsWith("?")) {
				p = p.substring(1);
			}
			if (p.length() == 0) {
				n = 0;
			} else {
				int semi = p.indexOf(';');
				n = Integer.parseInt(semi < 0 ? p : p.substring(0, semi));
			}
		} catch (NumberFormatException e) {
			return;
		}
		if (!active) {
			active = true;
		}
		applyMode(n);
	}

	public void applyMode(final int n) {
		switch (n) {
		case 0:
			closeOpenModeTags();
			mode = Mode.OPEN;
			break;
		case 1:
			if (mode == Mode.OPEN) {
				closeOpenModeTags();
			}
			mode = Mode.SECURE;
			break;
		case 2:
			if (mode == Mode.OPEN) {
				closeOpenModeTags();
			}
			mode = Mode.LOCKED;
			break;
		case 3:
			closeAllTags();
			mode = Mode.OPEN;
			defaultMode = Mode.OPEN;
			emitAscii("\u001B[0m");
			break;
		case 4:
			tempSecure = true;
			break;
		case 5:
			if (mode == Mode.OPEN) {
				closeOpenModeTags();
			}
			mode = Mode.OPEN;
			defaultMode = Mode.OPEN;
			break;
		case 6:
			if (mode == Mode.OPEN) {
				closeOpenModeTags();
			}
			mode = Mode.SECURE;
			defaultMode = Mode.SECURE;
			break;
		case 7:
			if (mode == Mode.OPEN) {
				closeOpenModeTags();
			}
			mode = Mode.LOCKED;
			defaultMode = Mode.LOCKED;
			break;
		default:
			if (n >= 10 && n <= 99) {
				LineTag lt = lineTags.get(Integer.valueOf(n));
				if (lt != null && lt.fore != null) {
					String sgr = MxpColor.foregroundSgr(lt.fore);
					if (sgr != null) {
						emitAscii("\u001B[" + sgr + "m");
					}
				}
			}
			break;
		}
	}

	private void handleNewline() {
		if (eatNextNewline) {
			eatNextNewline = false;
			return;
		}
		if (inParagraph) {
			emitAscii(" ");
			return;
		}
		if (mode == Mode.OPEN) {
			closeOpenModeTags();
		}
		mode = defaultMode;
		emit((byte) '\n');
	}

	private void handleTagString(final String inner) {
		boolean thisSecure = secureOk();
		tempSecure = false;
		MxpTag tag = MxpTag.parse(inner);
		if (tag == null) {
			emit((byte) '<');
			emitAscii(inner);
			emit((byte) '>');
			return;
		}
		if (tag.definition) {
			if (!thisSecure) {
				return;
			}
			handleDefinition(tag);
			return;
		}
		if (tag.closing) {
			handleClose(tag.canonical());
			return;
		}
		handleOpen(tag, thisSecure);
	}

	private boolean secureOk() {
		return mode == Mode.SECURE || tempSecure;
	}

	private void handleOpen(final MxpTag tag, final boolean secure) {
		String n = tag.canonical();
		ElementDef custom = elements.get(n);
		if (custom != null) {
			if (!custom.open && !secure) {
				return;
			}
			openCustom(custom, tag);
			return;
		}
		if (isOpenStyle(n)) {
			openStyle(n, tag);
			return;
		}
		if (!secure && !isOpenStyle(n)) {
			return;
		}
		if ("send".equals(n)) {
			openSend(tag);
		} else if ("a".equals(n)) {
			openAnchor(tag);
		} else if ("expire".equals(n)) {
			String group = tag.attrOrPos("name", 0);
			emitExpire(group == null ? "" : group);
		} else if ("version".equals(n)) {
			replyVersion();
		} else if ("support".equals(n) || "supports".equals(n)) {
			replySupport(tag);
		} else if ("var".equals(n) || "v".equals(n)) {
			openVar(tag);
		} else if ("br".equals(n)) {
			emit((byte) '\n');
		} else if ("nobr".equals(n)) {
			eatNextNewline = true;
		} else if ("p".equals(n)) {
			inParagraph = true;
			push(OpenElem.style("p"));
		} else if ("sbr".equals(n)) {
			emitAscii(" ");
		} else if ("hr".equals(n)) {
			emitAscii("\n--------------------------------\n");
		} else if ("dest".equals(n) || "destination".equals(n)) {
			openDest(tag);
		} else if ("frame".equals(n)) {
			if (tag.hasFlag("redirect") || tag.attr("redirect") != null) {
				String name = tag.attrOrPos("name", 0);
				openDestNamed(name);
			}
		} else if ("image".equals(n) || "img".equals(n)) {
			// Consumed. Drawing pictures is a later client feature.
		} else if ("sound".equals(n) || "music".equals(n)) {
			String fname = tag.attrOrPos("fname", 0);
			listener.playSound(fname,
					parseInt(tag.attrOrPos("v", 1), 100),
					parseInt(tag.attrOrPos("l", 2), 1),
					parseInt(tag.attrOrPos("p", 3), 50),
					tag.attrOrPos("t", 4),
					tag.attrOrPos("u", 5));
		} else if ("gauge".equals(n) || "stat".equals(n)) {
			String name = tag.attrOrPos("name", 0);
			String value = tag.attrOrPos("value", 1);
			if (name != null && value != null) {
				listener.setVariable(name, value);
			}
		} else if (n.length() == 2 && n.charAt(0) == 'h' && n.charAt(1) >= '1'
				&& n.charAt(1) <= '6') {
			OpenElem heading = OpenElem.style(n);
			emitAscii("\u001B[1m");
			heading.closeSgr = "\u001B[22m";
			push(heading);
		} else if ("relocate".equals(n) || "filter".equals(n) || "script".equals(n)
				|| "user".equals(n) || "password".equals(n) || "mxp".equals(n)
				|| "body".equals(n) || "head".equals(n) || "cpo".equals(n)) {
			if (!tag.empty) {
				push(OpenElem.capture(n));
			}
		}
	}

	private void handleClose(final String name) {
		if ("p".equals(name)) {
			inParagraph = false;
		}
		if ("dest".equals(name) || "destination".equals(name) || "frame".equals(name)) {
			flushDest();
			destName = null;
			destBuf = null;
		}
		closeNamed(name);
	}

	private void handleDefinition(final MxpTag tag) {
		String n = tag.canonical();
		if ("element".equals(n) || "el".equals(n)) {
			defineElement(tag);
		} else if ("attlist".equals(n) || "att".equals(n)) {
			String elName = tag.attrOrPos("name", 0);
			if (elName == null && tag.positional.size() > 0) {
				elName = tag.positional.get(0);
			}
			ElementDef def = elName == null ? null : elements.get(elName.toLowerCase(Locale.US));
			if (def != null) {
				String att = tag.attr("att");
				if (att == null && tag.positional.size() > 1) {
					att = tag.positional.get(1);
				}
				if (att != null) {
					def.att = parseAttList(att);
				}
			}
		} else if ("entity".equals(n) || "en".equals(n)) {
			defineEntity(tag);
		} else if ("tag".equals(n)) {
			int index = parseInt(tag.attrOrPos("index", 0), -1);
			if (index >= 20 && index <= 99) {
				LineTag lt = lineTags.get(Integer.valueOf(index));
				if (lt == null) {
					lt = new LineTag();
					lineTags.put(Integer.valueOf(index), lt);
				}
				if (tag.attr("fore") != null) {
					lt.fore = tag.attr("fore");
				}
				if (tag.attr("back") != null) {
					lt.back = tag.attr("back");
				}
				lt.gag = tag.hasFlag("gag");
				lt.window = tag.attr("windowname");
			}
		}
	}

	private void defineElement(final MxpTag tag) {
		String elName = tag.attrOrPos("name", 0);
		if (elName == null && tag.positional.size() > 0) {
			elName = tag.positional.get(0);
		}
		if (elName == null) {
			return;
		}
		String key = elName.toLowerCase(Locale.US);
		if (tag.hasFlag("delete")) {
			elements.remove(key);
			return;
		}
		ElementDef def = new ElementDef();
		def.name = key;
		def.open = tag.hasFlag("open");
		def.empty = tag.empty || tag.hasFlag("empty");
		def.definition = tag.attrOrPos("definition", 1);
		if (def.definition == null && tag.positional.size() > 1) {
			def.definition = tag.positional.get(1);
		}
		String att = tag.attr("att");
		if (att != null) {
			def.att = parseAttList(att);
		}
		def.flag = tag.attr("flag");
		String tagNo = tag.attr("tag");
		if (tagNo != null) {
			int idx = parseInt(tagNo, -1);
			if (idx >= 20 && idx <= 99) {
				LineTag lt = new LineTag();
				lineTags.put(Integer.valueOf(idx), lt);
				def.lineTag = idx;
			}
		}
		elements.put(key, def);
	}

	private void defineEntity(final MxpTag tag) {
		String name = tag.attrOrPos("name", 0);
		if (name == null && tag.positional.size() > 0) {
			name = tag.positional.get(0);
		}
		if (name == null) {
			return;
		}
		if (tag.hasFlag("delete")) {
			entities.delete(name);
			return;
		}
		String value = tag.attrOrPos("value", 1);
		if (value == null && tag.positional.size() > 1) {
			value = tag.positional.get(1);
		}
		if (value == null) {
			value = "";
		}
		if (tag.hasFlag("add")) {
			entities.addToList(name, value);
		} else if (tag.hasFlag("remove")) {
			entities.removeFromList(name, value);
		} else {
			entities.define(name, value, tag.hasFlag("publish"));
		}
		if (!tag.hasFlag("private")) {
			listener.setVariable(name, entities.get(name));
		}
	}

	private void openStyle(final String n, final MxpTag tag) {
		OpenElem el = OpenElem.style(n);
		if ("b".equals(n) || "bold".equals(n) || "strong".equals(n)) {
			emitAscii("\u001B[1m");
			el.closeSgr = "\u001B[22m";
		} else if ("i".equals(n) || "italic".equals(n) || "em".equals(n)) {
			emitAscii("\u001B[3m");
			el.closeSgr = "\u001B[23m";
		} else if ("u".equals(n) || "underline".equals(n)) {
			emitAscii("\u001B[4m");
			el.closeSgr = "\u001B[24m";
		} else if ("s".equals(n) || "strike".equals(n) || "strikeout".equals(n)) {
			emitAscii("\u001B[9m");
			el.closeSgr = "\u001B[29m";
		} else if ("high".equals(n) || "h".equals(n)) {
			emitAscii("\u001B[1m");
			el.closeSgr = "\u001B[22m";
		} else if ("color".equals(n) || "c".equals(n) || "font".equals(n)) {
			String fg = firstColor(tag);
			String bg = tag.attr("back");
			if (bg == null) {
				bg = tag.attr("background");
			}
			StringBuilder sgr = new StringBuilder("\u001B[");
			boolean any = false;
			String fgs = MxpColor.foregroundSgr(fg);
			if (fgs != null) {
				sgr.append(fgs);
				any = true;
			}
			String bgs = MxpColor.backgroundSgr(bg);
			if (bgs != null) {
				if (any) {
					sgr.append(';');
				}
				sgr.append(bgs);
				any = true;
			}
			if (any) {
				sgr.append('m');
				emitAscii(sgr.toString());
				el.closeSgr = "\u001B[39;49m";
			}
		}
		push(el);
	}

	private String firstColor(final MxpTag tag) {
		String fg = tag.attr("fore");
		if (fg == null) {
			fg = tag.attr("fg");
		}
		if (fg == null) {
			fg = tag.attr("color");
		}
		if (fg == null && tag.positional.size() > 0) {
			fg = tag.positional.get(0);
		}
		return fg;
	}

	private void openSend(final MxpTag tag) {
		OpenElem el = OpenElem.capture("send");
		el.href = tag.attrOrPos("href", 0);
		if (el.href == null) {
			el.href = tag.attr("xch_cmd");
		}
		el.hint = tag.attr("hint");
		el.expire = tag.attr("expire");
		el.prompt = tag.hasFlag("prompt") || tag.attr("prompt") != null;
		push(el);
	}

	private void openAnchor(final MxpTag tag) {
		OpenElem el = OpenElem.capture("a");
		el.href = tag.attrOrPos("href", 0);
		el.hint = tag.attr("hint");
		el.expire = tag.attr("expire");
		push(el);
	}

	private void openVar(final MxpTag tag) {
		OpenElem el = OpenElem.capture("var");
		el.varName = tag.attrOrPos("name", 0);
		push(el);
	}

	private void openDest(final MxpTag tag) {
		String name = tag.attrOrPos("name", 0);
		openDestNamed(name);
	}

	private void openDestNamed(final String name) {
		flushDest();
		if (name != null && name.length() > 0) {
			destName = name;
			destBuf = new ByteArrayOutputStream();
		}
		push(OpenElem.style("dest"));
	}

	private void openCustom(final ElementDef def, final MxpTag tag) {
		if (customDepth >= MAX_CUSTOM_DEPTH) {
			return;
		}
		if (def.empty) {
			expandCustom(def, tag, "", new byte[0]);
			return;
		}
		OpenElem el = OpenElem.capture("custom:" + def.name);
		el.custom = def;
		el.customTag = tag;
		push(el);
	}

	private void expandCustom(final ElementDef def, final MxpTag tag, final String text,
			final byte[] rendered) {
		if (def.definition == null || def.definition.length() == 0) {
			if (def.flag != null) {
				applyFlag(def.flag, text);
			}
			if (rendered != null && rendered.length > 0) {
				emit(rendered);
			} else {
				emitAscii(text);
			}
			return;
		}
		HashMap<String, String> values = bindAtt(def, tag);
		values.put("text", text);
		String expanded = substituteEntities(def.definition, values);
		expanded = expandRemainingEntities(expanded);
		customDepth++;
		try {
			ArrayList<MxpTag> opens = tagsIn(expanded);
			for (int i = 0; i < opens.size(); i++) {
				handleOpen(opens.get(i), true);
			}
			if (rendered != null && rendered.length > 0) {
				emit(rendered);
			} else {
				emitAscii(text);
			}
			for (int i = opens.size() - 1; i >= 0; i--) {
				handleClose(opens.get(i).canonical());
			}
		} finally {
			customDepth--;
		}
		if (def.flag != null) {
			applyFlag(def.flag, text);
		}
	}

	private void applyFlag(final String flag, final String text) {
		listener.onFlag(flag, text);
		String f = flag.trim();
		String lower = f.toLowerCase(Locale.US);
		if (lower.startsWith("set ")) {
			String var = f.substring(4).trim();
			listener.setVariable(var, text);
			entities.define(var, text, true);
		} else if (lower.equals("prompt")) {
			listener.setVariable("prompt", text);
		}
	}

	private void closeNamed(final String name) {
		int found = -1;
		for (int i = stack.size() - 1; i >= 0; i--) {
			OpenElem el = stack.get(i);
			if (el.matches(name)) {
				found = i;
				break;
			}
		}
		if (found < 0) {
			return;
		}
		for (int i = stack.size() - 1; i >= found; i--) {
			closeOne(stack.remove(i));
		}
	}

	private void closeOpenModeTags() {
		for (int i = stack.size() - 1; i >= 0; i--) {
			if (isOpenStyle(stack.get(i).name) || "p".equals(stack.get(i).name)) {
				closeOne(stack.remove(i));
			}
		}
	}

	private void closeAllTags() {
		for (int i = stack.size() - 1; i >= 0; i--) {
			closeOne(stack.remove(i));
		}
		inParagraph = false;
	}

	private void closeOne(final OpenElem el) {
		if (el.capture != null) {
			byte[] captured = el.capture.toByteArray();
			String plain = plainFromCapture(el, captured);
			if (el.name.startsWith("custom:")) {
				expandCustom(el.custom, el.customTag, plain, captured);
				return;
			}
			if ("send".equals(el.name)) {
				finishSend(el, plain, captured);
				return;
			}
			if ("a".equals(el.name)) {
				finishAnchor(el, captured);
				return;
			}
			if ("var".equals(el.name)) {
				emit(captured);
				if (el.varName != null) {
					entities.define(el.varName, plain, true);
					listener.setVariable(el.varName, plain);
				}
				return;
			}
			if (isDroppedSecure(el.name)) {
				return;
			}
			emit(captured);
			return;
		}
		if (el.closeSgr != null) {
			emitAscii(el.closeSgr);
		}
		if ("p".equals(el.name)) {
			inParagraph = false;
		}
	}

	private void finishSend(final OpenElem el, final String plain, final byte[] captured) {
		String cmd = el.href;
		if (cmd == null || cmd.length() == 0) {
			cmd = plain;
		}
		cmd = substituteText(cmd, plain);
		String href;
		if (cmd.indexOf('|') >= 0) {
			String hints = el.hint == null ? "" : el.hint;
			href = MxpLinks.menuHref(hints, cmd);
		} else if (el.prompt) {
			href = MxpLinks.promptHref(cmd);
		} else {
			href = MxpLinks.sendHref(cmd);
		}
		emitOsc(href, el.expire);
		emit(captured);
		emitOscClose();
	}

	private void finishAnchor(final OpenElem el, final byte[] captured) {
		String href = el.href == null ? "" : el.href;
		if (href.regionMatches(true, 0, "http://", 0, 7)
				|| href.regionMatches(true, 0, "https://", 0, 8)
				|| href.regionMatches(true, 0, "mailto:", 0, 7)) {
			emitOscRaw(href, el.expire);
		} else {
			emitOsc(MxpLinks.sendHref(href), el.expire);
		}
		emit(captured);
		emitOscClose();
	}

	private void emitExpire(final String group) {
		listener.expire(group);
		emitOscRaw(MxpLinks.expireHref(group), null);
		emitOscClose();
	}

	private void emitOsc(final String href, final String expire) {
		emitOscRaw(href, expire);
	}

	private void emitOscRaw(final String uri, final String expire) {
		StringBuilder sb = new StringBuilder();
		sb.append("\u001B]8;");
		String id = MxpLinks.expireId(expire);
		if (id != null) {
			sb.append("id=").append(id);
		}
		sb.append(';').append(uri).append((char) BEL);
		emitAscii(sb.toString());
	}

	private void emitOscClose() {
		emitAscii("\u001B]8;;");
		emit(BEL);
	}

	private void replyVersion() {
		String line = "\u001B[1z<VERSION MXP=" + MXP_SPEC_VERSION
				+ " CLIENT=" + clientName
				+ " VERSION=" + clientVersion
				+ " REGISTERED=NO>";
		listener.sendToMud(line + "\n");
	}

	private void replySupport(final MxpTag tag) {
		String[] asked = supportAsked(tag);
		StringBuilder sb = new StringBuilder("\u001B[1z<SUPPORTS");
		if (asked.length == 0) {
			sb.append(fullSupportList());
		} else {
			for (int i = 0; i < asked.length; i++) {
				String item = asked[i].toLowerCase(Locale.US);
				sb.append(' ');
				sb.append(supports(item) ? '+' : '-');
				sb.append(item);
			}
		}
		sb.append('>');
		listener.sendToMud(sb.toString() + "\n");
	}

	private static String fullSupportList() {
		return " +b +i +u +s +bold +italic +underline +strike +color +c +font +high"
				+ " +send +send.href +send.hint +send.expire +send.prompt +a +expire"
				+ " +version +support +element +el +attlist +entity +en +var +br"
				+ " +nobr +p +sbr +hr +dest +h1 +h2 +h3 +h4 +h5 +h6 +sound"
				+ " +color.fore +color.back +font.color";
	}

	private static boolean supports(final String item) {
		String x = item;
		if (x.endsWith(".*")) {
			x = x.substring(0, x.length() - 2);
		}
		if (x.startsWith("+") || x.startsWith("-")) {
			x = x.substring(1);
		}
		String list = fullSupportList();
		return list.contains(" +" + x) || list.contains(" +" + x + ".")
				|| list.contains(" +" + x + " ") || list.endsWith(" +" + x);
	}

	private static String[] supportAsked(final MxpTag tag) {
		ArrayList<String> all = new ArrayList<String>();
		all.addAll(tag.positional);
		for (String v : tag.named.values()) {
			if (v != null && v.length() > 0) {
				String[] parts = v.split("[\\s,]+");
				for (int i = 0; i < parts.length; i++) {
					if (parts[i].length() > 0) {
						all.add(parts[i]);
					}
				}
			}
		}
		return all.toArray(new String[all.size()]);
	}

	private void expandEntity(final String name) {
		String v = entities.expand(name);
		if (v == null) {
			emitAscii("&");
			emitAscii(name);
			emitAscii(";");
			return;
		}
		emitAscii(v);
	}

	private static boolean isDroppedSecure(final String n) {
		return "script".equals(n) || "relocate".equals(n) || "filter".equals(n)
				|| "user".equals(n) || "password".equals(n) || "mxp".equals(n)
				|| "body".equals(n) || "head".equals(n) || "cpo".equals(n);
	}

	private static boolean isOpenStyle(final String n) {
		return "b".equals(n) || "bold".equals(n) || "strong".equals(n)
				|| "i".equals(n) || "italic".equals(n) || "em".equals(n)
				|| "u".equals(n) || "underline".equals(n)
				|| "s".equals(n) || "strike".equals(n) || "strikeout".equals(n)
				|| "color".equals(n) || "c".equals(n) || "high".equals(n) || "h".equals(n)
				|| "font".equals(n);
	}

	private void push(final OpenElem el) {
		stack.add(el);
	}

	private void emit(final byte b) {
		OpenElem cap = topCapture();
		if (cap != null) {
			cap.capture.write(b);
			appendPlain(cap, b);
			return;
		}
		if (destBuf != null) {
			destBuf.write(b);
			return;
		}
		out.write(b);
	}

	private void emit(final byte[] data) {
		if (data == null) {
			return;
		}
		for (int i = 0; i < data.length; i++) {
			emit(data[i]);
		}
	}

	private void emitAscii(final String s) {
		if (s == null || s.length() == 0) {
			return;
		}
		try {
			emit(s.getBytes(encoding));
		} catch (UnsupportedEncodingException e) {
			emit(s.getBytes(Charset.forName("UTF-8")));
		}
	}

	private static void appendPlain(final OpenElem cap, final byte b) {
		int ub = b & 0xFF;
		if (cap.plainEsc == 1) {
			if (ub == '[') {
				cap.plainEsc = 2;
			} else if (ub == ']') {
				cap.plainEsc = 3;
			} else {
				cap.plainEsc = 0;
			}
			return;
		}
		if (cap.plainEsc == 2) {
			if (ub >= 0x40 && ub <= 0x7E) {
				cap.plainEsc = 0;
			}
			return;
		}
		if (cap.plainEsc == 3) {
			if (ub == BEL || ub == '\\') {
				cap.plainEsc = 0;
			}
			return;
		}
		if (ub == ESC) {
			cap.plainEsc = 1;
			return;
		}
		if (ub >= 32 || ub == '\t') {
			cap.plain.append((char) ub);
		}
	}

	private OpenElem topCapture() {
		for (int i = stack.size() - 1; i >= 0; i--) {
			if (stack.get(i).capture != null) {
				return stack.get(i);
			}
		}
		return null;
	}

	private void flushDest() {
		if (destBuf != null && destName != null && destBuf.size() > 0) {
			listener.destOutput(destName, destBuf.toByteArray());
			destBuf.reset();
		}
	}

	private String expandRemainingEntities(final String tmpl) {
		if (tmpl == null || tmpl.indexOf('&') < 0) {
			return tmpl == null ? "" : tmpl;
		}
		StringBuilder sb = new StringBuilder(tmpl.length());
		int i = 0;
		while (i < tmpl.length()) {
			int amp = tmpl.indexOf('&', i);
			if (amp < 0) {
				sb.append(tmpl, i, tmpl.length());
				break;
			}
			sb.append(tmpl, i, amp);
			int semi = tmpl.indexOf(';', amp + 1);
			if (semi < 0 || semi - amp > MAX_ENTITY) {
				sb.append('&');
				i = amp + 1;
				continue;
			}
			String name = tmpl.substring(amp + 1, semi);
			String v = entities.expand(name);
			if (v == null) {
				sb.append('&').append(name).append(';');
			} else {
				sb.append(v);
			}
			i = semi + 1;
		}
		return sb.toString();
	}

	private String plainFromCapture(final OpenElem el, final byte[] captured) {
		if (captured != null && captured.length > 0) {
			try {
				String decoded = new String(captured, encoding);
				return com.resurrection.blowtorch2.lib.service.Colorizer.stripAnsiEscapes(decoded);
			} catch (UnsupportedEncodingException e) {
				// fall through to the Latin-1 scratch buffer
			}
		}
		return el.plain == null ? "" : el.plain.toString();
	}

	private static boolean entityChar(final int index, final byte b) {
		char c = (char) (b & 0xFF);
		if (index == 0) {
			return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '#';
		}
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
				|| (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '#';
	}

	private static byte[] joinHold(final byte start, final StringBuilder rest) {
		byte[] r = rest.toString().getBytes(Charset.forName("ISO-8859-1"));
		byte[] out = new byte[1 + r.length];
		out[0] = start;
		System.arraycopy(r, 0, out, 1, r.length);
		return out;
	}

	private static int parseInt(final String s, final int def) {
		if (s == null || s.length() == 0) {
			return def;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static String substituteText(final String tmpl, final String text) {
		if (tmpl == null) {
			return text;
		}
		return replaceIgnoreCase(tmpl, "&text;", text);
	}

	private static String substituteEntities(final String tmpl,
			final HashMap<String, String> values) {
		if (tmpl == null) {
			return "";
		}
		String s = tmpl;
		for (String key : values.keySet()) {
			String v = values.get(key);
			s = replaceIgnoreCase(s, "&" + key + ";", v == null ? "" : v);
		}
		return s;
	}

	private static String replaceIgnoreCase(final String src, final String find,
			final String repl) {
		if (src == null) {
			return "";
		}
		String lower = src.toLowerCase(Locale.US);
		String f = find.toLowerCase(Locale.US);
		StringBuilder sb = new StringBuilder();
		int from = 0;
		int at;
		while ((at = lower.indexOf(f, from)) >= 0) {
			sb.append(src, from, at);
			sb.append(repl);
			from = at + find.length();
		}
		sb.append(src, from, src.length());
		return sb.toString();
	}

	private static ArrayList<AttDef> parseAttList(final String att) {
		ArrayList<AttDef> list = new ArrayList<AttDef>();
		if (att == null) {
			return list;
		}
		String[] parts = att.trim().split("\\s+");
		for (int i = 0; i < parts.length; i++) {
			String p = parts[i];
			if (p.length() == 0) {
				continue;
			}
			int eq = p.indexOf('=');
			AttDef d = new AttDef();
			if (eq < 0) {
				d.name = p;
				d.deflt = "";
			} else {
				d.name = p.substring(0, eq);
				d.deflt = unquoteVal(p.substring(eq + 1));
			}
			list.add(d);
		}
		return list;
	}

	private static String unquoteVal(final String v) {
		if (v.length() >= 2) {
			char a = v.charAt(0);
			char b = v.charAt(v.length() - 1);
			if ((a == '"' || a == '\'') && a == b) {
				return v.substring(1, v.length() - 1);
			}
		}
		return v;
	}

	private static HashMap<String, String> bindAtt(final ElementDef def, final MxpTag tag) {
		HashMap<String, String> values = new HashMap<String, String>();
		if (def.att != null) {
			for (int i = 0; i < def.att.size(); i++) {
				AttDef a = def.att.get(i);
				values.put(a.name.toLowerCase(Locale.US), a.deflt);
			}
		}
		int pos = 0;
		for (int i = 0; i < tag.positional.size(); i++) {
			if (def.att != null && pos < def.att.size()) {
				values.put(def.att.get(pos).name.toLowerCase(Locale.US), tag.positional.get(i));
				pos++;
			}
		}
		for (String k : tag.named.keySet()) {
			values.put(k.toLowerCase(Locale.US), tag.named.get(k));
		}
		return values;
	}

	private static ArrayList<MxpTag> tagsIn(final String def) {
		ArrayList<MxpTag> tags = new ArrayList<MxpTag>();
		int i = 0;
		while (i < def.length()) {
			int lt = def.indexOf('<', i);
			if (lt < 0) {
				break;
			}
			char quote = 0;
			int gt = -1;
			for (int j = lt + 1; j < def.length(); j++) {
				char c = def.charAt(j);
				if (quote != 0) {
					if (c == quote) {
						quote = 0;
					}
					continue;
				}
				if (c == '\'' || c == '"') {
					quote = c;
					continue;
				}
				if (c == '>') {
					gt = j;
					break;
				}
			}
			if (gt < 0) {
				break;
			}
			MxpTag t = MxpTag.parse(def.substring(lt + 1, gt));
			if (t != null && !t.closing) {
				tags.add(t);
			}
			i = gt + 1;
		}
		return tags;
	}

	private static final class ElementDef {
		String name;
		String definition;
		ArrayList<AttDef> att = new ArrayList<AttDef>();
		boolean open;
		boolean empty;
		String flag;
		int lineTag;
	}

	private static final class AttDef {
		String name;
		String deflt;
	}

	private static final class LineTag {
		String fore;
		String back;
		boolean gag;
		String window;
	}

	private static final class OpenElem {
		final String name;
		final ByteArrayOutputStream capture;
		final StringBuilder plain;
		String closeSgr;
		String href;
		String hint;
		String expire;
		boolean prompt;
		String varName;
		ElementDef custom;
		MxpTag customTag;
		/** 0 none, 1 ESC, 2 CSI, 3 OSC — so &text; is not polluted by SGR. */
		int plainEsc;

		private OpenElem(final String name, final boolean capture) {
			this.name = name;
			this.capture = capture ? new ByteArrayOutputStream() : null;
			this.plain = capture ? new StringBuilder() : null;
		}

		static OpenElem style(final String name) {
			return new OpenElem(name, false);
		}

		static OpenElem capture(final String name) {
			return new OpenElem(name, true);
		}

		boolean matches(final String closeName) {
			if (name.equals(closeName)) {
				return true;
			}
			if (name.startsWith("custom:") && name.substring(7).equals(closeName)) {
				return true;
			}
			return aliases(name, closeName);
		}

		private static boolean aliases(final String a, final String b) {
			return sameFamily(a, b, "b", "bold", "strong")
					|| sameFamily(a, b, "i", "italic", "em")
					|| sameFamily(a, b, "u", "underline")
					|| sameFamily(a, b, "s", "strike", "strikeout")
					|| sameFamily(a, b, "color", "c")
					|| sameFamily(a, b, "high", "h")
					|| sameFamily(a, b, "var", "v")
					|| sameFamily(a, b, "dest", "destination");
		}

		private static boolean sameFamily(final String a, final String b, final String... names) {
			boolean aa = false;
			boolean bb = false;
			for (int i = 0; i < names.length; i++) {
				if (names[i].equals(a)) {
					aa = true;
				}
				if (names[i].equals(b)) {
					bb = true;
				}
			}
			return aa && bb;
		}
	}
}
