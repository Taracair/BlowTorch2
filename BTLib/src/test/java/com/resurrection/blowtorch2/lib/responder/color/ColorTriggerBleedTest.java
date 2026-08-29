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
	 * the foreground" ù the last CSI the action writes is the paint itself.
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
		return stripStamps(new String(working.dumpToBytes(false), ENC));
	}

	/** A colour code as the last thing on the line is what closes the colour. */
	private static final Pattern CLOSED = Pattern.compile("\\[[0-9;]*m\n\\z");

	private static void assertTriggerColorPresent(String dumped) {
		assertTrue("no trigger colour in: " + visible(dumped),
				dumped.indexOf(TRIGGER_CODE) >= 0);
	}

	private static String visible(String s) {
		return stripStamps(s).replace(ESC, "<ESC>").replace("\n", "\\n");
	}

	/** Line arrival stamps ride in OSC; they are not part of the colour stream. */
	private static String stripStamps(String dumped) {
		if (dumped == null) {
			return null;
		}
		return dumped.replaceAll("\u001b\\]1337;btstamp=\\d+\u0007", "");
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
	 * Screenshot 29 Aug 2026: after {@code _chatnet} / {@code _vermin} the next
	 * uncoloured line should stay default grey until another trigger. The
	 * working-tree dump of that next dispatch is plain (Connection copies bleed
	 * from a Simple buffer that never parsed CSI), so the leak is the last CSI
	 * of the coloured line, re-parsed by the UI. Walk the screen tree the way
	 * {@code Window.onDraw} does. The leftover CSI is not the trigger paint
	 * (green / light blue) and is not what those following lines should be.
	 */
	@Test
	public void aFinishedColorTriggerLeavesTheNextUncolouredScreenLineGrey()
			throws Exception {
		TextTree chatnetScreen = paintThenPlainOnScreen(
				"[chatnet] Reeds yips, \"Was made to match\"\n",
				chatnet, TRIGGER_COLOR, "chatnet",
				"The acid blood sizzles and pops on your back.\n");
		assertNextLineStayedGrey(chatnetScreen, TRIGGER_COLOR);

		final int lightBlue = 51;
		TextTree verminScreen = paintThenPlainOnScreen(
				"[ VERMIN ] : someone says, \"hi\"\n",
				verminTag, lightBlue, "vermin",
				"The acid blood sizzles and pops on your back.\n");
		assertNextLineStayedGrey(verminScreen, lightBlue);
	}

	/**
	 * Same door, with a CSI already on the trigger line: that code must still
	 * stop at the newline so the next uncoloured line is grey, not that code.
	 */
	@Test
	public void aFinishedColorTriggerClosesALeftoverCsiBeforeTheNextScreenLine()
			throws Exception {
		TextTree screen = paintThenPlainOnScreen(
				ESC + "[36m[chatnet] Reeds yips, \"Was made to match\"\n",
				chatnet, TRIGGER_COLOR, "chatnet",
				"The acid blood sizzles and pops on your back.\n");
		assertNextLineStayedGrey(screen, TRIGGER_COLOR);
		Integer acid = ansiFgAt(screen, "The acid blood");
		assertTrue("acid blood inherited leftover CSI 36: "
				+ visible(new String(screen.dumpToBytes(true), ENC)),
				acid.intValue() != 36);
	}

	/**
	 * Combined SGR {@code [0;36m} resets then paints cyan. A skip that treated
	 * any 0 in the ops list as "already closed" would leave that cyan running.
	 */
	@Test
	public void aResetThenColourCsiStillClosesBeforeTheNextScreenLine()
			throws Exception {
		TextTree screen = paintThenPlainOnScreen(
				ESC + "[0;36m[chatnet] Reeds yips, \"Was made to match\"\n",
				chatnet, TRIGGER_COLOR, "chatnet",
				"The acid blood sizzles and pops on your back.\n");
		assertNextLineStayedGrey(screen, TRIGGER_COLOR);
		Integer acid = ansiFgAt(screen, "The acid blood");
		assertTrue("acid blood inherited CSI 0;36: "
				+ visible(new String(screen.dumpToBytes(true), ENC)),
				acid.intValue() != 36);
	}

	/**
	 * xterm index 0 is black, not SGR 0. A skip that looked for any 0 in the
	 * ops would treat {@code 38;5;0} as already closed.
	 */
	@Test
	public void anXtermZeroPaintStillClosesBeforeTheNextScreenLine()
			throws Exception {
		TextTree screen = paintThenPlainOnScreen(
				"[chatnet] Reeds yips, \"Was made to match\"\n",
				chatnet, 0, "chatnet",
				"The acid blood sizzles and pops on your back.\n");
		assertNextLineStayedGrey(screen, 0);
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
	 * green, then a later {@code Lilly says, "Yeahù"} line has {@code says}
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
	 * Same two colour triggers on one line: {@code [chatnet] ù says, "ù"}.
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
	 * colours, then gags, then {@code closeAtLineEnds} on what is left ù the
	 * coloured line is already gone. {@code lineToWindow} dumps that line into
	 * another tree and re-parses it, which is how a colour code reaches the
	 * stream.
	 */
	@Test
	public void gaggingTheColouredLineDoesNotPaintALaterSaysTrigger() throws Exception {
		// Main window last colour ù grey, the "Lilly" on the screenshot.
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

	/**
	 * Colour the first chunk, dump it the way the UI re-parses a dispatch, then
	 * append an uncoloured line. Returns the screen tree.
	 */
	private TextTree paintThenPlainOnScreen(String coloured, Pattern pattern,
			int color, String name, String plain) throws Exception {
		TextTree working = new TextTree();
		TextTree screen = new TextTree();
		TriggerColorState state = new TriggerColorState();
		working.setModCount(0);
		working.addBytesImpl(coloured.getBytes(ENC));
		fireColor(working, 0, working.getLines().get(0), pattern, color, name);
		state.closeAtLineEnds(working);
		screen.addBytesImpl(working.dumpToBytes(false));
		working.setModCount(0);
		working.addBytesImpl(plain.getBytes(ENC));
		state.closeAtLineEnds(working);
		screen.addBytesImpl(working.dumpToBytes(false));
		return screen;
	}

	private static void assertNextLineStayedGrey(TextTree screen, int triggerColor)
			throws Exception {
		Integer acid = ansiFgAt(screen, "The acid blood");
		assertTrue("acid blood was not on the screen tree", acid != null);
		String dumped = visible(new String(screen.dumpToBytes(true), ENC));
		assertTrue("acid blood inherited the trigger colour (xterm " + triggerColor
				+ ", fg " + acid + "): " + dumped,
				acid.intValue() != triggerColor);
		assertTrue("acid blood was not default grey (fg " + acid + "): " + dumped,
				acid.intValue() == 37);
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
		return stripStamps(new String(working.dumpToBytes(false), ENC));
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

	private static void fireReplace(TextTree working, int lineNumber,
			TextTree.Line line, Pattern pattern, String with) throws Exception {
		Matcher m = pattern.matcher(TextTree.deColorLine(line).toString());
		while (m.find()) {
			com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder action =
					new com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder();
			action.setWith(with);
			action.doResponse(null, working, lineNumber, null, line, m.start(),
					m.end() - 2, m.group(), null, "test", "host", 0, 0, false,
					null, null, null, "vermin", ENC);
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

	/**
	 * Screenshot 19 Aug 2026: trigger paints the word {@code opens} yellow.
	 * The first packet ends on that word; the rest of the sentence and the
	 * next line arrive together. Connection copies the main window's bleed
	 * onto the working tree for the second dispatch, so a restore that asks
	 * this line what colour the server was in answers yellow: the trigger's
	 * colour, now sitting on the window. The existing tests never fed a dump
	 * into a second tree, so they could not see that.
	 *
	 * <p>On the phone: {@code Taracair opens the door to the south.} is yellow
	 * after the word, and {@code You head through the door to the south.} is
	 * yellow too.
	 */
	@Test
	public void anOpensWordSplitAcrossPacketsDoesNotPaintTheNextLineOnScreen()
			throws Exception {
		final int yellow = 11;
		final Pattern opens = Pattern.compile("opens");
		TextTree working = new TextTree();
		TextTree screen = new TextTree();
		TriggerColorState state = new TriggerColorState();

		working.setModCount(0);
		working.addBytesImpl("Taracair opens".getBytes(ENC));
		fireColor(working, 0, working.getLines().get(0), opens, yellow, "opens");
		state.closeAtLineEnds(working);
		screen.addBytesImpl(working.dumpToBytes(false));

		working.setBleedColor(screen.getBleedColor());
		working.setModCount(0);
		working.addBytesImpl((" the door to the south.\n"
				+ "You head through the door to the south.\n").getBytes(ENC));
		for (int i = working.getLines().size() - 1; i >= 0; i--) {
			fireColor(working, i, working.getLines().get(i), opens, yellow, "opens");
		}
		state.closeAtLineEnds(working);
		screen.addBytesImpl(working.dumpToBytes(false));

		Integer youHead = xtermFgAt(screen, "You head");
		assertTrue("You head was not on the screen tree", youHead != null);
		assertTrue("You head inherited the opens trigger colour (xterm "
				+ youHead + "): " + visible(new String(screen.dumpToBytes(true), ENC)),
				youHead.intValue() != yellow);
	}

	/**
	 * Screenshot 19-20 Aug 2026: the whole sentence arrived in one packet.
	 * Yellow on {@code opens} must not run through "the door to the south."
	 */
	@Test
	public void anOpensWordOnAFinishedLineDoesNotPaintTheRestOfTheSentence()
			throws Exception {
		final int yellow = 11;
		final Pattern opens = Pattern.compile("opens");
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();
		working.setModCount(0);
		working.addBytesImpl("Taracair opens the door to the south.\n".getBytes(ENC));
		fireColor(working, 0, working.getLines().get(0), opens, yellow, "opens");
		state.closeAtLineEnds(working);

		Integer atOpens = xtermFgAt(working, "opens");
		assertTrue("opens was not painted yellow: " + visible(
				new String(working.dumpToBytes(true), ENC)),
				atOpens != null && atOpens.intValue() == yellow);
		Integer atDoor = xtermFgAt(working, "the door");
		assertTrue("the rest of the sentence stayed the opens colour (xterm "
				+ atDoor + "): " + visible(new String(working.dumpToBytes(true), ENC)),
				atDoor == null || atDoor.intValue() != yellow);
	}

	private static final int VERMIN_MUD_BG = 46;
	private static final int VERMIN_GREEN = 2;
	private final Pattern verminTag = Pattern.compile("\\[ VERMIN \\]");
	private static final String VERMIN_LINE =
			"[ VERMIN ] : Taracair says, \"test\"\n";

	/**
	 * Screenshot 26 Aug 2026: a live world paints a channel prefix with a
	 * background, then the player colours {@code [ VERMIN ]} green with
	 * "foreground only" (background 0/16/231). The paint must not keep the
	 * MUD's background CSI open across the matched span - that is the neon
	 * block with dark text.
	 */
	@Test
	public void aForegroundOnlyColorDoesNotKeepTheMudsBackgroundOnTheMatch()
			throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();
		working.setModCount(0);
		working.addBytesImpl((ESC + "[48;5;" + VERMIN_MUD_BG + "m" + VERMIN_LINE)
				.getBytes(ENC));
		fireColor(working, 0, working.getLines().get(0), verminTag, VERMIN_GREEN,
				"vermin");
		state.closeAtLineEnds(working);

		String dumped = visible(new String(working.dumpToBytes(true), ENC));
		Integer fg = xtermFgAt(working, "VERMIN");
		assertTrue("VERMIN was not painted green: " + dumped,
				fg != null && fg.intValue() == VERMIN_GREEN);
		Integer bg = xtermBgAt(working, "VERMIN");
		assertTrue("VERMIN kept the MUD background (xterm " + bg + "): " + dumped,
				bg == null || bg.intValue() != VERMIN_MUD_BG);
		Integer atSays = xtermFgAt(working, "says");
		assertTrue("the rest of the line stayed the trigger green (xterm "
				+ atSays + "): " + dumped,
				atSays == null || atSays.intValue() != VERMIN_GREEN);
	}

	/**
	 * Same trigger as the screenshot, with both actions: colour the tag green
	 * (foreground only) and replace {@code [ VERMIN ]} with {@code VERMIN:}.
	 * Worked example: {@code [ VERMIN ] : Taracair says, "test"} becomes green
	 * {@code VERMIN:} text without a neon block.
	 */
	@Test
	public void verminColorThenReplaceIsGreenWithoutTheMudBackground()
			throws Exception {
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();
		working.setModCount(0);
		working.addBytesImpl((ESC + "[48;5;" + VERMIN_MUD_BG + "m" + VERMIN_LINE)
				.getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);
		fireColor(working, 0, line, verminTag, VERMIN_GREEN, "vermin");
		fireReplace(working, 0, line, verminTag, "VERMIN:");
		state.closeAtLineEnds(working);

		String dumped = visible(new String(working.dumpToBytes(true), ENC));
		String plain = TextTree.deColorLine(working.getLines().get(0)).toString();
		assertTrue("replacement missing: " + plain, plain.startsWith("VERMIN:"));
		Integer fg = xtermFgAt(working, "VERMIN:");
		assertTrue("VERMIN: was not painted green: " + dumped,
				fg != null && fg.intValue() == VERMIN_GREEN);
		Integer bg = xtermBgAt(working, "VERMIN:");
		assertTrue("VERMIN: kept the MUD background (xterm " + bg + "): " + dumped,
				bg == null || bg.intValue() != VERMIN_MUD_BG);
		Integer atSays = xtermFgAt(working, "says");
		assertTrue("the rest of the line stayed the trigger green (xterm "
				+ atSays + "): " + dumped,
				atSays == null || atSays.intValue() != VERMIN_GREEN);
	}

	/**
	 * {@code 48;5;n} ù n is the colour index, not an SGR. Index 1 (bold) and
	 * 38 (another 38-intro) must not be copied into the restore list.
	 */
	@Test
	public void foregroundOpsSkipsXtermBackgroundIndex() {
		TextTree tree = new TextTree();
		java.util.List<Integer> fromBoldIndex = ColorAction.foregroundOps(
				tree.makeColor(java.util.Arrays.asList(
						Integer.valueOf(48), Integer.valueOf(5), Integer.valueOf(1))));
		assertTrue("xterm bg index 1 leaked as bold: " + fromBoldIndex,
				fromBoldIndex.isEmpty());
		java.util.List<Integer> fromFgIntroIndex = ColorAction.foregroundOps(
				tree.makeColor(java.util.Arrays.asList(
						Integer.valueOf(48), Integer.valueOf(5), Integer.valueOf(38))));
		assertTrue("xterm bg index 38 leaked as a 38-intro: " + fromFgIntroIndex,
				fromFgIntroIndex.isEmpty());
	}

	/**
	 * Painting a background on purpose still has to write that background.
	 * The foreground-only path must not steal this.
	 */
	@Test
	public void anExplicitBackgroundPaintStillAppliesToTheMatch() throws Exception {
		final int paintedBg = 20;
		TextTree working = new TextTree();
		TriggerColorState state = new TriggerColorState();
		working.setModCount(0);
		working.addBytesImpl("Lilly says, \"hi\"\n".getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);
		Matcher m = says.matcher(TextTree.deColorLine(line).toString());
		assertTrue(m.find());
		ColorAction action = new ColorAction();
		action.setColor(SAYS_COLOR);
		action.setBackgroundColor(paintedBg);
		action.doResponse(null, working, 0, null, line, m.start(), m.end() - 2,
				m.group(), null, "test", "host", 0, 0, false, null, null, null,
				"says", ENC);
		state.closeAtLineEnds(working);
		Integer bg = xtermBgAt(working, "says");
		assertTrue("explicit background was not painted: "
				+ visible(new String(working.dumpToBytes(true), ENC)),
				bg != null && bg.intValue() == paintedBg);
	}

	/**
	 * xterm-256 foreground at the first character of {@code needle}, or null
	 * when the needle is missing. 37 is the parser's default when nothing has
	 * named a foreground yet.
	 */
	private static Integer xtermFgAt(TextTree tree, String needle) {
		int fg = 37;
		for (int i = tree.getLines().size() - 1; i >= 0; i--) {
			String plain = TextTree.deColorLine(tree.getLines().get(i)).toString();
			int at = plain.indexOf(needle);
			int col = 0;
			int fgHere = fg;
			for (TextTree.Unit u : tree.getLines().get(i).getData()) {
				if (u instanceof TextTree.Color) {
					fgHere = xtermFgFrom(((TextTree.Color) u).getOperations(), fgHere);
				} else if (u instanceof TextTree.Text) {
					String s = ((TextTree.Text) u).getString();
					if (s == null) {
						continue;
					}
					if (at >= 0 && col <= at && at < col + s.length()) {
						return Integer.valueOf(fgHere);
					}
					col += s.length();
				}
			}
			fg = fgHere;
		}
		return null;
	}

	private static int xtermFgFrom(java.util.List<Integer> ops, int current) {
		return ansiFgFrom(ops, current);
	}

	/**
	 * Foreground at {@code needle}, carrying ANSI 30-37 / 90-97 across lines
	 * the way the draw loop does. {@code xtermFgAt} only saw {@code 38;5;n},
	 * so it could not see the cyan/magenta the MUD actually sent.
	 */
	private static Integer ansiFgAt(TextTree tree, String needle) {
		int fg = 37;
		for (int i = tree.getLines().size() - 1; i >= 0; i--) {
			String plain = TextTree.deColorLine(tree.getLines().get(i)).toString();
			int at = plain.indexOf(needle);
			int col = 0;
			int fgHere = fg;
			for (TextTree.Unit u : tree.getLines().get(i).getData()) {
				if (u instanceof TextTree.Color) {
					fgHere = ansiFgFrom(((TextTree.Color) u).getOperations(), fgHere);
				} else if (u instanceof TextTree.Text) {
					String s = ((TextTree.Text) u).getString();
					if (s == null) {
						continue;
					}
					if (at >= 0 && col <= at && at < col + s.length()) {
						return Integer.valueOf(fgHere);
					}
					col += s.length();
				}
			}
			fg = fgHere;
		}
		return null;
	}

	private static int ansiFgFrom(java.util.List<Integer> ops, int current) {
		if (ops == null) {
			return current;
		}
		int fg = current;
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 38 && i + 2 < ops.size() && ops.get(i + 1).intValue() == 5) {
				fg = ops.get(i + 2).intValue();
				i += 2;
			} else if (op == 0 || op == 39) {
				fg = 37;
			} else if (op >= 30 && op <= 37) {
				fg = op;
			} else if (op >= 90 && op <= 97) {
				fg = op;
			} else if (op == 48) {
				if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 5) {
					i += 2;
				} else if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 2) {
					i += 4;
				}
			}
		}
		return fg;
	}

	/**
	 * xterm-256 (or ANSI 40-47) background at the first character of
	 * {@code needle}, or null when the needle is missing or the background has
	 * been returned to default (49 / reset).
	 */
	private static Integer xtermBgAt(TextTree tree, String needle) {
		Integer bg = null;
		for (int i = tree.getLines().size() - 1; i >= 0; i--) {
			String plain = TextTree.deColorLine(tree.getLines().get(i)).toString();
			int at = plain.indexOf(needle);
			int col = 0;
			Integer bgHere = bg;
			for (TextTree.Unit u : tree.getLines().get(i).getData()) {
				if (u instanceof TextTree.Color) {
					bgHere = xtermBgFrom(((TextTree.Color) u).getOperations(), bgHere);
				} else if (u instanceof TextTree.Text) {
					String s = ((TextTree.Text) u).getString();
					if (s == null) {
						continue;
					}
					if (at >= 0 && col <= at && at < col + s.length()) {
						return bgHere;
					}
					col += s.length();
				}
			}
			bg = bgHere;
		}
		return null;
	}

	private static Integer xtermBgFrom(java.util.List<Integer> ops, Integer current) {
		if (ops == null) {
			return current;
		}
		Integer bg = current;
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 48 && i + 2 < ops.size() && ops.get(i + 1).intValue() == 5) {
				bg = Integer.valueOf(ops.get(i + 2).intValue());
				i += 2;
			} else if (op == 0 || op == 49) {
				bg = null;
			} else if (op >= 40 && op <= 47) {
				bg = Integer.valueOf(op);
			}
		}
		return bg;
	}
}
