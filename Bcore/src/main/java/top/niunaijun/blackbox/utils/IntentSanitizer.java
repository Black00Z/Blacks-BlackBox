package top.niunaijun.blackbox.utils;

import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;

public final class IntentSanitizer {
    private static final String TAG = "IntentSanitizer";
    private static final String CLASS_MARKER_PREFIX = "_B_|_class_extra_|";

    private IntentSanitizer() {
    }

    public static void sanitizeClassExtrasForIpc(Intent intent) {
        if (intent == null) {
            return;
        }
        sanitizeIntent(intent);
    }

    public static void restoreSanitizedClassExtras(Intent intent, ClassLoader classLoader) {
        if (intent == null) {
            return;
        }
        restoreIntent(intent, classLoader != null ? classLoader : IntentSanitizer.class.getClassLoader());
    }

    private static void sanitizeIntent(Intent intent) {
        sanitizeBundle(intent.getExtras());
        Intent selector = intent.getSelector();
        if (selector != null) {
            sanitizeIntent(selector);
        }
    }

    private static void sanitizeBundle(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        for (String key : new ArrayList<>(bundle.keySet())) {
            Object value;
            try {
                value = bundle.get(key);
            } catch (Throwable e) {
                Slog.w(TAG, "Removing unreadable extra before IPC: " + key + ", error=" + e.getClass().getSimpleName());
                bundle.remove(key);
                continue;
            }

            if (value instanceof Class<?>) {
                Class<?> clazz = (Class<?>) value;
                bundle.putString(CLASS_MARKER_PREFIX + key, clazz.getName());
                bundle.remove(key);
                Slog.d(TAG, "Sanitized Class extra for IPC: " + key + " -> " + clazz.getName());
                continue;
            }

            if (value instanceof Intent) {
                sanitizeIntent((Intent) value);
            } else if (value instanceof Bundle) {
                sanitizeBundle((Bundle) value);
            } else if (value instanceof ArrayList<?>) {
                sanitizeList((ArrayList<?>) value);
            }
        }
    }

    private static void sanitizeList(ArrayList<?> values) {
        for (Object value : values) {
            if (value instanceof Intent) {
                sanitizeIntent((Intent) value);
            } else if (value instanceof Bundle) {
                sanitizeBundle((Bundle) value);
            }
        }
    }

    private static void restoreIntent(Intent intent, ClassLoader classLoader) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            extras.setClassLoader(classLoader);
            restoreBundle(extras, classLoader);
        }
        Intent selector = intent.getSelector();
        if (selector != null) {
            restoreIntent(selector, classLoader);
        }
    }

    private static void restoreBundle(Bundle bundle, ClassLoader classLoader) {
        bundle.setClassLoader(classLoader);
        for (String key : new ArrayList<>(bundle.keySet())) {
            if (key.startsWith(CLASS_MARKER_PREFIX)) {
                String originalKey = key.substring(CLASS_MARKER_PREFIX.length());
                String className = bundle.getString(key);
                bundle.remove(key);
                if (className == null || bundle.containsKey(originalKey)) {
                    continue;
                }
                try {
                    bundle.putSerializable(originalKey, Class.forName(className, false, classLoader));
                    Slog.d(TAG, "Restored Class extra after IPC: " + originalKey + " <- " + className);
                } catch (Throwable e) {
                    Slog.w(TAG, "Failed to restore Class extra after IPC: " + originalKey + ", class=" + className + ", error=" + e.getClass().getSimpleName());
                }
                continue;
            }

            Object value;
            try {
                value = bundle.get(key);
            } catch (Throwable e) {
                Slog.w(TAG, "Removing unreadable extra after IPC: " + key + ", error=" + e.getClass().getSimpleName());
                bundle.remove(key);
                continue;
            }

            if (value instanceof Intent) {
                restoreIntent((Intent) value, classLoader);
            } else if (value instanceof Bundle) {
                restoreBundle((Bundle) value, classLoader);
            } else if (value instanceof ArrayList<?>) {
                restoreList((ArrayList<?>) value, classLoader);
            }
        }
    }

    private static void restoreList(ArrayList<?> values, ClassLoader classLoader) {
        for (Object value : values) {
            if (value instanceof Intent) {
                restoreIntent((Intent) value, classLoader);
            } else if (value instanceof Bundle) {
                restoreBundle((Bundle) value, classLoader);
            }
        }
    }
}
