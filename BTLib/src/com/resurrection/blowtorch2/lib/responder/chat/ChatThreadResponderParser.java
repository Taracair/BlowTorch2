package com.resurrection.blowtorch2.lib.responder.chat;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public final class ChatThreadResponderParser {

	static final String ATTR_THREAD_ID = "threadId";
	static final String ATTR_TITLE = "title";
	static final String ATTR_BODY = "body";
	static final String ATTR_REPLY_TEMPLATE = "replyTemplate";
	static final String ATTR_MINE = "mine";

	private ChatThreadResponderParser() {
	}

	public static void registerListeners(Element root, Object obj, TimerData currentTimer,
			TriggerData currentTrigger) {
		Element el = root.getChild(BasePluginParser.TAG_CHATTHREADRESPONDER);
		el.setStartElementListener(
				new ChatThreadElementListener(obj, currentTrigger, currentTimer));
	}

	public static void saveResponderToXML(XmlSerializer out, ChatThreadResponder r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_CHATTHREADRESPONDER);
		out.attribute("", ATTR_THREAD_ID, r.getThreadId());
		out.attribute("", ATTR_TITLE, r.getTitle());
		out.attribute("", ATTR_BODY, r.getBody());
		out.attribute("", ATTR_REPLY_TEMPLATE, r.getReplyTemplate());
		if (r.isMine()) {
			out.attribute("", ATTR_MINE, "true");
		}
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("", BasePluginParser.TAG_CHATTHREADRESPONDER);
	}
}
