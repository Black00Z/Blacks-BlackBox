package top.niunaijun.blackbox.fake.view;

import android.content.res.Resources;
import android.util.Log;

import java.util.Locale;

public class SafeResources extends Resources {
    private static final String TAG = "SafeResources";

    public SafeResources(Resources base) {
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    public static Resources wrap(Resources base) {
        if (base == null || base instanceof SafeResources) {
            return base;
        }
        return new SafeResources(base);
    }

    @Override
    public CharSequence getText(int id) throws NotFoundException {
        try {
            return super.getText(id);
        } catch (NotFoundException e) {
            String fallback = fallbackText(id);
            Log.w(TAG, "Missing text resource id=#" + Integer.toHexString(id) + ", returning fallback");
            return fallback;
        }
    }

    @Override
    public String getString(int id) throws NotFoundException {
        try {
            return super.getString(id);
        } catch (NotFoundException e) {
            String fallback = fallbackText(id);
            Log.w(TAG, "Missing string resource id=#" + Integer.toHexString(id) + ", returning fallback");
            return fallback;
        }
    }

    @Override
    public CharSequence getQuantityText(int id, int quantity) throws NotFoundException {
        try {
            return super.getQuantityText(id, quantity);
        } catch (NotFoundException e) {
            String fallback = fallbackText(id);
            Log.w(TAG, "Missing quantity text resource id=#" + Integer.toHexString(id) + ", returning fallback");
            return fallback;
        }
    }

    @Override
    public String getQuantityString(int id, int quantity) throws NotFoundException {
        try {
            return super.getQuantityString(id, quantity);
        } catch (NotFoundException e) {
            String fallback = fallbackText(id);
            Log.w(TAG, "Missing quantity string resource id=#" + Integer.toHexString(id) + ", returning fallback");
            return fallback;
        }
    }

    private static String fallbackText(int id) {
        return String.format(Locale.US, "res:#%08x", id);
    }
}
