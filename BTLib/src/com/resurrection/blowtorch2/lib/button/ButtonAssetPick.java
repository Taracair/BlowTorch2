package com.resurrection.blowtorch2.lib.button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.resurrection.blowtorch2.lib.window.FontCatalog;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

/**
 * System file picker for a button font or icon. The bytes are copied into
 * app storage so the button does not keep a content URI. The pending
 * listener lives only until the activity result, in this process.
 */
public final class ButtonAssetPick {

	public static final int REQUEST_FONT = 2105;
	public static final int REQUEST_IMAGE = 2106;

	public interface Listener {
		void onPicked(String path);
	}

	private static Listener pending;

	private ButtonAssetPick() {
	}

	public static void launch(final Context context, final boolean font, final Listener listener) {
		Activity activity = findActivity(context);
		if (activity == null || listener == null) {
			toast(context, "Cannot open the file picker from here.");
			return;
		}
		pending = listener;
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		if (font) {
			intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
					"font/ttf",
					"font/otf",
					"font/sfnt",
					"application/font-sfnt",
					"application/x-font-ttf",
					"application/vnd.ms-opentype",
					"application/octet-stream"
			});
		} else {
			intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
					"image/png",
					"image/jpeg",
					"image/svg+xml",
					"image/*"
			});
		}
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		try {
			activity.startActivityForResult(intent, font ? REQUEST_FONT : REQUEST_IMAGE);
		} catch (RuntimeException e) {
			pending = null;
			toast(context, "No file picker available.");
		}
	}

	/** @return true when this request belongs to the button picker. */
	public static boolean onActivityResult(final Activity activity, final int requestCode,
			final int resultCode, final Intent data) {
		if (requestCode != REQUEST_FONT && requestCode != REQUEST_IMAGE) {
			return false;
		}
		Listener listener = pending;
		pending = null;
		if (activity == null || resultCode != Activity.RESULT_OK || data == null
				|| data.getData() == null || listener == null) {
			return true;
		}
		boolean font = requestCode == REQUEST_FONT;
		String path = font ? copyFont(activity, data.getData()) : copyImage(activity, data.getData());
		if (path != null) {
			listener.onPicked(path);
		}
		return true;
	}

	private static String copyFont(final Activity activity, final Uri uri) {
		String name = FontCatalog.sanitizeImportFileName(displayName(activity, uri));
		if (!FontCatalog.isFontFileName(name)) {
			toast(activity, "Choose a .ttf or .otf file.");
			return null;
		}
		File dir = FontCatalog.importedFontsDir(activity.getFilesDir());
		File dest = copyTo(activity, uri, dir, name, 0);
		if (dest == null) {
			return null;
		}
		try {
			android.graphics.Typeface.createFromFile(dest);
		} catch (RuntimeException e) {
			dest.delete();
			toast(activity, "That file is not a usable font.");
			return null;
		}
		return dest.getAbsolutePath();
	}

	private static String copyImage(final Activity activity, final Uri uri) {
		String name = sanitizeImageName(displayName(activity, uri));
		if (name == null) {
			toast(activity, "Choose a PNG, JPEG, or SVG.");
			return null;
		}
		File dir = new File(activity.getFilesDir(), "button-images");
		File dest = copyTo(activity, uri, dir, name, ButtonFaces.MAX_SOURCE_BYTES);
		if (dest == null) {
			return null;
		}
		if (!ButtonFaceRef.isSvg(dest.getPath())) {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(dest.getPath(), opts);
			if (opts.outWidth <= 0 || opts.outHeight <= 0) {
				dest.delete();
				toast(activity, "That file is not a usable image.");
				return null;
			}
			if (opts.outWidth > ButtonFaces.MAX_SOURCE_EDGE
					|| opts.outHeight > ButtonFaces.MAX_SOURCE_EDGE) {
				dest.delete();
				toast(activity, "That image is too large.");
				return null;
			}
		}
		return dest.getAbsolutePath();
	}

	private static File copyTo(final Activity activity, final Uri uri, final File dir,
			final String name, final long maxBytes) {
		if (!dir.exists() && !dir.mkdirs()) {
			toast(activity, "Could not create the folder.");
			return null;
		}
		File dest = FontCatalog.uniqueImportTarget(dir, name);
		InputStream in = null;
		FileOutputStream out = null;
		try {
			in = activity.getContentResolver().openInputStream(uri);
			if (in == null) {
				toast(activity, "Could not read that file.");
				return null;
			}
			out = new FileOutputStream(dest);
			byte[] buf = new byte[8192];
			int n;
			long written = 0;
			while ((n = in.read(buf)) != -1) {
				written += n;
				if (maxBytes > 0 && written > maxBytes) {
					out.close();
					out = null;
					dest.delete();
					toast(activity, "That file is too large.");
					return null;
				}
				out.write(buf, 0, n);
			}
			out.flush();
			return dest;
		} catch (Exception e) {
			if (dest.exists()) {
				dest.delete();
			}
			toast(activity, "Could not copy that file.");
			return null;
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	static String sanitizeImageName(final String raw) {
		if (raw == null) {
			return null;
		}
		String name = raw.trim();
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0 && slash < name.length() - 1) {
			name = name.substring(slash + 1);
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
				b.append(c);
			}
		}
		name = b.toString();
		String lower = name.toLowerCase(java.util.Locale.US);
		if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
				|| lower.endsWith(".svg")) {
			return name;
		}
		return null;
	}

	private static String displayName(final Activity activity, final Uri uri) {
		Cursor cursor = null;
		try {
			cursor = activity.getContentResolver().query(uri,
					new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int at = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (at >= 0) {
					return cursor.getString(at);
				}
			}
		} catch (RuntimeException ignored) {
			return "picked";
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return "picked";
	}

	private static Activity findActivity(final Context context) {
		Context c = context;
		while (c instanceof ContextWrapper) {
			if (c instanceof Activity) {
				return (Activity) c;
			}
			c = ((ContextWrapper) c).getBaseContext();
		}
		return null;
	}

	private static void toast(final Context context, final String text) {
		if (context == null) {
			return;
		}
		Toast.makeText(context, text, Toast.LENGTH_LONG).show();
	}
}
