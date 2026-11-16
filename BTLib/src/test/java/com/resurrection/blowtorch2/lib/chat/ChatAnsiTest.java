package com.resurrection.blowtorch2.lib.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.trigger.style.SgrRegisters;
import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;
import com.resurrection.blowtorch2.lib.window.TextTree;

public class ChatAnsiTest {

	private static final String ESC = "\u001b";

	@Test
	public void boldGreenRoundTripsFromTheLine() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[1;32mHi\n").getBytes("UTF-8"));
		StyleLineModel model = StyleLineModel.build(tree.getLines().get(0),
				SgrRegisters.defaults());
		String colored = ChatAnsi.encodeSlice(model, "Hi");
		assertTrue(colored.indexOf("Hi") >= 0);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(colored);
		assertEquals("Hi", text(pieces));
		assertTrue(pieces.get(0).bold);
		assertTrue(pieces.get(0).fgArgb != 0);
	}

	@Test
	public void plainLineStoresNoCodes() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl("Hi\n".getBytes("UTF-8"));
		StyleLineModel model = StyleLineModel.build(tree.getLines().get(0),
				SgrRegisters.defaults());
		assertEquals("", ChatAnsi.encodeSlice(model, "Hi"));
	}

	@Test
	public void boldOnlyKeepsTheLetters() {
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(ESC + "[1mHi");
		assertEquals("Hi", text(pieces));
		assertEquals(1, pieces.size());
		assertTrue(pieces.get(0).bold);
		assertEquals(0, pieces.get(0).fgArgb);
	}

	@Test
	public void brightWhiteIsWhiteNotTheBubbleColor() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[1;37mHi\n").getBytes("UTF-8"));
		StyleLineModel model = StyleLineModel.build(tree.getLines().get(0),
				SgrRegisters.defaults());
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(ChatAnsi.encodeSlice(model, "Hi"));
		assertEquals("Hi", text(pieces));
		assertTrue(pieces.get(0).bold);
		assertEquals(0xFFFFFFFF, pieces.get(0).fgArgb);
	}

	@Test
	public void normalColorAfterBrightDoesNotStayBright() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[1;32mHi" + ESC + "[0m" + ESC + "[33mthere\n")
				.getBytes("UTF-8"));
		StyleLineModel model = StyleLineModel.build(tree.getLines().get(0),
				SgrRegisters.defaults());
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(
				ChatAnsi.encodeSlice(model, "Hithere"));
		assertEquals("Hithere", text(pieces));
		assertEquals(2, pieces.size());
		assertTrue(pieces.get(0).bold);
		assertEquals(0xFF55FF55, pieces.get(0).fgArgb);
		assertFalse(pieces.get(1).bold);
		assertEquals(0xFFBBBB00, pieces.get(1).fgArgb);
	}

	@Test
	public void capturesThatAreNotAdjacentKeepTheirColors() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[0m tells you '"
				+ ESC + "[33mhello" + ESC + "[0m\n");
		String matched = "Alice tells you 'hello'";
		Pattern pattern = Pattern.compile("(\\w+) tells you '([^']*)'");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1 $2", map,
				matched, pattern);
		assertEquals("Alice hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals(3, pieces.size());
		assertEquals("Alice", pieces.get(0).text);
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
		assertFalse(pieces.get(0).bold);
		assertEquals(" ", pieces.get(1).text);
		assertEquals(0, pieces.get(1).fgArgb);
		assertFalse(pieces.get(1).bold);
		assertEquals("hello", pieces.get(2).text);
		assertEquals(0xFFBBBB00, pieces.get(2).fgArgb);
		assertFalse(pieces.get(2).bold);
	}

	@Test
	public void repeatedWordUsesThatGroupNotTheFirstCopy() throws Exception {
		StyleLineModel model = line(ESC + "[32mBob" + ESC + "[0m tells "
				+ ESC + "[31mBob" + ESC + "[0m hi\n");
		String matched = "Bob tells Bob hi";
		Pattern pattern = Pattern.compile("(\\w+) tells (\\w+) (.*)");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Bob");
		map.put("2", "Bob");
		map.put("3", "hi");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$2", map,
				matched, pattern);
		assertEquals("Bob", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Bob", text(pieces));
		assertEquals(0xFFBB0000, pieces.get(0).fgArgb);
		assertFalse(painted.colored.equals(ChatAnsi.encodeSlice(model, "Bob")));
	}

	@Test
	public void laterCopyOfTheSameMatchKeepsThatCopy() throws Exception {
		StyleLineModel model = line(ESC + "[32mwaves " + ESC + "[31mwaves\n");
		String matched = "waves";
		Pattern pattern = Pattern.compile("(\\w+)");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "waves");
		int second = model.plain.indexOf("waves", 1);
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1", map,
				matched, pattern, second);
		assertEquals("waves", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("waves", text(pieces));
		assertEquals(0xFFBB0000, pieces.get(0).fgArgb);
		List<ChatAnsi.Piece> first = ChatAnsi.pieces(
				ChatAnsi.encodeSlice(model, "waves", 0));
		assertEquals(0xFF00BB00, first.get(0).fgArgb);
		List<ChatAnsi.Piece> missed = ChatAnsi.pieces(
				ChatAnsi.encodeSlice(model, "waves", 1));
		assertEquals(0xFF00BB00, missed.get(0).fgArgb);
	}

	@Test
	public void spaceBetweenAdjacentCapturesKeepsThatColors() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[33m hello\n");
		String matched = "Alice hello";
		Pattern pattern = Pattern.compile("(\\w+) (\\w+)");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1 $2", map,
				matched, pattern);
		assertEquals("Alice hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals("Alice", pieces.get(0).text);
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
		assertEquals(" hello", text(pieces).substring(5));
		assertEquals(0xFFBBBB00, pieces.get(1).fgArgb);
	}

	@Test
	public void explicitBoldOverridesTheFollowingCapture() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[0m tells you '"
				+ ESC + "[33mhello" + ESC + "[0m\n");
		String matched = "Alice tells you 'hello'";
		Pattern pattern = Pattern.compile("(\\w+) tells you '([^']*)'");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1 \\27[1m$2",
				map, matched, pattern);
		assertEquals("Alice hello", painted.plain);
		assertTrue(painted.plain.indexOf('\u001b') < 0);
		assertTrue(painted.plain.indexOf('\\') < 0);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals("Alice", pieces.get(0).text);
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
		assertFalse(pieces.get(0).bold);
		assertEquals("hello", pieces.get(pieces.size() - 1).text);
		assertTrue(pieces.get(pieces.size() - 1).bold);
		assertEquals(0, pieces.get(pieces.size() - 1).fgArgb);
	}

	@Test
	public void explicitCodeDoesNotKeepThePreviousCaptureColor() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[0m tells you '"
				+ ESC + "[33mhello" + ESC + "[0m\n");
		String matched = "Alice tells you 'hello'";
		Pattern pattern = Pattern.compile("(\\w+) tells you '([^']*)'");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1\\27[1m$2",
				map, matched, pattern);
		assertEquals("Alicehello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alicehello", text(pieces));
		assertEquals("Alice", pieces.get(0).text);
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
		assertEquals("hello", pieces.get(pieces.size() - 1).text);
		assertTrue(pieces.get(pieces.size() - 1).bold);
		assertEquals(0, pieces.get(pieces.size() - 1).fgArgb);
	}

	@Test
	public void resetKeepsTheColorOfTheGapOnTheLine() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[33m hello\n");
		String matched = "Alice hello";
		Pattern pattern = Pattern.compile("(\\w+) (\\w+)");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1\\27[0m $2",
				map, matched, pattern);
		assertEquals("Alice hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals("Alice", pieces.get(0).text);
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
		assertEquals(" ", pieces.get(1).text);
		assertEquals(0xFFBBBB00, pieces.get(1).fgArgb);
		assertEquals("hello", pieces.get(2).text);
		assertEquals(0xFFBBBB00, pieces.get(2).fgArgb);
	}

	@Test
	public void resetReturnsToTheLineColor() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[0m tells you '"
				+ ESC + "[33mhello" + ESC + "[0m\n");
		String matched = "Alice tells you 'hello'";
		Pattern pattern = Pattern.compile("(\\w+) tells you '([^']*)'");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model,
				"\\27[1m$1\\27[0m $2", map, matched, pattern);
		assertEquals("Alice hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals("Alice", pieces.get(0).text);
		assertTrue(pieces.get(0).bold);
		assertEquals(0, pieces.get(0).fgArgb);
		assertEquals("hello", pieces.get(pieces.size() - 1).text);
		assertEquals(0xFFBBBB00, pieces.get(pieces.size() - 1).fgArgb);
		assertFalse(pieces.get(pieces.size() - 1).bold);
	}

	@Test
	public void captureTextIsNotScannedForEscapes() throws Exception {
		StyleLineModel model = line("a\\27[1mb\n");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "a\\27[1mb");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1", map,
				"a\\27[1mb", Pattern.compile("(.*)"));
		assertEquals("a\\27[1mb", painted.plain);
		assertEquals("", painted.colored);
	}

	@Test
	public void doubledBackslashStaysVisible() throws Exception {
		StyleLineModel model = line("hi\n");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "\\\\27[1mhi",
				null, "hi", null);
		assertEquals("\\27[1mhi", painted.plain);
		assertEquals("", painted.colored);
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Bob");
		painted = ChatAnsi.paintTemplate(model, "\\\\hello $1", map, "Bob",
				Pattern.compile("(\\w+)"));
		assertEquals("\\\\hello Bob", painted.plain);
	}

	@Test
	public void laterExplicitCodeKeepsEarlierBold() throws Exception {
		StyleLineModel model = line("hi\n");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model,
				"\\27[1mhello \\27[32mworld", null, "hi", null);
		assertEquals("hello world", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("hello world", text(pieces));
		assertEquals("hello ", pieces.get(0).text);
		assertTrue(pieces.get(0).bold);
		assertEquals(0, pieces.get(0).fgArgb);
		assertEquals("world", pieces.get(pieces.size() - 1).text);
		assertTrue(pieces.get(pieces.size() - 1).bold);
		assertEquals(0xFF55FF55, pieces.get(pieces.size() - 1).fgArgb);
	}

	@Test
	public void resetBetweenLiteralAndCaptureKeepsTheGap() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[33m hello\n");
		String matched = "Alice hello";
		Pattern pattern = Pattern.compile("(\\w+) (\\w+)");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "$1 \\27[0m$2",
				map, matched, pattern);
		assertEquals("Alice hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals(" ", pieces.get(1).text);
		assertEquals(0xFFBBBB00, pieces.get(1).fgArgb);
	}

	@Test
	public void explicitCaptureThenResetKeepsTheNextGap() throws Exception {
		StyleLineModel model = line(ESC + "[32mAlice" + ESC + "[33m hello\n");
		String matched = "Alice hello";
		Pattern pattern = Pattern.compile("(\\w+) (\\w+)");
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Alice");
		map.put("2", "hello");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model,
				"\\27[1m$1\\27[0m $2", map, matched, pattern);
		assertEquals("Alice hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("Alice hello", text(pieces));
		assertEquals("Alice", pieces.get(0).text);
		assertTrue(pieces.get(0).bold);
		assertEquals(0, pieces.get(0).fgArgb);
		assertEquals(" ", pieces.get(1).text);
		assertEquals(0xFFBBBB00, pieces.get(1).fgArgb);
		assertFalse(pieces.get(1).bold);
		assertEquals("hello", pieces.get(2).text);
		assertEquals(0xFFBBBB00, pieces.get(2).fgArgb);
	}

	@Test
	public void literalPrefixOfTheHitDoesNotUseTheAnchor() throws Exception {
		StyleLineModel model = line(ESC + "[32mhello " + ESC + "[31mhellox\n");
		int anchor = model.plain.indexOf("hellox");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "hello", null,
				"hellox", null, anchor);
		assertEquals("hello", painted.plain);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("hello", text(pieces));
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
	}

	@Test
	public void literalBodyStillSlicesTheLine() throws Exception {
		StyleLineModel model = line(ESC + "[32mhello" + ESC + "[0m there\n");
		ChatAnsi.Painted painted = ChatAnsi.paintTemplate(model, "hello", null,
				"hello there", null);
		List<ChatAnsi.Piece> pieces = ChatAnsi.pieces(painted.colored);
		assertEquals("hello", painted.plain);
		assertEquals("hello", text(pieces));
		assertEquals(0xFF00BB00, pieces.get(0).fgArgb);
	}

	private static StyleLineModel line(String bytes) throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl(bytes.getBytes("UTF-8"));
		return StyleLineModel.build(tree.getLines().get(0), SgrRegisters.defaults());
	}

	private static String text(List<ChatAnsi.Piece> pieces) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < pieces.size(); i++) {
			b.append(pieces.get(i).text);
		}
		return b.toString();
	}
}
