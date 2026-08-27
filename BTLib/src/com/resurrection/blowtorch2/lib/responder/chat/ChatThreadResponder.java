package com.resurrection.blowtorch2.lib.responder.chat;

import java.io.IOException;
import java.util.HashMap;
import java.util.ListIterator;

import org.keplerproject.luajava.LuaState;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import com.resurrection.blowtorch2.lib.chat.ChatStore;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * Copy a matching MUD line into a chat thread. The line stays in the game
 * window — this action never gags.
 *
 * <p>{@code threadId} is the conversation key after {@code $1} substitution
 * (literal {@code vermin}, or {@code $1} for whoever sent a tell).
 * {@code title} defaults to that id. {@code body} defaults to the matched line.
 *
 * <p>{@code replyTemplate} is stored on the thread the first time it is seen.
 * Trigger captures ({@code $1}, {@code $2}, …) are substituted then.
 * {@code $text} is <em>not</em> a trigger capture: it is a placeholder for the
 * reply box and is left as written. Example: {@code tell $1 $text} becomes
 * {@code tell Bob $text} when {@code $1} was Bob.
 */
public class ChatThreadResponder extends TriggerResponder implements Parcelable {

	private String threadId;
	private String title;
	private String body;
	private String replyTemplate;

	public ChatThreadResponder() {
		super(RESPONDER_TYPE.CHAT_THREAD);
		threadId = "";
		title = "";
		body = "";
		replyTemplate = "";
		setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	public ChatThreadResponder copy() {
		ChatThreadResponder tmp = new ChatThreadResponder();
		tmp.threadId = this.threadId;
		tmp.title = this.title;
		tmp.body = this.body;
		tmp.replyTemplate = this.replyTemplate;
		tmp.setFireType(this.getFireType());
		return tmp;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof ChatThreadResponder)) {
			return false;
		}
		ChatThreadResponder test = (ChatThreadResponder) o;
		if (!eq(test.threadId, this.threadId)) {
			return false;
		}
		if (!eq(test.title, this.title)) {
			return false;
		}
		if (!eq(test.body, this.body)) {
			return false;
		}
		if (!eq(test.replyTemplate, this.replyTemplate)) {
			return false;
		}
		return test.getFireType() == this.getFireType();
	}

	@Override
	public int hashCode() {
		int h = threadId == null ? 0 : threadId.hashCode();
		h = 31 * h + (title == null ? 0 : title.hashCode());
		h = 31 * h + (body == null ? 0 : body.hashCode());
		h = 31 * h + (replyTemplate == null ? 0 : replyTemplate.hashCode());
		return h;
	}

	@Override
	public boolean doResponse(Context c, TextTree tree, int lineNumber,
			ListIterator<TextTree.Line> iterator, TextTree.Line line, int start, int end,
			String matched, Object source, String displayname, String host, int port,
			int triggernumber, boolean windowIsOpen, Handler dispatcher,
			HashMap<String, String> captureMap, LuaState L, String name, String encoding) {
		if (windowIsOpen) {
			if (getFireType() == FIRE_WHEN.WINDOW_CLOSED || getFireType() == FIRE_WHEN.WINDOW_NEVER) {
				return false;
			}
		} else {
			if (getFireType() == FIRE_WHEN.WINDOW_OPEN || getFireType() == FIRE_WHEN.WINDOW_NEVER) {
				return false;
			}
		}
		if (c == null) {
			return false;
		}
		try {
			String tid = translate(threadId, captureMap);
			if (tid == null) {
				tid = "";
			}
			if (tid.length() == 0) {
				return false;
			}
			String resolvedTitle = title == null || title.length() == 0
					? tid
					: translate(title, captureMap);
			String resolvedBody;
			if (body == null || body.length() == 0) {
				resolvedBody = matched == null ? "" : matched;
			} else {
				resolvedBody = translate(body, captureMap);
			}
			ChatStore store = ChatStore.forWorld(c, displayname);
			String tmpl = replyTemplate == null ? "" : replyTemplate;
			String seed = "";
			if (tmpl.length() > 0) {
				// $n substitutes; $text is not $ + digits, so it is left alone.
				seed = translate(tmpl, captureMap);
			}
			store.append(tid, resolvedTitle, resolvedBody, seed);
		} catch (RuntimeException e) {
			BlowTorchLogger.logThrowable("ChatThreadResponder", e);
		} catch (Exception e) {
			BlowTorchLogger.logThrowable("ChatThreadResponder", e);
		}
		return false;
	}

	public ChatThreadResponder(Parcel in) {
		super(RESPONDER_TYPE.CHAT_THREAD);
		readFromParcel(in);
	}

	public static final Parcelable.Creator<ChatThreadResponder> CREATOR =
			new Parcelable.Creator<ChatThreadResponder>() {
				public ChatThreadResponder createFromParcel(Parcel source) {
					return new ChatThreadResponder(source);
				}

				public ChatThreadResponder[] newArray(int size) {
					return new ChatThreadResponder[size];
				}
			};

	public int describeContents() {
		return 0;
	}

	public void readFromParcel(Parcel in) {
		setThreadId(in.readString());
		setTitle(in.readString());
		setBody(in.readString());
		setReplyTemplate(in.readString());
		String fireType = in.readString();
		if (FIRE_WINDOW_OPEN.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_OPEN);
		} else if (FIRE_WINDOW_CLOSED.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_CLOSED);
		} else if (FIRE_ALWAYS.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		} else if (FIRE_NEVER.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		}
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(threadId);
		out.writeString(title);
		out.writeString(body);
		out.writeString(replyTemplate);
		out.writeString(getFireType().getString());
	}

	public String getThreadId() {
		return threadId;
	}

	public void setThreadId(String threadId) {
		this.threadId = threadId != null ? threadId : "";
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title != null ? title : "";
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body != null ? body : "";
	}

	public String getReplyTemplate() {
		return replyTemplate;
	}

	public void setReplyTemplate(String replyTemplate) {
		this.replyTemplate = replyTemplate != null ? replyTemplate : "";
	}

	@Override
	public void saveResponderToXML(XmlSerializer out)
			throws IllegalArgumentException, IllegalStateException, IOException {
		ChatThreadResponderParser.saveResponderToXML(out, this);
	}

	private static boolean eq(String a, String b) {
		if (a == null) {
			return b == null;
		}
		return a.equals(b);
	}
}
