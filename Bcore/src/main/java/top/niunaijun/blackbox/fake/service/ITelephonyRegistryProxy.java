package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.com.android.internal.telephony.BRITelephonyRegistryStub;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class ITelephonyRegistryProxy extends BinderInvocationStub {
    public ITelephonyRegistryProxy() {
        super(BRServiceManager.get().getService("telephony.registry"));
    }

    @Override
    protected Object getWho() {
        return BRITelephonyRegistryStub.get().asInterface(BRServiceManager.get().getService("telephony.registry"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("telephony.registry");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("listenForSubscriber")
    public static class ListenForSubscriber extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("listen")
    public static class Listen extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("listenWithEventList")
    public static class ListenWithEventList extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            try {
                return method.invoke(who, args);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                if (cause instanceof SecurityException || cause instanceof IllegalArgumentException) {
                    String msg = cause.getMessage();
                    if (msg == null
                            || msg.contains("listen")
                            || msg.contains("does not belong to")
                            || msg.contains("callingPackage")
                            || msg.contains("Calling uid")
                            || msg.contains("Uid ")) {
                        return null;
                    }
                }
                throw cause;
            }
        }
    }

    @ProxyMethod("listenWithEventListForSubscriber")
    public static class ListenWithEventListForSubscriber extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            try {
                return method.invoke(who, args);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                if (cause instanceof SecurityException || cause instanceof IllegalArgumentException) {
                    String msg = cause.getMessage();
                    if (msg == null
                            || msg.contains("listen")
                            || msg.contains("does not belong to")
                            || msg.contains("callingPackage")
                            || msg.contains("Calling uid")
                            || msg.contains("Uid ")) {
                        return null;
                    }
                }
                throw cause;
            }
        }
    }
}
