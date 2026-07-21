package com.resurrection.blowtorch2.lib.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

/**
 * Leaf zip / file I/O for launcher settings backup and restore.
 * Dialogs and Activity result routing stay on {@link Launcher}.
 */
final class LauncherBackupIO {

	private LauncherBackupIO() {
	}

	static void copySettingsFile(File source, File dest) throws IOException {
		InputStream in = new FileInputStream(source);
		OutputStream out = new FileOutputStream(dest);
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

	static int zipSettingsDirectory(File sourceDir, File zipFile) throws IOException {
		int copied = 0;
		if (sourceDir == null || !sourceDir.isDirectory()) {
			throw new IOException("Settings directory unavailable");
		}
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
		try {
			String[] names = sourceDir.list();
			if (names != null) {
				byte[] buffer = new byte[4096];
				for (String name : names) {
					if (name == null || !name.endsWith(".xml")) {
						continue;
					}
					File file = new File(sourceDir, name);
					if (!file.isFile()) {
						continue;
					}
					ZipEntry entry = new ZipEntry(name);
					zos.putNextEntry(entry);
					FileInputStream in = new FileInputStream(file);
					try {
						int len;
						while ((len = in.read(buffer)) > 0) {
							zos.write(buffer, 0, len);
						}
					} finally {
						in.close();
					}
					zos.closeEntry();
					copied++;
				}
			}
		} finally {
			zos.close();
		}
		return copied;
	}

	static void mirrorBackupToDocuments(File zipFile) throws IOException {
		File docsRoot = new File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
				"BlowTorch2/backups");
		if (!docsRoot.mkdirs() && !docsRoot.exists()) {
			return;
		}
		copySettingsFile(zipFile, new File(docsRoot, zipFile.getName()));
	}

	private static final class SettingsImportCandidate {
		final File file;
		final String label;

		SettingsImportCandidate(File file, String label) {
			this.file = file;
			this.label = label;
		}
	}

	static void collectSettingsImportCandidates(Context context,
			ArrayList<File> choices, ArrayList<String> labels) {
		File defaultBackup = SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_BACKUPS);
		File sd = Environment.getExternalStorageDirectory();
		File[] scanRoots = new File[] {
				defaultBackup,
				new File(sd, "BlowTorch2/backups"),
				new File(sd, "BlowTorch/backups"),
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
						"BlowTorch2/backups"),
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
						"BlowTorch2"),
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
		};
		java.util.HashSet<String> seen = new java.util.HashSet<String>();
		ArrayList<SettingsImportCandidate> candidates = new ArrayList<SettingsImportCandidate>();
		for (File root : scanRoots) {
			if (root == null || !root.exists()) {
				continue;
			}
			boolean allowFolders = root.getAbsolutePath().contains("BlowTorch2");
			File[] entries = root.listFiles();
			if (entries == null) {
				continue;
			}
			for (File entry : entries) {
				if (entry.isDirectory()) {
					if (!allowFolders || entry.getName().endsWith(".zip")) {
						continue;
					}
					String[] inner = entry.list();
					if (inner != null && inner.length > 0) {
						boolean hasXml = false;
						for (String name : inner) {
							if (name.endsWith(".xml")) {
								hasXml = true;
								break;
							}
						}
						if (hasXml) {
							String key = entry.getAbsolutePath();
							if (seen.add(key)) {
								candidates.add(new SettingsImportCandidate(entry,
										"[folder] " + root.getName() + "/" + entry.getName()));
							}
						}
					}
					continue;
				}
				if (!entry.getName().endsWith(".zip")) {
					continue;
				}
				String key = entry.getAbsolutePath();
				if (seen.add(key)) {
					candidates.add(new SettingsImportCandidate(entry,
							root.getName() + "/" + entry.getName()));
				}
			}
		}
		java.util.Collections.sort(candidates, new Comparator<SettingsImportCandidate>() {
			public int compare(SettingsImportCandidate a, SettingsImportCandidate b) {
				return Long.signum(b.file.lastModified() - a.file.lastModified());
			}
		});
		for (SettingsImportCandidate candidate : candidates) {
			choices.add(candidate.file);
			labels.add(candidate.label);
		}
	}

	static int restoreBackupFromUri(Context context, Uri uri) throws IOException {
		InputStream in = context.getContentResolver().openInputStream(uri);
		if (in == null) {
			throw new IOException("Could not open selected file");
		}
		File tempZip = new File(SDCardUtils.resolveCacheDir(context), "import_settings.zip");
		OutputStream out = new FileOutputStream(tempZip);
		byte[] buffer = new byte[4096];
		int len;
		while ((len = in.read(buffer)) > 0) {
			out.write(buffer, 0, len);
		}
		in.close();
		out.close();
		return restoreZipBackup(context, tempZip);
	}

	static int restoreBackup(Context context, File backup) throws IOException {
		if (backup.isDirectory()) {
			return restoreDirectoryBackup(context, backup);
		}
		return restoreZipBackup(context, backup);
	}

	static int restoreDirectoryBackup(Context context, File backupDir) throws IOException {
		int restored = 0;
		String[] names = backupDir.list();
		if (names == null) {
			return 0;
		}
		File filesDir = context.getFilesDir();
		for (String name : names) {
			if (!name.endsWith(".xml")) {
				continue;
			}
			copySettingsFile(new File(backupDir, name), new File(filesDir, name));
			restored++;
		}
		return restored;
	}

	static int restoreZipBackup(Context context, File zipFile) throws IOException {
		int restored = 0;
		ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
		ZipEntry entry;
		byte[] buffer = new byte[4096];
		File filesDir = context.getFilesDir();
		while ((entry = zis.getNextEntry()) != null) {
			if (entry.isDirectory() || !entry.getName().endsWith(".xml")) {
				zis.closeEntry();
				continue;
			}
			File out = new File(filesDir, new File(entry.getName()).getName());
			FileOutputStream fos = new FileOutputStream(out);
			int len;
			while ((len = zis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			zis.closeEntry();
			restored++;
		}
		zis.close();
		return restored;
	}
}
