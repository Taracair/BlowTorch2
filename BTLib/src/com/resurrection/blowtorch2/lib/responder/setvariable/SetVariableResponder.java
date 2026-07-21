package com.resurrection.blowtorch2.lib.responder.setvariable;

import java.io.IOException;
import java.util.HashMap;
import java.util.ListIterator;

import org.keplerproject.luajava.LuaState;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * Sets a session variable (optionally expanding {@code $1} captures in name/value).
 */
public class SetVariableResponder extends TriggerResponder implements Parcelable {

	private String variableName;
	private String variableValue;

	public SetVariableResponder() {
		super(RESPONDER_TYPE.SET_VARIABLE);
		variableName = "";
		variableValue = "";
		setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	public SetVariableResponder copy() {
		SetVariableResponder tmp = new SetVariableResponder();
		tmp.variableName = this.variableName;
		tmp.variableValue = this.variableValue;
		tmp.setFireType(this.getFireType());
		return tmp;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof SetVariableResponder)) {
			return false;
		}
		SetVariableResponder test = (SetVariableResponder) o;
		if (!test.variableName.equals(this.variableName)) {
			return false;
		}
		if (!test.variableValue.equals(this.variableValue)) {
			return false;
		}
		return test.getFireType() == this.getFireType();
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
		String key = translate(variableName, captureMap);
		String val = translate(variableValue, captureMap);
		if (key == null || key.length() == 0 || dispatcher == null) {
			return false;
		}
		Message msg = dispatcher.obtainMessage(Connection.MESSAGE_SET_VARIABLE);
		msg.obj = new String[] { key, val != null ? val : "" };
		dispatcher.sendMessage(msg);
		return false;
	}

	public SetVariableResponder(Parcel in) {
		super(RESPONDER_TYPE.SET_VARIABLE);
		readFromParcel(in);
	}

	public static final Parcelable.Creator<SetVariableResponder> CREATOR =
			new Parcelable.Creator<SetVariableResponder>() {
				public SetVariableResponder createFromParcel(Parcel source) {
					return new SetVariableResponder(source);
				}

				public SetVariableResponder[] newArray(int size) {
					return new SetVariableResponder[size];
				}
			};

	public int describeContents() {
		return 0;
	}

	public void readFromParcel(Parcel in) {
		setVariableName(in.readString());
		setVariableValue(in.readString());
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
		out.writeString(variableName);
		out.writeString(variableValue);
		out.writeString(getFireType().getString());
	}

	public String getVariableName() {
		return variableName;
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName != null ? variableName : "";
	}

	public String getVariableValue() {
		return variableValue;
	}

	public void setVariableValue(String variableValue) {
		this.variableValue = variableValue != null ? variableValue : "";
	}

	@Override
	public void saveResponderToXML(XmlSerializer out)
			throws IllegalArgumentException, IllegalStateException, IOException {
		SetVariableResponderParser.saveResponderToXML(out, this);
	}
}
