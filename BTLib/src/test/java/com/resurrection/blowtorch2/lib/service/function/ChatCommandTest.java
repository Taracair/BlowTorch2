package com.resurrection.blowtorch2.lib.service.function;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.resurrection.blowtorch2.lib.window.ChatPanelController;

public class ChatCommandTest {

	@Test
	public void bareAndOpenAreOpen() {
		assertEquals(ChatCommand.ACTION_OPEN, ChatCommand.parse("").action);
		assertEquals(ChatCommand.ACTION_OPEN, ChatCommand.parse("   ").action);
		assertEquals(ChatCommand.ACTION_OPEN, ChatCommand.parse("open").action);
		assertEquals(ChatCommand.ACTION_OPEN, ChatCommand.parse("Open").action);
		assertEquals(ChatCommand.ACTION_OPEN, ChatCommand.parse("toggle").action);
		assertNull(ChatCommand.parse("open").threadId);
	}

	@Test
	public void closeAndHideAreClose() {
		assertEquals(ChatCommand.ACTION_CLOSE, ChatCommand.parse("close").action);
		assertEquals(ChatCommand.ACTION_CLOSE, ChatCommand.parse("HIDE").action);
		assertNull(ChatCommand.parse("close").threadId);
	}

	@Test
	public void helpAndQuestionAreHelp() {
		assertEquals(ChatCommand.ACTION_HELP, ChatCommand.parse("help").action);
		assertEquals(ChatCommand.ACTION_HELP, ChatCommand.parse("?").action);
	}

	@Test
	public void otherArgIsThreadId() {
		ChatCommand.Parse p = ChatCommand.parse("ooc");
		assertEquals(ChatCommand.ACTION_THREAD, p.action);
		assertEquals("ooc", p.threadId);
		assertEquals("Bob the baker", ChatCommand.parse("Bob the baker").threadId);
	}

	@Test
	public void fillReplyReplacesTextOnly() {
		assertEquals("tell Bob hi", ChatPanelController.fillReply("tell Bob $text", "hi"));
		assertEquals("c look", ChatPanelController.fillReply("c $text", "look"));
		assertEquals("tell $name hi", ChatPanelController.fillReply("tell $name $text", "hi"));
		assertEquals("", ChatPanelController.fillReply(null, "hi"));
		assertEquals("c ", ChatPanelController.fillReply("c $text", ""));
	}

	@Test
	public void fillReplyFallsBackToDollarOneWhenNoTextPlaceholder() {
		assertEquals("C hello", ChatPanelController.fillReply("C $1", "hello"));
		assertEquals("tell $1 hi", ChatPanelController.fillReply("tell $1 $text", "hi"));
	}

	@Test
	public void replyLooksUnfilledDetectsPlaceholders() {
		assertTrue(ChatPanelController.replyLooksUnfilled("tell $1 hi"));
		assertFalse(ChatPanelController.replyLooksUnfilled("tell Bob hi"));
		assertFalse(ChatPanelController.replyLooksUnfilled("C hello"));
		assertFalse(ChatPanelController.replyLooksUnfilled("tell $name hi"));
	}

	@Test
	public void captureFormTellDollarOneTextIsNotReadyToSend() {
		assertFalse(ChatPanelController.replyTemplateReadyToSend("tell $1 $text"));
		assertTrue(ChatPanelController.replyTemplateReadyToSend("tell Bob $text"));
		assertTrue(ChatPanelController.replyTemplateReadyToSend("c $text"));
		assertTrue(ChatPanelController.replyTemplateReadyToSend("C $1"));
		assertFalse(ChatPanelController.replyTemplateReadyToSend(""));
		assertFalse(ChatPanelController.replyTemplateReadyToSend(null));
	}
}
