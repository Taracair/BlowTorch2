package com.resurrection.blowtorch2.lib.responder.tap;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Tappable-word rule for the binder. Pattern is already
 * {@code TriggerData.getCompiledPattern().pattern()} — compiling the player's
 * raw text here would turn a literal trigger into a regex. Do not send the
 * whole trigger map; a rebuild can hit the transaction limit silently.
 */
public class TapRuleData implements Parcelable {

	private final String pattern;
	private final String[] commands;
	private final boolean tapSendsFirst;
	private final boolean underline;
	private final boolean bold;
	private final boolean frame;
	private final int group;

	public TapRuleData(final String pattern, final String[] commands,
			final boolean tapSendsFirst, final boolean underline,
			final boolean bold, final boolean frame, final int group) {
		this.pattern = pattern != null ? pattern : "";
		this.commands = commands != null ? commands : new String[0];
		this.tapSendsFirst = tapSendsFirst;
		this.underline = underline;
		this.bold = bold;
		this.frame = frame;
		this.group = group;
	}

	private TapRuleData(final Parcel in) {
		pattern = in.readString();
		String[] read = in.createStringArray();
		commands = read != null ? read : new String[0];
		tapSendsFirst = in.readInt() != 0;
		underline = in.readInt() != 0;
		bold = in.readInt() != 0;
		frame = in.readInt() != 0;
		group = in.readInt();
	}

	public String getPattern() {
		return pattern;
	}

	public String[] getCommands() {
		return commands;
	}

	public boolean isTapSendsFirst() {
		return tapSendsFirst;
	}

	public boolean isUnderline() {
		return underline;
	}

	public boolean isBold() {
		return bold;
	}

	public boolean isFrame() {
		return frame;
	}

	public int getGroup() {
		return group;
	}

	/**
	 * Content equality, because this is what decides whether the window is told
	 * anything at all: the service rebuilds the rules on every trigger rebuild
	 * and compares the new list with the old one. A trigger that carries no tap
	 * action — the usual case for a trigger switching another trigger on and
	 * off mid-fight — produces an identical list, and then nothing crosses the
	 * binder and nothing is recompiled.
	 */
	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof TapRuleData)) {
			return false;
		}
		TapRuleData other = (TapRuleData) o;
		// tapSendsFirst is part of this on purpose: it is the only difference
		// between two otherwise identical rules, and if it were left out,
		// ticking the box in the editor would never reach the window.
		return tapSendsFirst == other.tapSendsFirst && underline == other.underline
				&& bold == other.bold && frame == other.frame
				&& group == other.group && pattern.equals(other.pattern)
				&& java.util.Arrays.equals(commands, other.commands);
	}

	@Override
	public int hashCode() {
		int h = pattern.hashCode();
		h = 31 * h + java.util.Arrays.hashCode(commands);
		h = 31 * h + (tapSendsFirst ? 8 : 0);
		h = 31 * h + (underline ? 1 : 0);
		h = 31 * h + (bold ? 2 : 0);
		h = 31 * h + (frame ? 4 : 0);
		return 31 * h + group;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(final Parcel out, final int flags) {
		out.writeString(pattern);
		out.writeStringArray(commands);
		out.writeInt(tapSendsFirst ? 1 : 0);
		out.writeInt(underline ? 1 : 0);
		out.writeInt(bold ? 1 : 0);
		out.writeInt(frame ? 1 : 0);
		out.writeInt(group);
	}

	public static final Parcelable.Creator<TapRuleData> CREATOR =
			new Parcelable.Creator<TapRuleData>() {
		public TapRuleData createFromParcel(final Parcel in) {
			return new TapRuleData(in);
		}

		public TapRuleData[] newArray(final int size) {
			return new TapRuleData[size];
		}
	};
}
