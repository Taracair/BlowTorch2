package com.resurrection.blowtorch2.lib.responder.speak;

import java.io.IOException;
import java.util.HashMap;
import java.util.ListIterator;

import org.keplerproject.luajava.LuaState;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.util.SpeechEngine;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * TTS template ({@code $1} / {@code $word}) via {@link SpeechEngine} in
 * {@code :stellar}.
 */
public class SpeakResponder extends TriggerResponder implements Parcelable {

	private String message;
	/** Cut off whatever is being said and say this instead. */
	private boolean interrupt;

	/**
	 * Whether this action may say so when the media volume is off.
	 *
	 * <p>On by default, and the same field the sound action carries, so turning
	 * the warning off on one kind of alerting trigger and not the other is not a
	 * thing a player can accidentally do.
	 */
	private boolean warnWhenSilent;

	public SpeakResponder() {
		super(RESPONDER_TYPE.SPEAK);
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
		message = "";
		interrupt = false;
		warnWhenSilent = true;
	}

	public SpeakResponder(RESPONDER_TYPE pType) {
		super(pType);
	}

	public SpeakResponder copy() {
		SpeakResponder tmp = new SpeakResponder();
		tmp.message = this.message;
		tmp.interrupt = this.interrupt;
		tmp.warnWhenSilent = this.warnWhenSilent;
		tmp.setFireType(this.getFireType());
		return tmp;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof SpeakResponder)) {
			return false;
		}
		SpeakResponder test = (SpeakResponder) o;
		if (test.interrupt != this.interrupt) {
			return false;
		}
		if (test.warnWhenSilent != this.warnWhenSilent) {
			return false;
		}
		// String content, not identity. The toast responder next door compares
		// with != and so reports two identical messages as different; do not
		// copy that.
		if (test.message == null ? this.message != null
				: !test.message.equals(this.message)) {
			return false;
		}
		if (test.getFireType() != this.getFireType()) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int h = message == null ? 0 : message.hashCode();
		h = 31 * h + (interrupt ? 1 : 0);
		return 31 * h + (warnWhenSilent ? 1 : 0);
	}

	public SpeakResponder(Parcel in) {
		super(RESPONDER_TYPE.SPEAK);
		readFromParcel(in);
	}

	private void readFromParcel(Parcel in) {
		setMessage(in.readString());
		setInterrupt(in.readInt() != 0);
		setWarnWhenSilent(in.readInt() != 0);
		String fireType = in.readString();
		if (TriggerResponder.FIRE_WINDOW_OPEN.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_OPEN);
		} else if (TriggerResponder.FIRE_WINDOW_CLOSED.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_CLOSED);
		} else if (TriggerResponder.FIRE_NEVER.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		}
	}

	@Override
	public boolean doResponse(Context c, TextTree tree, int lineNumber,
			ListIterator<TextTree.Line> iterator, TextTree.Line line, int start, int end,
			String matched, Object source, String displayname, String host, int port,
			int triggernumber, boolean windowIsOpen, Handler dispatcher,
			HashMap<String, String> captureMap, LuaState L, String name, String encoding) {
		if (windowIsOpen) {
			if (this.getFireType() == FIRE_WHEN.WINDOW_CLOSED
					|| this.getFireType() == FIRE_WHEN.WINDOW_NEVER) {
				return false;
			}
		} else {
			if (this.getFireType() == FIRE_WHEN.WINDOW_OPEN
					|| this.getFireType() == FIRE_WHEN.WINDOW_NEVER) {
				return false;
			}
		}
		String translated = translate(message, captureMap);
		SpeechEngine.get(c).speak(translated, interrupt, warnWhenSilent);
		return false;
	}

	public static Parcelable.Creator<SpeakResponder> CREATOR =
			new Parcelable.Creator<SpeakResponder>() {

		public SpeakResponder createFromParcel(Parcel source) {
			return new SpeakResponder(source);
		}

		public SpeakResponder[] newArray(int size) {
			return new SpeakResponder[size];
		}
	};

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(message);
		out.writeInt(interrupt ? 1 : 0);
		out.writeInt(warnWhenSilent ? 1 : 0);
		out.writeString(this.getFireType().getString());
	}

	public void setMessage(String message) {
		this.message = message == null ? "" : message;
	}

	public String getMessage() {
		return message;
	}

	public void setInterrupt(boolean interrupt) {
		this.interrupt = interrupt;
	}

	public boolean getInterrupt() {
		return interrupt;
	}

	public void setWarnWhenSilent(boolean warnWhenSilent) {
		this.warnWhenSilent = warnWhenSilent;
	}

	public boolean getWarnWhenSilent() {
		return warnWhenSilent;
	}

	@Override
	public void saveResponderToXML(XmlSerializer out)
			throws IllegalArgumentException, IllegalStateException, IOException {
		SpeakResponderParser.saveSpeakResponderToXML(out, this);
	}
}
