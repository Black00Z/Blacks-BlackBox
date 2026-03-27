package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.text.TextUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.Slog;


@ScanClass(IInputMethodManagerProxy.class)
public class IInputMethodManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IInputMethodManagerProxy";

    public IInputMethodManagerProxy() {
        super(BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE);
        return asInputMethodManagerInterface(binder);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method != null ? method.getName() : null;
        Object[] originalArgs = cloneArgs(args);
        rewriteCallingPackage(args);
        rewriteEditorInfoPackage(args);
        rewriteCallingUid(args);
        if (isImeStartMethod(methodName)) {
            rewriteTailUserIdCandidates(args);
        }
        try {
            Object result = super.invoke(proxy, method, args);
            if (isShowSoftInputMethod(methodName)) {
                Class<?> rt = method != null ? method.getReturnType() : null;
                if (rt == Boolean.TYPE || rt == Boolean.class) {
                    if (Boolean.FALSE.equals(result)) {
                        Object retry = retryWithOriginalArgs(proxy, method, originalArgs, methodName);
                        if (retry instanceof Boolean && Boolean.TRUE.equals(retry)) {
                            return true;
                        }
                        Slog.w(TAG, "Forcing true return for " + methodName + " to avoid IME client cancellation");
                        return true;
                    }
                }
            }
            return result;
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (isCrossUserImeSecurity(cause)) {
                String safeMethodName = methodName != null ? methodName : "unknown";
                if (isImeStartMethod(methodName)) {
                    Slog.w(TAG, "IME cross-user enforcement in " + safeMethodName + ", retrying with host userId");
                    rewriteLastUserIdForceHost(args);
                    try {
                        return super.invoke(proxy, method, args);
                    } catch (Throwable retryError) {
                        Throwable retryCause = retryError.getCause() != null ? retryError.getCause() : retryError;
                        if (isCrossUserImeSecurity(retryCause)) {
                            Slog.w(TAG, "IME cross-user enforcement persists in " + safeMethodName + ", returning safe default");
                            return safeReturnValue(method);
                        }
                        throw retryCause;
                    }
                }
                if (isShowSoftInputMethod(methodName)) {
                    Object retry = retryWithOriginalArgs(proxy, method, originalArgs, safeMethodName);
                    if (retry != null) {
                        return retry;
                    }
                    Slog.w(TAG, "IME cross-user enforcement in " + safeMethodName + ", forcing showSoftInput success");
                    return true;
                }
                Slog.w(TAG, "IME cross-user enforcement in " + safeMethodName + ", returning safe default");
                return safeReturnValue(method);
            }
            throw e;
        }
    }

    private static void rewriteCallingPackage(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        String vPkg = null;
        try {
            vPkg = BActivityThread.getAppPackageName();
        } catch (Throwable ignored) {
        }
        String hostPkg = BlackBoxCore.getHostPkg();
        String currentProcessPkg = getCurrentProcessPackageName();
        if (TextUtils.isEmpty(hostPkg)) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof String)) {
                continue;
            }
            String value = (String) args[i];
            if (TextUtils.isEmpty(value) || TextUtils.equals(value, hostPkg)) {
                continue;
            }
            if (TextUtils.equals(value, vPkg)
                    || TextUtils.equals(value, currentProcessPkg)
                    || isVirtualPackageName(value)) {
                args[i] = hostPkg;
            }
        }
    }

    private static String getCurrentProcessPackageName() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentPackageName = activityThread.getDeclaredMethod("currentPackageName");
            currentPackageName.setAccessible(true);
            Object value = currentPackageName.invoke(null);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isVirtualPackageName(String value) {
        if (TextUtils.isEmpty(value) || value.indexOf('.') <= 0) {
            return false;
        }
        try {
            return BlackBoxCore.get().isInstalled(value, BlackBoxCore.getUserId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void rewriteEditorInfoPackage(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        String hostPkg = BlackBoxCore.getHostPkg();
        if (TextUtils.isEmpty(hostPkg)) {
            return;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            Class<?> c = arg.getClass();
            if (!"android.view.inputmethod.EditorInfo".equals(c.getName())) {
                continue;
            }
            try {
                Field packageNameField = c.getField("packageName");
                Object current = packageNameField.get(arg);
                if (current instanceof String && !TextUtils.equals((String) current, hostPkg)) {
                    packageNameField.set(arg, hostPkg);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object asInputMethodManagerInterface(IBinder binder) {
        if (binder == null) {
            return null;
        }
        try {
            Class<?> stubClass = Class.forName("com.android.internal.view.IInputMethodManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.e(TAG, "Unable to resolve IInputMethodManager interface", e);
            return null;
        }
    }

    private static Object[] cloneArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        return args.clone();
    }

    private Object retryWithOriginalArgs(Object proxy, Method method, Object[] originalArgs, String methodName) {
        if (originalArgs == null || method == null || proxy == null) {
            return null;
        }
        try {
            Slog.w(TAG, "Retrying " + methodName + " with original IME args");
            return super.invoke(proxy, method, originalArgs.clone());
        } catch (Throwable retryError) {
            Throwable retryCause = retryError.getCause() != null ? retryError.getCause() : retryError;
            Slog.w(TAG, "Retry with original IME args failed in " + methodName + ": " + retryCause);
            return null;
        }
    }

    private static void rewriteLastUserIdForceHost(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        int hostUserId = BlackBoxCore.getHostUserId();
        if (hostUserId == 0) {
            return;
        }
        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof Integer) {
                args[i] = hostUserId;
                return;
            }
        }
    }

    private static void rewriteTailUserIdCandidates(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        int hostUserId = BlackBoxCore.getHostUserId();
        if (hostUserId == 0) {
            return;
        }
        int patched = 0;
        for (int i = args.length - 1; i >= 0 && patched < 2; i--) {
            Object arg = args[i];
            if (arg instanceof Integer) {
                int value = (Integer) arg;
                if (value == 0 || value == -1) {
                    args[i] = hostUserId;
                    patched++;
                }
            }
        }
    }

    private static boolean isImeStartMethod(String methodName) {
        if (methodName == null) {
            return false;
        }
        return methodName.contains("startInput")
                || methodName.contains("windowGainedFocus")
                || methodName.contains("showSoftInput");
    }

    private static boolean isShowSoftInputMethod(String methodName) {
        return methodName != null && methodName.contains("showSoftInput");
    }

    private static void rewriteCallingUid(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        int bUid = BlackBoxCore.getBUid();
        int hostUid = BlackBoxCore.getHostUid();
        if (bUid <= 0 || hostUid <= 0 || bUid == hostUid) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Integer) {
                int value = (Integer) arg;
                if (value == bUid) {
                    args[i] = hostUid;
                }
            }
        }
    }

    private static boolean isCrossUserImeSecurity(Throwable t) {
        if (!(t instanceof SecurityException || t instanceof IllegalArgumentException)) {
            return false;
        }
        String msg = t.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("INTERACT_ACROSS_USERS")
                || msg.contains("access user 0")
                || msg.contains("getPackagesForUid")
                || msg.contains("Neither user")
                || msg.contains("InputMethodManagerService");
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
        if (List.class.isAssignableFrom(rt)) {
            return Collections.emptyList();
        }
        if (rt.isArray()) {
            return Array.newInstance(rt.getComponentType(), 0);
        }
        return null;
    }

    @ProxyMethod("startInputOrWindowGainedFocus")
    public static class StartInputOrWindowGainedFocus extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }
}
