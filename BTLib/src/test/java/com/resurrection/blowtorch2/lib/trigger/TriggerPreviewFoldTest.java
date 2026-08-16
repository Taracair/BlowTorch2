package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TriggerPreviewFoldTest {

	@Test
	public void sixLinesStayOpen() {
		assertEquals(6, TriggerEditorDialog.countPreviewLines("a\nb\nc\nd\ne\nf"));
		assertFalse(TriggerEditorDialog.shouldCollapsePreview("a\nb\nc\nd\ne\nf"));
	}

	@Test
	public void sevenLinesFold() {
		assertEquals(7, TriggerEditorDialog.countPreviewLines("a\nb\nc\nd\ne\nf\ng"));
		assertTrue(TriggerEditorDialog.shouldCollapsePreview("a\nb\nc\nd\ne\nf\ng"));
	}

	@Test
	public void emptyIsNotFolded() {
		assertEquals(0, TriggerEditorDialog.countPreviewLines(""));
		assertFalse(TriggerEditorDialog.shouldCollapsePreview(""));
		assertFalse(TriggerEditorDialog.shouldCollapsePreview(null));
	}

	@Test
	public void longOneLineRegexFoldsEvenWithoutNewlines() {
		StringBuilder oneLine = new StringBuilder();
		for (int i = 0; i < TriggerEditorDialog.PREVIEW_COLLAPSE_AFTER_CHARS + 1; i++) {
			oneLine.append('x');
		}
		assertEquals(1, TriggerEditorDialog.countPreviewLines(oneLine));
		assertTrue(TriggerEditorDialog.shouldCollapsePreview(oneLine));
	}

	@Test
	public void bloodtimerShapedPreviewFolds() {
		String preview = "Trigger «_bloodtimer» watches server output for:\n"
				+ "«(Blood pulses weakly from Taracair's wounds.|Blood runs freely "
				+ "from Taracair's open wounds.|Blood spurts from one of Taracair's "
				+ "gaping wounds.)»\n(mode: regular expression)\n"
				+ "Compiles. 3 capture group(s): $1..$3 in the responses.";
		assertTrue(TriggerEditorDialog.shouldCollapsePreview(preview));
	}
}
