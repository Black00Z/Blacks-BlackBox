package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;
import android.os.Bundle;
import android.text.TextUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.os.BRServiceManager;
import black.com.android.internal.telephony.BRISubStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.Slog;

/** Binder-level hook for the "isub" service (com.android.internal.telephony.ISub). */
public class ISubServiceProxy extends BinderInvocationStub {
    public static final String TAG = "ISubSvcProxy";
    private static final String SERVICE_NAME = "isub";

    public ISubServiceProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(SERVICE_NAME);
        if (binder == null) {
            return null;
        }
        return BRISubStub.get().asInterface(binder);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            rewriteCallingPackage(args);
            return super.invoke(proxy, method, args);
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof SecurityException || cause instanceof IllegalArgumentException) {
                String msg = cause.getMessage();
                if (msg != null && (msg.contains("does not belong to")
                        || msg.contains("callingPackage")
                        || msg.contains("Calling uid")
                        || msg.contains("Uid "))) {
                    Slog.w(TAG, "UID/package enforcement in isub call " + method.getName() + ", returning safe default: " + msg);
                    return safeReturnValue(method);
                }
            }
            throw e;
        }
    }

    private static void rewriteCallingPackage(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }

        String vPkg;
        try {
            vPkg = BActivityThread.getAppPackageName();
        } catch (Throwable ignored) {
            return;
        }

        String hostPkg = BlackBoxCore.getHostPkg();
        if (TextUtils.isEmpty(vPkg) || TextUtils.isEmpty(hostPkg) || TextUtils.equals(vPkg, hostPkg)) {
            return;
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String && TextUtils.equals((String) args[i], vPkg)) {
                args[i] = hostPkg;
            }
        }
    }

    private static Object safeReturnValue(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == null || rt == Void.TYPE) {
            return null;
        }
        if (rt == Boolean.TYPE) {
            return false;
        }
        if (rt == Integer.TYPE) {
            return 0;
        }
        if (rt == Long.TYPE) {
            return 0L;
        }
        if (rt == Float.TYPE) {
            return 0f;
        }
        if (rt == Double.TYPE) {
            return 0d;
        }
        if (rt == String.class) {
            return "";
        }
        if (List.class.isAssignableFrom(rt)) {
            return Collections.emptyList();
        }
        if (rt == Bundle.class) {
            return new Bundle();
        }
        if (rt.isArray()) {
            return Array.newInstance(rt.getComponentType(), 0);
        }
        return null;
    }
}
