package com.resurrection.blowtorch2.lib.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

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

	/** Copy packaged Lua libs into app data when {@code BLOWTORCH_LUA_LIBS_VERSION} changes. */
	public static void syncLuaLibsFromAssets(Context context) throws IOException, PackageManager.NameNotFoundException {
		ApplicationInfo ai = context.getApplicationContext().getPackageManager()
				.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
		String dataDir = ai.dataDir;
		deleteRecursive(new File(dataDir + "/lua/lib"));
		deleteRecursive(new File(dataDir + "/lua/share"));

		File lualib = new File(dataDir + "/lua/lib/5.1/");
		if (!lualib.exists()) {
			lualib.mkdirs();
		}
		File luashare = new File(dataDir + "/lua/share/5.1/");
		if (!luashare.exists()) {
			luashare.mkdirs();
		}
		File luareshdpi = new File(dataDir + "/lua/share/5.1/res/hdpi");
		if (!luareshdpi.exists()) {
			luareshdpi.mkdirs();
		}
		File luaresmdpi = new File(dataDir + "/lua/share/5.1/res/mdpi");
		if (!luaresmdpi.exists()) {
			luaresmdpi.mkdirs();
		}
		File luaresldpi = new File(dataDir + "/lua/share/5.1/res/ldpi");
		if (!luaresldpi.exists()) {
			luaresldpi.mkdirs();
		}

		AssetManager assetManager = context.getAssets();
		String[] files = assetManager.list("lib/lua/5.1");
		if (files != null) {
			for (String filename : files) {
				InputStream in = assetManager.open("lib/lua/5.1/" + filename);
				File tmp = new File(lualib, filename);
				if (!tmp.exists()) {
					tmp.createNewFile();
				}
				OutputStream out = new FileOutputStream(tmp);
				copyStream(in, out);
				in.close();
				out.flush();
				out.close();
			}
		}

		files = assetManager.list("share/lua/5.1");
		if (files != null) {
			for (String filename : files) {
				if (!filename.equals("res")) {
					copyAssetPath(assetManager, "share/lua/5.1/" + filename, new File(luashare, filename));
				}
			}
		}

		copyResDensity(assetManager, "share/lua/5.1/res/hdpi", luareshdpi);
		copyResDensity(assetManager, "share/lua/5.1/res/mdpi", luaresmdpi);
		copyResDensity(assetManager, "share/lua/5.1/res/ldpi", luaresldpi);
	}

	public static void ensureCurrentVersion(Context context) {
		SharedPreferences prefs = context.getSharedPreferences("SERVICE_INFO", 0);
		int libsver = prefs.getInt("CURRENT_LUA_LIBS_VERSION", 0);
		try {
			ApplicationInfo ai = context.getApplicationContext().getPackageManager()
					.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
			int packagever = ai.metaData.getInt("BLOWTORCH_LUA_LIBS_VERSION");
			if (packagever != libsver) {
				ensureNativeLibsInDataDir(context);
				syncLuaLibsFromAssets(context);
				prefs.edit().putInt("CURRENT_LUA_LIBS_VERSION", packagever).apply();
			}
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void copyResDensity(AssetManager assetManager, String assetDir, File destDir)
			throws IOException {
		String[] files = assetManager.list(assetDir);
		if (files == null) {
			return;
		}
		for (String filename : files) {
			InputStream in = assetManager.open(assetDir + "/" + filename);
			File tmp = new File(destDir, filename);
			if (!tmp.exists()) {
				tmp.createNewFile();
			}
			OutputStream out = new FileOutputStream(tmp);
			copyStream(in, out);
			in.close();
			out.flush();
			out.close();
		}
	}

	private static void copyAssetPath(AssetManager assetManager, String assetPath, File dest)
			throws IOException {
		String[] children = assetManager.list(assetPath);
		if (children == null || children.length == 0) {
			File parent = dest.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			InputStream in = assetManager.open(assetPath);
			OutputStream out = new FileOutputStream(dest);
			copyStream(in, out);
			in.close();
			out.flush();
			out.close();
			return;
		}
		if (!dest.exists()) {
			dest.mkdirs();
		}
		for (String child : children) {
			copyAssetPath(assetManager, assetPath + "/" + child, new File(dest, child));
		}
	}

	private static void deleteRecursive(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}
		if (file.exists()) {
			file.delete();
		}
	}

	private static void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[COPY_BUFFER_SIZE];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}
}
