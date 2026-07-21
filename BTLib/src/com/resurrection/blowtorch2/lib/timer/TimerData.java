package com.resurrection.blowtorch2.lib.timer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.responder.color.ColorAction;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponder;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;

import android.os.Parcel;
import android.os.Parcelable;

public class TimerData implements Parcelable {

	public static final String DEFAULT_GROUP = "";

	private String name;
	private Integer ordinal;
	private Integer seconds;
	private boolean repeat;
	private boolean playing;
	private long startTime;
	private int remainingTime;
	private String group = DEFAULT_GROUP;

	private List<TriggerResponder> responders;

	public TimerData() {
		name="";
		ordinal=0;
		seconds=30;
		repeat=true;
		playing = false;
		group = DEFAULT_GROUP;
		responders = new ArrayList<TriggerResponder>();
	}

	public void reset() {
	}

	public TimerData copy() {

		TimerData tmp = new TimerData();
		tmp.name = this.name;
		tmp.ordinal = this.ordinal;
		tmp.seconds = this.seconds;
		tmp.repeat = this.repeat;
		tmp.playing = this.playing;
		tmp.remainingTime =  this.remainingTime;
		tmp.group = this.group;
		for(TriggerResponder responder : this.responders) {
			tmp.responders.add(responder.copy());
		}

		return tmp;

	}

	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof TimerData)) return false;
		TimerData test = (TimerData)o;
		if(!test.name.equals(this.name)) return false;
		if(test.ordinal != this.ordinal) return false;
		if(test.seconds != this.seconds) return false;
		if(test.repeat != this.repeat) return false;
		if(test.playing != this.playing) return false;
		if (test.group == null) {
			if (this.group != null && this.group.length() > 0) return false;
		} else if (!test.group.equals(this.group)) {
			return false;
		}
		Iterator<TriggerResponder> test_responders = test.responders.iterator();
		Iterator<TriggerResponder> my_responders = this.responders.iterator();
		while(test_responders.hasNext()) {
			TriggerResponder test_responder = test_responders.next();
			TriggerResponder my_responder = my_responders.next();
			if(!test_responder.equals(my_responder)) return false;
		}

		return true;
	}

	public static final Parcelable.Creator<TimerData> CREATOR = new Parcelable.Creator<TimerData>() {

		public TimerData createFromParcel(Parcel arg0) {
			return new TimerData(arg0);
		}

		public TimerData[] newArray(int arg0) {
			return new TimerData[arg0];
		}
	};

	public TimerData(Parcel in) {
		readFromParcel(in);
	}

	public void readFromParcel(Parcel in) {
		setName(in.readString());
		setOrdinal(in.readInt());
		setSeconds(in.readInt());
		setRepeat( (in.readInt() == 1) ? true : false);
		setPlaying( (in.readInt() == 1) ? true : false);
		setRemainingTime( in.readInt());
		setGroup(in.readString());
		int numresponders = in.readInt();
		responders = new ArrayList<TriggerResponder>();

		for(int i = 0;i<numresponders;i++) {
			int type = in.readInt();
			switch(type) {
			case TriggerResponder.RESPONDER_TYPE_NOTIFICATION:
				NotificationResponder resp = in.readParcelable(NotificationResponder.class.getClassLoader());
				responders.add(resp);
				break;
			case TriggerResponder.RESPONDER_TYPE_TOAST:
				ToastResponder toasty = in.readParcelable(ToastResponder.class.getClassLoader());
				responders.add(toasty);
				break;
			case TriggerResponder.RESPONDER_TYPE_ACK:
				AckResponder ack = in.readParcelable(AckResponder.class.getClassLoader());
				responders.add(ack);
				break;
			case TriggerResponder.RESPONDER_TYPE_SCRIPT:
				ScriptResponder scr = in.readParcelable(ScriptResponder.class.getClassLoader());
				responders.add(scr);
				break;
			case TriggerResponder.RESPONDER_TYPE_GAG:
				GagAction gag = in.readParcelable(GagAction.class.getClassLoader());
				responders.add(gag);
				break;
			case TriggerResponder.RESPONDER_TYPE_REPLACE:
				ReplaceResponder rep = in.readParcelable(ReplaceResponder.class.getClassLoader());
				responders.add(rep);
				break;
			case TriggerResponder.RESPONDER_TYPE_COLOR:
				ColorAction color = in.readParcelable(ColorAction.class.getClassLoader());
				responders.add(color);
				break;
			default:
				break;
			}
		}
	}
	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel o, int flags) {
		o.writeString(name);
		o.writeInt(ordinal);
		o.writeInt(seconds);
		o.writeInt((repeat) ? 1 : 0);
		o.writeInt((playing) ? 1 : 0);
		o.writeInt(remainingTime);
		o.writeString(group != null ? group : DEFAULT_GROUP);
		o.writeInt(responders.size());
		for(TriggerResponder responder : responders) {
			o.writeInt(responder.getType().getIntVal());
			o.writeParcelable(responder, 0);
		}

	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setOrdinal(Integer ordinal) {
		this.ordinal = ordinal;
	}

	public Integer getOrdinal() {
		return ordinal;
	}

	public void setSeconds(Integer seconds) {
		this.seconds = seconds;
	}

	public Integer getSeconds() {
		return seconds;
	}

	public void setRepeat(boolean repeat) {
		this.repeat = repeat;
	}

	public boolean isRepeat() {
		return repeat;
	}

	public void setResponders(List<TriggerResponder> responders) {
		this.responders = responders;
	}

	public List<TriggerResponder> getResponders() {
		return responders;
	}

	public void setPlaying(boolean playing) {
		this.playing = playing;
	}

	public boolean isPlaying() {
		return playing;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public int getRemainingTime() {
		return remainingTime;
	}

	public void setRemainingTime(int remainingTime) {
		this.remainingTime = remainingTime;
	}

	public String getGroup() {
		return group != null ? group : DEFAULT_GROUP;
	}

	public void setGroup(String group) {
		this.group = group != null ? group : DEFAULT_GROUP;
	}

}
