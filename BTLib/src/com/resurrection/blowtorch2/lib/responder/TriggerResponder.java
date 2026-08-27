package com.resurrection.blowtorch2.lib.responder;

import java.io.IOException;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keplerproject.luajava.LuaState;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.window.TextTree;

import android.content.Context;
import android.os.Handler;
import android.os.Parcelable;


public abstract class TriggerResponder implements Parcelable {

	
	public static final int RESPONDER_TYPE_TOAST = 101;
	public static final int RESPONDER_TYPE_NOTIFICATION = 102;
	public static final int RESPONDER_TYPE_ACK = 103;
	public static final int RESPONDER_TYPE_SCRIPT = 104;
	public static final int RESPONDER_TYPE_COLOR = 105;
	public static final int RESPONDER_TYPE_REPLACE = 106;
	public static final int RESPONDER_TYPE_GAG = 107;
	public static final int RESPONDER_TYPE_SET_VARIABLE = 108;
	public static final int RESPONDER_TYPE_TAP = 109;
	public static final int RESPONDER_TYPE_SPEAK = 110;
	public static final int RESPONDER_TYPE_SOUND = 111;
	public static final int RESPONDER_TYPE_CHAT_THREAD = 112;
	
	public enum RESPONDER_TYPE {
		NOTIFICATION(RESPONDER_TYPE_NOTIFICATION),
		TOAST(RESPONDER_TYPE_TOAST),
		ACK(RESPONDER_TYPE_ACK),
		SCRIPT(RESPONDER_TYPE_SCRIPT), 
		REPLACE(RESPONDER_TYPE_REPLACE),
		COLOR(RESPONDER_TYPE_COLOR),
		GAG(RESPONDER_TYPE_GAG),
		SET_VARIABLE(RESPONDER_TYPE_SET_VARIABLE),
		TAP(RESPONDER_TYPE_TAP),
		SPEAK(RESPONDER_TYPE_SPEAK),
		SOUND(RESPONDER_TYPE_SOUND),
		CHAT_THREAD(RESPONDER_TYPE_CHAT_THREAD);
		private int value;
		
		private RESPONDER_TYPE(int i) {
			value = i;
		}
		
		public int getIntVal() {
			return value;
		}
	}
	
	private RESPONDER_TYPE type;
	
	public static final String FIRE_WINDOW_OPEN = "windowOpen";
	public static final String FIRE_WINDOW_CLOSED = "windowClosed";
	public static final String FIRE_ALWAYS = "always";
	public static final String FIRE_NEVER = "none";
	
	public enum FIRE_WHEN {
		WINDOW_CLOSED(FIRE_WINDOW_CLOSED),
		WINDOW_OPEN(FIRE_WINDOW_OPEN),
		WINDOW_BOTH(FIRE_ALWAYS),
		WINDOW_NEVER(FIRE_NEVER);
		
		private String value;
		
		private FIRE_WHEN(String i) {
			if(i != null) {
				value = i;
			} else {
				value = "always";
			}
		}
			
		public String getString() {
			return value;
		}
	}
	
	private FIRE_WHEN fireType;
	
	public TriggerResponder(RESPONDER_TYPE pType) {
		setType(pType);
	}

	public void setType(RESPONDER_TYPE type) {
		this.type = type;
	}

	public RESPONDER_TYPE getType() {
		return type;
	}
	
	public void addFireType(FIRE_WHEN in) {
		//will always be WINDOW_OPEN or WINDOW_CLOSED
		switch(in) {
		case WINDOW_OPEN:
			if(fireType == FIRE_WHEN.WINDOW_CLOSED || fireType == FIRE_WHEN.WINDOW_BOTH) {
				fireType = FIRE_WHEN.WINDOW_BOTH;
			} else if(fireType == FIRE_WHEN.WINDOW_OPEN || fireType == FIRE_WHEN.WINDOW_NEVER) {
				fireType = FIRE_WHEN.WINDOW_OPEN;
			}
			break;
		case WINDOW_CLOSED:
			if(fireType == FIRE_WHEN.WINDOW_OPEN || fireType == FIRE_WHEN.WINDOW_BOTH) {
				fireType = FIRE_WHEN.WINDOW_BOTH;
			} else if(fireType == FIRE_WHEN.WINDOW_CLOSED || fireType == FIRE_WHEN.WINDOW_NEVER) {
				fireType = FIRE_WHEN.WINDOW_CLOSED;
			}
			break;
		}
		//Log.e("RESPONDER","ADDED " + in.getString() + " FIRE TYPE NOW " + fireType.getString());
	}
	
	public void removeFireType(FIRE_WHEN in) {
		switch(in) {
		case WINDOW_OPEN:
			if(fireType == FIRE_WHEN.WINDOW_BOTH) {
				fireType = FIRE_WHEN.WINDOW_CLOSED;
			} else if (fireType == FIRE_WHEN.WINDOW_OPEN) {
				fireType = FIRE_WHEN.WINDOW_NEVER;
			}
			break;
		case WINDOW_CLOSED:
			if(fireType == FIRE_WHEN.WINDOW_BOTH) {
				fireType = FIRE_WHEN.WINDOW_OPEN;
			} else if (fireType == FIRE_WHEN.WINDOW_CLOSED) {
				fireType = FIRE_WHEN.WINDOW_NEVER;
			}
			break;
		default:
			break;
		}
		
		//Log.e("RESPONDER","REMOVED " + in.getString() + " FIRE TYPE NOW " + fireType.getString());
	}
	
	public abstract boolean doResponse(Context c,TextTree tree,int lineNumber,ListIterator<TextTree.Line> iterator,TextTree.Line line,int start,int end,String matched,Object source,String displayname,String host,int port,int triggernumber,boolean windowIsOpen,Handler dispatcher,HashMap<String,String> captureMap,LuaState L,String name,String encoding) throws IteratorModifiedException;
	public abstract TriggerResponder copy();
	//public abstract void writeToParcel(Parcel in,int args);

	public void setFireType(FIRE_WHEN fireType) {
		this.fireType = fireType;
	}

	public FIRE_WHEN getFireType() {
		return fireType;
	}
	
	/**
	 * Substitute {@code $1}, {@code $2}, … from the capture map.
	 *
	 * <p>Delegates to {@link CaptureSubstitution}, which is where the logic and
	 * its tests live. The buffers this used to keep in instance fields are gone:
	 * they were shared by every call on a responder, and triggers and timers do
	 * not run on the same thread.
	 *
	 * @param input Text with {@code $n} references.
	 * @param map Capture number to captured text.
	 * @return The substituted text.
	 */
	public String translate(String input, HashMap<String, String> map) {
		return CaptureSubstitution.apply(input, map);
	}

	public abstract void saveResponderToXML(XmlSerializer out) throws IllegalArgumentException, IllegalStateException, IOException;
	
	
	
}
