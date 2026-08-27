package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

/**
 * What one line of scrollback actually costs, and therefore what the buffer cap
 * really caps. Answers HANDOFF item 7 ("Text buffer size — measure TextTree.
 * Lines vs chars, real ceiling").
 *
 * <p><b>Counts, not heap deltas.</b> This runs on HotSpot; the app runs on ART,
 * where object headers, reference width and {@code String} layout all differ.
 * A {@code Runtime.totalMemory()} reading here would be wrong on the device by
 * an unknown factor, so the test counts the objects the buffer allocates and
 * prices them with an explicit ART model ({@link #SIZES}). The counts are the
 * measurement; the byte totals are that model applied to them, and are marked
 * as modelled everywhere they appear.
 *
 * <p>Run it and read the table:
 * <pre>
 * ./gradlew :BTLib:testDebugUnitTest --tests '*TextTreeFootprintTest*' -i
 * </pre>
 *
 * <p>The assertions only pin the facts the model rests on (double storage of
 * every text run, prune trimming to exactly the cap). They are deliberately
 * loose on byte totals so that re-measuring after a change is a matter of
 * reading the printout, not of chasing a failing bound.
 */
public class TextTreeFootprintTest {

	private static final String ESC = "";

	// ---- ART size model ---------------------------------------------------
	//
	// ART on a 64-bit device: 8-byte object header (class word + lock word),
	// 4-byte heap references (compressed), 8-byte allocation alignment.
	// Strings are compact — one byte per char while the content is Latin-1,
	// which MUD text and ANSI sequences are.
	private static final class SIZES {
		static final int HEADER = 8;
		static final int REF = 4;
		static final int ALIGN = 8;

		static int align(int n) {
			return ((n + ALIGN - 1) / ALIGN) * ALIGN;
		}

		/** header + 3 ints + 3 refs + 1 int + this$0 (Line is an inner class). */
		static final int LINE = align(HEADER + 12 + 3 * REF + 4 + REF);
		/** header + prev/next/item. */
		static final int LIST_NODE = align(HEADER + 3 * REF);
		/** header + first/last/size/modCount. */
		static final int LINKED_LIST = align(HEADER + 2 * REF + 8);
		/** Unit: header + charcount + bytecount + type ref + this$0. */
		static final int UNIT = align(HEADER + 8 + REF + REF);
		/** Text: Unit + data ref + bin ref + link flag. */
		static final int TEXT = align(HEADER + 8 + REF + REF + 2 * REF + 1);
		/** Color: Unit + bin ref + operations ref. */
		static final int COLOR = align(HEADER + 8 + REF + REF + 2 * REF);
		/** header + elementData ref + size + modCount. */
		static final int ARRAY_LIST = align(HEADER + REF + 8);
		/** header + value. Cached for -128..127; anything else is fresh. */
		static final int BOXED_INT = align(HEADER + 4);

		/** header + length + payload. */
		static int byteArray(int len) {
			return align(HEADER + 4 + len);
		}

		/** header + count + hash + inline Latin-1 bytes. */
		static int string(int chars) {
			return align(HEADER + 8 + chars);
		}

		/** header + length + refs. Capacity is modelled as size — a floor. */
		static int objectArray(int len) {
			return align(HEADER + 4 + REF * len);
		}
	}

	/** Everything one filled {@link TextTree} holds, counted by kind. */
	private static final class Footprint {
		String profile;
		int cap;
		int lines;
		int textUnits;
		int whitespaceUnits;
		int colorUnits;
		int otherUnits;
		int chars;           // characters the player can see
		int stringChars;     // chars held in Text.data
		int binBytes;        // bytes held in Text.bin + Color.bin — the same content again
		int boxedInts;
		int cachedBoxedInts; // -128..127, shared by the JDK/ART cache
		long objectArrayCells;

		long modelledBytes() {
			long total = SIZES.LINKED_LIST;
			total += (long) lines * (SIZES.LINE + SIZES.LIST_NODE + SIZES.LINKED_LIST);
			total += (long) (textUnits + whitespaceUnits) * SIZES.TEXT;
			total += (long) colorUnits * SIZES.COLOR;
			total += (long) otherUnits * SIZES.UNIT;
			// one list node per unit, inside the line's own LinkedList
			total += (long) (textUnits + whitespaceUnits + colorUnits + otherUnits)
					* SIZES.LIST_NODE;
			total += (long) colorUnits * SIZES.ARRAY_LIST;
			total += (long) (boxedInts - cachedBoxedInts) * SIZES.BOXED_INT;
			return total;
		}

		/** Strings, byte[] and Object[] priced per instance rather than in bulk. */
		long payloadBytes;

		long totalBytes() {
			return modelledBytes() + payloadBytes;
		}

		double bytesPerLine() {
			return lines == 0 ? 0 : (double) totalBytes() / lines;
		}

		double bytesPerChar() {
			return chars == 0 ? 0 : (double) totalBytes() / chars;
		}

		/** Units per line is what drives the cost — a word is an object. */
		double unitsPerLine() {
			int units = textUnits + whitespaceUnits + colorUnits + otherUnits;
			return lines == 0 ? 0 : (double) units / lines;
		}
	}

	private static Footprint measure(String profile, TextTree tree) {
		Footprint f = new Footprint();
		f.profile = profile;
		f.cap = tree.getMaxLines();
		LinkedList<TextTree.Line> lines = tree.getLines();
		f.lines = lines.size();
		for (TextTree.Line line : lines) {
			for (TextTree.Unit u : line.getData()) {
				switch (u.type) {
				case TEXT:
				case WHITESPACE:
					TextTree.Text t = (TextTree.Text) u;
					if (u.type == TextTree.UNIT_TYPE.WHITESPACE) {
						f.whitespaceUnits++;
					} else {
						f.textUnits++;
					}
					int len = t.data == null ? 0 : t.data.length();
					f.chars += len;
					f.stringChars += len;
					f.payloadBytes += SIZES.string(len);
					int bin = t.bin == null ? 0 : t.bin.length;
					f.binBytes += bin;
					f.payloadBytes += SIZES.byteArray(bin);
					break;
				case COLOR:
					TextTree.Color c = (TextTree.Color) u;
					f.colorUnits++;
					int cbin = c.bin == null ? 0 : c.bin.length;
					f.binBytes += cbin;
					f.payloadBytes += SIZES.byteArray(cbin);
					if (c.operations != null) {
						f.boxedInts += c.operations.size();
						f.objectArrayCells += c.operations.size();
						f.payloadBytes += SIZES.objectArray(c.operations.size());
						for (Integer op : c.operations) {
							if (op != null && op.intValue() >= -128 && op.intValue() <= 127) {
								f.cachedBoxedInts++;
							}
						}
					}
					break;
				default:
					f.otherUnits++;
					break;
				}
			}
		}
		return f;
	}

	// ---- text profiles ----------------------------------------------------

	/**
	 * 80 columns with a word boundary every six characters. This is not typical
	 * prose — it is the worst word density a normal-length line can have, and
	 * word density is what the unit count follows. Real text (the user manual
	 * profile) sits well under it.
	 */
	private static String plainLine(int i) {
		StringBuilder sb = new StringBuilder(80);
		sb.append("You are standing in a quiet room. Exit ").append(i % 10);
		while (sb.length() < 79) {
			sb.append(' ').append("stone");
		}
		sb.setLength(79);
		return sb.append('\n').toString();
	}

	/** Combat spam: a colour change every few words, which is the expensive shape. */
	private static String colouredLine(int i) {
		return ESC + "[1;31m" + "The goblin" + ESC + "[0m hits " + ESC + "[1;33myou"
				+ ESC + "[0m for " + ESC + "[1;37m" + (i % 40 + 1) + ESC + "[0m damage."
				+ ESC + "[38;5;" + (i % 256) + "m" + " [" + i + "]" + ESC + "[0m\n";
	}

	/** One unwrapped line far past the visible width — a map, a who list, a paste. */
	private static String longLine(int width) {
		StringBuilder sb = new StringBuilder(width + 1);
		while (sb.length() < width) {
			sb.append("#.=+ ");
		}
		sb.setLength(width);
		return sb.append('\n').toString();
	}

	private static TextTree fill(int lines, Profile profile) throws Exception {
		TextTree tree = new TextTree();
		tree.setMaxLines(lines);
		// Feed it the way the network does: Connection.dispatch calls addBytesImpl
		// on the raw chunk, so the parser — and its Color units — is in play.
		for (int i = 0; i < lines; i++) {
			tree.addBytesImpl(profile.line(i).getBytes("UTF-8"));
		}
		tree.prune();
		return tree;
	}

	private interface Profile {
		String line(int i);
	}

	private static final Profile PLAIN = new Profile() {
		public String line(int i) {
			return plainLine(i);
		}
	};

	private static final Profile COLOURED = new Profile() {
		public String line(int i) {
			return colouredLine(i);
		}
	};

	private static final Profile LONG = new Profile() {
		public String line(int i) {
			return longLine(2000);
		}
	};

	/**
	 * Real text rather than a generator, when the source tree is next to us.
	 * Falls back to the plain profile so the test still runs from anywhere.
	 */
	private static Profile manual() {
		List<String> lines = new ArrayList<String>();
		File f = new File("../docs/user-manual.md");
		if (!f.isFile()) {
			f = new File("docs/user-manual.md");
		}
		if (f.isFile()) {
			Reader r = null;
			try {
				r = new InputStreamReader(new FileInputStream(f), "UTF-8");
				StringBuilder sb = new StringBuilder();
				int ch;
				while ((ch = r.read()) != -1) {
					sb.append((char) ch);
					if (ch == '\n') {
						lines.add(sb.toString());
						sb.setLength(0);
					}
				}
			} catch (Exception ignored) {
				// no real text available; the fallback below covers it
			} finally {
				if (r != null) {
					try {
						r.close();
					} catch (Exception ignored) {
					}
				}
			}
		}
		if (lines.isEmpty()) {
			return PLAIN;
		}
		final List<String> source = lines;
		return new Profile() {
			public String line(int i) {
				return source.get(i % source.size());
			}
		};
	}

	// ---- the measurement --------------------------------------------------

	@Test
	public void reportBufferFootprint() throws Exception {
		int defaultCap = 2000;   // WindowToken.DEFAULT_BUFFER_SIZE
		int absoluteCap = 8000;  // measurement fixture, not TextTree.ABSOLUTE_MAX_LINES (20000)

		List<Footprint> rows = new ArrayList<Footprint>();
		Profile real = manual();
		String realLabel = real == PLAIN
				? "REAL TEXT MISSING - plain again" : "real text (user manual)";
		rows.add(measure("80-col, one word per 6 chars", fill(defaultCap, PLAIN)));
		rows.add(measure("coloured combat, 2000 lines", fill(defaultCap, COLOURED)));
		rows.add(measure(realLabel + ", 2000 lines", fill(defaultCap, real)));
		rows.add(measure("2000-char unwrapped, 2000 lines", fill(defaultCap, LONG)));
		rows.add(measure("coloured combat, 8000 lines", fill(absoluteCap, COLOURED)));

		StringBuilder out = new StringBuilder();
		out.append("\nTextTree footprint — object counts measured, bytes modelled for ART\n");
		out.append(String.format("%-32s %6s %7s %7s %7s %8s %8s %9s %8s %7s%n",
				"profile", "lines", "text", "colour", "other", "units/ln", "chars",
				"modelKiB", "B/line", "B/char"));
		for (Footprint f : rows) {
			out.append(String.format("%-32s %6d %7d %7d %7d %8.1f %8d %9.0f %8.0f %7.1f%n",
					f.profile, f.lines, f.textUnits + f.whitespaceUnits, f.colorUnits,
					f.otherUnits, f.unitsPerLine(), f.chars, f.totalBytes() / 1024.0,
					f.bytesPerLine(), f.bytesPerChar()));
		}
		out.append("\nStorage of the same content, per profile:\n");
		for (Footprint f : rows) {
			out.append(String.format("%-32s String chars %8d   byte[] bytes %8d   boxed ints %6d (%d shared)%n",
					f.profile, f.stringChars, f.binBytes, f.boxedInts, f.cachedBoxedInts));
		}
		System.out.println(out);

		// The facts the model rests on, pinned so they cannot drift unnoticed.
		for (Footprint f : rows) {
			assertEquals("prune must leave exactly the cap", f.cap, f.lines);
			assertTrue("every profile must produce text units", f.textUnits > 0);
			assertTrue("Text keeps the same content as String and as byte[]",
					f.binBytes >= f.stringChars);
			assertTrue("a line is many objects, not one", f.unitsPerLine() > 1.0);
		}
		Footprint plain = rows.get(0);
		Footprint coloured = rows.get(1);
		// Per line is not comparable across profiles (the lines are not the same
		// length); per visible character is.
		assertTrue("colour must cost more per character than plain text",
				coloured.bytesPerChar() > plain.bytesPerChar());
	}

	/** The cap counts lines, so one line of any length still counts as one. */
	@Test
	public void capCountsLinesNotCharacters() throws Exception {
		TextTree tree = new TextTree();
		tree.setMaxLines(100);
		for (int i = 0; i < 150; i++) {
			tree.addBytesImpl(longLine(2000).getBytes("UTF-8"));
		}
		tree.prune();
		assertEquals(100, tree.getLines().size());
		Footprint f = measure("cap check", tree);
		assertTrue("100 lines of 2000 chars is far past 100 lines of 80",
				f.chars > 100 * 1000);
	}

	/**
	 * The caps have to travel with the text, or the trip trims the buffer.
	 *
	 * <p>{@code WindowToken.writeToParcel} sends the whole buffer as bytes and the
	 * reader rebuilds it into a brand new {@code TextTree}. A fresh tree starts at
	 * the 2000-line default, so before the caps were parcelled alongside the
	 * bytes, a player who asked for more lost the difference every time a token
	 * crossed the binder. This reproduces both halves of that pair of calls in the
	 * order the token does them.
	 *
	 * <p>The service tree does reach these sizes: Options → Text Buffer Size gets
	 * there through {@code Window} (case {@code buffer_size}) →
	 * {@code MainWindow.MESSAGE_WINDOWBUFFERMAXCHANGED} →
	 * {@code Connection.updateWindowBufferMaxValue} →
	 * {@code WindowToken.setBufferSize}.
	 */
	@Test
	public void theParcelPathKeepsWhatTheCapsAllow() throws Exception {
		TextTree big = new TextTree();
		big.setMaxLines(8000);
		for (int i = 0; i < 8000; i++) {
			big.addBytesImpl(plainLine(i).getBytes("UTF-8"));
		}
		assertEquals(8000, big.getLines().size());

		byte[] parcelled = big.dumpToBytes(true);
		TextTree rebuilt = new TextTree();
		// Reading constructor: caps first, then the text.
		rebuilt.setMaxLines(8000);
		rebuilt.addBytesImpl(parcelled);

		System.out.println(String.format(
				"parcel path: %d lines in, %d bytes on the wire, %d lines out (cap %d)",
				8000, parcelled.length, rebuilt.getLines().size(), rebuilt.getMaxLines()));
		assertEquals("the cap travelled, so the text did too",
				8000, rebuilt.getLines().size());

		// Without the caps — the old order — the same bytes lose three quarters.
		TextTree uncapped = new TextTree();
		uncapped.addBytesImpl(parcelled);
		assertEquals(2000, uncapped.getLines().size());
	}

	/** Below the floor and above the ceiling the setter clamps rather than obeys. */
	@Test
	public void bufferSizeIsClamped() {
		TextTree tree = new TextTree();
		tree.setMaxLines(1);
		assertEquals(100, tree.getMaxLines());
		tree.setMaxLines(1000000);
		assertEquals(20000, tree.getMaxLines());
		assertEquals(TextTree.ABSOLUTE_MAX_LINES, TextTree.clampMaxLines(55000));
		assertEquals(TextTree.MIN_LINES, TextTree.clampMaxLines(1));
		assertEquals(20000, TextTree.clampMaxLines(20000));
	}

	/**
	 * The byte budget is what actually bounds the heap: whichever of lines or
	 * bytes runs out first wins, and a world of long lines runs out of bytes.
	 */
	@Test
	public void theByteBudgetTrimsBeforeTheLineCap() throws Exception {
		TextTree tree = new TextTree();
		tree.setMaxLines(20000);
		tree.setMaxBytes(512 * 1024); // WindowToken.BUFFER_BYTE_BUDGET
		for (int i = 0; i < 2000; i++) {
			tree.addBytesImpl(longLine(2000).getBytes("UTF-8"));
		}
		assertTrue("long lines must run out of bytes long before 20000 lines",
				tree.getLines().size() < 400);
		assertTrue("and stay inside the budget", tree.getTotalBytes() <= 512 * 1024);

		// Ordinary text reaches the line cap first, so the budget never shows.
		TextTree prose = new TextTree();
		prose.setMaxLines(2000);
		prose.setMaxBytes(512 * 1024);
		for (int i = 0; i < 2500; i++) {
			prose.addBytesImpl(plainLine(i).getBytes("UTF-8"));
		}
		assertEquals(2000, prose.getLines().size());
	}

	/** One line, however big, is shown rather than pruned into nothing. */
	@Test
	public void theBudgetNeverEmptiesTheBuffer() throws Exception {
		TextTree tree = new TextTree();
		tree.setMaxBytes(1024);
		tree.addBytesImpl(longLine(50000).getBytes("UTF-8"));
		assertEquals(1, tree.getLines().size());
	}
}
