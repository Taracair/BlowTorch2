package com.resurrection.blowtorch2.lib.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;

/**
 * Writing a file the app cannot afford to lose.
 *
 * <p>Everything here exists because {@code openFileOutput} truncates the target before
 * a single byte of the new content arrives. For a settings file — every alias, trigger,
 * button and colour a player has — that means dying part way through leaves nothing to
 * come back to.
 *
 * <p>Two separate protections, against two separate losses:
 *
 * <ul>
 * <li><b>Staging and rename</b> guards against a <em>truncated</em> file. The content
 * goes to a sibling and is renamed into place, which within one directory is atomic:
 * a reader sees the old file or the new one, never half of either.</li>
 * <li><b>The .bak copy</b> guards against a <em>complete but wrong</em> file. A save
 * that succeeds while writing nonsense is a real failure mode here — it is how a whole
 * button set quietly went back to factory colours — and no amount of atomicity helps,
 * because the bad write is perfectly well-formed.</li>
 * </ul>
 *
 * <p>The backup keeps one generation, like the launcher list has always done. Two bad
 * saves in a row still lose the good copy; anything better needs timestamped history,
 * which is a bigger decision than this class should be making.
 */
public final class AtomicFiles {

	private AtomicFiles() {
	}

	/**
	 * Replace a file in the app's private directory, all of it or none of it.
	 *
	 * @param context Any context; the application context is used.
	 * @param name File name within {@code getFilesDir()}, no path separators.
	 * @param data The new contents.
	 * @param keepBackup Copy the existing file to {@code name + ".bak"} first.
	 * @throws IOException if the new contents could not be put in place. The previous
	 *         file is left alone in that case, which is the whole point.
	 */
	public static void writeInternal(final Context context, final String name,
			final byte[] data, final boolean keepBackup) throws IOException {
		if (context == null || name == null || data == null) {
			throw new IOException("nothing to write");
		}
		Context app = context.getApplicationContext();
		File dir = app.getFilesDir();
		File live = new File(dir, name);

		if (keepBackup && live.isFile() && live.length() > 0) {
			// Copy rather than rename: renaming would leave a moment with no live file
			// at all, and trading a corruption risk for a disappearance risk is not a
			// trade. A settings file is small enough that the copy is not felt on a
			// save the player asked for.
			copy(live, new File(dir, name + ".bak"));
		}

		String stagingName = name + ".new";
		FileOutputStream out = null;
		try {
			out = app.openFileOutput(stagingName, Context.MODE_PRIVATE);
			out.write(data);
			out.flush();
			// close() means the kernel took the bytes, not that storage did. Without
			// this the rename below can outlive its own data and leave a file that
			// looks valid and is empty.
			out.getFD().sync();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
					// Already synced; a failure to close costs nothing at this point.
				}
			}
		}

		File staged = new File(dir, stagingName);
		if (!staged.renameTo(live)) {
			staged.delete();
			throw new IOException("could not put " + name + " in place");
		}
	}

	/**
	 * Restore whatever {@link #writeInternal} last backed up.
	 *
	 * @param context Any context.
	 * @param name The live file name, without the {@code .bak} suffix.
	 * @return true if a backup existed and is now the live file.
	 */
	public static boolean restoreBackup(final Context context, final String name) {
		if (context == null || name == null) {
			return false;
		}
		File dir = context.getApplicationContext().getFilesDir();
		File backup = new File(dir, name + ".bak");
		if (!backup.isFile() || backup.length() == 0) {
			return false;
		}
		try {
			// Through the same staging path, so restoring cannot itself truncate.
			byte[] data = readAll(backup);
			writeInternal(context, name, data, false);
			return true;
		} catch (IOException e) {
			BlowTorchLogger.logThrowable("AtomicFiles.restoreBackup", e);
			return false;
		}
	}

	private static byte[] readAll(final File f) throws IOException {
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
				return exact;
			}
			return buf;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
					// Read-only handle; nothing is at stake in closing it.
				}
			}
		}
	}

	private static void copy(final File from, final File to) throws IOException {
		FileInputStream in = null;
		FileOutputStream out = null;
		try {
			in = new FileInputStream(from);
			out = new FileOutputStream(to);
			byte[] buf = new byte[8192];
			int read;
			while ((read = in.read(buf)) > 0) {
				out.write(buf, 0, read);
			}
			out.flush();
			out.getFD().sync();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
					// Read-only handle.
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
					// Already synced above.
				}
			}
		}
	}
}
