package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
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

        int hostUserId = BlackBoxCore.getHostUserId();
        if (hostUserId == 0) {
            hostUserId = Process.myUid() / 100000;
        }
        if (hostUserId == 0) {
            return;
        }

        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof Integer) {
                int requestedUserId = (Integer) args[i];
                if (requestedUserId == 0) {
                    args[i] = hostUserId;
                }
                break;
            }
        }
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
