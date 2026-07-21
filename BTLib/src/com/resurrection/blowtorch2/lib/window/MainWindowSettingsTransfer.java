package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.resurrection.blowtorch2.lib.service.plugin.settings.Option;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

/**
 * Session settings import / export / reset dialogs and SAF URI transfer.
 * Menu routing and permission request codes stay on {@link MainWindow}.
 */
final class MainWindowSettingsTransfer {

	static final int REQUEST_IMPORT_SETTINGS_XML = 2101;
	static final int REQUEST_EXPORT_SETTINGS_XML = 2102;

	private final MainWindow activity;
	private final Pattern xmlinsensitive = Pattern.compile("^.+\\.[Xx][Mm][Ll]$");
	private final Matcher xmlimatcher = xmlinsensitive.matcher("");
	private EditText filenameExport = null;

	MainWindowSettingsTransfer(MainWindow activity) {
		this.activity = activity;
	}

	private String getConfiguredDefaultSettingsDirectory() {
		try {
			if (activity.service != null) {
				SettingsGroup g = activity.service.getSettings();
				if (g != null) {
					Option o = g.findOptionByKey("default_settings_directory");
					if (o instanceof StringOption) {
						Object v = ((StringOption) o).getValue();
						if (v instanceof String) {
							return ((String) v).trim();
						}
					}
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return "";
	}

	private File resolveSessionSettingsDirectory(boolean external) {
		return SDCardUtils.resolveDefaultSettingsDirectory(
				activity, external, getConfiguredDefaultSettingsDirectory());
	}

	void doImportDialog(boolean external) {
		final boolean hasExternal = external;
		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Import Settings");
		CharSequence[] items = new CharSequence[] {
				"Pick file…",
				"Import from default directory"
		};
		b.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (which == 0) {
					pickImportSettingsFile();
				} else {
					showDefaultDirectoryImportList(hasExternal);
				}
			}
		});
		b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		b.show();
	}

	private void pickImportSettingsFile() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
				"text/xml",
				"application/xml",
				"application/octet-stream"
		});
		activity.startActivityForResult(intent, REQUEST_IMPORT_SETTINGS_XML);
	}

	private void showDefaultDirectoryImportList(boolean external) {
		String custom = getConfiguredDefaultSettingsDirectory();
		if (SDCardUtils.isContentUri(custom)
				&& SDCardUtils.mapTreeUriToFile(Uri.parse(custom)) == null) {
			showSafTreeImportList(Uri.parse(custom));
			return;
		}

		File btermdir = resolveSessionSettingsDirectory(external);
		if (!btermdir.exists()) {
			//noinspection ResultOfMethodCallIgnored
			btermdir.mkdirs();
		}

		FilenameFilter xml_only = new FilenameFilter() {
			public boolean accept(File arg0, String arg1) {
				xmlimatcher.reset(arg1);
				return xmlimatcher.matches();
			}
		};

		HashMap<String,String> xmlfiles = new HashMap<String,String>();
		File[] listed = btermdir.listFiles(xml_only);
		if (listed != null) {
			for (File xml : listed) {
				if (xml != null && xml.isFile()) {
					xmlfiles.put(xml.getName(), xml.getPath());
				}
			}
		}

		if (xmlfiles.isEmpty()) {
			Toast.makeText(activity,
					"No .xml settings in:\n" + btermdir.getAbsolutePath(),
					Toast.LENGTH_LONG).show();
			return;
		}

		Set<String> xmlkeys = xmlfiles.keySet();
		List<String> sortedxmlkeys = new ArrayList<String>(xmlkeys);
		Collections.sort(sortedxmlkeys, String.CASE_INSENSITIVE_ORDER);

		String[] xmlentries = new String[sortedxmlkeys.size()];
		String[] xmlpaths = new String[sortedxmlkeys.size()];
		int i = 0;
		for (String file : sortedxmlkeys) {
			xmlentries[i] = file;
			xmlpaths[i] = xmlfiles.get(file);
			i++;
		}

		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Import from default directory");
		b.setItems(xmlentries, new ImportDialogListener(xmlpaths));
		b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		b.show();
	}

	private void showSafTreeImportList(Uri treeUri) {
		DocumentFile tree = DocumentFile.fromTreeUri(activity, treeUri);
		if (tree == null || !tree.isDirectory()) {
			Toast.makeText(activity, "Cannot open selected settings folder.", Toast.LENGTH_LONG).show();
			return;
		}
		HashMap<String, String> xmlfiles = new HashMap<String, String>();
		DocumentFile[] children = tree.listFiles();
		if (children != null) {
			for (DocumentFile child : children) {
				if (child == null || !child.isFile()) {
					continue;
				}
				String name = child.getName();
				if (name == null) {
					continue;
				}
				xmlimatcher.reset(name);
				if (xmlimatcher.matches()) {
					xmlfiles.put(name, child.getUri().toString());
				}
			}
		}
		if (xmlfiles.isEmpty()) {
			Toast.makeText(activity,
					"No .xml settings in selected folder.",
					Toast.LENGTH_LONG).show();
			return;
		}
		Set<String> xmlkeys = xmlfiles.keySet();
		List<String> sortedxmlkeys = new ArrayList<String>(xmlkeys);
		Collections.sort(sortedxmlkeys, String.CASE_INSENSITIVE_ORDER);
		final String[] xmlentries = new String[sortedxmlkeys.size()];
		final String[] xmlUris = new String[sortedxmlkeys.size()];
		int i = 0;
		for (String file : sortedxmlkeys) {
			xmlentries[i] = file;
			xmlUris[i] = xmlfiles.get(file);
			i++;
		}
		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Import from default directory");
		b.setItems(xmlentries, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				importSettingsFromUri(Uri.parse(xmlUris[which]));
			}
		});
		b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		b.show();
	}

	private class ImportDialogListener implements DialogInterface.OnClickListener {
		String[] items = null;

		ImportDialogListener(String[] items) {
			this.items = items;
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			try {
				activity.service.loadSettingsFromPath(items[which]);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	void doExportDialog() {
		final boolean external = SDCardUtils.hasStoragePermissions(activity);
		final String custom = getConfiguredDefaultSettingsDirectory();
		final String dirLabel;
		if (SDCardUtils.isContentUri(custom)
				&& SDCardUtils.mapTreeUriToFile(Uri.parse(custom)) == null) {
			dirLabel = custom;
		} else {
			dirLabel = resolveSessionSettingsDirectory(external).getAbsolutePath();
		}

		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Export Settings");

		LinearLayout layout = new LinearLayout(activity);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad / 2, pad, pad / 2);

		TextView hint = new TextView(activity);
		hint.setText("Default directory:\n" + dirLabel);
		layout.addView(hint);

		filenameExport = new EditText(activity);
		filenameExport.setHint("Enter file name");
		filenameExport.setSingleLine(true);
		layout.addView(filenameExport);
		b.setView(layout);

		b.setPositiveButton("Export to default directory", null);
		b.setNeutralButton("Choose location…", null);
		b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		final AlertDialog d = b.create();
		d.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String name = filenameExport.getText() != null
								? filenameExport.getText().toString().trim() : "";
						if (TextUtils.isEmpty(name)) {
							Toast.makeText(activity, "Enter a file name first.", Toast.LENGTH_SHORT).show();
							return;
						}
						activity.myhandler.sendMessage(
								activity.myhandler.obtainMessage(MainWindow.MESSAGE_EXPORTSETTINGS, name));
						d.dismiss();
					}
				});
				d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String name = filenameExport.getText() != null
								? filenameExport.getText().toString().trim() : "";
						if (TextUtils.isEmpty(name)) {
							name = "settings.xml";
						}
						xmlimatcher.reset(name);
						if (!xmlimatcher.matches()) {
							name = name + ".xml";
						}
						pickExportSettingsFile(name);
						d.dismiss();
					}
				});
			}
		});
		d.show();
	}

	private void pickExportSettingsFile(String suggestedName) {
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("application/xml");
		intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
		activity.startActivityForResult(intent, REQUEST_EXPORT_SETTINGS_XML);
	}

	void importSettingsFromUri(Uri uri) {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = activity.getContentResolver().openInputStream(uri);
			if (in == null) {
				Toast.makeText(activity, "Could not open selected file.", Toast.LENGTH_LONG).show();
				return;
			}
			File tmp = new File(activity.getCacheDir(), "import_settings_saf.xml");
			out = new FileOutputStream(tmp);
			byte[] buf = new byte[4096];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.flush();
			activity.service.loadSettingsFromPath(tmp.getAbsolutePath());
		} catch (Exception e) {
			Toast.makeText(activity, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		} finally {
			if (in != null) try { in.close(); } catch (IOException ignored) {}
			if (out != null) try { out.close(); } catch (IOException ignored) {}
		}
	}

	void exportSettingsToUri(Uri uri) {
		File tmp = new File(activity.getCacheDir(), "export_settings_saf.xml");
		try {
			activity.doExportSettings(tmp.getAbsolutePath());
			InputStream in = null;
			OutputStream out = null;
			try {
				in = new FileInputStream(tmp);
				out = activity.getContentResolver().openOutputStream(uri);
				if (out == null) {
					Toast.makeText(activity, "Could not write selected location.", Toast.LENGTH_LONG).show();
					return;
				}
				byte[] buf = new byte[4096];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				out.flush();
				Toast.makeText(activity, "Settings exported.", Toast.LENGTH_LONG).show();
			} finally {
				if (in != null) try { in.close(); } catch (IOException ignored) {}
				if (out != null) try { out.close(); } catch (IOException ignored) {}
			}
		} catch (Exception e) {
			Toast.makeText(activity, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		} finally {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
		}
	}

	void doResetDialog() {
		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Reset Settings?");
		b.setMessage("Are you sure you want to reset settings?");
		b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				activity.myhandler.sendEmptyMessage(MainWindow.MESSAGE_DORESETSETTINGS);
				dialog.dismiss();
			}
		});

		b.setNegativeButton("No", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		AlertDialog d = b.create();
		d.show();
	}

	boolean handleSettingsTransferResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_IMPORT_SETTINGS_XML && resultCode == MainWindow.RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				importSettingsFromUri(uri);
			}
			return true;
		}
		if (requestCode == REQUEST_EXPORT_SETTINGS_XML && resultCode == MainWindow.RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				exportSettingsToUri(uri);
			}
			return true;
		}
		return false;
	}

	void showPermissionsMessage(boolean granted) {
		File rootDir = SDCardUtils.resolveBlowTorchRoot(activity);
		boolean shared = SDCardUtils.isUsingSharedBlowTorchRoot(activity);
		String message;
		if (granted && shared) {
			message = "Storage ready:\n" + rootDir.getAbsolutePath()
					+ "\n(settings, backups, launcher, session_logs, logs)";
		} else if (granted) {
			message = "All-files not granted — using app folder:\n" + rootDir.getAbsolutePath()
					+ "\nTap Manage Storage Access and allow All files access for /BlowTorch/.";
		} else {
			message = String.format(activity.getString(
					com.resurrection.blowtorch2.lib.R.string.sd_perm_denies),
					rootDir.getAbsolutePath());
		}
		Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
	}
}
