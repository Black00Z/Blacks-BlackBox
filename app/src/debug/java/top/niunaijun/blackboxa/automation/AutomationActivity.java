package top.niunaijun.blackboxa.automation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

/**
 * Debug-only automation entrypoint.
 *
 * Invokable via adb:
 *   adb shell am start -n top.niunaijun.blackbox/top.niunaijun.blackboxa.automation.AutomationActivity \
 *     --es apk_path "/sdcard/Android/data/top.niunaijun.blackbox/files/automation/target.apk" \
 *     --es package_name "com.instagram.android" \
 *     --ei user_id 0 \
 *     --ez launch true
 */
public class AutomationActivity extends Activity {

    private static final String TAG = "AutomationActivity";

    public static final String EXTRA_APK_PATH = "apk_path";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_LAUNCH = "launch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        runAutomation(getIntent(), "onCreate");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        runAutomation(intent, "onNewIntent");
    }

    private void runAutomation(Intent intent, String entry) {
        final String apkPath = intent != null ? intent.getStringExtra(EXTRA_APK_PATH) : null;
        final String packageName = intent != null ? intent.getStringExtra(EXTRA_PACKAGE_NAME) : null;
        final int userId = intent != null ? intent.getIntExtra(EXTRA_USER_ID, 0) : 0;
        final boolean shouldLaunch = intent == null || intent.getBooleanExtra(EXTRA_LAUNCH, true);

        final boolean hasApkPath = apkPath != null && !apkPath.trim().isEmpty();
        final boolean hasPackageName = packageName != null && !packageName.trim().isEmpty();

        if (!hasApkPath && !hasPackageName) {
            Log.e(TAG, entry + ": Missing extra: one of {" + EXTRA_APK_PATH + ", " + EXTRA_PACKAGE_NAME + "} is required");
            finish();
            return;
        }

        final File apkFile;
        if (hasApkPath) {
            apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                Log.e(TAG, entry + ": APK does not exist: " + apkPath);
                finish();
                return;
            }
        } else {
            apkFile = null;
        }

        Log.i(TAG, entry + ": Starting automation: install apkPath=" + apkPath + " packageName=" + packageName + " userId=" + userId + " launch=" + shouldLaunch);

        new Thread(() -> {
            long startMs = System.currentTimeMillis();
            try {
                if (hasPackageName) {
                    try {
                        BlackBoxCore.get().clearPackage(packageName, userId);
                        Log.i(TAG, entry + ": Cleared existing package state: packageName=" + packageName + " userId=" + userId);
                    } catch (Throwable clearError) {
                        Log.w(TAG, entry + ": clearPackage failed (continuing): packageName=" + packageName + " userId=" + userId + " err=" + clearError.getMessage());
                    }
                }

                final InstallResult installResult;
                if (hasPackageName) {
                    installResult = BlackBoxCore.get().installPackageAsUser(packageName, userId);
                } else {
                    installResult = BlackBoxCore.get().installPackageAsUser(apkFile, userId);
                }
                long elapsedMs = System.currentTimeMillis() - startMs;

                if (installResult == null || !installResult.success) {
                    String msg = installResult != null ? installResult.msg : "null InstallResult";
                    Log.e(TAG, entry + ": Install failed (" + elapsedMs + "ms): " + msg);
                    finishSafely();
                    return;
                }

                String pkg = installResult.packageName;
                Log.i(TAG, entry + ": Install success (" + elapsedMs + "ms): packageName=" + pkg);

                if (shouldLaunch && pkg != null && !pkg.trim().isEmpty()) {
                    try {
                        boolean launchOk = BlackBoxCore.get().launchApk(pkg, userId);
                        Log.i(TAG, entry + ": Launch result: " + launchOk + " packageName=" + pkg + " userId=" + userId);
                    } catch (Throwable e) {
                        Log.e(TAG, entry + ": Launch threw: " + e.getMessage(), e);
                    }
                }

                finishSafely();
            } catch (Throwable e) {
                Log.e(TAG, entry + ": Automation failed: " + e.getMessage(), e);
                finishSafely();
            }
        }).start();
    }

    private void finishSafely() {
        try {
            runOnUiThread(() -> {
                try {
                    finish();
                } catch (Throwable e) {
                    Log.e(TAG, "finish failed: " + e.getMessage(), e);
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "finishSafely failed: " + e.getMessage(), e);
            try {
                finish();
            } catch (Throwable ignored) {
                // ignored
            }
        }
    }
}
