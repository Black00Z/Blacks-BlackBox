package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.Process;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.view.accessibility.BRIAccessibilityManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.utils.Slog;


public class IAccessibilityManagerProxy extends BinderInvocationStub {
    public static final String TAG = "AccessibilityManagerStub";

    public IAccessibilityManagerProxy() {
        super(BRServiceManager.get().getService(Context.ACCESSIBILITY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAccessibilityManagerStub.get().asInterface(BRServiceManager.get().getService(Context.ACCESSIBILITY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethods({"interrupt", "sendAccessibilityEvent", "addClient",
            "getInstalledAccessibilityServiceList", "getEnabledAccessibilityServiceList",
            "addAccessibilityInteractionConnection", "getWindowToken"})
    public static class ReplaceUserId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                int index = args.length - 1;
                Object arg = args[index];
                if (arg instanceof Integer) {
                    int hostUserId = BlackBoxCore.getHostUserId();
                    if (hostUserId == 0) {
                        hostUserId = Process.myUid() / 100000;
                    }
                    if (hostUserId != 0) {
                        args[index] = hostUserId;
                    }
                }
            }

            try {
                return method.invoke(who, args);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                if (cause instanceof SecurityException || cause instanceof IllegalArgumentException) {
                    String msg = cause.getMessage();
                    if (msg != null && (msg.contains("INTERACT_ACROSS_USERS")
                            || msg.contains("access user 0")
                            || msg.contains("getPackagesForUid")
                            || msg.contains("run as user 0"))) {
                        Slog.w(TAG, "Bypassing accessibility cross-user enforcement in " + method.getName() + ": " + msg);
                        return safeReturnValue(method);
                    }
                }
                throw cause;
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
            return null;
        }
    }
}
