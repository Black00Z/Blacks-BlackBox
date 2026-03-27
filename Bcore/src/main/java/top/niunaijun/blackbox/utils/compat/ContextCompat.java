package top.niunaijun.blackbox.utils.compat;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Process;
import android.text.TextUtils;
import android.util.*;

import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import black.android.content.AttributionSourceStateContext;
import black.android.content.BRAttributionSource;
import black.android.content.BRAttributionSourceState;
import black.android.content.BRContentResolver;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;


public class ContextCompat {
    public static final String TAG = "ContextCompat";

    public static void fixAttributionSourceState(Object obj, String packageName, int uid) {
        Object mAttributionSourceState;
        if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
            mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();

            AttributionSourceStateContext attributionSourceStateContext = BRAttributionSourceState.get(mAttributionSourceState);
            attributionSourceStateContext._set_packageName(packageName);
            attributionSourceStateContext._set_uid(uid);
            fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), packageName, uid);
        }
    }

    public static void fix(Context context) {
        try {
            
            if (context == null) {
                Slog.w(TAG, "Context is null, skipping ContextCompat.fix");
                return;
            }
            
            int deep = 0;
            while (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
                deep++;
                if (deep >= 10) {
                    return;
                }
            }
            
            
            if (context == null) {
                Slog.w(TAG, "Base context is null after unwrapping, skipping ContextCompat.fix");
                return;
            }
            
            BRContextImpl.get(context)._set_mPackageManager(null);
            try {
                context.getPackageManager();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            String contextPkg = null;
            try {
                contextPkg = context.getPackageName();
            } catch (Throwable ignored) {
            }

            String threadPkg = null;
            try {
                threadPkg = BActivityThread.getAppPackageName();
            } catch (Throwable ignored) {
            }

            String hostPkg = BlackBoxCore.getHostPkg();
            String pkg;
            if (!TextUtils.isEmpty(contextPkg) && !TextUtils.equals(contextPkg, hostPkg)) {
                pkg = contextPkg;
            } else if (!TextUtils.isEmpty(threadPkg)) {
                pkg = threadPkg;
            } else if (!TextUtils.isEmpty(contextPkg)) {
                pkg = contextPkg;
            } else {
                pkg = hostPkg;
            }

            BRContextImpl.get(context)._set_mBasePackageName(pkg);
            BRContextImplKitkat.get(context)._set_mOpPackageName(pkg);

            // Android 14+/16 work profiles are sensitive to userId mismatches.
            // Ensure ContextImpl user fields reflect the real Android host user.
            int hostUserId = BlackBoxCore.getHostUserId();
            if (hostUserId == 0) {
                hostUserId = Process.myUid() / 100000;
            }
            if (hostUserId != 0) {
                forceContextUser(context, hostUserId);
            }
            
            try {
                BRContentResolver.get(context.getContentResolver())._set_mPackageName(pkg);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to fix content resolver: " + e.getMessage());
            }

            if (BuildCompat.isS()) {
                try {
                    int uid = -1;
                    try {
                        uid = BActivityThread.getUid();
                    } catch (Throwable ignored) {
                    }
                    if (uid <= 0) {
                        uid = BlackBoxCore.getHostUid();
                    }

                    fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), pkg, uid);
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to fix attribution source state: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error in ContextCompat.fix: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void forceContextUser(Context context, int userId) {
        try {
            Class<?> c = context.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("mUser");
                    f.setAccessible(true);
                    Object userHandle = buildUserHandle(userId);
                    if (userHandle != null) {
                        f.set(context, userHandle);
                    }
                    break;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> c = context.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("mUserId");
                    f.setAccessible(true);
                    f.setInt(context, userId);
                    break;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object buildUserHandle(int userId) {
        try {
            Class<?> clazz = Class.forName("android.os.UserHandle");
            java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(userId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
