package com.resurrection.blowtorch2.lib.ui;

import java.io.File;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.view.View;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

public class SDCardUtils {

    public static String getSDCardRoot(AppCompatActivity a, boolean external) {
        String exportDir = ConfigurationLoader.getConfigurationValue("exportDirectory", a);
        if (external) {
            return "/" + exportDir;
        }
        return resolveAppExternalDir(a).getAbsolutePath();
    }

    /**
     * App-specific external files dir, falling back to internal files when
     * {@link Context#getExternalFilesDir(String)} returns null (common on modern Android).
     */
    public static File resolveAppExternalDir(Context context) {
        File ext = context.getExternalFilesDir(null);
        if (ext != null) {
            return ext;
        }
        return context.getFilesDir();
    }

    /**
     * Prefer external cache; fall back to internal cache when external is unavailable.
     */
    public static File resolveCacheDir(Context context) {
        File ext = context.getExternalCacheDir();
        if (ext != null) {
            return ext;
        }
        File internal = context.getCacheDir();
        if (internal != null) {
            return internal;
        }
        return context.getFilesDir();
    }

    /**
     * Session Import/Export default directory:
     * <ol>
     *   <li>Options → Miscellaneous → {@code default_settings_directory} when set</li>
     *   <li>else shared storage {@code /&lt;exportDirectory&gt;/} (e.g. /BlowTorch) when permitted</li>
     *   <li>else app external files (with internal files fallback)</li>
     * </ol>
     * When {@code customPath} is a {@code content://} tree URI that cannot be mapped to a
     * filesystem path, falls through to the shared/app defaults. Callers that need the SAF
     * tree should use {@link #isContentUri(String)} and DocumentFile themselves.
     */
    /**
     * True when {@code dir} exists (or was created) and is writable.
     * On modern Android, READ/WRITE_EXTERNAL_STORAGE does <em>not</em> allow creating
     * {@code /storage/emulated/0/BlowTorch} without all-files access — mkdirs fails silently.
     */
    public static boolean ensureWritableDirectory(File dir) {
        if (dir == null) {
            return false;
        }
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir.isDirectory() && dir.canWrite();
    }

    public static boolean isContentUri(String path) {
        return path != null && path.trim().startsWith("content:");
    }

    /**
     * Map a SAF tree URI to a {@link File} when it points at primary external storage.
     * Returns null for secondary volumes / providers that are not File-accessible.
     */
    public static File mapTreeUriToFile(Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        String docId;
        try {
            docId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            return null;
        }
        if (TextUtils.isEmpty(docId)) {
            return null;
        }
        String[] split = docId.split(":", 2);
        if (split.length == 0 || !"primary".equalsIgnoreCase(split[0])) {
            return null;
        }
        File root = Environment.getExternalStorageDirectory();
        if (root == null) {
            return null;
        }
        if (split.length < 2 || TextUtils.isEmpty(split[1])) {
            return root;
        }
        return new File(root, split[1]);
    }

    /**
     * Take persistable URI permission and return a path the rest of the app can store:
     * a filesystem absolute path when the tree maps to primary storage, otherwise the
     * {@code content://} tree URI string.
     */
    public static String persistDirectorySelection(Context context, Uri treeUri, int takeFlags) {
        if (context == null || treeUri == null) {
            return "";
        }
        int flags = takeFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (flags == 0) {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (SecurityException e) {
            // Some providers do not support persistable grants; still store the URI.
            e.printStackTrace();
        }
        File mapped = mapTreeUriToFile(treeUri);
        if (mapped != null) {
            ensureWritableDirectory(mapped);
            return mapped.getAbsolutePath();
        }
        return treeUri.toString();
    }

    public static File resolveDefaultSettingsDirectory(Context context, boolean hasSharedStorage,
            String customPath) {
        if (!TextUtils.isEmpty(customPath)) {
            String trimmed = customPath.trim();
            if (isContentUri(trimmed)) {
                File mapped = mapTreeUriToFile(Uri.parse(trimmed));
                if (mapped != null && ensureWritableDirectory(mapped)) {
                    return mapped;
                }
                // Unmapped content tree — fall through for File-based callers.
            } else {
                File custom = new File(trimmed);
                if (ensureWritableDirectory(custom)) {
                    return custom;
                }
                // Fall through if the custom path cannot be created/written.
            }
        }
        String exportDir = ConfigurationLoader.getConfigurationValue("exportDirectory", context);
        if (hasSharedStorage) {
            File root = Environment.getExternalStorageDirectory();
            if (root != null) {
                File dir = new File(root, exportDir);
                if (ensureWritableDirectory(dir)) {
                    return dir;
                }
            }
        }
        return resolveAppExternalDir(context);
    }

    public static String[] getStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
        }
        return new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
    }

    public static boolean hasStoragePermissions(AppCompatActivity activity) {
        return PermissionHelper.allGranted(activity, getStoragePermissions());
    }

    /**
     * Requests notification and storage permissions once at launcher startup.
     */
    public static void requestStartupPermissions(final AppCompatActivity activity, View root, final int code) {
        java.util.ArrayList<String> needed = new java.util.ArrayList<String>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (String permission : PermissionHelper.getNotificationPermissions()) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(permission);
                }
            }
        }
        for (String permission : getStoragePermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                needed.add(permission);
            }
        }
        if (needed.isEmpty()) {
            return;
        }
        int featureRes = PermissionHelper.featureMessageForRequestCode(code);
        PermissionHelper.ensurePermissions(activity, root, code,
                needed.toArray(new String[needed.size()]), featureRes, null);
    }

    public static boolean hasPermissions(final AppCompatActivity activity, View root, final int code) {
        return hasPermissions(activity, root, code, null);
    }

    public static boolean hasPermissions(final AppCompatActivity activity, View root, final int code,
            final Runnable onGranted) {
        int featureRes = PermissionHelper.featureMessageForRequestCode(code);
        return PermissionHelper.ensurePermissions(activity, root, code, getStoragePermissions(), featureRes, onGranted);
    }
}
