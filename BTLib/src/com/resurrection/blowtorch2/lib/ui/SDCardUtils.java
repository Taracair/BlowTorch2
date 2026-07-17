package com.resurrection.blowtorch2.lib.ui;

import java.io.File;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.view.View;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

/**
 * Shared storage under {@code /BlowTorch/} (outside {@code Android/data}) with
 * fixed subfolders for settings, backups, launcher lists, session logs, and app logs.
 * On Android 11+ this requires {@link Settings#ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION}.
 */
public class SDCardUtils {

    public static final String SUBDIR_SETTINGS = "settings";
    public static final String SUBDIR_BACKUPS = "backups";
    public static final String SUBDIR_LAUNCHER = "launcher";
    public static final String SUBDIR_SESSION_LOGS = "session_logs";
    public static final String SUBDIR_LOGS = "logs";

    public static String getExportDirectoryName(Context context) {
        String name = ConfigurationLoader.getConfigurationValue("exportDirectory", context);
        if (TextUtils.isEmpty(name)) {
            return "BlowTorch";
        }
        return name;
    }

    /**
     * Preferred shared root: {@code /storage/emulated/0/BlowTorch} (or configured name).
     * Does not create the directory.
     */
    public static File getPreferredBlowTorchRoot(Context context) {
        File storage = Environment.getExternalStorageDirectory();
        if (storage == null) {
            return null;
        }
        return new File(storage, getExportDirectoryName(context));
    }

    /** True when the app may create/write arbitrary folders on shared storage. */
    public static boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        // Pre-R: WRITE_EXTERNAL_STORAGE is enough when granted.
        return true;
    }

    public static boolean needsAllFilesAccessPrompt() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager();
    }

    /**
     * Opens the system screen to grant all-files access (Android 11+).
     * @return true if an intent was started
     */
    public static boolean openAllFilesAccessSettings(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(intent);
                return true;
            } catch (Exception e2) {
                e2.printStackTrace();
                return false;
            }
        }
    }

    public static String getSDCardRoot(AppCompatActivity a, boolean external) {
        return resolveBlowTorchRoot(a).getAbsolutePath();
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
     * True when {@code dir} exists (or was created) and is writable.
     * On modern Android without all-files access, creating
     * {@code /storage/emulated/0/BlowTorch} fails — mkdirs returns false.
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
            e.printStackTrace();
        }
        File mapped = mapTreeUriToFile(treeUri);
        if (mapped != null) {
            ensureWritableDirectory(mapped);
            return mapped.getAbsolutePath();
        }
        return treeUri.toString();
    }

    /**
     * Default shared root for all BlowTorch files.
     * Prefers {@code /BlowTorch} when writable (all-files / legacy write); otherwise
     * {@code &lt;app external&gt;/BlowTorch} so layout stays consistent even without grant.
     */
    public static File resolveBlowTorchRoot(Context context) {
        File preferred = getPreferredBlowTorchRoot(context);
        if (preferred != null && ensureWritableDirectory(preferred)) {
            ensureStandardSubdirectories(preferred);
            return preferred;
        }
        File fallback = new File(resolveAppExternalDir(context), getExportDirectoryName(context));
        ensureWritableDirectory(fallback);
        ensureStandardSubdirectories(fallback);
        return fallback;
    }

    /** Create the standard subfolders under a BlowTorch root. */
    public static void ensureStandardSubdirectories(File root) {
        if (root == null) {
            return;
        }
        ensureWritableDirectory(new File(root, SUBDIR_SETTINGS));
        ensureWritableDirectory(new File(root, SUBDIR_BACKUPS));
        ensureWritableDirectory(new File(root, SUBDIR_LAUNCHER));
        ensureWritableDirectory(new File(root, SUBDIR_SESSION_LOGS));
        ensureWritableDirectory(new File(root, SUBDIR_LOGS));
    }

    public static File resolveBlowTorchSubdir(Context context, String subdir) {
        File dir = new File(resolveBlowTorchRoot(context), subdir);
        ensureWritableDirectory(dir);
        return dir;
    }

    /**
     * Whether shared {@code /BlowTorch} (outside Android/data) is actually usable.
     */
    public static boolean isUsingSharedBlowTorchRoot(Context context) {
        File preferred = getPreferredBlowTorchRoot(context);
        File actual = resolveBlowTorchRoot(context);
        return preferred != null && actual != null
                && preferred.getAbsolutePath().equals(actual.getAbsolutePath());
    }

    /**
     * Session Import/Export default directory:
     * <ol>
     *   <li>Options → Miscellaneous → {@code default_settings_directory} when set</li>
     *   <li>else {@code /BlowTorch/settings} (or app-external BlowTorch/settings fallback)</li>
     * </ol>
     */
    public static File resolveDefaultSettingsDirectory(Context context, boolean hasSharedStorage,
            String customPath) {
        if (!TextUtils.isEmpty(customPath)) {
            String trimmed = customPath.trim();
            if (isContentUri(trimmed)) {
                File mapped = mapTreeUriToFile(Uri.parse(trimmed));
                if (mapped != null && ensureWritableDirectory(mapped)) {
                    return mapped;
                }
            } else {
                File custom = new File(trimmed);
                if (ensureWritableDirectory(custom)) {
                    return custom;
                }
            }
        }
        // hasSharedStorage is retained for callers; resolution always tries shared root first.
        return resolveBlowTorchSubdir(context, SUBDIR_SETTINGS);
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
        if (needsAllFilesAccessPrompt()) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return PermissionHelper.allGranted(activity, getStoragePermissions());
        }
        return hasAllFilesAccess();
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
            if (hasAllFilesAccess()) {
                resolveBlowTorchRoot(activity);
            }
            return;
        }
        int featureRes = PermissionHelper.featureMessageForRequestCode(code);
        PermissionHelper.ensurePermissions(activity, root, code,
                needed.toArray(new String[needed.size()]), featureRes, new Runnable() {
                    @Override
                    public void run() {
                        if (hasAllFilesAccess()) {
                            resolveBlowTorchRoot(activity);
                        }
                    }
                });
    }

    public static boolean hasPermissions(final AppCompatActivity activity, View root, final int code) {
        return hasPermissions(activity, root, code, null);
    }

    public static boolean hasPermissions(final AppCompatActivity activity, View root, final int code,
            final Runnable onGranted) {
        if (needsAllFilesAccessPrompt()) {
            openAllFilesAccessSettings(activity);
            return false;
        }
        int featureRes = PermissionHelper.featureMessageForRequestCode(code);
        return PermissionHelper.ensurePermissions(activity, root, code, getStoragePermissions(), featureRes,
                new Runnable() {
                    @Override
                    public void run() {
                        resolveBlowTorchRoot(activity);
                        if (onGranted != null) {
                            onGranted.run();
                        }
                    }
                });
    }
}
