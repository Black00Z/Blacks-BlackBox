package top.niunaijun.blackbox.fake.service;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.Collections;

import black.android.app.role.BRIRoleManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

public class IRoleManagerProxy extends BinderInvocationStub {
    public IRoleManagerProxy() {
        super(BRServiceManager.get().getService(Context.ROLE_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIRoleManagerStub.get().asInterface(BRServiceManager.get().getService(Context.ROLE_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ROLE_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("isRoleHeld")
    public static class IsRoleHeld extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ProxyMethod("isRoleHeldAsUser")
    public static class IsRoleHeldAsUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ProxyMethod("getRoleHoldersAsUser")
    public static class GetRoleHoldersAsUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Collections.emptyList();
        }
    }

    @ProxyMethod("getRoleHolders")
    public static class GetRoleHolders extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Collections.emptyList();
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            MethodParameterUtils.replaceSequenceAppPkg(args, 1);
            MethodParameterUtils.replaceLastUid(args);
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            Slog.w(TAG, "RoleManager invoke: SecurityException in " + method.getName() + ", returning sandbox-safe default", e);
            return getDefaultReturnValue(method);
        }
    }

    private Object getDefaultReturnValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class || returnType == Boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == Integer.class) {
            return 0;
        }
        if (returnType == long.class || returnType == Long.class) {
            return 0L;
        }
        if (returnType.isAssignableFrom(Collections.emptyList().getClass())
                || java.util.List.class.isAssignableFrom(returnType)) {
            return Collections.emptyList();
        }
        return null;
    }
}
