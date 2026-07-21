package com.resurrection.blowtorch2.lib.trigger.condition;

import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Root AND/OR group of {@link ConditionLeaf} nodes. Empty group = always true.
 */
public class ConditionGroup implements Parcelable {

	public enum Op {
		AND("and"),
		OR("or");

		private final String xmlValue;

		Op(String xmlValue) {
			this.xmlValue = xmlValue;
		}

		public String getXmlValue() {
			return xmlValue;
		}

		public static Op fromXml(String raw) {
			if (raw != null && "or".equalsIgnoreCase(raw.trim())) {
				return OR;
			}
			return AND;
		}
	}

	private Op op;
	private List<ConditionLeaf> children;

	public ConditionGroup() {
		op = Op.AND;
		children = new ArrayList<ConditionLeaf>();
	}

	public ConditionGroup copy() {
		ConditionGroup tmp = new ConditionGroup();
		tmp.op = this.op;
		for (ConditionLeaf leaf : children) {
			tmp.children.add(leaf.copy());
		}
		return tmp;
	}

	public boolean isEmpty() {
		return children == null || children.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof ConditionGroup)) {
			return false;
		}
		ConditionGroup other = (ConditionGroup) o;
		if (op != other.op) {
			return false;
		}
		if (children.size() != other.children.size()) {
			return false;
		}
		for (int i = 0; i < children.size(); i++) {
			if (!children.get(i).equals(other.children.get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int h = op != null ? op.hashCode() : 0;
		for (ConditionLeaf leaf : children) {
			h = 31 * h + leaf.hashCode();
		}
		return h;
	}

	public Op getOp() {
		return op;
	}

	public void setOp(Op op) {
		this.op = op != null ? op : Op.AND;
	}

	public List<ConditionLeaf> getChildren() {
		return children;
	}

	public void setChildren(List<ConditionLeaf> children) {
		this.children = children != null ? children : new ArrayList<ConditionLeaf>();
	}

	private ConditionGroup(Parcel in) {
		op = Op.fromXml(in.readString());
		int n = in.readInt();
		children = new ArrayList<ConditionLeaf>(n);
		for (int i = 0; i < n; i++) {
			ConditionLeaf leaf = in.readParcelable(ConditionLeaf.class.getClassLoader());
			if (leaf != null) {
				children.add(leaf);
			}
		}
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(op != null ? op.getXmlValue() : Op.AND.getXmlValue());
		out.writeInt(children.size());
		for (ConditionLeaf leaf : children) {
			out.writeParcelable(leaf, 0);
		}
	}

	public static final Parcelable.Creator<ConditionGroup> CREATOR =
			new Parcelable.Creator<ConditionGroup>() {
				public ConditionGroup createFromParcel(Parcel source) {
					return new ConditionGroup(source);
				}

				public ConditionGroup[] newArray(int size) {
					return new ConditionGroup[size];
				}
			};
}
