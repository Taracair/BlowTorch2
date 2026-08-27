package com.resurrection.blowtorch2.lib.responder.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

import java.util.HashMap;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.responder.CaptureSubstitution;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.trigger.TriggerEditorDialog;

/**
 * Send-to-thread action: copy/equals, editor labels, and {@code $text}
 * surviving capture substitution.
 */
public class ChatThreadResponderTest {

	@Test
	public void copyIsIndependent() {
		ChatThreadResponder a = new ChatThreadResponder();
		a.setThreadId("$1");
		a.setTitle("Tell");
		a.setBody("");
		a.setReplyTemplate("tell $1 $text");
		a.setFireType(FIRE_WHEN.WINDOW_OPEN);
		ChatThreadResponder b = a.copy();
		assertEquals(a, b);
		assertNotSame(a, b);
		b.setThreadId("vermin");
		assertFalse(a.equals(b));
		assertEquals("$1", a.getThreadId());
		a.setMine(true);
		assertFalse(a.equals(b));
		b.setMine(true);
		b.setThreadId("$1");
		assertEquals(a, b);
	}

	@Test
	public void editorLabelsUseSendToThreadAndThreadId() {
		ChatThreadResponder r = new ChatThreadResponder();
		r.setThreadId("vermin");
		r.setTitle("VERMIN");
		assertEquals("Send to thread", TriggerEditorDialog.actionTypeLabel(r));
		assertEquals("vermin · VERMIN", TriggerEditorDialog.actionSummary(r));
		r.setTitle("vermin");
		assertEquals("vermin", TriggerEditorDialog.actionSummary(r));
	}

	@Test
	public void replyTemplateLeavesDollarTextAndSubstitutesCaptures() {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("1", "Bob");
		assertEquals("tell Bob $text",
				CaptureSubstitution.apply("tell $1 $text", map));
		assertEquals("c $text", CaptureSubstitution.apply("c $text", map));
		ChatThreadResponder r = new ChatThreadResponder();
		assertEquals("tell Bob $text", r.translate("tell $1 $text", map));
	}

	@Test
	public void doResponseNeverGagsAndSkipsNullContext() {
		ChatThreadResponder r = new ChatThreadResponder();
		r.setThreadId("vermin");
		boolean gag = r.doResponse(null, null, 0, null, null, 0, 0,
				"[ VERMIN ] hello", null, "world", "host", 0, 0, true, null,
				null, null, "t", "UTF-8");
		assertFalse(gag);
	}

	@Test
	public void xmlTagIsChatthread() {
		assertEquals("chatthread",
				com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser.TAG_CHATTHREADRESPONDER);
	}
}
