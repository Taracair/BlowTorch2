package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.responder.IteratorModifiedException;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.service.TriggerColorState;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * A colour trigger paints text by putting a colour code into the stream, and a
 * colour code in a terminal runs until the next one. These drive the same path
 * the connection does -- parse a chunk, fire the colour trigger on it, dump the
 * result back to bytes -- and say where that code has to stop.
 *
 * <p>Two requirements pull against each other, and the fix has flipped between
 * them twice (121e374b, 7bb2f999):
 *
 * <ul>
 * <li>The rest of a line that arrives in a later TCP packet belongs to the
 * match and must keep the trigger's colour.</li>
 * <li>The next line does not. Leaving the code open painted every line after
 * it until the server sent a colour of its own.</li>
 * </ul>
 */
public class ColorTriggerBleedTest {

	private static final String ESC = "";
	private static final String ENC = "ISO-8859-1";
	/** What the trigger paints with: xterm 256 foreground 45. */
	private static final int TRIGGER_COLOR = 45;
	private static final String TRIGGER_CODE = ESC + "[38;5;" + TRIGGER_COLOR + "m";
	/**
	 * ColorAction skips painting a background at 0, 16, or 231. 16 is "only
	 * the foreground" — the last CSI the action writes is the paint itself.
	 */
	private static final int NO_BACKGROUND = 16;

	private final Pattern chatnet = Pattern.compile("\\[chatnet\\].*");

	/**
	 * One dispatch: bytes in, trigger fired on every line it matches, bytes out.
	 * Mirrors Connection.dispatch -- parse into the working tree, run triggers,
	 * close what they left open, dump, empty.
	 */
	private String dispatch(TextTree working, TriggerColorState state, String chunk)
			throws UnsupportedEncodingException {
		working.setModCount(0);
		working.addBytesImpl(chunk.getBytes(ENC));

		// Oldest line first, as the connection walks them.
		for (int i = working.getLines().size() - 1; i >= 0; i--) {
			TextTree.Line line = working.getLines().get(i);
			String plain = TextTree.deColorLine(line).toString();
			Matcher m = chatnet.matcher(plain);
			while (m.find()) {
				ColorAction action = new ColorAction();
				action.setColor(TRIGGER_COLOR);
				// Connection passes the last matched character's index minus one;
				// ColorAction adds it back. Same arithmetic here.
				action.doResponse(null, working, i, null, line, m.start(), m.end() - 2,
						m.group(), null, "test", "host", 0, 0, false, null, null, null,
						"chatnet", ENC);
			}
		}

		state.closeAtLineEnds(working);
		working.updateMetrics();
		return new String(working.dumpToBytes(false), ENC);
	}

	/** A colour code as the last thing on the line is what closes the colour. */
	private static final Pattern CLOSED = Pattern.compile("\\[[0-9;]*m\n\\z");

	private static void assertTriggerColorPresent(String dumped) {
		assertTrue("no trigger colour in: " + visible(dumped),
				dumped.indexOf(TRIGGER_CODE) >= 0);
	}

	private static String visible(String s) {
		return s.replace(ESC, "<ESC>").replace("\n", "\\n");
	}

	/**
	 * The bug on the screenshot: the chat line is complete, and the lines under
	 * it -- other output, and the echo of what the player typed -- came out in
	 * the trigger's colour.
	 */
	@Test
	public void aFinishedLineDoesNotPaintTheNextOne() throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();

		String first = dispatch(working, state,
				ESC + "[36m[chatnet] Reeds yips, \"Was made to match\"\n");
		assertTriggerColorPresent(first);
		assertTrue("colour left running past the end of the line: " + visible(first),
				CLOSED.matcher(first).find());

		// And the line after it carries no colour of its own, so it is only safe
		// because the one above was closed.
		String second = dispatch(working, state, "Remove helm\n");
		assertEquals("Remove helm\n", second);
	}

	/**
	 * What 7bb2f999 was fixing, and what must keep working: the sentence is cut
	 * by a TCP packet boundary, and its second half is still the chat line.
	 */
	@Test
	public void anUnfinishedLineKeepsTheColourForItsRest() throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();

		String first = dispatch(working, state,
				ESC + "[36m[chatnet] Reeds yips, \"Yes, dsuit's thickness");
		assertTriggerColorPresent(first);
		assertTrue("nothing may close the colour while the line is open: "
				+ visible(first), first.endsWith("thickness"));

		String second = dispatch(working, state, " was increased to 4\"\n");
		// No colour code before the continuation -- it inherits the trigger's.
		assertTrue("the rest of the line must not be recoloured: " + visible(second),
				second.startsWith(" was increased"));
		// ...and the colour still has to stop at the end of that line.
		assertTrue("colour left running past the finished line: " + visible(second),
				CLOSED.matcher(second).find());

		String third = dispatch(working, state, "Remove helm\n");
		assertEquals("Remove helm\n", third);
	}

	/** A line the trigger never touched is passed through unchanged. */
	@Test
	public void anUntouchedLineIsNotRewritten() throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();
		assertEquals("Taracair says, \"asdf\"\n",
				dispatch(working, state, "Taracair says, \"asdf\"\n"));
	}

	/**
	 * The match ends before the line does. That restore was always there; it
	 * must still be the colour the line was running in, not the trigger's.
	 */
	@Test
	public void textAfterTheMatchGoesBackToTheServersColour() throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();

		// The trigger here matches only the bracket, so " tail" follows the match.
		Pattern old = Pattern.compile("\\[chatnet\\]");
		TextTree.Line line;
		working.setModCount(0);
		working.addBytesImpl((ESC + "[36m[chatnet] tail\n").getBytes(ENC));
		line = working.getLines().get(0);
		Matcher m = old.matcher(TextTree.deColorLine(line).toString());
		assertTrue(m.find());
		ColorAction action = new ColorAction();
		action.setColor(TRIGGER_COLOR);
		action.doResponse(null, working, 0, null, line, m.start(), m.end() - 2,
				m.group(), null, "test", "host", 0, 0, false, null, null, null,
				"chatnet", ENC);
		state.closeAtLineEnds(working);
		working.updateMetrics();
		String dumped = new String(working.dumpToBytes(false), ENC);

		assertTrue("the tail must be restored to the line's colour: " + visible(dumped),
				dumped.indexOf(ESC + "[36m tail") > 0 || dumped.indexOf(ESC + "[36m") > 0);
		assertTrue(dumped.endsWith("\n"));
	}

	/** Word trigger from the screenshot: paints {@code says} magenta (xterm 13). */
	private static final int SAYS_COLOR = 13;
	private static final String SAYS_CODE = ESC + "[38;5;" + SAYS_COLOR + "m";
	private final Pattern says = Pattern.compile("says");
	private static final String LILLY =
			"Lilly says, \"Yeah that would be neat\"\n";

	/**
	 * Screenshot 18 Aug 2026: a finished {@code [chatnet]} line is painted
	 * green, then a later {@code Lilly says, "Yeah…"} line has {@code says}
	 * magenta and the quoted rest in the chatnet green. Two colour triggers,
	 * two dispatches, close-at-end as the connection does when the first line
	 * stays in the buffer.
	 */
	@Test
	public void aLaterSaysTriggerDoesNotRestoreTheChatnetColour() throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();

		working.setModCount(0);
		working.addBytesImpl((ESC + "[36m[chatnet] Reeds yips, \"Was made to match\"\n")
				.getBytes(ENC));
		fireColor(working, 0, working.getLines().get(0), chatnet, TRIGGER_COLOR, "chatnet");
		state.closeAtLineEnds(working);
		working.dumpToBytes(false);

		String second = dispatchSays(working, state, LILLY);
		assertSaysPainted(second);
		assertQuoteIsNotChatnetGreen(second, "two ColorActions");
	}

	/**
	 * Same two colour triggers on one line: {@code [chatnet] … says, "…"}.
	 * The second restore must not pick up the first trigger's colour unit.
	 */
	@Test
	public void twoColorTriggersOnOneLineDoNotLeaveTheFirstColourAfterTheSecond()
			throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();
		working.setModCount(0);
		working.addBytesImpl((ESC + "[36m[chatnet] Lilly says, \"Yeah that would be neat\"\n")
				.getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);
		fireColor(working, 0, line, chatnet, TRIGGER_COLOR, "chatnet");
		fireColor(working, 0, line, says, SAYS_COLOR, "says");
		state.closeAtLineEnds(working);
		working.updateMetrics();
		String dumped = new String(working.dumpToBytes(false), ENC);
		assertSaysPainted(dumped);
		assertQuoteIsNotChatnetGreen(dumped, "two ColorActions on one line");
	}

	/**
	 * Same two lines as the screenshot, but the chatnet trigger also gags the
	 * line off the working tree (retarget to an extra-text window). Connection
	 * colours, then gags, then {@code closeAtLineEnds} on what is left — the
	 * coloured line is already gone. {@code lineToWindow} dumps that line into
	 * another tree and re-parses it, which is how a colour code reaches the
	 * stream.
	 */
	@Test
	public void gaggingTheColouredLineDoesNotPaintALaterSaysTrigger() throws Exception {
		// Main window last colour — grey, the "Lilly" on the screenshot.
		TextTree main = new TextTree();
		main.addBytesImpl((ESC + "[37mRemove helm\n").getBytes(ENC));

		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();

		working.setModCount(0);
		working.addBytesImpl((ESC + "[36m[chatnet] Reeds yips, \"Was made to match\"\n")
				.getBytes(ENC));
		TextTree.Line chatnetLine = working.getLines().get(0);
		fireColor(working, 0, chatnetLine, chatnet, TRIGGER_COLOR, "chatnet");
		gagLine(working, 0, chatnetLine);
		// Connection.lineToWindow: dump the taken line, parse it into the extra
		// window's buffer.
		retargetLikeConnection(chatnetLine);
		state.closeAtLineEnds(working);

		// Next dispatch copies the *main* window's bleed, as Connection does.
		working.setBleedColor(main.getBleedColor());
		String second = dispatchSays(working, state, LILLY);
		assertSaysPainted(second);
		assertQuoteIsNotChatnetGreen(second, "gag+retarget");
		String afterSays = second.substring(second.indexOf("says"));
		assertTrue("quote should return to the main window's grey, not the extra "
				+ "window's colour: " + visible(second),
				afterSays.indexOf("[37;49m") >= 0 || afterSays.indexOf("[37m") >= 0);
	}

	private String dispatchSays(TextTree working, TriggerColorState state, String chunk)
			throws UnsupportedEncodingException {
		working.setModCount(0);
		working.addBytesImpl(chunk.getBytes(ENC));
		for (int i = working.getLines().size() - 1; i >= 0; i--) {
			TextTree.Line line = working.getLines().get(i);
			fireColor(working, i, line, says, SAYS_COLOR, "says");
		}
		state.closeAtLineEnds(working);
		working.updateMetrics();
		return new String(working.dumpToBytes(false), ENC);
	}

	private void fireColor(TextTree working, int lineNumber, TextTree.Line line,
			Pattern pattern, int color, String name) {
		Matcher m = pattern.matcher(TextTree.deColorLine(line).toString());
		while (m.find()) {
			ColorAction action = new ColorAction();
			action.setColor(color);
			action.setBackgroundColor(NO_BACKGROUND);
			action.doResponse(null, working, lineNumber, null, line, m.start(),
					m.end() - 2, m.group(), null, "test", "host", 0, 0, false,
					null, null, null, name, ENC);
		}
	}

	/**
	 * GagAction without a retarget: drop the line from the working tree. No
	 * Handler, no Android. The taken line object is still the caller's to dump.
	 */
	private static void gagLine(TextTree working, int lineNumber, TextTree.Line line) {
		GagAction gag = new GagAction();
		try {
			gag.doResponse(null, working, lineNumber, null, line, 0, 0,
					TextTree.deColorLine(line).toString(), null, "test", "host",
					0, 0, false, null, null, null, "chatnet", ENC);
		} catch (IteratorModifiedException expected) {
			// GagAction always throws this after mutating the line list.
		}
	}

	/** {@code Connection.lineToWindow} when the gag's retarget is an extra window. */
	private static void retargetLikeConnection(TextTree.Line line)
			throws UnsupportedEncodingException {
		TextTree tmp = new TextTree();
		tmp.appendLine(line);
		tmp.updateMetrics();
		byte[] dumped = tmp.dumpToBytes(false);
		TextTree extraBuffer = new TextTree();
		extraBuffer.addBytesImpl(dumped);
	}

	private static void assertSaysPainted(String dumped) {
		assertTrue("says was not painted magenta: " + visible(dumped),
				dumped.indexOf(SAYS_CODE) >= 0);
	}

	private static void assertQuoteIsNotChatnetGreen(String dumped, String via) {
		int saysAt = dumped.indexOf("says");
		assertTrue("no says in: " + visible(dumped), saysAt >= 0);
		String afterSays = dumped.substring(saysAt);
		assertTrue(via + " leaked chatnet colour onto the quote after says: "
				+ visible(dumped),
				afterSays.indexOf("38;5;" + TRIGGER_COLOR) < 0);
	}
}
