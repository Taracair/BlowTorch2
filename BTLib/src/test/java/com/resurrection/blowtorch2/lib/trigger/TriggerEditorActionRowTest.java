package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.window.EditorHelp;

/**
 * Row labels for EXECUTE ACTIONS, and the editor {@code ?} still carrying
 * pattern help plus the CONDITIONS essay that left the canvas.
 */
public class TriggerEditorActionRowTest {

	@Test
	public void ackRowSplitsTypeAndSummary() {
		AckResponder ack = new AckResponder();
		ack.setAckWith("kill $1");
		assertEquals("Ack", TriggerEditorDialog.actionTypeLabel(ack));
		assertEquals("kill $1", TriggerEditorDialog.actionSummary(ack));
	}

	@Test
	public void duplicateEmptyAcksAreEqualSoIndexOfCannotKeyRows() {
		AckResponder first = new AckResponder();
		AckResponder second = new AckResponder();
		java.util.ArrayList<com.resurrection.blowtorch2.lib.responder.TriggerResponder> list =
				new java.util.ArrayList<com.resurrection.blowtorch2.lib.responder.TriggerResponder>();
		list.add(first);
		list.add(second);
		assertEquals(0, list.indexOf(second));
		assertEquals(1, list.lastIndexOf(second));
	}

	@Test
	public void gagHasTypeAndNoSummary() {
		GagAction gag = new GagAction();
		assertEquals("Gag", TriggerEditorDialog.actionTypeLabel(gag));
		assertEquals("", TriggerEditorDialog.actionSummary(gag));
	}

	@Test
	public void setVariableSummarisesNameAndValue() {
		SetVariableResponder sv = new SetVariableResponder();
		sv.setVariableName("fighting");
		sv.setVariableValue("1");
		assertEquals("Set Variable", TriggerEditorDialog.actionTypeLabel(sv));
		assertEquals("fighting=1", TriggerEditorDialog.actionSummary(sv));
	}

	@Test
	public void nullResponderIsBlank() {
		assertEquals("", TriggerEditorDialog.actionTypeLabel(null));
		assertEquals("", TriggerEditorDialog.actionSummary(null));
	}

	@Test
	public void editorHelpMergesPatternAndConditions() {
		assertTrue(TriggerEditorDialog.PATTERN_HELP_TEXT.contains("LITERAL?"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("_cerb"));
	}
}
