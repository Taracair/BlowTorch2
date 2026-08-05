package com.resurrection.blowtorch2.lib.responder.tap;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * One tappable-word rule, in the form that crosses the binder.
 *
 * <p>The window needs four things to mark a word and act on a tap: what to
 * look for, what to send, how to draw it and which part of the match is the
 * word. This carries exactly those, and nothing else.
 *
 * <p>It exists because the UI used to build the rules itself out of
 * {@code getTriggerData()} — the whole trigger map, every responder and every
 * condition of every trigger, pulled across the binder on the UI thread. That
 * was one dialog opening; then a trigger rebuild started notifying the window,
 * so it became something that ran while the player was playing, and a trigger
 * that enables another trigger can rebuild several times a second. A profile
 * of a couple of hundred triggers is also within reach of the binder's
 * transaction limit, and the failure there is silent: the exception is caught
 * and words simply stop lighting up.
 *
 * <p>The pattern travels as text and is compiled on the other side. It is
 * {@code TriggerData.getCompiledPattern().pattern()}, i.e. already quoted for a
 * literal trigger and already the alias-resolved form, so recompiling it with
 * no flags is the same pattern the trigger itself matches with — compiling the
 * player's raw text here would turn a literal trigger into a regex.
 */
public class TapRuleData implements Parcelable {

	private final String pattern;
	private final String[] commands;
	private final boolean underline;
	private final boolean bold;
	private final boolean frame;
	private final int group;

	public TapRuleData(final String pattern, final String[] commands, final boolean underline,
			final boolean bold, final boolean frame, final int group) {
		this.pattern = pattern != null ? pattern : "";
		this.commands = commands != null ? commands : new String[0];
		this.underline = underline;
		this.bold = bold;
		this.frame = frame;
		this.group = group;
	}

	private TapRuleData(final Parcel in) {
		pattern = in.readString();
		String[] read = in.createStringArray();
		commands = read != null ? read : new String[0];
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
		return underline == other.underline && bold == other.bold && frame == other.frame
				&& group == other.group && pattern.equals(other.pattern)
				&& java.util.Arrays.equals(commands, other.commands);
	}

	@Override
	public int hashCode() {
		int h = pattern.hashCode();
		h = 31 * h + java.util.Arrays.hashCode(commands);
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
