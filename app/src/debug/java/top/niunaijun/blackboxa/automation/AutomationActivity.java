package top.niunaijun.blackboxa.automation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;

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

    // Optional behavior toggles.
    // Defaults are chosen to be safe for "already installed" use-cases.
    public static final String EXTRA_CLEAR = "clear";   // default: true for apk_path installs, false otherwise
    public static final String EXTRA_INSTALL = "install"; // default: true

    // Optional fake-location injection (host-side config).
    public static final String EXTRA_FAKE_LOCATION_ENABLE = "fake_location_enable"; // default: false
    public static final String EXTRA_FAKE_LOCATION_LAT = "fake_location_lat";
    public static final String EXTRA_FAKE_LOCATION_LNG = "fake_location_lng";
    public static final String EXTRA_FAKE_LOCATION_MODE = "fake_location_mode"; // default: OWN_MODE

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

        final boolean shouldInstall = intent == null || intent.getBooleanExtra(EXTRA_INSTALL, true);
        final boolean defaultClear = hasApkPath;
        final boolean shouldClear = intent != null ? intent.getBooleanExtra(EXTRA_CLEAR, defaultClear) : defaultClear;

        final boolean enableFakeLocation = intent != null && intent.getBooleanExtra(EXTRA_FAKE_LOCATION_ENABLE, false);
        final double fakeLat = getDoubleOrStringExtra(intent, EXTRA_FAKE_LOCATION_LAT, 0d);
        final double fakeLng = getDoubleOrStringExtra(intent, EXTRA_FAKE_LOCATION_LNG, 0d);
        final int fakeMode = intent != null ? intent.getIntExtra(EXTRA_FAKE_LOCATION_MODE, BLocationManager.OWN_MODE) : BLocationManager.OWN_MODE;

        Log.i(TAG, entry + ": Starting automation: install apkPath=" + apkPath + " packageName=" + packageName + " userId=" + userId + " install=" + shouldInstall + " clear=" + shouldClear + " launch=" + shouldLaunch);

        new Thread(() -> {
            long startMs = System.currentTimeMillis();
            try {
                // If the caller provided a package name and it's already installed in BlackBox,
                // prefer launching it directly (no clear/reinstall). This matches real-world
                // "already installed" debugging scenarios.
                if (hasPackageName) {
                    boolean alreadyInstalled = false;
                    try {
                        alreadyInstalled = BlackBoxCore.get().isInstalled(packageName, userId);
                    } catch (Throwable ignored) {
                    }

                    if (alreadyInstalled && !shouldInstall) {
                        if (shouldClear) {
                            try {
                                BlackBoxCore.get().clearPackage(packageName, userId);
                                Log.i(TAG, entry + ": Cleared package data: packageName=" + packageName + " userId=" + userId);
                            } catch (Throwable clearError) {
                                Log.w(TAG, entry + ": clearPackage failed (continuing): packageName=" + packageName + " userId=" + userId + " err=" + clearError.getMessage());
                            }
                        }

                        if (shouldLaunch) {
                            try {
                                boolean launchOk = BlackBoxCore.get().launchApk(packageName, userId);
                                Log.i(TAG, entry + ": Launch result: " + launchOk + " packageName=" + packageName + " userId=" + userId);
                            } catch (Throwable e) {
                                Log.e(TAG, entry + ": Launch threw: " + e.getMessage(), e);
                            }
                        }

                        finishSafely();
                        return;
                    }

                    if (!shouldInstall) {
                        Log.e(TAG, entry + ": Package not installed in BlackBox and install=false: packageName=" + packageName + " userId=" + userId);
                        finishSafely();
                        return;
                    }
                }

                if (shouldClear && hasPackageName) {
                    try {
                        BlackBoxCore.get().clearPackage(packageName, userId);
                        Log.i(TAG, entry + ": Cleared package data: packageName=" + packageName + " userId=" + userId);
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

                if (enableFakeLocation && pkg != null && !pkg.trim().isEmpty()) {
                    try {
                        BLocationManager.get().setPattern(userId, pkg, fakeMode);
                        BLocationManager.get().setLocation(userId, pkg, new BLocation(fakeLat, fakeLng));
                        Log.i(TAG, entry + ": Fake location set: packageName=" + pkg + " userId=" + userId + " mode=" + fakeMode + " lat=" + fakeLat + " lng=" + fakeLng);
                    } catch (Throwable t) {
                        Log.w(TAG, entry + ": Failed to set fake location (continuing): " + t.getMessage());
                    }
                }

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

    private static double getDoubleOrStringExtra(Intent intent, String key, double def) {
        if (intent == null || key == null || key.trim().isEmpty()) {
            return def;
        }
        try {
            if (intent.hasExtra(key)) {
                return intent.getDoubleExtra(key, def);
            }
        } catch (Throwable ignored) {
            // ignore
        }
        try {
            String s = intent.getStringExtra(key);
            if (s != null && !s.trim().isEmpty()) {
                return Double.parseDouble(s.trim());
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return def;
    }
}
