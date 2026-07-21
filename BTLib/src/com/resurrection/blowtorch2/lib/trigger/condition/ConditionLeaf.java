package com.resurrection.blowtorch2.lib.trigger.condition;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Single condition: trigger enabled/disabled or session variable equals/exists.
 */
public class ConditionLeaf implements Parcelable {

	private ConditionType type;
	private String name;
	private String plugin;
	private String value;

	public ConditionLeaf() {
		type = ConditionType.TRIGGER_ENABLED;
		name = "";
		plugin = "";
		value = "";
	}

	public ConditionLeaf(ConditionType type, String name, String plugin, String value) {
		this.type = type != null ? type : ConditionType.TRIGGER_ENABLED;
		this.name = name != null ? name : "";
		this.plugin = plugin != null ? plugin : "";
		this.value = value != null ? value : "";
	}

	public ConditionLeaf copy() {
		return new ConditionLeaf(type, name, plugin, value);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof ConditionLeaf)) {
			return false;
		}
		ConditionLeaf other = (ConditionLeaf) o;
		if (type != other.type) {
			return false;
		}
		if (!name.equals(other.name)) {
			return false;
		}
		if (!plugin.equals(other.plugin)) {
			return false;
		}
		return value.equals(other.value);
	}

	@Override
	public int hashCode() {
		int h = type != null ? type.hashCode() : 0;
		h = 31 * h + name.hashCode();
		h = 31 * h + plugin.hashCode();
		h = 31 * h + value.hashCode();
		return h;
	}

	public ConditionType getType() {
		return type;
	}

	public void setType(ConditionType type) {
		this.type = type != null ? type : ConditionType.TRIGGER_ENABLED;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name != null ? name : "";
	}

	public String getPlugin() {
		return plugin;
	}

	public void setPlugin(String plugin) {
		this.plugin = plugin != null ? plugin : "";
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value != null ? value : "";
	}

	/** Human-readable one-liner for lists. */
	public String summary() {
		switch (type) {
		case TRIGGER_ENABLED:
			return "Trigger " + qualifiedTriggerName() + " is enabled";
		case TRIGGER_DISABLED:
			return "Trigger " + qualifiedTriggerName() + " is disabled";
		case VARIABLE_EQUALS:
			return "Variable " + name + " equals " + value;
		case VARIABLE_EXISTS:
			return "Variable " + name + " exists";
		default:
			return type != null ? type.displayLabel() : "";
		}
	}

	/** {@code plugin:name} when plugin is set, else {@code name}. */
	public String qualifiedTriggerName() {
		if (plugin != null && plugin.length() > 0) {
			return plugin + ":" + name;
		}
		return name != null ? name : "";
	}

	private ConditionLeaf(Parcel in) {
		String typeXml = in.readString();
		type = ConditionType.fromXml(typeXml);
		if (type == null) {
			type = ConditionType.TRIGGER_ENABLED;
		}
		name = in.readString();
		if (name == null) {
			name = "";
		}
		plugin = in.readString();
		if (plugin == null) {
			plugin = "";
		}
		value = in.readString();
		if (value == null) {
			value = "";
		}
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(type != null ? type.getXmlValue() : ConditionType.TRIGGER_ENABLED.getXmlValue());
		out.writeString(name);
		out.writeString(plugin);
		out.writeString(value);
	}

	public static final Parcelable.Creator<ConditionLeaf> CREATOR =
			new Parcelable.Creator<ConditionLeaf>() {
				public ConditionLeaf createFromParcel(Parcel source) {
					return new ConditionLeaf(source);
				}

				public ConditionLeaf[] newArray(int size) {
					return new ConditionLeaf[size];
				}
			};
}
