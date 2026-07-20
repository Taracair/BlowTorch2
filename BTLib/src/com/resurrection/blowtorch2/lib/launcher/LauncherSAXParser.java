package com.resurrection.blowtorch2.lib.launcher;

import java.util.ArrayList;
import java.util.HashMap;

import org.xml.sax.Attributes;

import android.content.Context;
import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.RootElement;
import android.sax.StartElementListener;
import android.util.Xml;

import com.resurrection.blowtorch2.lib.settings.BaseParser;

public class LauncherSAXParser extends BaseParser {

	final MudConnection current_item = new MudConnection();
	final ArrayList<ServerAccount> current_accounts = new ArrayList<ServerAccount>();

	public LauncherSAXParser(String location, Context context) {
		super(location, context);

	}

	public LauncherSettings load() {

		final LauncherSettings tmp = new LauncherSettings();

		RootElement root = new RootElement("root");
		Element launcher = root.getChild(BaseParser.TAG_LAUNCHER);
		Element item = launcher.getChild(BaseParser.TAG_ITEM);
		Element account = item.getChild(BaseParser.TAG_ACCOUNT);

		final HashMap<String,MudConnection> items_read = new HashMap<String,MudConnection>();

		launcher.setStartElementListener(new StartElementListener() {

			public void start(Attributes a) {
				tmp.setCurrentVersion( (a.getValue("",BaseParser.ATTR_VERSION) == null) ? "v1.0.4" : a.getValue("",BaseParser.ATTR_VERSION));
			}
		});

		item.setStartElementListener(new StartElementListener() {

			public void start(Attributes a) {
				current_accounts.clear();
				current_item.setDisplayName( (a.getValue("",BaseParser.ATTR_NAME) == null) ? "Mud" : a.getValue("",BaseParser.ATTR_NAME));
				current_item.setHostName((a.getValue("",BaseParser.ATTR_HOST) == null) ? "host not set" : a.getValue("",BaseParser.ATTR_HOST));
				current_item.setPortString((a.getValue("",BaseParser.ATTR_PORT) == null) ? "4002" : a.getValue("",BaseParser.ATTR_PORT));
				current_item.setLastPlayed((a.getValue("",BaseParser.ATTR_DATEPLAYED) == null) ? "11-25-2010 11:53am" : a.getValue("",BaseParser.ATTR_DATEPLAYED));
				String desc = a.getValue("", BaseParser.ATTR_DESCRIPTION);
				current_item.setDescription(desc != null ? desc : "");
				String offlineAttr = a.getValue("", BaseParser.ATTR_OFFLINE);
				boolean offline = "true".equalsIgnoreCase(offlineAttr)
						|| "1".equals(offlineAttr)
						|| BuiltinTutorial.isTutorialHost(current_item.getHostName());
				current_item.setOffline(offline);
			}

		});

		account.setStartElementListener(new StartElementListener() {
			public void start(Attributes a) {
				ServerAccount acc = new ServerAccount();
				acc.setLabel(a.getValue("", BaseParser.ATTR_ACCOUNT_LABEL));
				acc.setLogin(a.getValue("", BaseParser.ATTR_ACCOUNT_LOGIN));
				acc.setPassword(a.getValue("", BaseParser.ATTR_ACCOUNT_PASSWORD));
				acc.setMail(a.getValue("", BaseParser.ATTR_ACCOUNT_MAIL));
				if (!acc.isEmpty()) {
					current_accounts.add(acc);
				}
			}
		});

		item.setEndElementListener(new EndElementListener() {
			public void end() {
				MudConnection copy = current_item.copy();
				ArrayList<ServerAccount> accounts = new ArrayList<ServerAccount>();
				for (ServerAccount acc : current_accounts) {
					accounts.add(acc.copy());
				}
				copy.setAccounts(accounts);
				items_read.put(copy.getDisplayName(), copy);
			}
		});

		launcher.setEndElementListener(new EndElementListener() {

			public void end() {
				tmp.setList(items_read);
			}

		});

		try {
			Xml.parse(this.getInputStream(), Xml.Encoding.UTF_8, root.getContentHandler());
		} catch (Exception e) {
			//throw new RuntimeException(e);
			e.printStackTrace();
			//get all the files in private storage and list them.
			return null;
		}

		return tmp;
	}

}
