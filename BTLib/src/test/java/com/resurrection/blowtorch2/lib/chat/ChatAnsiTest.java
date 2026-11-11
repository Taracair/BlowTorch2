package com.resurrection.blowtorch2.lib.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

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

	private static String text(List<ChatAnsi.Piece> pieces) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < pieces.size(); i++) {
			b.append(pieces.get(i).text);
		}
		return b.toString();
	}
}
