package top.niunaijun.blackbox.fake.delegate;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Build;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import black.android.app.BRActivity;
import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.hook.IInjectHook;
import top.niunaijun.blackbox.fake.service.HCallbackProxy;
import top.niunaijun.blackbox.fake.service.IActivityClientProxy;
import top.niunaijun.blackbox.fake.view.SafeResources;
import top.niunaijun.blackbox.utils.HackAppUtils;
import top.niunaijun.blackbox.utils.compat.ActivityCompat;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

public final class AppInstrumentation extends BaseInstrumentationDelegate implements IInjectHook {

    private static final String TAG = AppInstrumentation.class.getSimpleName();
    private static final Set<String> sForcePlainApplicationPackages = ConcurrentHashMap.newKeySet();

    private static AppInstrumentation sAppInstrumentation;

    public static AppInstrumentation get() {
        if (sAppInstrumentation == null) {
            synchronized (AppInstrumentation.class) {
                if (sAppInstrumentation == null) {
                    sAppInstrumentation = new AppInstrumentation();
                }
            }
        }
        return sAppInstrumentation;
    }

    public AppInstrumentation() {
    }

    @Override
    public void injectHook() {
        try {
            Instrumentation mInstrumentation = getCurrInstrumentation();
            if (mInstrumentation == this || checkInstrumentation(mInstrumentation))
                return;
            mBaseInstrumentation = (Instrumentation) mInstrumentation;
            BRActivityThread.get(BlackBoxCore.mainThread())._set_mInstrumentation(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Instrumentation getCurrInstrumentation() {
        Object currentActivityThread = BlackBoxCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mInstrumentation();
    }

    @Override
    public boolean isBadEnv() {
        return !checkInstrumentation(getCurrInstrumentation());
    }

    private boolean checkInstrumentation(Instrumentation instrumentation) {
        if (instrumentation instanceof AppInstrumentation) {
            return true;
        }
        Class<?> clazz = instrumentation.getClass();
        if (Instrumentation.class.equals(clazz)) {
            return false;
        }
        do {
            assert clazz != null;
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (Instrumentation.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object obj = field.get(instrumentation);
                        if ((obj instanceof AppInstrumentation)) {
                            return true;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        } while (!Instrumentation.class.equals(clazz));
        return false;
    }

    private void checkHCallback() {
        HookManager.get().checkEnv(HCallbackProxy.class);
    }

    private void checkActivity(Activity activity) {
        Log.d(TAG, "callActivityOnCreate: " + activity.getClass().getName());
        HackAppUtils.enableQQLogOutput(activity.getPackageName(), activity.getClassLoader());
        checkHCallback();
        HookManager.get().checkEnv(IActivityClientProxy.class);
        ActivityInfo info = BRActivity.get(activity).mActivityInfo();
        ContextCompat.fix(activity);
        forceActivityContextPackage(activity);
        ensureActivityResourceContext(activity);
        ActivityCompat.fix(activity);
        if (info.theme != 0) {
            activity.getTheme().applyStyle(info.theme, true);
        }
        ActivityManagerCompat.setActivityOrientation(activity, info.screenOrientation);
    }

    private void forceActivityContextPackage(Activity activity) {
        if (activity == null) {
            return;
        }

        String pkg = activity.getPackageName();
        if (pkg == null || pkg.length() == 0) {
            return;
        }

        Context context = activity;
        int deep = 0;
        while (context instanceof ContextWrapper && deep++ < 10) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == null || base == context) {
                break;
            }
            context = base;
        }

        try {
            BRContextImpl.get(context)._set_mBasePackageName(pkg);
        } catch (Throwable ignored) {
        }
        try {
            BRContextImplKitkat.get(context)._set_mOpPackageName(pkg);
        } catch (Throwable ignored) {
        }
    }

    private void ensureActivityResourceContext(Activity activity) {
        if (activity == null) {
            return;
        }

        ApplicationInfo appInfo;
        try {
            appInfo = activity.getApplicationInfo();
        } catch (Throwable ignored) {
            return;
        }

        if (appInfo == null || appInfo.labelRes == 0) {
            return;
        }

        Resources currentRes;
        try {
            currentRes = activity.getResources();
            currentRes.getText(appInfo.labelRes);
            return;
        } catch (Throwable ignored) {
        }

        String pkg = activity.getPackageName();
        if (TextUtils.isEmpty(pkg)) {
            return;
        }

        boolean safeFallbackPkg = sForcePlainApplicationPackages.contains(pkg);
        if (safeFallbackPkg) {
            try {
                Resources safeRes = SafeResources.wrap(activity.getResources());
                forceResourcesOnContextChain(activity, safeRes);
            } catch (Throwable ignored) {
            }
        }

        try {
            Context packageContext = activity.createPackageContext(pkg,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Resources packageRes = packageContext.getResources();
            packageRes.getText(appInfo.labelRes);

            if (safeFallbackPkg) {
                packageRes = SafeResources.wrap(packageRes);
            }

            boolean patched = forceResourcesOnContextChain(activity, packageRes);
            if (patched) {
                Log.w(TAG, "Auto-repaired resource context for pkg=" + pkg + " activity=" + activity.getClass().getName());
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        } catch (Throwable t) {
            Log.w(TAG, "Failed to auto-repair resource context for " + pkg, t);
        }
    }

    private static boolean forceResourcesOnContextChain(Context context, Resources resources) {
        boolean changed = false;
        Context cursor = context;
        int deep = 0;
        while (cursor != null && deep++ < 12) {
            changed |= setFieldIfPresent(cursor, "mResources", resources);
            // Force theme recreation to bind against new Resources implementation.
            changed |= setFieldIfPresent(cursor, "mTheme", null);

            if (cursor instanceof ContextWrapper) {
                Context base = ((ContextWrapper) cursor).getBaseContext();
                if (base == null || base == cursor) {
                    break;
                }
                cursor = base;
            } else {
                break;
            }
        }
        return changed;
    }

    private static boolean setFieldIfPresent(Object target, String fieldName, Object value) {
        if (target == null) {
            return false;
        }

        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Context safeContext = ensureNonNullApplicationContext(context);
        ContextCompat.fix(safeContext);
        String packageName = resolvePackageName(safeContext);

        if (shouldUsePlainApplicationDirectly(packageName, className)) {
            return createPlainApplication(cl, safeContext, packageName, className, "cached-failure-policy", null);
        }

        try {
            return super.newApplication(cl, className, safeContext);
        } catch (Throwable t) {
            if (shouldFallbackToPlainApplication(className, safeContext, t)
                    && !Application.class.getName().equals(className)) {
                if (!TextUtils.isEmpty(packageName)) {
                    sForcePlainApplicationPackages.add(packageName);
                }
                return createPlainApplication(cl, safeContext, packageName, className, "auto-detected-init-failure", t);
            }

            if (t instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) t;
            }
            if (t instanceof InstantiationException) {
                throw (InstantiationException) t;
            }
            if (t instanceof IllegalAccessException) {
                throw (IllegalAccessException) t;
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException(t);
        }
    }

    private static Application createPlainApplication(
            ClassLoader cl,
            Context context,
            String packageName,
            String originalClassName,
            String reason,
            Throwable error
    ) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (error != null) {
            Log.w(TAG, "Application init failed for package=" + packageName
                    + ", class=" + originalClassName
                    + ", switching to plain Application via " + reason, error);
        } else {
            Log.w(TAG, "Using plain Application for package=" + packageName
                    + ", class=" + originalClassName
                    + " due to " + reason);
        }
        Application app = AppInstrumentation.get().superNewApplication(cl, Application.class.getName(), context);
        AppInstrumentation.get().installSafeResourcesForPackageContext(packageName, context);
        return app;
    }

    private Application superNewApplication(ClassLoader cl, String className, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(cl, className, context);
    }

    private static boolean shouldUsePlainApplicationDirectly(String packageName, String className) {
        if (Application.class.getName().equals(className) || TextUtils.isEmpty(packageName)) {
            return false;
        }
        return sForcePlainApplicationPackages.contains(packageName);
    }

    private static String resolvePackageName(Context context) {
        if (context == null) {
            return null;
        }
        try {
            return context.getPackageName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean shouldFallbackToPlainApplication(String className, Context context, Throwable t) {
        if (Application.class.getName().equals(className) || isFrameworkApplicationClass(className)) {
            return false;
        }

        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (cur instanceof NullPointerException && isApplicationAttachFailure(cur)) {
                return true;
            }
            if (msg != null && (msg.contains("Unable to instantiate application")
                    || msg.contains("attachBaseContext")
                    || msg.contains("LoadedApk.mPackageName")
                    || msg.contains("LoadedApk.makeApplication"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFrameworkApplicationClass(String className) {
        return !TextUtils.isEmpty(className) && className.startsWith("android.");
    }

    private static boolean isApplicationAttachFailure(Throwable t) {
        StackTraceElement[] stackTrace = t != null ? t.getStackTrace() : null;
        if (stackTrace == null) {
            return false;
        }
        for (StackTraceElement element : stackTrace) {
            String cls = element.getClassName();
            String method = element.getMethodName();
            if (("android.app.Application".equals(cls) && "attach".equals(method))
                    || ("android.app.LoadedApk".equals(cls) && method != null && method.contains("makeApplication"))
                    || (method != null && method.contains("attachBaseContext"))) {
                return true;
            }
        }
        return false;
    }

    private static Context ensureNonNullApplicationContext(Context context) {
        if (context == null) {
            return null;
        }
        try {
            if (context.getApplicationContext() != null) {
                return context;
            }
        } catch (Throwable ignored) {
        }

        final Context base = context;
        return new android.content.ContextWrapper(base) {
            @Override
            public Context getApplicationContext() {
                try {
                    Context app = base.getApplicationContext();
                    if (app != null) {
                        return app;
                    }
                } catch (Throwable ignored) {
                }
                return this;
            }
        };
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        checkActivity(activity);
        super.callActivityOnCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        checkActivity(activity);
        super.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        checkHCallback();
        maybeForceStableLocale(app);
        super.callApplicationOnCreate(app);
    }

    private void maybeForceStableLocale(Application app) {
        if (app == null) {
            return;
        }

        String pkg;
        try {
            pkg = app.getPackageName();
        } catch (Throwable ignored) {
            return;
        }

        if (TextUtils.isEmpty(pkg) || !sForcePlainApplicationPackages.contains(pkg)) {
            return;
        }

        installSafeResourcesForPackageContext(pkg, app.getBaseContext());

        try {
            Resources res = app.getResources();
            if (res == null) {
                return;
            }

            Configuration oldConfig = res.getConfiguration();
            Configuration newConfig = new Configuration(oldConfig);
            Locale stableLocale = Locale.US;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                newConfig.setLocale(stableLocale);
            } else {
                newConfig.locale = stableLocale;
            }
            res.updateConfiguration(newConfig, res.getDisplayMetrics());
            Log.w(TAG, "Applied stable-locale resource fallback for package=" + pkg);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to apply stable-locale resource fallback for package=" + pkg, t);
        }
    }

    private void installSafeResourcesForPackageContext(String pkg, Context context) {
        if (TextUtils.isEmpty(pkg) || context == null) {
            return;
        }
        if (!sForcePlainApplicationPackages.contains(pkg)) {
            return;
        }

        try {
            Resources safe = SafeResources.wrap(context.getResources());
            forceResourcesOnContextChain(context, safe);

            Object loadedApk = extractLoadedApk(context);
            if (loadedApk != null) {
                setFieldIfPresent(loadedApk, "mResources", safe);
            }
            Log.w(TAG, "Installed package-root SafeResources for package=" + pkg + " class=" + safe.getClass().getName());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to install package-root SafeResources for package=" + pkg, t);
        }
    }

    private static Object extractLoadedApk(Context context) {
        Class<?> c = context.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("mPackageInfo");
                f.setAccessible(true);
                return f.get(context);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return super.newActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            return mBaseInstrumentation.newActivity(cl, className, intent);
        }
    }
}
