package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

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
}
