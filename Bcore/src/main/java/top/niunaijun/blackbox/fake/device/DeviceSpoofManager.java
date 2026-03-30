package top.niunaijun.blackbox.fake.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.NativeCore;
import top.niunaijun.blackbox.utils.Md5Utils;
import top.niunaijun.blackbox.utils.Slog;

public final class DeviceSpoofManager {
    private static final String TAG = "DeviceSpoofManager";
    private static final String PREFS_NAME = "DeviceSpoofProfiles";

    private static final String KEY_MANUFACTURER = "manufacturer_";
    private static final String KEY_MODEL = "model_";
    private static final String KEY_BRAND = "brand_";
    private static final String KEY_DEVICE = "device_";
    private static final String KEY_PRODUCT = "product_";
    private static final String KEY_FINGERPRINT = "fingerprint_";
    private static final String KEY_SERIAL = "serial_";
    private static final String KEY_ANDROID_ID = "android_id_";

    private DeviceSpoofManager() {
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences prefs() {
        return BlackBoxCore.getContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public static DeviceSpoofProfile getProfile(int userId) {
        DeviceSpoofProfile defaults = getDefaultProfile(userId);
        SharedPreferences prefs = prefs();
        return new DeviceSpoofProfile(
                valueOrDefault(prefs.getString(KEY_MANUFACTURER + userId, null), defaults.manufacturer),
                valueOrDefault(prefs.getString(KEY_BRAND + userId, null), defaults.brand),
                valueOrDefault(prefs.getString(KEY_MODEL + userId, null), defaults.model),
                valueOrDefault(prefs.getString(KEY_DEVICE + userId, null), defaults.device),
                valueOrDefault(prefs.getString(KEY_PRODUCT + userId, null), defaults.product),
                valueOrDefault(prefs.getString(KEY_FINGERPRINT + userId, null), defaults.fingerprint),
                valueOrDefault(prefs.getString(KEY_SERIAL + userId, null), defaults.serial),
                valueOrDefault(prefs.getString(KEY_ANDROID_ID + userId, null), defaults.androidId)
        );
    }

    public static DeviceSpoofProfile getDefaultProfile(int userId) {
        return new DeviceSpoofProfile(
                "Google",
                "google",
                "Pixel 6",
                "oriole",
                "oriole",
                "google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys",
                "1A2B3C4D5E6F",
                stableAndroidId("bbb_default_" + userId)
        );
    }

    public static DeviceSpoofProfile getSamsungPreset(int userId) {
        return new DeviceSpoofProfile(
                "Samsung",
                "samsung",
                "Galaxy S26+",
                "s26plus",
                "s26plus",
                "samsung/s26plus/s26plus:16/BBX1.260329.001/S26PLUS:user/release-keys",
                "S26PBBX260329",
                stableAndroidId("bbb_samsung_" + userId)
        );
    }

    public static void saveUserProfile(int userId, String manufacturer, String model, String androidId) {
        DeviceSpoofProfile profile = buildCustomProfile(userId, manufacturer, model, androidId);
        prefs().edit()
                .putString(KEY_MANUFACTURER + userId, profile.manufacturer)
                .putString(KEY_BRAND + userId, profile.brand)
                .putString(KEY_MODEL + userId, profile.model)
                .putString(KEY_DEVICE + userId, profile.device)
                .putString(KEY_PRODUCT + userId, profile.product)
                .putString(KEY_FINGERPRINT + userId, profile.fingerprint)
                .putString(KEY_SERIAL + userId, profile.serial)
                .putString(KEY_ANDROID_ID + userId, profile.androidId)
                .commit();
    }

    public static void resetToDefaults(int userId) {
        prefs().edit()
                .remove(KEY_MANUFACTURER + userId)
                .remove(KEY_BRAND + userId)
                .remove(KEY_MODEL + userId)
                .remove(KEY_DEVICE + userId)
                .remove(KEY_PRODUCT + userId)
                .remove(KEY_FINGERPRINT + userId)
                .remove(KEY_SERIAL + userId)
                .remove(KEY_ANDROID_ID + userId)
                .commit();
    }

    public static void applyToCurrentProcess(int userId) {
        try {
            DeviceSpoofProfile profile = getProfile(userId);
            DeviceBuildSpoofer.apply(profile);
            NativeCore.setDeviceSpoof(
                    profile.manufacturer,
                    profile.brand,
                    profile.model,
                    profile.device,
                    profile.product,
                    profile.fingerprint,
                    profile.serial
            );
            Slog.d(TAG, "Applied device spoof for user " + userId + ": " + profile.manufacturer + " " + profile.model);
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to apply device spoof for user " + userId + ": " + e.getMessage());
        }
    }

    public static String getAndroidIdForCurrentUser() {
        int userId;
        try {
            userId = BActivityThread.getUserId();
        } catch (Throwable e) {
            userId = 0;
        }
        return getProfile(userId).androidId;
    }

    public static boolean isValidAndroidId(String value) {
        return !TextUtils.isEmpty(value) && value.matches("(?i)[0-9a-f]{16}");
    }

    private static DeviceSpoofProfile buildCustomProfile(int userId, String manufacturer, String model, String androidId) {
        String safeManufacturer = normalizeManufacturer(manufacturer);
        String safeBrand = normalizeBrand(safeManufacturer);
        String safeModel = normalizeModel(model);
        String safeDevice = slugify(safeModel);
        String safeProduct = safeDevice;
        String safeFingerprint = safeBrand + "/" + safeProduct + "/" + safeDevice + ":16/BBX1.260329.001/BBB001:user/release-keys";
        String safeSerial = stableSerial(safeManufacturer + "_" + safeModel + "_" + userId);
        String safeAndroidId = normalizeAndroidId(androidId, "bbb_custom_" + userId + "_" + safeDevice);
        return new DeviceSpoofProfile(
                safeManufacturer,
                safeBrand,
                safeModel,
                safeDevice,
                safeProduct,
                safeFingerprint,
                safeSerial,
                safeAndroidId
        );
    }

    private static String normalizeManufacturer(String manufacturer) {
        if (TextUtils.isEmpty(manufacturer)) {
            return "Google";
        }
        String trimmed = manufacturer.trim();
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase(Locale.US);
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.US) + trimmed.substring(1);
    }

    private static String normalizeBrand(String manufacturer) {
        return slugify(manufacturer);
    }

    private static String normalizeModel(String model) {
        if (TextUtils.isEmpty(model)) {
            return "Pixel 6";
        }
        return model.trim();
    }

    private static String normalizeAndroidId(String androidId, String seed) {
        if (!TextUtils.isEmpty(androidId)) {
            String normalized = androidId.trim().toLowerCase(Locale.US);
            if (isValidAndroidId(normalized)) {
                return normalized;
            }
        }
        return stableAndroidId(seed);
    }

    private static String stableAndroidId(String seed) {
        String hash = Md5Utils.md5(seed);
        if (TextUtils.isEmpty(hash)) {
            return "0f0f0f0f0f0f0f0f";
        }
        String normalized = hash.toLowerCase(Locale.US);
        return normalized.length() >= 16 ? normalized.substring(0, 16) : String.format(Locale.US, "%1$-16s", normalized).replace(' ', '0');
    }

    private static String stableSerial(String seed) {
        String hash = Md5Utils.md5(seed);
        if (TextUtils.isEmpty(hash)) {
            return "BBB000000000";
        }
        String normalized = hash.toUpperCase(Locale.US);
        return normalized.length() >= 12 ? normalized.substring(0, 12) : String.format(Locale.US, "%1$-12s", normalized).replace(' ', '0');
    }

    private static String valueOrDefault(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static String slugify(String input) {
        if (TextUtils.isEmpty(input)) {
            return "device";
        }
        String normalized = input.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        return TextUtils.isEmpty(normalized) ? "device" : normalized;
    }
}
