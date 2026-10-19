package com.resurrection.blowtorch2.lib.button;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** System folder picker for {@link IconFolder}. Read permission is kept. */
public final class IconFolderPick {

	public static final int REQUEST = 2107;

	private IconFolderPick() {
	}

	public static void launch(final Context context) {
		Activity activity = findActivity(context);
		if (activity == null) {
			toast(context, "Cannot open the folder picker from here.");
			return;
		}
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
				| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
				| Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
		try {
			activity.startActivityForResult(intent, REQUEST);
		} catch (RuntimeException e) {
			toast(context, "No folder picker available.");
		}
	}

	/** @return true when this request belongs to the icon folder picker. */
	public static boolean onActivityResult(final Activity activity, final int requestCode,
			final int resultCode, final Intent data) {
		if (requestCode != REQUEST) {
			return false;
		}
		if (activity == null || resultCode != Activity.RESULT_OK || data == null
				|| data.getData() == null) {
			return true;
		}
		Uri uri = data.getData();
		int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
		if (flags == 0) {
			flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
		}
		try {
			activity.getContentResolver().takePersistableUriPermission(uri, flags);
		} catch (SecurityException e) {
			toast(activity, "Could not keep permission to that folder.");
			return true;
		}
		if (!IconFolder.store(activity, uri)) {
			toast(activity, "Could not remember that folder.");
			return true;
		}
		String name = IconFolder.displayName(activity, uri);
		toast(activity, name == null || name.length() == 0
				? "Icon folder set." : "Icon folder: " + name);
		return true;
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
