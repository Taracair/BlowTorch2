package com.offsetnull.bt.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.offsetnull.bt.R;

import android.view.View;

/**
 * Runtime permission flow with reminders when the user denies access needed for a feature.
 */
public final class PermissionHelper {

    private static final String PREFS = "BLOWTORCH_PERMISSIONS";

    public static final int FEATURE_CONNECT = 1;
    public static final int FEATURE_IMPORT = 2;
    public static final int FEATURE_EXPORT = 3;
    public static final int FEATURE_SALVAGE = 4;
    public static final int FEATURE_NOTIFICATIONS = 5;
    public static final int FEATURE_STARTUP = 6;

    private PermissionHelper() {
    }

    public static boolean hasInternetPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean allGranted(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String[] getNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[] { Manifest.permission.POST_NOTIFICATIONS };
        }
        return new String[0];
    }

    public static boolean ensureInternetForFeature(final AppCompatActivity activity, final int featureRes,
            final Runnable onGranted) {
        if (hasInternetPermission(activity)) {
            if (onGranted != null) {
                onGranted.run();
            }
            return true;
        }
        showFeatureBlockedDialog(activity, featureRes, null, 0, true);
        return false;
    }

    /**
     * Requests missing permissions or shows a reminder when the user previously denied them.
     *
     * @return true when all permissions are already granted and {@code onGranted} was invoked.
     */
    public static boolean ensurePermissions(final AppCompatActivity activity, View anchor, final int requestCode,
            final String[] permissions, final int featureRes, final Runnable onGranted) {
        if (permissions == null || permissions.length == 0) {
            if (onGranted != null) {
                onGranted.run();
            }
            return true;
        }
        if (allGranted(activity, permissions)) {
            if (onGranted != null) {
                onGranted.run();
            }
            return true;
        }
        if (shouldShowAnyRationale(activity, permissions)) {
            Snackbar.make(anchor, activity.getString(R.string.permission_rationale_template,
                            activity.getString(featureRes)),
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            markRequested(activity, permissions);
                            ActivityCompat.requestPermissions(activity, permissions, requestCode);
                        }
                    })
                    .show();
            return false;
        }
        if (!wasEverRequested(activity, permissions)) {
            markRequested(activity, permissions);
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
            return false;
        }
        showFeatureBlockedDialog(activity, featureRes, permissions, requestCode, false);
        return false;
    }

    public static void handlePermissionResult(final AppCompatActivity activity, View anchor, int requestCode,
            int expectedRequestCode, String[] permissions, int[] grantResults, final int featureRes,
            final Runnable onGranted, final Runnable onDenied) {
        if (requestCode != expectedRequestCode) {
            return;
        }
        if (permissions == null || grantResults == null || permissions.length == 0) {
            return;
        }
        if (allGranted(activity, permissions)) {
            if (onGranted != null) {
                onGranted.run();
            }
            return;
        }
        if (onDenied != null) {
            onDenied.run();
        }
        if (shouldShowAnyRationale(activity, permissions)) {
            Snackbar.make(anchor, activity.getString(R.string.permission_denied_retry_template,
                            activity.getString(featureRes)),
                    Snackbar.LENGTH_LONG)
                    .setAction(R.string.permission_try_again, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(activity, permissions, expectedRequestCode);
                        }
                    })
                    .show();
        } else {
            showFeatureBlockedDialog(activity, featureRes, permissions, expectedRequestCode, false);
        }
    }

    public static void showFeatureBlockedDialog(final AppCompatActivity activity, final int featureRes,
            final String[] permissions, final int requestCode, final boolean internetOnly) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_required_title)
                .setMessage(activity.getString(R.string.permission_blocked_template,
                        activity.getString(featureRes)))
                .setNegativeButton(android.R.string.cancel, null);
        if (internetOnly) {
            builder.setPositiveButton(R.string.permission_open_settings, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    openAppSettings(activity);
                }
            });
        } else {
            builder.setPositiveButton(R.string.permission_open_settings, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    openAppSettings(activity);
                }
            });
            if (permissions != null && permissions.length > 0) {
                builder.setNeutralButton(R.string.permission_try_again, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(activity, permissions, requestCode);
                    }
                });
            }
        }
        builder.show();
    }

    public static void openAppSettings(AppCompatActivity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

    private static boolean shouldShowAnyRationale(AppCompatActivity activity, String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }

    private static void markRequested(Context context, String[] permissions) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String permission : permissions) {
            editor.putBoolean(requestKey(permission), true);
        }
        editor.apply();
    }

    private static boolean wasEverRequested(Context context, String[] permissions) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (String permission : permissions) {
            if (prefs.getBoolean(requestKey(permission), false)) {
                return true;
            }
        }
        return false;
    }

    private static String requestKey(String permission) {
        return "requested_" + permission;
    }

    public static int featureMessageForRequestCode(int requestCode) {
        switch (requestCode) {
            case 100:
                return R.string.permission_feature_storage_info;
            case 101:
                return R.string.permission_feature_salvage;
            case 102:
                return R.string.permission_feature_export;
            case 103:
                return R.string.permission_feature_import;
            case 104:
                return R.string.permission_feature_startup;
            case 5000:
                return R.string.permission_feature_storage_info;
            case 5001:
                return R.string.permission_feature_export;
            case 5002:
                return R.string.permission_feature_import;
            case 5003:
                return R.string.permission_feature_notifications;
            default:
                return R.string.permission_feature_storage;
        }
    }
}
