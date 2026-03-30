package top.niunaijun.blackbox.fake.service.context.providers;

import android.net.Uri;
import android.os.IInterface;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.media.BlackBoxMediaContract;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;

public class VirtualMediaProviderStub extends ClassInvocationStub implements BContentProvider {
    private IInterface mBase;

    @Override
    public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
        mBase = contentProviderProxy;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    protected void onBindMethod() {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }

        AttributionSourceUtils.fixAttributionSourceInArgs(args);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Uri) {
                    args[i] = BlackBoxMediaContract.toPrivateUri((Uri) arg, BActivityThread.getUserId());
                }
            }
        }

        Object result = method.invoke(mBase, args);
        if (result instanceof Uri) {
            return BlackBoxMediaContract.toPublicUri((Uri) result);
        }
        return result;
    }
}
