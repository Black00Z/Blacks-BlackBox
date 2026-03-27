package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.app.BRIUiModeManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

public class IUiModeManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IUiModeMgrProxy";
    private static final String SERVICE_NAME = "uimode";

    public IUiModeManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        return BRIUiModeManagerStub.get().asInterface(BRServiceManager.get().getService(SERVICE_NAME));
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
        rewriteArgs(method, args);
        try {
            return super.invoke(proxy, method, args);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (cause instanceof SecurityException || cause instanceof IllegalArgumentException) {
                String msg = cause.getMessage();
                if (msg != null && (msg.contains("run as user 0")
                        || msg.contains("INTERACT_ACROSS_USERS")
                        || msg.contains("getForceInvertState")
                        || msg.contains("cannot query")
                        || msg.contains("callingPackage")
                        || msg.contains("does not belong to"))) {
                    Slog.w(TAG, "Bypassing UiMode enforcement for " + method.getName() + ": " + msg);
                    return safeReturnValue(method);
                }
            }
            throw cause;
        }
    }

    private static void rewriteArgs(Method method, Object[] args) {
        MethodParameterUtils.replaceAllAppPkg(args);
        if (args == null || args.length == 0) {
            return;
        }

        String name = method != null ? method.getName() : "";
        if ("getForceInvertState".equals(name)
                || name.contains("ForUser")
                || name.contains("AsUser")
                || name.contains("NightMode")) {
            for (int i = 0; i < args.length; i++) {
                MethodParameterUtils.replaceUserIdIfNeeded(args, i);
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
        return null;
    }

    @ProxyMethod("getForceInvertState")
    public static class getForceInvertState extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            rewriteArgs(method, args);
            // Returning disabled avoids cross-user enforcement crash on Android 16 work profile.
            return 0;
        }
    }
}
