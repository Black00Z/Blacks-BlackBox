package top.niunaijun.blackbox.core.env;

import android.content.Context;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.Slog;

public final class ExternalActivityGuard {
    private static final String TAG = "ExternalActivityGuard";
    private static final String PREFS_NAME = "blackbox_external_activity_guard";
    private static final long OPEN_HOST_ACTIVITY_GRACE_MS = 3 * 60 * 1000L;

    private ExternalActivityGuard() {
    }

    private static String key(String packageName, int userId) {
        return "guard_until_" + userId + "_" + packageName;
    }

    private static Context context() {
        return BlackBoxCore.getContext();
    }

    public static void markOpenHostActivityLaunch(String packageName, int userId, String targetPackage) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        long until = System.currentTimeMillis() + OPEN_HOST_ACTIVITY_GRACE_MS;
        context()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(key(packageName, userId), until)
                .apply();
        Slog.d(TAG, "Marked external activity grace for " + packageName + " userId=" + userId
                + " target=" + targetPackage + " until=" + until);
    }

    public static void clear(String packageName, int userId) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        context()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(key(packageName, userId))
                .apply();
    }

    public static long getRemainingMs(String packageName, int userId) {
        if (packageName == null || packageName.isEmpty()) {
            return 0L;
        }
        long until = context()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(key(packageName, userId), 0L);
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L && until != 0L) {
            clear(packageName, userId);
            return 0L;
        }
        return remaining;
    }
}
