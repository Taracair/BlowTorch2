package com.resurrection.blowtorch2.lib.window;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.content.Context;

import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Per-world learned command pairings, as {@code .xml} beside the world's
 * settings so backup/restore takes them. Prefixed so the launcher cannot
 * mistake the file for a world.
 */
public final class CommandKnowledgeStore {

	/**
	 * Prefixed so it cannot be mistaken for a world's settings file.
	 *
	 * <p>Worlds are listed from the launcher's own file, not by reading this
	 * folder, so an extra name here cannot turn into a world that does not
	 * exist — but a name that reads as one would still mislead whoever opens
	 * the folder next.
	 */
	private static final String PREFIX = "wordpairs-";

	private CommandKnowledgeStore() {
	}

	/** The file this world's pairings live in. */
	public static File fileFor(final Context context, final String profile) {
		if (context == null) {
			return null;
		}
		return new File(context.getFilesDir(), PREFIX + safeName(profile) + ".xml");
	}

	/**
	 * Write what has been learned, or remove the file when there is nothing.
	 *
	 * @param context any context.
	 * @param profile the world.
	 * @param source where the knowledge is held.
	 */
	public static void save(final Context context, final String profile,
			final WordSuggestions source) {
		File f = fileFor(context, profile);
		if (f == null || source == null) {
			return;
		}
		String body = source.exportCommandKnowledge();
		try {
			if (body.length() == 0) {
				// Nothing learned. Leaving a stale file would restore yesterday's
				// pairings over a bag the player has just emptied.
				if (f.exists() && !f.delete()) {
					BlowTorchLogger.logMinor("CommandKnowledgeStore.save",
							new java.io.IOException("could not remove " + f.getName()));
				}
				return;
			}
			// Whole file at once. It is a few kilobytes and it is rewritten from
			// what is in memory, so a partial write is only ever the newest save
			// and the reader skips rows it cannot parse.
			FileOutputStream out = new FileOutputStream(f);
			try {
				out.write(body.getBytes("UTF-8"));
				out.flush();
				out.getFD().sync();
			} finally {
				out.close();
			}
		} catch (Exception e) {
			BlowTorchLogger.logMinor("CommandKnowledgeStore.save", e);
		}
	}

	/**
	 * Read this world's pairings into the completer, replacing what it holds.
	 *
	 * @param context any context.
	 * @param profile the world.
	 * @param into where to put them.
	 */
	public static void load(final Context context, final String profile,
			final WordSuggestions into) {
		if (into == null) {
			return;
		}
		File f = fileFor(context, profile);
		if (f == null || !f.isFile()) {
			into.clearCommandKnowledge();
			return;
		}
		try {
			InputStream in = new FileInputStream(f);
			try {
				ByteArrayOutputStream buf = new ByteArrayOutputStream();
				byte[] chunk = new byte[4096];
				int n;
				while ((n = in.read(chunk)) > 0) {
					buf.write(chunk, 0, n);
				}
				into.importCommandKnowledge(new String(buf.toByteArray(), "UTF-8"));
			} finally {
				in.close();
			}
		} catch (Exception e) {
			BlowTorchLogger.logMinor("CommandKnowledgeStore.load", e);
			into.clearCommandKnowledge();
		}
	}

	/** Throw this world's pairings away, on disk as well as in memory. */
	public static void erase(final Context context, final String profile) {
		File f = fileFor(context, profile);
		if (f != null && f.isFile() && !f.delete()) {
			BlowTorchLogger.logMinor("CommandKnowledgeStore.erase",
					new java.io.IOException("could not remove " + f.getName()));
		}
	}

	/**
	 * A world name reduced to something that can be a file name.
	 *
	 * <p>World names are typed by the player and can hold anything, including
	 * separators. Two worlds whose names differ only in punctuation would then
	 * share a file, which is worse than either of them having an ugly one.
	 */
	static String safeName(final String profile) {
		if (profile == null || profile.length() == 0) {
			return "default";
		}
		StringBuilder b = new StringBuilder(profile.length());
		for (int i = 0; i < profile.length(); i++) {
			char c = profile.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				b.append(Character.toLowerCase(c));
			} else {
				b.append('_');
			}
		}
		// The name is also the only thing telling two worlds apart here, so a
		// name that reduces to nothing must not become everyone's file.
		String out = b.toString();
		return out.replace("_", "").length() == 0
				? "world" + Integer.toHexString(profile.hashCode()) : out;
	}
}
