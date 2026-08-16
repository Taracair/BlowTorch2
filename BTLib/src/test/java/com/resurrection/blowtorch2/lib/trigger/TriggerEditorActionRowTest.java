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
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("combat_mode"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("OPEN AND CLOSED"));
	}

	@Test
	public void confineFireWhenCheckBoxesToleratesNull() {
		TriggerEditorDialog.confineFireWhenCheckBoxes(null, null);
	}

	@Test
	public void actionRowXmlCapsCheckBoxAndPadsTheListBeforeNewAction() throws Exception {
		java.io.File layouts = layoutDir();
		String row = read(new java.io.File(layouts, "editor_action_row.xml"));
		assertTrue("CheckBox must drop the 40dp Material control ripple",
				row.contains("android:background=\"@null\""));
		assertTrue("each fire-when box needs its own clip wrapper",
				row.contains("android:clipChildren=\"true\""));
		assertTrue("height cap, not wrap_content + minHeight (that still grows)",
				row.contains("android:layout_height=\"28dip\""));

		String trigger = read(new java.io.File(layouts, "trigger_editor_dialog.xml"));
		assertTrue(actionListPadsBeforeNewAction(trigger, "trigger_action_list"));
		String timer = read(new java.io.File(layouts, "timer_editor_dialog.xml"));
		assertTrue(actionListPadsBeforeNewAction(timer, "timer_action_list"));
	}

	private static boolean actionListPadsBeforeNewAction(String xml, String listId) {
		int list = xml.indexOf("android:id=\"@+id/" + listId + "\"");
		int button = xml.indexOf("android:text=\"New Action\"");
		if (list < 0 || button < 0 || button < list) {
			return false;
		}
		return xml.substring(list, button).contains("android:paddingBottom=\"12dip\"");
	}

	private static java.io.File layoutDir() {
		java.io.File[] candidates = new java.io.File[] {
				new java.io.File("res/layout"),
				new java.io.File("BTLib/res/layout"),
		};
		for (java.io.File dir : candidates) {
			if (new java.io.File(dir, "editor_action_row.xml").isFile()) {
				return dir;
			}
		}
		throw new AssertionError("editor_action_row.xml not found from "
				+ new java.io.File(".").getAbsolutePath());
	}

	private static String read(java.io.File file) throws java.io.IOException {
		return new String(java.nio.file.Files.readAllBytes(file.toPath()),
				java.nio.charset.StandardCharsets.UTF_8);
	}
}
