package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;
import android.os.LocaleList;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;

public class ILocaleManagerProxy extends BinderInvocationStub {
    private static final String SERVICE_NAME = "locale";

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        return asInterface(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getApplicationLocales")
    public static class GetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            // Privacy-first: do not delegate per-app locale state to the host.
            // Chromium only needs a non-crashing answer here.
            return LocaleList.getEmptyLocaleList();
        }
    }

    @ProxyMethod("setApplicationLocales")
    public static class SetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            return null;
        }
    }

    @ProxyMethod("getOverrideLocaleConfig")
    public static class GetOverrideLocaleConfig extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            return null;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return super.invoke(proxy, method, args);
        } catch (IllegalArgumentException | SecurityException e) {
            Slog.w(TAG, "LocaleManager invoke: returning sandbox-safe default for " + method.getName(), e);
            return getDefaultReturnValue(method);
        }
    }

    private static Object asInterface(IBinder binder) {
        try {
            Class<?> stubClass = Class.forName("android.app.ILocaleManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            throw new IllegalStateException("Unable to bind locale service stub", e);
        }
    }

    private Object getDefaultReturnValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE || returnType == Boolean.class) {
            return false;
        }
        if (returnType == Integer.TYPE || returnType == Integer.class) {
            return 0;
        }
        if (returnType == Long.TYPE || returnType == Long.class) {
            return 0L;
        }
        if (returnType == LocaleList.class) {
            return LocaleList.getEmptyLocaleList();
        }
        return null;
    }
}
