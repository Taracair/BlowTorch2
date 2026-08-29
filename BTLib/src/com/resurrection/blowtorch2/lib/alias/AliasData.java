package com.resurrection.blowtorch2.lib.alias;


import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;

public class AliasData implements Parcelable {
	
	private String pre;
	private String post;
	private boolean enabled;
	private AliasLocalEcho localEcho = AliasLocalEcho.INHERIT;
	private List<SetVariableResponder> setVariables;
	
	public AliasData() {
		pre = "";
		post = "";
		enabled = true;
		localEcho = AliasLocalEcho.INHERIT;
		setVariables = new ArrayList<SetVariableResponder>();
	}
	
	public AliasData(String pPre, String pPost,boolean enabled) {
		pre = pPre;
		post = pPost;
		this.enabled = enabled;
		this.localEcho = AliasLocalEcho.INHERIT;
		setVariables = new ArrayList<SetVariableResponder>();
	}
	
	public AliasData copy() {
		AliasData tmp = new AliasData();
		tmp.pre = this.pre;
		tmp.post = this.post;
		tmp.enabled = this.enabled;
		tmp.localEcho = this.localEcho;
		tmp.setVariables = copySetVariables(this.setVariables);
		return tmp;
	}
	
	public boolean equals(Object o) {
		if(o == this) return true;
		if( !(o instanceof AliasData)) return false;
		AliasData t = (AliasData)o;
		if(!t.pre.equals(this.pre)) return false;
		if(!t.post.equals(this.post)) return false;
		if(t.enabled != this.enabled) return false;
		if(t.localEcho != this.localEcho) return false;
		List<SetVariableResponder> mine = getSetVariables();
		List<SetVariableResponder> theirs = t.getSetVariables();
		if (mine.size() != theirs.size()) return false;
		for (int i = 0; i < mine.size(); i++) {
			SetVariableResponder a = mine.get(i);
			SetVariableResponder b = theirs.get(i);
			if (a == null ? b != null : !a.equals(b)) {
				return false;
			}
		}
		return true;
	}
	
	public static final Parcelable.Creator<AliasData> CREATOR = new Parcelable.Creator<AliasData>() {

		public AliasData createFromParcel(Parcel arg0) {
			return new AliasData(arg0);
		}

		public AliasData[] newArray(int arg0) {
			return new AliasData[arg0];
		}
	};
	
	public AliasData(Parcel p) {
		readFromParcel(p);
	}

	private void readFromParcel(Parcel p) {
		this.pre = p.readString();
		this.post = p.readString();
		this.setEnabled((p.readInt() == 0) ? false : true);
		// Appended field: older parcels without it still unparcel via
		// dataAvail(); missing → INHERIT (behaviour-preserving).
		if (p.dataAvail() > 0) {
			this.localEcho = AliasLocalEcho.fromOrdinalSafe(p.readInt());
		} else {
			this.localEcho = AliasLocalEcho.INHERIT;
		}
		// Second append: nested Set Variable rows. Missing → empty list,
		// same as a profile that never had them. Always consume the count
		// when present so a Map of aliases stays in step across the binder.
		this.setVariables = new ArrayList<SetVariableResponder>();
		if (p.dataAvail() > 0) {
			int n = p.readInt();
			for (int i = 0; i < n; i++) {
				SetVariableResponder r = p.readParcelable(
						SetVariableResponder.class.getClassLoader());
				if (r != null) {
					this.setVariables.add(r);
				}
			}
		}
	}



	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel o, int flags) {
		o.writeString(this.pre);
		o.writeString(this.post);
		if(this.enabled) {
			o.writeInt(1);
		} else {
			o.writeInt(0);
		}
		o.writeInt(localEcho != null ? localEcho.ordinal() : AliasLocalEcho.INHERIT.ordinal());
		List<SetVariableResponder> list = getSetVariables();
		o.writeInt(list.size());
		for (SetVariableResponder r : list) {
			o.writeParcelable(r, flags);
		}
	}

	public String getPre() {
		return pre;
	}

	public void setPre(String pre) {
		this.pre = pre;
	}

	public String getPost() {
		return post;
	}

	public void setPost(String post) {
		this.post = post;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public AliasLocalEcho getLocalEcho() {
		return localEcho != null ? localEcho : AliasLocalEcho.INHERIT;
	}

	public void setLocalEcho(AliasLocalEcho localEcho) {
		this.localEcho = localEcho != null ? localEcho : AliasLocalEcho.INHERIT;
	}

	/**
	 * Set Variable rows that run when this alias matches. Empty by default.
	 * Never null.
	 */
	public List<SetVariableResponder> getSetVariables() {
		if (setVariables == null) {
			setVariables = new ArrayList<SetVariableResponder>();
		}
		return setVariables;
	}

	public void setSetVariables(List<SetVariableResponder> setVariables) {
		this.setVariables = copySetVariables(setVariables);
	}

	private static List<SetVariableResponder> copySetVariables(
			List<SetVariableResponder> in) {
		List<SetVariableResponder> out = new ArrayList<SetVariableResponder>();
		if (in == null) {
			return out;
		}
		for (SetVariableResponder r : in) {
			if (r != null) {
				out.add(r.copy());
			}
		}
		return out;
	}
	
	
	
	
	
}
