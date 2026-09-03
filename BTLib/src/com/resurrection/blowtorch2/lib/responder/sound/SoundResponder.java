package com.resurrection.blowtorch2.lib.responder.sound;

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
import com.resurrection.blowtorch2.lib.util.TriggerSounds;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * Per-trigger sound ({@code bundled:key} or a file path). Runs in
 * {@code :stellar}. A missing file is logged, not silent. {@code .dobell} is
 * one profile-wide reaction; this is not.
 */
public class SoundResponder extends TriggerResponder implements Parcelable {

	/** Shortest gap between two firings, in milliseconds. */
	public static final int DEFAULT_MIN_GAP_MS = 250;

	/** Loudest this responder plays, as a percentage. */
	public static final int DEFAULT_VOLUME_PERCENT = 100;

	private String soundPath;
	/**
	 * Shortest gap between two of this responder's sounds.
	 *
	 * <p>Per responder rather than global: a heartbeat trigger that fires every
	 * line wants a long gap, while a tell wants none. Zero turns it off.
	 */
	private int minGapMs;

	/** 0..100. */
	private int volumePercent;

	/**
	 * Whether this action may say so when the volume is off.
	 *
	 * <p>On by default: the failure it reports has no other symptom, and a
	 * player who has not met it does not know to look. Per action rather than
	 * only global, because one trigger firing into a deliberately muted phone
	 * should not have to cost the warning everywhere else.
	 */
	private boolean warnWhenSilent;

	public SoundResponder() {
		super(RESPONDER_TYPE.SOUND);
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
		soundPath = "";
		minGapMs = DEFAULT_MIN_GAP_MS;
		volumePercent = DEFAULT_VOLUME_PERCENT;
		warnWhenSilent = true;
	}

	public SoundResponder(RESPONDER_TYPE pType) {
		super(pType);
	}

	public SoundResponder copy() {
		SoundResponder tmp = new SoundResponder();
		tmp.soundPath = this.soundPath;
		tmp.minGapMs = this.minGapMs;
		tmp.volumePercent = this.volumePercent;
		tmp.warnWhenSilent = this.warnWhenSilent;
		tmp.setFireType(this.getFireType());
		return tmp;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof SoundResponder)) {
			return false;
		}
		SoundResponder test = (SoundResponder) o;
		if (test.minGapMs != this.minGapMs) {
			return false;
		}
		if (test.volumePercent != this.volumePercent) {
			return false;
		}
		if (test.warnWhenSilent != this.warnWhenSilent) {
			return false;
		}
		// String content, not identity — see the note in SpeakResponder.
		if (test.soundPath == null ? this.soundPath != null
				: !test.soundPath.equals(this.soundPath)) {
			return false;
		}
		if (test.getFireType() != this.getFireType()) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int h = soundPath == null ? 0 : soundPath.hashCode();
		h = 31 * h + minGapMs;
		h = 31 * h + volumePercent;
		return 31 * h + (warnWhenSilent ? 1 : 0);
	}

	public SoundResponder(Parcel in) {
		super(RESPONDER_TYPE.SOUND);
		readFromParcel(in);
	}

	private void readFromParcel(Parcel in) {
		setSoundPath(in.readString());
		setMinGapMs(in.readInt());
		setVolumePercent(in.readInt());
		// Written after the three that came before it, and read in the same
		// order. An older parcel does not exist — responders cross the binder
		// whole, never partially — but the order is still the contract.
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
		TriggerSounds.play(c, soundPath, volumePercent / 100f,
				rateKey(displayname, name), minGapMs, warnWhenSilent);
		return false;
	}

	/**
	 * Rate-limit key is world|trigger name. {@code getNotificationId()}
	 * increments per fire, so it never suppressed. A trigger fires per match.
	 */
	static String rateKey(final String displayname, final String name) {
		return (displayname == null ? "" : displayname)
				+ "|" + (name == null ? "" : name);
	}

	public static Parcelable.Creator<SoundResponder> CREATOR =
			new Parcelable.Creator<SoundResponder>() {

		public SoundResponder createFromParcel(Parcel source) {
			return new SoundResponder(source);
		}

		public SoundResponder[] newArray(int size) {
			return new SoundResponder[size];
		}
	};

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(soundPath);
		out.writeInt(minGapMs);
		out.writeInt(volumePercent);
		out.writeInt(warnWhenSilent ? 1 : 0);
		out.writeString(this.getFireType().getString());
	}

	public void setSoundPath(String soundPath) {
		this.soundPath = soundPath == null ? "" : soundPath;
	}

	public String getSoundPath() {
		return soundPath;
	}

	public void setMinGapMs(int minGapMs) {
		this.minGapMs = minGapMs < 0 ? 0 : minGapMs;
	}

	public int getMinGapMs() {
		return minGapMs;
	}

	public void setVolumePercent(int volumePercent) {
		if (volumePercent < 0) {
			volumePercent = 0;
		}
		if (volumePercent > 100) {
			volumePercent = 100;
		}
		this.volumePercent = volumePercent;
	}

	public int getVolumePercent() {
		return volumePercent;
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
		SoundResponderParser.saveSoundResponderToXML(out, this);
	}
}
