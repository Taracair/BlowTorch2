package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.io.FileInputStream;

import android.content.Context;

import com.resurrection.blowtorch2.lib.util.AtomicFiles;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Per-world button-use counts, beside the world's settings so a backup that
 * copies {@code *.xml} takes them. Prefixed so the launcher cannot mistake
 * the file for a world. The body is the {@code v1} text from {@code buttonheat.lua}.
 */
public final class ButtonHeatStore {

	private static final String PREFIX = "buttonheat-";

	private ButtonHeatStore() {
	}

	/** File name only, no separators. Same world-name folding as command pairings. */
	public static String fileName(final String profile) {
		return PREFIX + CommandKnowledgeStore.safeName(profile) + ".xml";
	}

	public static void save(final Context context, final String profile, final String body) {
		if (context == null || profile == null) {
			return;
		}
		String text = body == null ? "" : body;
		try {
			AtomicFiles.writeInternal(context, fileName(profile),
					text.getBytes("UTF-8"), false);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("ButtonHeatStore.save", e);
		}
	}

	/** Empty string when this world has no file yet. */
	public static String load(final Context context, final String profile) {
		if (context == null || profile == null) {
			return "";
		}
		Context app = context.getApplicationContext();
		if (app == null) {
			return "";
		}
		File f = new File(app.getFilesDir(), fileName(profile));
		if (!f.isFile()) {
			return "";
		}
		FileInputStream in = null;
		try {
			in = new FileInputStream(f);
			byte[] buf = new byte[(int) f.length()];
			int off = 0;
			while (off < buf.length) {
				int n = in.read(buf, off, buf.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			if (off != buf.length) {
				byte[] exact = new byte[off];
				System.arraycopy(buf, 0, exact, 0, off);
				return new String(exact, "UTF-8");
			}
			return new String(buf, "UTF-8");
		} catch (Exception e) {
			BlowTorchLogger.logMinor("ButtonHeatStore.load", e);
			return "";
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (Exception ignored) {
					// Read-only handle.
				}
			}
		}
	}
}
