package top.niunaijun.blackbox.fake.device;

import android.os.Build;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import top.niunaijun.blackbox.utils.Slog;

public final class DeviceBuildSpoofer {
    private static final String TAG = "DeviceBuildSpoofer";

    private DeviceBuildSpoofer() {
    }

    public static void apply(DeviceSpoofProfile profile) {
        if (profile == null) {
            return;
        }
        setStaticStringField("MANUFACTURER", profile.manufacturer);
        setStaticStringField("BRAND", profile.brand);
        setStaticStringField("MODEL", profile.model);
        setStaticStringField("DEVICE", profile.device);
        setStaticStringField("PRODUCT", profile.product);
        setStaticStringField("FINGERPRINT", profile.fingerprint);
        setStaticStringField("SERIAL", profile.serial);
    }

    private static void setStaticStringField(String fieldName, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        try {
            Field field = Build.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            try {
                field.set(null, value);
                return;
            } catch (Throwable ignored) {
            }

            Object unsafe = getUnsafe();
            if (unsafe == null) {
                throw new IllegalStateException("Unsafe unavailable for " + fieldName);
            }
            Method staticFieldBase = unsafe.getClass().getMethod("staticFieldBase", Field.class);
            Method staticFieldOffset = unsafe.getClass().getMethod("staticFieldOffset", Field.class);
            Method putObject = unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class);
            Object base = staticFieldBase.invoke(unsafe, field);
            long offset = ((Number) staticFieldOffset.invoke(unsafe, field)).longValue();
            putObject.invoke(unsafe, base, offset, value);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to spoof Build." + fieldName + ": " + e.getMessage());
        }
    }

    private static Object getUnsafe() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to access Unsafe: " + e.getMessage());
            return null;
        }
    }
}
