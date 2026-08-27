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
				applyItemAttributes(current_item,
						a.getValue("", BaseParser.ATTR_NAME),
						a.getValue("", BaseParser.ATTR_HOST),
						a.getValue("", BaseParser.ATTR_PORT),
						a.getValue("", BaseParser.ATTR_DATEPLAYED),
						a.getValue("", BaseParser.ATTR_DESCRIPTION),
						a.getValue("", BaseParser.ATTR_OFFLINE),
						a.getValue("", BaseParser.ATTR_TLS),
						a.getValue("", BaseParser.ATTR_FAVORITE));
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

	/**
	 * Apply launcher {@code <item>} attributes onto a connection.
	 * Absent {@code favorite}/{@code tls} means false, same as before those
	 * attributes existed. Package-visible for JVM round-trip tests.
	 */
	static void applyItemAttributes(MudConnection dest, String name, String host,
			String port, String lastPlayed, String description, String offlineAttr,
			String tlsAttr, String favoriteAttr) {
		dest.setDisplayName(name == null ? "Mud" : name);
		dest.setHostName(host == null ? "host not set" : host);
		dest.setPortString(port == null ? "4002" : port);
		dest.setLastPlayed(lastPlayed == null ? "11-25-2010 11:53am" : lastPlayed);
		dest.setDescription(description != null ? description : "");
		boolean offline = attrTrue(offlineAttr)
				|| BuiltinTutorial.isTutorialHost(dest.getHostName());
		dest.setOffline(offline);
		dest.setUseTls(attrTrue(tlsAttr));
		dest.setFavorite(attrTrue(favoriteAttr));
	}

	static boolean attrTrue(String value) {
		return "true".equalsIgnoreCase(value) || "1".equals(value);
	}

}
