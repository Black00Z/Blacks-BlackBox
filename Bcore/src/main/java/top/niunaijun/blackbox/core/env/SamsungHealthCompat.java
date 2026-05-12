package top.niunaijun.blackbox.core.env;

import android.content.Context;
import android.content.SharedPreferences;

import top.niunaijun.blackbox.BlackBoxCore;

public final class SamsungHealthCompat {
    private static final String PREFS_NAME = "CompatRules";
    private static final String KEY_HOST_SAMSUNG_ACCOUNT_FALLBACK_PREFIX = "shealth_host_sa_fallback_";
    private static final String KEY_LAST_SASDK_REDIRECT_USER_ID = "shealth_last_sasdk_redirect_user_id";

    private SamsungHealthCompat() {
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences prefs() {
        Context context = BlackBoxCore.getContext();
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public static boolean isHostSamsungAccountFallbackEnabled(int userId, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        try {
            return prefs().getBoolean(keyFor(userId, packageName), false);
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void setHostSamsungAccountFallbackEnabled(int userId, String packageName, boolean enabled) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return;
        }
        try {
            prefs().edit().putBoolean(keyFor(userId, packageName), enabled).commit();
        } catch (Throwable ignored) {
        }
    }

    public static void setLastSaSdkRedirectUserId(int userId) {
        try {
            prefs().edit().putInt(KEY_LAST_SASDK_REDIRECT_USER_ID, userId).commit();
        } catch (Throwable ignored) {
        }
    }

    public static int getLastSaSdkRedirectUserId() {
        try {
            return prefs().getInt(KEY_LAST_SASDK_REDIRECT_USER_ID, 0);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static String keyFor(int userId, String packageName) {
        return KEY_HOST_SAMSUNG_ACCOUNT_FALLBACK_PREFIX + userId + "_" + packageName;
    }
}
