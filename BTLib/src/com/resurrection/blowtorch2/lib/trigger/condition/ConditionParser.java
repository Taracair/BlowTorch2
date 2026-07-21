package com.resurrection.blowtorch2.lib.trigger.condition;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionGroup.Op;

import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.StartElementListener;

/**
 * SAX load / XML save for {@code <conditions>} under a trigger or timer.
 */
public final class ConditionParser {

	private ConditionParser() {
	}

	public static void registerListeners(Element trigger, final TriggerData currentTrigger) {
		Element conditions = trigger.getChild(BasePluginParser.TAG_CONDITIONS);
		conditions.setStartElementListener(new StartElementListener() {
			public void start(Attributes a) {
				ConditionGroup group = new ConditionGroup();
				group.setOp(Op.fromXml(a.getValue("", BasePluginParser.ATTR_CONDITIONS_OP)));
				currentTrigger.setConditions(group);
			}
		});
		conditions.setEndElementListener(new EndElementListener() {
			public void end() {
				// no-op; children already attached
			}
		});

		Element condition = conditions.getChild(BasePluginParser.TAG_CONDITION);
		condition.setStartElementListener(new StartElementListener() {
			public void start(Attributes a) {
				ConditionLeaf leaf = parseConditionLeaf(a);
				if (leaf == null) {
					return;
				}
				ConditionGroup group = currentTrigger.getConditions();
				if (group == null) {
					group = new ConditionGroup();
					currentTrigger.setConditions(group);
				}
				group.getChildren().add(leaf);
			}
		});
	}

	public static void registerListeners(Element timer, final TimerData currentTimer) {
		Element conditions = timer.getChild(BasePluginParser.TAG_CONDITIONS);
		conditions.setStartElementListener(new StartElementListener() {
			public void start(Attributes a) {
				ConditionGroup group = new ConditionGroup();
				group.setOp(Op.fromXml(a.getValue("", BasePluginParser.ATTR_CONDITIONS_OP)));
				currentTimer.setConditions(group);
			}
		});
		conditions.setEndElementListener(new EndElementListener() {
			public void end() {
				// no-op; children already attached
			}
		});

		Element condition = conditions.getChild(BasePluginParser.TAG_CONDITION);
		condition.setStartElementListener(new StartElementListener() {
			public void start(Attributes a) {
				ConditionLeaf leaf = parseConditionLeaf(a);
				if (leaf == null) {
					return;
				}
				ConditionGroup group = currentTimer.getConditions();
				if (group == null) {
					group = new ConditionGroup();
					currentTimer.setConditions(group);
				}
				group.getChildren().add(leaf);
			}
		});
	}

	private static ConditionLeaf parseConditionLeaf(Attributes a) {
		ConditionLeaf leaf = new ConditionLeaf();
		ConditionType type = ConditionType.fromXml(
				a.getValue("", BasePluginParser.ATTR_CONDITION_TYPE));
		if (type == null) {
			return null;
		}
		leaf.setType(type);
		String name = a.getValue("", BasePluginParser.ATTR_NAME);
		if (name == null) {
			name = a.getValue("", BasePluginParser.ATTR_CONDITION_KEY);
		}
		leaf.setName(name != null ? name : "");
		String plugin = a.getValue("", BasePluginParser.ATTR_CONDITION_PLUGIN);
		leaf.setPlugin(plugin != null ? plugin : "");
		String value = a.getValue("", BasePluginParser.ATTR_CONDITION_VALUE);
		leaf.setValue(value != null ? value : "");
		return leaf;
	}

	public static void saveConditionsToXML(XmlSerializer out, TriggerData trigger)
			throws IllegalArgumentException, IllegalStateException, IOException {
		if (trigger == null) {
			return;
		}
		saveConditionsToXML(out, trigger.getConditions());
	}

	public static void saveConditionsToXML(XmlSerializer out, TimerData timer)
			throws IllegalArgumentException, IllegalStateException, IOException {
		if (timer == null) {
			return;
		}
		saveConditionsToXML(out, timer.getConditions());
	}

	private static void saveConditionsToXML(XmlSerializer out, ConditionGroup group)
			throws IllegalArgumentException, IllegalStateException, IOException {
		if (group == null || group.isEmpty()) {
			return;
		}
		out.startTag("", BasePluginParser.TAG_CONDITIONS);
		out.attribute("", BasePluginParser.ATTR_CONDITIONS_OP,
				group.getOp() != null ? group.getOp().getXmlValue() : Op.AND.getXmlValue());
		for (ConditionLeaf leaf : group.getChildren()) {
			if (leaf == null || leaf.getType() == null) {
				continue;
			}
			out.startTag("", BasePluginParser.TAG_CONDITION);
			out.attribute("", BasePluginParser.ATTR_CONDITION_TYPE, leaf.getType().getXmlValue());
			out.attribute("", BasePluginParser.ATTR_NAME, leaf.getName());
			if (leaf.getPlugin() != null && leaf.getPlugin().length() > 0) {
				out.attribute("", BasePluginParser.ATTR_CONDITION_PLUGIN, leaf.getPlugin());
			}
			if (leaf.getType() == ConditionType.VARIABLE_EQUALS
					|| (leaf.getValue() != null && leaf.getValue().length() > 0)) {
				out.attribute("", BasePluginParser.ATTR_CONDITION_VALUE, leaf.getValue());
			}
			out.endTag("", BasePluginParser.TAG_CONDITION);
		}
		out.endTag("", BasePluginParser.TAG_CONDITIONS);
	}
}
