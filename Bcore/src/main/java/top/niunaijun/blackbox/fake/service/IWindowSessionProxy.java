package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
import android.content.Context;
import android.os.Process;
import android.view.WindowManager;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;



public class IWindowSessionProxy extends BinderInvocationStub {
    public static final String TAG = "WindowSessionStub";

    private IInterface mSession;

    public IWindowSessionProxy(IInterface session) {
        super(session.asBinder());
        mSession = session;
    }

    @Override
    protected Object getWho() {
        return mSession;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method != null && "addToDisplayAsUser".equals(method.getName())) {
            rewriteRequestedUserId(args);
        }
        return super.invoke(proxy, method, args);
    }

    private static void rewriteRequestedUserId(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }

        int hostUserId = resolveHostUserId();
        // hostUserId may legitimately be 0 on the primary/owner profile.
        if (hostUserId < 0) {
            return;
        }

        // Heuristic: on modern Android, addToDisplayAsUser(...) places userId right after
        // the initial (IWindow, LayoutParams, viewVisibility, displayId) arguments.
        // That is typically index 4. Keep the older "last int" fallback for older signatures.
        if (args.length > 4 && args[4] instanceof Integer) {
            int requestedUserId = (Integer) args[4];
            if (requestedUserId != hostUserId) {
                args[4] = hostUserId;
            }
            return;
        }

        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof Integer) {
                int requestedUserId = (Integer) args[i];
                if (requestedUserId != hostUserId) {
                    args[i] = hostUserId;
                }
                break;
            }
        }
    }

    private static int resolveHostUserId() {
        // Prefer deriving from a real ApplicationInfo UID (not virtualized Process.myUid()).
        try {
            Context ctx = BlackBoxCore.getContext();
            if (ctx != null && ctx.getApplicationInfo() != null && ctx.getApplicationInfo().uid > 0) {
                return ctx.getApplicationInfo().uid / 100000;
            }
        } catch (Throwable ignored) {
        }

        int hostUserId = BlackBoxCore.getHostUserId();
        if (hostUserId > 0) {
            return hostUserId;
        }

        int hostUid = BlackBoxCore.getHostUid();
        if (hostUid > 0) {
            return hostUid / 100000;
        }

        // Last resort: may be virtualized in guest processes, but better than 0.
        return Process.myUid() / 100000;
    }

    @ProxyMethod("addToDisplay")
    public static class AddToDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof WindowManager.LayoutParams) {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                    lp.packageName = BlackBoxCore.getHostPkg();
                    if (BlackBoxCore.get().isDisableFlagSecure()) {
                        lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                    }
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addToDisplayAsUser")
    public static class AddToDisplayAsUser extends AddToDisplay {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            rewriteRequestedUserId(args);
            return super.hook(who, method, args);
        }
    }

    @ProxyMethod("relayout")
    public static class Relayout extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof WindowManager.LayoutParams) {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                    if (BlackBoxCore.get().isDisableFlagSecure()) {
                        lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
}
