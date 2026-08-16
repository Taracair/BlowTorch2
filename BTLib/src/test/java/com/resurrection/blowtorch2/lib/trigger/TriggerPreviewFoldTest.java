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
}
