package com.resurrection.blowtorch2.lib.responder.gag;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.regex.Matcher;

import org.keplerproject.luajava.LuaState;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.resurrection.blowtorch2.lib.responder.IteratorModifiedException;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;

public class GagAction extends TriggerResponder implements Parcelable {
	public static boolean DEFAULT_GAGLOG = true;
	public static boolean DEFAULT_GAGOUTPUT = true;
	
	private boolean gagLog = DEFAULT_GAGLOG;
	private boolean gagOutput = DEFAULT_GAGOUTPUT;
	
	private String retarget = null;
	public GagAction(RESPONDER_TYPE pType) {
		super(pType);
		// TODO Auto-generated constructor stub
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void writeToParcel(Parcel o, int flags) {
		// TODO Auto-generated method stub
		o.writeInt(gagLog ? 1:0);
		o.writeInt(gagOutput ? 1:0);
		o.writeString(retarget);
		o.writeString(this.getFireType().getString());
	}

	@Override
	public boolean doResponse(Context c, TextTree tree,int lineNumber,ListIterator<TextTree.Line> iterator,Line line, int start,int end,String matched,
			Object source, String displayname,String host,int port, int triggernumber,
			boolean windowIsOpen, Handler dispatcher,
			HashMap<String, String> captureMap, LuaState L, String name,String encoding) throws IteratorModifiedException {
			//iterator.pr
			if(windowIsOpen) {
				if(this.getFireType() == FIRE_WHEN.WINDOW_CLOSED || this.getFireType() == FIRE_WHEN.WINDOW_NEVER) return false;
			} else {
				if(this.getFireType() == FIRE_WHEN.WINDOW_OPEN || this.getFireType() == FIRE_WHEN.WINDOW_NEVER) return false;
			}
		
			if (lineNumber < 0 || tree.getLines() == null) {
				return false;
			}
			// Newest-first list. The match starts on its oldest line, so the
			// block is that index and the newer ones under it. Removing high
			// to low leaves the lower indices alone.
			int span = linesSpanned(matched);
			java.util.List<Line> taken = new java.util.ArrayList<Line>(span);
			for (int i = 0; i < span; i++) {
				int at = lineNumber - i;
				if (at < 0 || at >= tree.getLines().size()) {
					break;
				}
				taken.add(tree.getLines().remove(at));
			}
			if (taken.isEmpty()) {
				return false;
			}

			if (retarget != null) {
				// taken[0] is the oldest line. The other window appends, so
				// that end has to go first or the block arrives upside down.
				for (int i = 0; i < taken.size(); i++) {
					Message msg = dispatcher.obtainMessage(Connection.MESSAGE_LINETOWINDOW, taken.get(i));
					Bundle b = msg.getData();
					b.putString("TARGET", retarget);
					msg.setData(b);
					dispatcher.sendMessage(msg);
				}
			}

			// Next newer line. Staying at lineNumber walks off the list once
			// more than one line was removed.
			int resumeAt = lineNumber - taken.size() + 1;
			int sizeNow = tree.getLines().size();
			if (resumeAt < 0) {
				resumeAt = 0;
			} else if (resumeAt > sizeNow) {
				resumeAt = sizeNow;
			}
			throw new IteratorModifiedException(tree.getLines().listIterator(resumeAt));
			
	}

	/**
	 * How many lines a match covers.
	 *
	 * <p>One more than the newlines inside it: a single-line match has none and
	 * covers one line, which is what every gag written before patterns could
	 * span lines still does.
	 *
	 * @param matched the text the trigger matched.
	 * @return at least 1.
	 */
	static int linesSpanned(final String matched) {
		if (matched == null) {
			return 1;
		}
		int lines = 1;
		for (int i = 0; i < matched.length(); i++) {
			if (matched.charAt(i) == '\n') {
				lines++;
			}
		}
		return lines;
	}

	@Override
	public TriggerResponder copy() {
		GagAction tmp = new GagAction(RESPONDER_TYPE.GAG);
		tmp.setGagLog(this.isGagLog());
		tmp.setGagOutput(this.gagOutput);
		tmp.setRetarget(this.getRetarget());
		tmp.setFireType(this.getFireType());
		return tmp;
	}
	
	public boolean equals(Object o) {
		if(this == o) return true;
		if(!(o instanceof GagAction)) return false;
		GagAction b = (GagAction)o;
		GagAction a = this;
		if(a.gagLog != b.gagLog) return false;
		if(a.gagOutput != b.gagOutput) return false;
		if(a.retarget == null) {
			if(b.retarget != null) return false;
		} else if(!a.retarget.equals(b.retarget)) {
			return false;
		}
		if(a.getFireType() != b.getFireType()) return false;
		return true;
	}
	
	public GagAction(Parcel in) {
		super(RESPONDER_TYPE.GAG);
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
		readFromParcel(in);
	}

	public GagAction() {
		// TODO Auto-generated constructor stub
		super(RESPONDER_TYPE.GAG);
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	private void readFromParcel(Parcel in) {
		// TODO Auto-generated method stub
		this.setGagLog((in.readInt() == 1) ? true : false );
		this.setGagOutput((in.readInt() == 1) ? true : false );
		this.setRetarget(in.readString());
		String fireType = in.readString();
		
		if(fireType.equals(FIRE_WINDOW_OPEN)) {
			setFireType(FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(FIRE_WINDOW_CLOSED)) {
			setFireType(FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(FIRE_ALWAYS)) {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		} else if (fireType.equals(FIRE_NEVER)) {
			setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		}
	}

	@Override
	public void saveResponderToXML(XmlSerializer out)
			throws IllegalArgumentException, IllegalStateException, IOException {
		// TODO Auto-generated method stub
		GagActionParser.saveGagActionToXML(out,this);
	}

	public void setGagLog(boolean gagLog) {
		this.gagLog = gagLog;
	}

	public boolean isGagLog() {
		return gagLog;
	}

	public void setGagOutput(boolean gagOutput) {
		this.gagOutput = gagOutput;
	}

	public boolean isGagOutput() {
		return gagOutput;
	}

	/** Empty / whitespace-only strings are treated as no retarget (null). */
	public void setRetarget(String retarget) {
		if(retarget == null || retarget.trim().length() == 0) {
			this.retarget = null;
		} else {
			this.retarget = retarget;
		}
	}

	public String getRetarget() {
		return retarget;
	}
	
	public static Parcelable.Creator<GagAction> CREATOR = new Parcelable.Creator<GagAction>() {

		public GagAction createFromParcel(Parcel source) {
			return new GagAction(source);
		}

		public GagAction[] newArray(int size) {
			return new GagAction[size];
		}
		
	};

}
