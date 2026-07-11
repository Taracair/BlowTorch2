package com.offsetnull.bt.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * Ensures Lua {@code package.cpath} can locate native modules such as marshal.
 */
public final class LuaLibraryHelper {
	private static final int COPY_BUFFER_SIZE = 4096;

	private LuaLibraryHelper() {
	}

	public static String buildCPath(Context context, String dataDir) {
		StringBuilder cpath = new StringBuilder();
		if (dataDir != null) {
			cpath.append(dataDir).append("/lib/lib?.so");
		}
		try {
			ApplicationInfo ai = context.getApplicationContext().getPackageManager()
					.getApplicationInfo(context.getPackageName(), 0);
			if (ai.nativeLibraryDir != null) {
				if (cpath.length() > 0) {
					cpath.append(';');
				}
				cpath.append(ai.nativeLibraryDir).append("/lib?.so");
			}
		} catch (PackageManager.NameNotFoundException e) {
			// keep dataDir-only path
		}
		return cpath.toString();
	}

	public static void ensureNativeLibsInDataDir(Context context) throws IOException, PackageManager.NameNotFoundException {
		ApplicationInfo ai = context.getApplicationContext().getPackageManager()
				.getApplicationInfo(context.getPackageName(), 0);
		if (ai.nativeLibraryDir == null) {
			return;
		}
		File sourceDir = new File(ai.nativeLibraryDir);
		File[] libs = sourceDir.listFiles();
		if (libs == null) {
			return;
		}
		File destDir = new File(ai.dataDir, "lib");
		if (!destDir.exists() && !destDir.mkdirs()) {
			return;
		}
		for (File src : libs) {
			if (!src.isFile() || !src.getName().endsWith(".so")) {
				continue;
			}
			File dst = new File(destDir, src.getName());
			if (!dst.exists() || dst.length() != src.length()) {
				copyFile(src, dst);
			}
		}
	}

	private static void copyFile(File src, File dst) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(src);
			out = new FileOutputStream(dst);
			byte[] buffer = new byte[COPY_BUFFER_SIZE];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			out.flush();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
		}
	}
}
