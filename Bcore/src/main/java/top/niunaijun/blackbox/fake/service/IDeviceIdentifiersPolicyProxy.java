package top.niunaijun.blackbox.fake.service;


import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;

import android.os.Bundle;
import android.text.TextUtils;

import black.android.os.BRIDeviceIdentifiersPolicyServiceStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Md5Utils;
import top.niunaijun.blackbox.utils.Slog;


public class IDeviceIdentifiersPolicyProxy extends BinderInvocationStub {
    public static final String TAG = "IDeviceIdPolicyProxy";

    public IDeviceIdentifiersPolicyProxy() {
        super(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected Object getWho() {
        return BRIDeviceIdentifiersPolicyServiceStub.get().asInterface(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("device_identifiers");
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
                    Slog.w(TAG, "UID/package enforcement in device_identifiers call " + method.getName() + ", returning safe default: " + msg);
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
            return Md5Utils.md5(BlackBoxCore.getHostPkg());
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

    @ProxyMethod("getSerialForPackage")
    public static class x extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {


            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    @ProxyMethod("getImeiForSlot")
    public static class getImeiForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    @ProxyMethod("getMeidForSlot")
    public static class getMeidForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }
}
