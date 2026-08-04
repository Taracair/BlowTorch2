package com.resurrection.blowtorch2.lib.responder.tap;

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
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;

/**
 * Makes what the trigger matched tappable: the player touches the word and the
 * command goes to the game, with {@code $word} replaced by the text that was
 * hit.
 *
 * <p><b>Why this responder does nothing when it fires.</b> A colour trigger
 * works by putting an ANSI code into the byte stream, and the window rebuilds
 * the colour from those bytes on the other side of the binder. "This word is
 * tappable" has no byte to carry it, so marking the text here would mark a
 * buffer the window never sees — the window builds its own from the bytes.
 *
 * <p>So the rule travels instead of the mark. The window reads the trigger list
 * over the binder it already has, keeps the ones carrying a TapAction, and
 * matches the pattern itself while drawing — the same place it already finds
 * links. That also means a tappable word stays tappable for as long as the line
 * is in the buffer, instead of only at the instant the trigger fired.
 */
public class TapAction extends TriggerResponder implements Parcelable {

	/** Placeholder replaced with the text that was tapped. */
	public static final String WORD_TOKEN = "$word";

	public static final String DEFAULT_COMMAND = "look " + WORD_TOKEN;

	/**
	 * What a tap can send. One entry is the ordinary case and stays a plain tap
	 * that fires straight away; more than one turns the tap into a menu, which
	 * is why this is a list and not a second optional field. Never empty — an
	 * empty list would mean a word that lights up and then does nothing.
	 */
	private java.util.ArrayList<String> commands = new java.util.ArrayList<String>();
	private boolean underline = true;
	private boolean bold;
	private boolean frame;
	private boolean recolor;
	private int color = 0xFF66CCFF;

	public TapAction() {
		super(RESPONDER_TYPE.TAP);
		commands.add(DEFAULT_COMMAND);
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	public TapAction(RESPONDER_TYPE pType) {
		super(pType);
		commands.add(DEFAULT_COMMAND);
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	@Override
	public boolean doResponse(Context c, TextTree tree, int lineNumber,
			ListIterator<TextTree.Line> iterator, Line line, int pstart, int pend,
			String matched, Object source, String displayname, String host, int port,
			int triggernumber, boolean windowIsOpen, Handler dispatcher,
			HashMap<String, String> captureMap, LuaState L, String name, String encoding) {
		// Nothing to do at match time on purpose — see the class comment. The
		// window applies this rule while it draws.
		return false;
	}

	@Override
	public TriggerResponder copy() {
		TapAction tmp = new TapAction(RESPONDER_TYPE.TAP);
		tmp.commands = new java.util.ArrayList<String>(this.commands);
		tmp.underline = this.underline;
		tmp.bold = this.bold;
		tmp.frame = this.frame;
		tmp.recolor = this.recolor;
		tmp.color = this.color;
		tmp.setFireType(this.getFireType());
		return tmp;
	}

	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof TapAction)) {
			return false;
		}
		TapAction b = (TapAction) o;
		if (!this.commands.equals(b.commands)) {
			return false;
		}
		if (this.underline != b.underline) {
			return false;
		}
		if (this.bold != b.bold) {
			return false;
		}
		if (this.frame != b.frame) {
			return false;
		}
		if (this.recolor != b.recolor) {
			return false;
		}
		if (this.color != b.color) {
			return false;
		}
		return this.getFireType() == b.getFireType();
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel o, int flags) {
		o.writeStringList(commands);
		o.writeInt(underline ? 1 : 0);
		o.writeInt(bold ? 1 : 0);
		o.writeInt(frame ? 1 : 0);
		o.writeInt(recolor ? 1 : 0);
		o.writeInt(color);
		o.writeString(this.getFireType().getString());
	}

	public TapAction(Parcel in) {
		super(RESPONDER_TYPE.TAP);
		readFromParcel(in);
	}

	private void readFromParcel(Parcel in) {
		java.util.ArrayList<String> read = in.createStringArrayList();
		setCommands(read);
		underline = in.readInt() != 0;
		bold = in.readInt() != 0;
		frame = in.readInt() != 0;
		recolor = in.readInt() != 0;
		color = in.readInt();

		String fireType = in.readString();
		if (FIRE_WINDOW_OPEN.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_OPEN);
		} else if (FIRE_WINDOW_CLOSED.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_CLOSED);
		} else if (FIRE_NEVER.equals(fireType)) {
			setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		}
	}

	public static Parcelable.Creator<TapAction> CREATOR = new Parcelable.Creator<TapAction>() {
		public TapAction createFromParcel(Parcel source) {
			return new TapAction(source);
		}

		public TapAction[] newArray(int size) {
			return new TapAction[size];
		}
	};

	@Override
	public void saveResponderToXML(XmlSerializer out)
			throws IllegalArgumentException, IllegalStateException, IOException {
		TapActionParser.saveTapActionToXML(out, this);
	}

	/** The command a plain tap sends: the first one on the list. */
	public String getCommand() {
		return commands.get(0);
	}

	/** Replaces the whole list with a single command. */
	public void setCommand(String command) {
		commands.clear();
		commands.add(command != null && command.trim().length() > 0
				? command : DEFAULT_COMMAND);
	}

	/** Every command, in menu order. The first one is the default. */
	public java.util.List<String> getCommands() {
		return java.util.Collections.unmodifiableList(commands);
	}

	/**
	 * Replaces the list, dropping blanks. An empty or null list falls back to
	 * the default command rather than leaving a word that lights up and does
	 * nothing when it is tapped.
	 */
	public void setCommands(java.util.List<String> newCommands) {
		java.util.ArrayList<String> kept = new java.util.ArrayList<String>();
		if (newCommands != null) {
			for (String c : newCommands) {
				if (c != null && c.trim().length() > 0) {
					kept.add(c.trim());
				}
			}
		}
		if (kept.isEmpty()) {
			kept.add(DEFAULT_COMMAND);
		}
		commands = kept;
	}

	/** Appends one command; blanks are ignored. */
	public void addCommand(String command) {
		if (command != null && command.trim().length() > 0) {
			commands.add(command.trim());
		}
	}

	/** True when a tap has to ask which command the player meant. */
	public boolean hasMenu() {
		return commands.size() > 1;
	}

	public boolean isUnderline() {
		return underline;
	}

	public void setUnderline(boolean underline) {
		this.underline = underline;
	}

	public boolean isBold() {
		return bold;
	}

	public void setBold(boolean bold) {
		this.bold = bold;
	}

	public boolean isFrame() {
		return frame;
	}

	public void setFrame(boolean frame) {
		this.frame = frame;
	}

	public boolean isRecolor() {
		return recolor;
	}

	public void setRecolor(boolean recolor) {
		this.recolor = recolor;
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}
}
