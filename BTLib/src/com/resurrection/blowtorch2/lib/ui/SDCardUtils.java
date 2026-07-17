package com.resurrection.blowtorch2.lib.ui;

import java.io.File;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
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
     */
    public static File resolveDefaultSettingsDirectory(Context context, boolean hasSharedStorage,
            String customPath) {
        if (!TextUtils.isEmpty(customPath)) {
            File custom = new File(customPath.trim());
            if (!custom.exists()) {
                //noinspection ResultOfMethodCallIgnored
                custom.mkdirs();
            }
            return custom;
        }
        String exportDir = ConfigurationLoader.getConfigurationValue("exportDirectory", context);
        if (hasSharedStorage) {
            File root = Environment.getExternalStorageDirectory();
            File dir = new File(root, exportDir);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            return dir;
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
