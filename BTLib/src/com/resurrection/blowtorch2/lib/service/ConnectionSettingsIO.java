/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import android.Manifest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keplerproject.luajava.LuaState;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.button.SlickButtonData;
import com.resurrection.blowtorch2.lib.launcher.BuiltinTutorial;
import com.resurrection.blowtorch2.lib.service.plugin.ConnectionSettingsPlugin;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ConnectionSetttingsParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.Option;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.VersionProbeParser;
import com.resurrection.blowtorch2.lib.settings.ColorSetSettings;
import com.resurrection.blowtorch2.lib.settings.HyperSAXParser;
import com.resurrection.blowtorch2.lib.settings.HyperSettings;
import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import android.widget.RelativeLayout;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

/** Settings export/import/load orchestration for a Connection. */
final class ConnectionSettingsIO {

	/** Minimum starting font size for the fit routine. */
	private static final float MIN_FONT_SIZE = 8.0f;

	/** Target char width for fit routine. */
	private static final float TARGET_FIT_WIDTH = 80.0f;

	/** Value of -3. */
	private static final int NEGATIVE_THREE = -3;

	/** Value of -2. */
	private static final int NEGATIVE_TWO = -2;

	/** String name of the default output window. */
	private static final String MAIN_WINDOW = "mainDisplay";

	private final Pattern xmlExtensionPattern = Pattern.compile("^.+\\.[xX][mM][lL]$");
	private final Matcher xmlExtensionMatcher = xmlExtensionPattern.matcher("");

	private final Connection host;

	ConnectionSettingsIO(final Connection host) {
		this.host = host;
	}

	/** The main starting point for the save settings routine. This is called for a few different locations. */
	void saveMainSettings() {
		if (host.mSettings == null) {
			return;
		}
		Pattern invalidchars = Pattern.compile("\\W");
		Matcher replacebadchars = invalidchars.matcher(host.mDisplay);
		String prefsname = replacebadchars.replaceAll("");
		prefsname = prefsname.replaceAll("/", "");
		String rootPath = prefsname + ".xml";
		exportSettings(new File(host.mService.getApplicationContext().getFilesDir(), rootPath).getAbsolutePath());
	}
	
	/** Export settings routine. Called from either the main settings save routine or the export settings dialog.
	 * 
	 * @param path Absolute filesystem path, or a bare file name (resolved under the default settings directory).
	 */
	void exportSettings(final String path) {
		boolean domessage = false;
		boolean addextra = false;
		String filename = path == null ? "" : path.trim();
		Context appCtx = host.mService.getApplicationContext();
		boolean external = SDCardUtils.hasAllFilesAccess()
				|| ContextCompat.checkSelfPermission(appCtx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED;
		File cachedir = SDCardUtils.resolveCacheDir(appCtx);
		String btdir = SDCardUtils.resolveBlowTorchSubdir(appCtx, SDCardUtils.SUBDIR_SETTINGS).getAbsolutePath();

		if (!filename.startsWith("/")) {
			if (TextUtils.isEmpty(filename)) {
				host.mService.dispatchToast("Export failed: enter a file name.", true);
				return;
			}
			domessage = true;
			String customDir = readDefaultSettingsDirectoryOption();
			if (SDCardUtils.isContentUri(customDir)
					&& SDCardUtils.mapTreeUriToFile(Uri.parse(customDir)) == null) {
				xmlExtensionMatcher.reset(filename);
				if (!xmlExtensionMatcher.matches()) {
					filename = filename + ".xml";
					addextra = true;
				}
				exportSettingsToTreeUri(appCtx, Uri.parse(customDir), filename, domessage, addextra);
				return;
			}
			File destDir = SDCardUtils.resolveDefaultSettingsDirectory(appCtx, external, customDir);
			if (!destDir.exists()) {
				//noinspection ResultOfMethodCallIgnored
				destDir.mkdirs();
			}
			btdir = destDir.getAbsolutePath();
			filename = new File(destDir, filename).getAbsolutePath();
			xmlExtensionMatcher.reset(filename);
			if (!xmlExtensionMatcher.matches()) {
				filename = filename + ".xml";
				addextra = true;
			}
		}
		
		//try to output the file.
		boolean passed = true;
		File file = new File(filename);
		FileOutputStream fos = null;
		File tmpfile = null;
		String filesDirPath = appCtx.getFilesDir().getAbsolutePath();
		boolean internalSettingsFile = false;
		String internalFileName = null;
		try {
			File filesDir = appCtx.getFilesDir().getCanonicalFile();
			File target = new File(filename).getCanonicalFile();
			if (filesDir.equals(target.getParentFile())) {
				internalSettingsFile = true;
				internalFileName = target.getName();
			}
		} catch (IOException e) {
			if (filename.startsWith(filesDirPath + File.separator)) {
				internalSettingsFile = true;
				internalFileName = filename.substring(filesDirPath.length() + 1);
			}
		}
		try {
			String foo = ConnectionSetttingsParser.outputXML(host.mSettings, host.mPlugins);
			byte[] xmlBytes = foo.getBytes("UTF-8");
			if (internalSettingsFile) {
				fos = appCtx.openFileOutput(internalFileName, Context.MODE_PRIVATE);
				fos.write(xmlBytes);
				fos.close();
				fos = null;
			} else {
				if (cachedir != null && !cachedir.exists()) {
					//noinspection ResultOfMethodCallIgnored
					cachedir.mkdirs();
				}
				tmpfile = File.createTempFile("settings", "xml", cachedir);
				fos = new FileOutputStream(tmpfile);
				fos.write(xmlBytes);
				fos.close();
				fos = null;
			}
		} catch (Exception e) {
			try {
				host.mService.dispatchSaveError(e.getLocalizedMessage());
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
			passed = false;
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// ignore
				}
				fos = null;
			}
			if (passed && !internalSettingsFile && tmpfile != null) {
				File makeme = new File(btdir);
				makeme.mkdirs();
				File parent = file.getParentFile();
				if (parent != null) {
					parent.mkdirs();
				}
				boolean success = tmpfile.renameTo(file);
				if (success) {
					Log.e("BT","file shadow copy success");
				} else {
					Log.e("BT","renameTo failed, falling back to stream copy");
					FileInputStream copyIn = null;
					FileOutputStream copyOut = null;
					try {
						copyIn = new FileInputStream(tmpfile);
						copyOut = new FileOutputStream(file);
						byte[] buf = new byte[4096];
						int len;
						while ((len = copyIn.read(buf)) > 0) {
							copyOut.write(buf, 0, len);
						}
						copyOut.flush();
						Log.e("BT","stream copy success");
					} catch (IOException copyEx) {
						Log.e("BT","stream copy failed: " + copyEx.getMessage());
						passed = false;
						try {
							host.mService.dispatchSaveError(copyEx.getLocalizedMessage());
						} catch (RemoteException e1) {
							e1.printStackTrace();
						}
					} finally {
						if (copyIn != null) try { copyIn.close(); } catch (IOException ignored) {}
						if (copyOut != null) try { copyOut.close(); } catch (IOException ignored) {}
					}
					tmpfile.delete();
				}
			}
		}
		
		
		for (String link : host.mLinkMap.keySet()) {
			ArrayList<String> plugins  = host.mLinkMap.get(link);
			boolean doExport = false;
			String fullpath = "";
			for (String plugin : plugins) {
				Plugin p = host.mPluginMap.get(plugin);
				if (p.getSettings().isDirty()) {
					doExport = true;
					fullpath = p.getFullPath();
				}
			}
			
			if (doExport) {
				XmlSerializer out = Xml.newSerializer();
				StringWriter writer = new StringWriter();
				
				File extfile = null;
				FileOutputStream extfilestream = null;
				passed = true;
				File extcachedir = SDCardUtils.resolveCacheDir(host.mService.getApplicationContext());
				//File cachedir = this.getContext().getCacheDir();
				//FileOutputStream fos = null;
				File tmppluginfile = null;
				String currentplugin = "";
				try {
				
				out.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
				out.setOutput(writer);
				out.startDocument("UTF-8", true);
				out.startTag("", "blowtorch");
				out.attribute("", "xmlversion", "2");
				out.startTag("", "plugins");
				
				for (String plugin :plugins) {
					currentplugin = plugin;
					Plugin p = host.mPluginMap.get(plugin);
					PluginParser.saveToXml(out, p);
					p.getSettings().setDirty(false);
				}
				
				out.endTag("", "plugins");
				out.endTag("", "blowtorch");
				out.endDocument();
				
				tmppluginfile = File.createTempFile("plugin_settings", "xml",extcachedir);
				
				extfile = new File(fullpath);
				extfilestream = new FileOutputStream(tmppluginfile);
				extfilestream.write(writer.toString().getBytes());
				extfilestream.close();
				} catch(Exception e) {
					try {
						host.mService.dispatchPluginSaveError(currentplugin,e.getLocalizedMessage());
					} catch (RemoteException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					passed = false;
				} finally {
					if(extfilestream != null) {
						try {
							extfilestream.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					if(passed) {
						long start = System.currentTimeMillis();
						boolean success = tmppluginfile.renameTo(extfile);
						int duraction = (int)(System.currentTimeMillis() - start);
						if(success) {
							Log.e("BT","Plugin shadow copy success, took " + duraction);
						} else {
							Log.e("BT","Plugin renameTo failed, falling back to stream copy");
							FileInputStream pCopyIn = null;
							FileOutputStream pCopyOut = null;
							try {
								pCopyIn = new FileInputStream(tmppluginfile);
								pCopyOut = new FileOutputStream(extfile);
								byte[] buf = new byte[4096];
								int len;
								while ((len = pCopyIn.read(buf)) > 0) {
									pCopyOut.write(buf, 0, len);
								}
								pCopyOut.flush();
								Log.e("BT","Plugin stream copy success");
							} catch (IOException copyEx) {
								Log.e("BT","Plugin stream copy failed: " + copyEx.getMessage());
							} finally {
								if (pCopyIn != null) try { pCopyIn.close(); } catch (IOException ignored) {}
								if (pCopyOut != null) try { pCopyOut.close(); } catch (IOException ignored) {}
							}
							tmppluginfile.delete();
						}
					}
				}
			}
			
		}
		
		
		if (domessage) {
			String message = "Settings Exported to " + filename;
			if (addextra) {
				message = message + "\n.xml extension added.";
			}
			host.mService.dispatchToast(message, true);
		}
	}

	/** Write settings XML into a SAF document tree when the default directory is a content:// URI. */
	void exportSettingsToTreeUri(Context appCtx, Uri treeUri, String displayName,
			boolean domessage, boolean addextra) {
		DocumentFile tree = DocumentFile.fromTreeUri(appCtx, treeUri);
		if (tree == null || !tree.canWrite()) {
			host.mService.dispatchToast("Export failed: cannot write to selected folder.", true);
			return;
		}
		String name = displayName;
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		DocumentFile existing = tree.findFile(name);
		if (existing != null) {
			existing.delete();
		}
		DocumentFile outFile = tree.createFile("application/xml", name);
		if (outFile == null) {
			host.mService.dispatchToast("Export failed: could not create file in selected folder.", true);
			return;
		}
		OutputStream out = null;
		try {
			String foo = ConnectionSetttingsParser.outputXML(host.mSettings, host.mPlugins);
			out = appCtx.getContentResolver().openOutputStream(outFile.getUri());
			if (out == null) {
				host.mService.dispatchToast("Export failed: could not open output stream.", true);
				return;
			}
			out.write(foo.getBytes("UTF-8"));
			out.flush();
			if (domessage) {
				String message = "Settings Exported to " + name;
				if (addextra) {
					message = message + "\n.xml extension added.";
				}
				host.mService.dispatchToast(message, true);
			}
		} catch (Exception e) {
			try {
				host.mService.dispatchSaveError(e.getLocalizedMessage());
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
		}
	}
	/** The real workhorse method for importing settings.
	 * 
	 * @param path The path of the settings to load.
	 * @param save flag to save the settings after loading.
	 * @param loadmessage verb for loading(true) or importing(false)
	 */
	void importSettings(final String path, final boolean save, final boolean loadmessage) {
		host.shutdownPlugins();
		
		String verb = null;
		if (loadmessage) {
			verb = "Loading";
		} else {
			verb = "Importing";
		}
		
		VersionProbeParser vpp = new VersionProbeParser(path, host.mService.getApplicationContext());

		
		try {
			boolean isLegacy = vpp.isLegacy();
			if (isLegacy) {
				Log.e("XMLPARSE", "LOADING V1 SETTINGS FROM PATH: " + path);
				
				if(!path.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())) {
					//internal legacy file being loaded, export the settings to the external blowtorch directory.
					//convert path to external settings directory.
					//get the package path.
					//host.mService.getApplicationContext().getFilesDir();
					File f = new File(host.mService.getApplicationContext().getFilesDir(),path);
					String file = f.getName();
					//File p = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/BlowTorch/recovered/");
					File p = new File(SDCardUtils.resolveAppExternalDir(host.mService), "recovered");
					if(!p.exists()) {
						p.mkdirs();
					}
					File newfile = new File(p,file);
					
					if(!newfile.exists()) {
						newfile.createNewFile();
					}
					
				    InputStream in = new FileInputStream(f);
				    OutputStream out = new FileOutputStream(newfile);

				    
				    // Transfer bytes from in to out
				    byte[] buf = new byte[1024];
				    int len;
				    while ((len = in.read(buf)) > 0) {
				        out.write(buf, 0, len);
				    }
				    in.close();
				    out.close();
					
				}
				
				HyperSAXParser p = new HyperSAXParser(path, host.mService.getApplicationContext());
				HyperSettings s = p.load();
				
				ApplicationInfo ai = null;
				try {
					ai = host.mService.getApplicationContext().getPackageManager().getApplicationInfo(host.mService.getPackageName(), PackageManager.GET_META_DATA);
				} catch (NameNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String dataDir = ai.dataDir;
				
				//load up the default settings and then merge the old settings into the new settings.
				ArrayList<Plugin> tmpplugs = new ArrayList<Plugin>();
				ConnectionSetttingsParser newsettings = new ConnectionSetttingsParser(null, host.mService.getApplicationContext(), tmpplugs, host.mHandler, host);
				tmpplugs = newsettings.load(host,dataDir);
				
				Plugin buttonwindow = tmpplugs.get(1);
				ConnectionSettingsPlugin root_settings = (ConnectionSettingsPlugin)tmpplugs.get(0);
				
				if (path != null) { //import old buttons
				
				//slag out the old settings and RAM them into the new ones.
				LuaState pL = buttonwindow.getLuaState();
				
				pL.newTable();
				for (String key : s.getButtonSets().keySet()) {
					ColorSetSettings defaults = s.getSetSettings().get(key);
					//Vector<SlickButtonData> data = s.getButtonSets().get(key);
					pL.newTable();
					
					if (defaults.getPrimaryColor() != SlickButtonData.DEFAULT_COLOR) {
						pL.pushString("primaryColor");
						pL.pushNumber(defaults.getPrimaryColor());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getSelectedColor() != SlickButtonData.DEFAULT_SELECTED_COLOR) {
						pL.pushString("selectedColor");
						pL.pushNumber(defaults.getSelectedColor());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getFlipColor() != SlickButtonData.DEFAULT_FLIP_COLOR) {
						pL.pushString("flipColor");
						pL.pushNumber(defaults.getFlipColor());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getLabelColor() != SlickButtonData.DEFAULT_LABEL_COLOR) {
						pL.pushString("labelColor");
						pL.pushNumber(defaults.getLabelColor());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getFlipLabelColor() != SlickButtonData.DEFAULT_FLIPLABEL_COLOR) {
						pL.pushString("flipLabelColor");
						pL.pushNumber(defaults.getFlipLabelColor());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getButtonWidth() != SlickButtonData.DEFAULT_BUTTON_WDITH) {
						pL.pushString("width");
						pL.pushNumber(defaults.getButtonWidth());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getButtonHeight() != SlickButtonData.DEFAULT_BUTTON_HEIGHT) {
						pL.pushString("height");
						pL.pushNumber(defaults.getButtonHeight());
						pL.setTable(NEGATIVE_THREE);
					}
					
					if (defaults.getLabelSize() != SlickButtonData.DEFAULT_LABEL_SIZE) {
						pL.pushString("labelSize");
						pL.pushNumber(defaults.getLabelSize());
						pL.setTable(NEGATIVE_THREE);
					}
					
					pL.setField(NEGATIVE_TWO, key);
				}
				
				pL.setGlobal("buttonset_defaults");
				
				pL.newTable();
				
				for (String name : s.getButtonSets().keySet()) {
					//String name = key;
					ColorSetSettings defaults = s.getSetSettings().get(name);
					Vector<SlickButtonData> data = s.getButtonSets().get(name);
					pL.newTable();
					int counter = 1;
					for (SlickButtonData button : data) {
						pL.newTable();
						if (defaults.getPrimaryColor() != button.getPrimaryColor()) {
							pL.pushString("primaryColor");
							pL.pushNumber(button.getPrimaryColor());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getSelectedColor() != button.getSelectedColor()) {
							pL.pushString("selectedColor");
							pL.pushNumber(button.getSelectedColor());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getFlipColor() != button.getFlipColor()) {
							pL.pushString("flipColor");
							pL.pushNumber(button.getFlipColor());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getLabelColor() != button.getLabelColor()) {
							pL.pushString("labelColor");
							pL.pushNumber(button.getLabelColor());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getFlipLabelColor() != button.getFlipLabelColor()) {
							pL.pushString("flipLabelColor");
							pL.pushNumber(button.getFlipLabelColor());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getButtonWidth() != button.getWidth()) {
							pL.pushString("width");
							pL.pushNumber(button.getWidth());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getButtonHeight() != button.getHeight()) {
							pL.pushString("height");
							pL.pushNumber(button.getHeight());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (defaults.getLabelSize() != button.getLabelSize()) {
							pL.pushString("labelSize");
							pL.pushNumber(button.getLabelSize());
							pL.setTable(NEGATIVE_THREE);
						}
						
						if (button.getTargetSet() != null && !button.getTargetSet().equals("")) {
							pL.pushString("switchTo");
							pL.pushString(button.getTargetSet());
							pL.setTable(NEGATIVE_THREE);
						}
						
						pL.pushString("x");
						pL.pushNumber(button.getX());
						pL.setTable(NEGATIVE_THREE);
						
						pL.pushString("y");
						pL.pushNumber(button.getY());
						pL.setTable(NEGATIVE_THREE);
						
						pL.pushString("label");
						pL.pushString(button.getLabel());
						pL.setTable(NEGATIVE_THREE);
						
						pL.pushString("command");
						pL.pushString(button.getText());
						pL.setTable(NEGATIVE_THREE);
						
						pL.pushString("flipLabel");
						pL.pushString(button.getFlipLabel());
						pL.setTable(NEGATIVE_THREE);
						
						pL.pushString("flipCommand");
						pL.pushString(button.getFlipCommand());
						pL.setTable(NEGATIVE_THREE);
						
						
						
						pL.rawSetI(NEGATIVE_TWO, counter);
						counter++;
					}
					
					pL.setField(NEGATIVE_TWO, name);
				}
				
				pL.setGlobal("buttonsets");
				
				pL.pushString(s.getLastSelected());
				pL.setGlobal("current_set");
				
				pL.getGlobal("legacyButtonsImported");
				if (pL.getLuaObject(-1).isFunction()) {
					pL.call(0, 0);
				}
				
				} else {
					//default settings are being loaded.
					//run the adjustment for the new buttons
					LuaState pL = buttonwindow.getLuaState();
					pL.getGlobal("debug");
					pL.getField(-1, "traceback");
					pL.getGlobal("alignDefaultButtons");
					if (pL.isFunction(-1)) {
						int ret = pL.pcall(0, 1, NEGATIVE_TWO);
						if (ret != 0) {
							host.dispatchLuaError(pL.getLuaObject(-1).getString());
						}
					} else {
						pL.pop(1);
					}
				}
				//s.getSetSettings();
				
				
				
				
				WindowToken tmp = root_settings.getSettings().getWindows().get("mainDisplay");
				tmp.importV1Settings(s);
				
				//handle button settings.
				SettingsGroup buttonops = buttonwindow.getSettings().getOptions();
				String hfedit = s.getHapticFeedbackMode();
				if (hfedit.equals("auto")) {
					buttonops.setOption("haptic_edit", Integer.toString(0));
				} else if (hfedit.equals("always")) {
					buttonops.setOption("haptic_edit", Integer.toString(1));
				} else if (hfedit.equals("none")) {
					buttonops.setOption("haptic_edit", Integer.toString(2));
				}
				
				String hfpress = s.getHapticFeedbackOnPress();
				if (hfpress.equals("auto")) {
					buttonops.setOption("haptic_press", Integer.toString(0));
				} else if (hfpress.equals("always")) {
					buttonops.setOption("haptic_press", Integer.toString(1));
				} else if (hfpress.equals("none")) {
					buttonops.setOption("haptic_press", Integer.toString(2));
				}
				
				String hfflip = s.getHapticFeedbackOnFlip();
				if (hfflip.equals("auto")) {
					buttonops.setOption("haptic_flip", Integer.toString(0));
				} else if (hfflip.equals("always")) {
					buttonops.setOption("haptic_flip", Integer.toString(1));
				} else if (hfflip.equals("none")) {
					buttonops.setOption("haptic_flip", Integer.toString(2));
				}
				String summary = Colorizer.getWhiteColor() + verb + " legacy settings file.\n";
				host.loadPlugins(tmpplugs, summary);
				root_settings.importV1Settings(s);
				if (!s.isRoundButtons()) {
					buttonops.setOption("button_roundness", Integer.toString(0));
				} 
			} else {
				int version = vpp.getVersionNumber();
				if (version == 2) {
					Log.e("XMLPARSE", "LOADING V2 SETTINGS FROM PATH: " + path);
					ArrayList<Plugin> tmpplugs = new ArrayList<Plugin>();
					ConnectionSetttingsParser csp = new ConnectionSetttingsParser(path, host.mService.getApplicationContext(), tmpplugs, host.mHandler, host);
					ApplicationInfo ai = null;
					try {
						ai = host.mService.getApplicationContext().getPackageManager().getApplicationInfo(host.mService.getPackageName(), PackageManager.GET_META_DATA);
					} catch (NameNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					String dataDir = ai.dataDir;
					tmpplugs = csp.load(host,dataDir);
					
					if (path == null) {
						Plugin buttonwindow = tmpplugs.get(1);
						//LuaState L = buttonwindow.getLuaState();
						LuaState pL = buttonwindow.getLuaState();
						pL.getGlobal("debug");
						pL.getField(-1, "traceback");
						pL.getGlobal("alignDefaultButtons");
						if (pL.isFunction(-1)) {
							int ret = pL.pcall(0, 1, NEGATIVE_TWO);
							if (ret != 0) {
								host.dispatchLuaError("ERROR IN DEFAULT BUTTONS:" + (pL.getLuaObject(-1).getString()));
							}
						} else {
							pL.pop(1);
						}
					}
					String summary = Colorizer.getWhiteColor() + verb + " settings file.\n";
					host.loadPlugins(tmpplugs, summary);
				} else {
					Log.e("XMLPARSE", "ERROR IN LOADING V2 SETTINGS, DID NOT FIND PROPER XMLVERSION NUMBER");
					try {
						host.mService.dispatchXMLError("Error " + verb.toLowerCase(Locale.US) + " settings, invalid or missing version attribute.\n");
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					return;
				}
				
			}
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
			try {
				host.mService.dispatchXMLError(e.getLocalizedMessage());
				return;
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
		}
		
		if (path == null || BuiltinTutorial.isTutorialHost(host.mHost)
				|| BuiltinTutorial.DISPLAY_NAME.equals(host.mDisplay)) {
			// New profiles + offline tutorial: readable phone text (~20), not 80-col squeeze.
			int fitted = calculate80CharFontSize();
			int use = Math.max(20, fitted);
			if (BuiltinTutorial.isTutorialHost(host.mHost)
					|| BuiltinTutorial.DISPLAY_NAME.equals(host.mDisplay)) {
				use = 20;
			}
			String fontStr = Integer.toString(use);
			host.mSettings.getSettings().getOptions().setOption("font_size", fontStr);
			host.mSettings.setLineSize(use);
			if (host.mWindows != null && !host.mWindows.isEmpty() && host.mWindows.get(0) != null
					&& host.mWindows.get(0).getSettings() != null) {
				host.mWindows.get(0).getSettings().setOption("font_size", fontStr);
			}
		}
		
		host.buildTriggerSystem();
		if (save) {
			saveMainSettings();
		}
	}
	
	/** Entry point to load the internal settings. */
	void loadInternalSettings() {
		Pattern invalidchars = Pattern.compile("\\W");
		Matcher replacebadchars = invalidchars.matcher(host.mDisplay);
		String prefsname = replacebadchars.replaceAll("");
		prefsname = prefsname.replaceAll("/", "");
		String rootPath = prefsname + ".xml";
		File settingsFile = new File(host.mService.getApplicationContext().getFilesDir(), rootPath);
		if (!settingsFile.exists()) {
			importSettings(null, false, true);
		} else {
			importSettings(rootPath, false, true);
		}
	}
	
	/** Build settings page routine. This is used by the settings loading routine.
	 * Inserts the main window's text/font settings (titled "Window") after the
	 * Program Settings "Display" group so Options is not two menus both named Window.
	 */
	@SuppressWarnings("deprecation")
	void buildSettingsPage() {
		if (host.mSettings.getSettings().getWindows().size() < 1) {
			WindowToken token = new WindowToken(MAIN_WINDOW, null, null, host.mDisplay);
			RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT);
			LayoutGroup g = new LayoutGroup();
			g.setType(LayoutGroup.LAYOUT_TYPE.normal);
			g.setLandscapeParams(p);
			g.setPortraitParams(p);
			host.mWindows.add(0, token);
		} else {
			host.mWindows.add(0, host.mSettings.getSettings().getWindows().get(MAIN_WINDOW));
		}
		
		host.mSettings.doBackgroundStartup();
		for (Plugin pl : host.mPlugins) {
			pl.doBackgroundStartup();
		}
		
		host.mSettings.buildAliases();
		for (Plugin pl : host.mPlugins) {
			if (pl != null && pl.isEnabled()) {
				pl.buildAliases();
			}
		}
		
		host.buildTriggerSystem();
		host.mWindows.get(0).getSettings().setListener(host.new WindowSettingsChangedListener(host.mWindows.get(0).getName()));
		int insertAt = indexAfterDisplayGroup(host.mSettings.getSettings().getOptions());
		host.mSettings.getSettings().getOptions().addOptionAt(host.mWindows.get(0).getSettings(), insertAt);
	}
	/** Place WindowToken settings right after Display (NAWS/orientation), else at index 0. */
	private static int indexAfterDisplayGroup(final SettingsGroup root) {
		if (root == null || root.getOptions() == null) {
			return 0;
		}
		java.util.ArrayList<Option> opts = root.getOptions();
		for (int i = 0; i < opts.size(); i++) {
			Option o = opts.get(i);
			if (o == null) {
				continue;
			}
			if ("display_group".equals(o.getKey()) || "Display".equals(o.getTitle())) {
				return i + 1;
			}
		}
		return 0;
	}
	/** Utility method that generates the font size necessary to fit 80 chars to the window width.
	 * 
	 * @return the font size that will produce nearest to 80 chars as possible.
	 */
	int calculate80CharFontSize() {
		int windowWidth = host.mService.getResources().getDisplayMetrics().widthPixels;
		if (host.mService.getResources().getDisplayMetrics().heightPixels > windowWidth) {
			windowWidth = host.mService.getResources().getDisplayMetrics().heightPixels;
		}
		float fontSize = MIN_FONT_SIZE;
		float delta = 1.0f;
		Paint p = new Paint();
		p.setTextSize(MIN_FONT_SIZE);
		//p.setTypeface(Typeface.createFromFile(service.getFontName()));
		p.setTypeface(Typeface.MONOSPACE);
		boolean done = false;
		
		float charWidth = p.measureText("A");
		float charsPerLine = windowWidth / charWidth;
		
		if (charsPerLine < TARGET_FIT_WIDTH) {
			//for QVGA screens, this test will always fail on the first step.
			done = true;
		} else {
			fontSize += delta;
			p.setTextSize(fontSize);
		}
		
		while (!done) {
			charWidth = p.measureText("A");
			charsPerLine = windowWidth / charWidth;
			if (charsPerLine < TARGET_FIT_WIDTH) {
				done = true;
				fontSize -= delta; //return to the previous font size that produced > 80 characters.
			} else {
				fontSize += delta;
				p.setTextSize(fontSize);
			}
		}
		return (int) fontSize;
	}
	String readDefaultSettingsDirectoryOption() {
		try {
			if (host.mSettings == null) {
				return "";
			}
			Object opt = host.mSettings.getSettings().getOptions().findOptionByKey("default_settings_directory");
			if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption) {
				Object val = ((com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption) opt).getValue();
				if (val instanceof String) {
					return ((String) val).trim();
				}
			}
		} catch (Exception ignored) {
		}
		return "";
	}
}
