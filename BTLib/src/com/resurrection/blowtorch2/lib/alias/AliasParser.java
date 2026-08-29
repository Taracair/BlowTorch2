package com.resurrection.blowtorch2.lib.alias;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import android.sax.Element;

import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponderParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;

public class AliasParser {
	//PluginSettings settings = null;
	//final AliasData current_alias = new AliasData();
	//public AliasParser(Element root, PluginSettings settings) {
		//this.settings = settings;
		//registerListeners(root);
	//}
	public static void registerListeners(Element root,PluginParser.NewItemCallback callback,AliasData current_alias) {
		Element aliases = root.getChild(BasePluginParser.TAG_ALIASES);
		Element alias = aliases.getChild(BasePluginParser.TAG_ALIAS);
		// Copy on end(), not start(): nested <setVariable> must attach first.
		// ConnectionSetttingsParser and PluginParser both come through here.
		alias.setElementListener(new AliasElementListener(callback,current_alias));
		SetVariableResponderParser.registerListeners(alias, current_alias, null, null);
	}
	
	public static void saveAliasToXML(XmlSerializer out,AliasData data) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_ALIAS);
		out.attribute("", BasePluginParser.ATTR_PRE, data.getPre());
		out.attribute("", BasePluginParser.ATTR_POST, data.getPost());
		if(data.isEnabled()) {
			out.attribute("", "enabled", (data.isEnabled() == true) ? "true" : "false");
		}
		String localEcho = data.getLocalEcho().toAttribute();
		if (localEcho != null) {
			out.attribute("", BasePluginParser.ATTR_LOCAL_ECHO, localEcho);
		}
		for (SetVariableResponder r : data.getSetVariables()) {
			if (r != null) {
				r.saveResponderToXML(out);
			}
		}
		out.endTag("", BasePluginParser.TAG_ALIAS);
	}
	
	
	
	
	
}
