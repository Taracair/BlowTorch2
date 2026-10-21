package com.resurrection.blowtorch2.lib.button;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.resurrection.blowtorch2.lib.util.AtomicFiles;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.View;

/**
 * One app-wide folder of icon files. The URI is a file both processes read.
 * The service reads it on each command. The UI process caches it for drawing.
 */
public final class IconFolder {

	static final String FILE = "icon-folder-uri";

	private static volatile String cachedUri;
	private static volatile boolean cacheLoaded;
	private static String watchedUri;
	private static android.database.ContentObserver observer;
	private static final List<WeakReference<View>> VIEWS = new ArrayList<WeakReference<View>>();

	private IconFolder() {
	}

	public static final class Snapshot {
		public final boolean configured;
		public final boolean readable;
		public final String displayName;
		public final List<String> names;
		/** Subfolders directly in the icon folder. */
		public final List<String> folders;

		private Snapshot(final boolean configured, final boolean readable,
				final String displayName, final List<String> names, final List<String> folders) {
			this.configured = configured;
			this.readable = readable;
			this.displayName = displayName;
			this.names = names;
			this.folders = folders;
		}

		public IconFaceResolver.Folder folder() {
			if (!configured) {
				return IconFaceResolver.Folder.none();
			}
			if (!readable) {
				return IconFaceResolver.Folder.unreadable();
			}
			return IconFaceResolver.Folder.of(names);
		}

		public String line() {
			if (!configured) {
				return "No icon folder.";
			}
			if (!readable) {
				return "Cannot read the icon folder.";
			}
			if (displayName == null || displayName.length() == 0) {
				return "Icon folder: chosen";
			}
			return "Icon folder: " + displayName;
		}
	}

	public static boolean isNamed(final String spec) {
		return spec != null && spec.startsWith("named:");
	}

	/** Always from disk. Commands run in the other process. */
	public static String readUri(final Context context) {
		if (context == null) {
			return null;
		}
		File file = new File(context.getApplicationContext().getFilesDir(), FILE);
		if (!file.isFile() || file.length() == 0 || file.length() > 4096) {
			return null;
		}
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			byte[] buf = new byte[(int) file.length()];
			int off = 0;
			while (off < buf.length) {
				int n = in.read(buf, off, buf.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			if (off <= 0) {
				return null;
			}
			String text = new String(buf, 0, off, "UTF-8").trim();
			if (text.length() == 0) {
				return null;
			}
			return text;
		} catch (IOException e) {
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	/** UI-process cache. {@link #store} updates it in the process that picked the folder. */
	public static String uriForDraw(final Context context) {
		if (!cacheLoaded) {
			cachedUri = readUri(context);
			cacheLoaded = true;
		}
		return cachedUri;
	}

	public static Snapshot snapshot(final Context context) {
		String raw = readUri(context);
		if (raw == null) {
			return new Snapshot(false, true, null, Collections.<String>emptyList(),
					Collections.<String>emptyList());
		}
		try {
			Uri tree = Uri.parse(raw);
			String name = displayName(context, tree);
			List<String> folders = new ArrayList<String>();
			List<String> names = childNames(context, tree, folders);
			if (names == null) {
				return new Snapshot(true, false, name, Collections.<String>emptyList(),
						Collections.<String>emptyList());
			}
			return new Snapshot(true, true, name, names, folders);
		} catch (RuntimeException e) {
			return new Snapshot(true, false, null, Collections.<String>emptyList(),
					Collections.<String>emptyList());
		}
	}

	public static boolean store(final Context context, final Uri uri) {
		if (context == null || uri == null) {
			return false;
		}
		try {
			AtomicFiles.writeInternal(context, FILE, uri.toString().getBytes("UTF-8"), false);
		} catch (IOException e) {
			return false;
		}
		cachedUri = uri.toString();
		cacheLoaded = true;
		dropWatch(context);
		ButtonFaces.forgetNamed();
		invalidateViews();
		return true;
	}

	/**
	 * Why this named file cannot be a button picture, or null when it can.
	 * Same byte and edge caps as the editor's copied image.
	 */
	public static String rejection(final Context context, final String filename) {
		if (ButtonFaceRef.namedPath(filename) == null) {
			return "No file " + filename + " in the icon folder.";
		}
		InputStream in = openAt(context, filename, readUri(context));
		if (in == null) {
			return "No file " + filename + " in the icon folder.";
		}
		try {
			byte[] bytes = readCapped(in, ButtonFaces.MAX_SOURCE_BYTES);
			if (bytes == TOO_LARGE) {
				return "That file is too large.";
			}
			if (bytes == null) {
				return "That file is not a usable image.";
			}
			if (ButtonFaceRef.isSvg(filename)) {
				return null;
			}
			android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
			if (bounds.outWidth < 1 || bounds.outHeight < 1) {
				return "That file is not a usable image.";
			}
			if (bounds.outWidth > ButtonFaces.MAX_SOURCE_EDGE
					|| bounds.outHeight > ButtonFaces.MAX_SOURCE_EDGE) {
				return "That image is too large.";
			}
			return null;
		} catch (IOException e) {
			return "That file is not a usable image.";
		} catch (RuntimeException e) {
			return "That file is not a usable image.";
		} finally {
			try {
				in.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static final byte[] TOO_LARGE = new byte[0];

	private static byte[] readCapped(final InputStream in, final int max) throws IOException {
		java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		byte[] tmp = new byte[8192];
		int n;
		int total = 0;
		while ((n = in.read(tmp)) >= 0) {
			if (n == 0) {
				continue;
			}
			total += n;
			if (total > max) {
				return TOO_LARGE;
			}
			buf.write(tmp, 0, n);
		}
		if (buf.size() == 0) {
			return null;
		}
		return buf.toByteArray();
	}

	public static InputStream open(final Context context, final String filename) {
		return openAt(context, filename, uriForDraw(context));
	}

	private static InputStream openAt(final Context context, final String filename, final String raw) {
		if (context == null || raw == null || ButtonFaceRef.namedPath(filename) == null) {
			return null;
		}
		try {
			Uri tree = Uri.parse(raw);
			String[] parts = filename.trim().split("/");
			String docId = DocumentsContract.getTreeDocumentId(tree);
			for (int i = 0; i < parts.length && docId != null; i++) {
				docId = childId(context, tree, docId, parts[i], i < parts.length - 1);
			}
			if (docId == null) {
				return null;
			}
			Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
			return context.getContentResolver().openInputStream(doc);
		} catch (RuntimeException e) {
			return null;
		} catch (java.io.FileNotFoundException e) {
			return null;
		}
	}

	/** Remember a view that paints {@code named:} so a folder change can redraw it. */
	public static void noteDrawing(final View view) {
		if (view == null) {
			return;
		}
		synchronized (VIEWS) {
			boolean seen = false;
			for (int i = VIEWS.size() - 1; i >= 0; i--) {
				View have = VIEWS.get(i).get();
				if (have == null) {
					VIEWS.remove(i);
				} else if (have == view) {
					seen = true;
				}
			}
			if (!seen) {
				VIEWS.add(new WeakReference<View>(view));
			}
		}
		ensureWatch(view.getContext());
	}

	public static String displayName(final Context context, final Uri tree) {
		if (context == null || tree == null) {
			return null;
		}
		Cursor cursor = null;
		try {
			String treeId = DocumentsContract.getTreeDocumentId(tree);
			Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
			cursor = context.getContentResolver().query(doc, new String[] {
					DocumentsContract.Document.COLUMN_DISPLAY_NAME
			}, null, null, null);
			if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
				return cursor.getString(0);
			}
		} catch (RuntimeException e) {
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return null;
	}

	private static List<String> childNames(final Context context, final Uri tree,
			final List<String> folders) {
		Cursor cursor = null;
		try {
			cursor = queryChildren(context, tree, DocumentsContract.getTreeDocumentId(tree));
			if (cursor == null) {
				return null;
			}
			List<String> names = new ArrayList<String>();
			int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
			int mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
			while (cursor.moveToNext()) {
				if (nameCol < 0 || cursor.isNull(nameCol)) {
					continue;
				}
				if (mimeCol >= 0 && !cursor.isNull(mimeCol)
						&& DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mimeCol))) {
					folders.add(cursor.getString(nameCol));
					continue;
				}
				names.add(cursor.getString(nameCol));
			}
			return names;
		} catch (RuntimeException e) {
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/** The one child of {@code parentId} with this name: a folder when {@code dir}, else a file. */
	private static String childId(final Context context, final Uri tree, final String parentId,
			final String filename, final boolean dir) {
		Cursor cursor = null;
		try {
			cursor = queryChildren(context, tree, parentId);
			if (cursor == null) {
				return null;
			}
			int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
			int idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
			int mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
			String found = null;
			while (cursor.moveToNext()) {
				if (nameCol < 0 || idCol < 0 || cursor.isNull(nameCol) || cursor.isNull(idCol)) {
					continue;
				}
				boolean isDir = mimeCol >= 0 && !cursor.isNull(mimeCol)
						&& DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(mimeCol));
				if (isDir != dir) {
					continue;
				}
				if (!filename.equals(cursor.getString(nameCol))) {
					continue;
				}
				if (found != null) {
					return null;
				}
				found = cursor.getString(idCol);
			}
			return found;
		} catch (RuntimeException e) {
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private static Cursor queryChildren(final Context context, final Uri tree, final String parentId) {
		Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
		return context.getContentResolver().query(children, new String[] {
				DocumentsContract.Document.COLUMN_DISPLAY_NAME,
				DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				DocumentsContract.Document.COLUMN_MIME_TYPE
		}, null, null, null);
	}

	private static void ensureWatch(final Context context) {
		if (context == null) {
			return;
		}
		String raw = uriForDraw(context);
		if (raw == null) {
			return;
		}
		if (raw.equals(watchedUri) && observer != null) {
			return;
		}
		dropWatch(context);
		try {
			Uri tree = Uri.parse(raw);
			String treeId = DocumentsContract.getTreeDocumentId(tree);
			final Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
			observer = new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
				@Override
				public void onChange(final boolean selfChange) {
					ButtonFaces.forgetNamed();
					invalidateViews();
				}
			};
			context.getApplicationContext().getContentResolver()
					.registerContentObserver(children, true, observer);
			watchedUri = raw;
		} catch (RuntimeException e) {
			observer = null;
			watchedUri = null;
		}
	}

	private static void dropWatch(final Context context) {
		if (observer != null && context != null) {
			try {
				context.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
			} catch (RuntimeException ignored) {
			}
		}
		observer = null;
		watchedUri = null;
	}

	private static void invalidateViews() {
		List<View> live = new ArrayList<View>();
		synchronized (VIEWS) {
			for (int i = VIEWS.size() - 1; i >= 0; i--) {
				View view = VIEWS.get(i).get();
				if (view == null) {
					VIEWS.remove(i);
				} else {
					live.add(view);
				}
			}
		}
		for (int i = 0; i < live.size(); i++) {
			live.get(i).invalidate();
		}
	}
}
