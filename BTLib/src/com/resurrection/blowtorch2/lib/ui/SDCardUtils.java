package com.resurrection.blowtorch2.lib.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.view.View;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

public class SDCardUtils {

    public static String getSDCardRoot(AppCompatActivity a, boolean external) {
        try {
            String exportDir = ConfigurationLoader.getConfigurationValue("exportDirectory",a);
            Context c = a.createPackageContext(a.getPackageName(), Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            String dir = (external == true) ? "/" + exportDir : c.getExternalFilesDir(null).getAbsolutePath();
            return dir;
        } catch(PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
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
