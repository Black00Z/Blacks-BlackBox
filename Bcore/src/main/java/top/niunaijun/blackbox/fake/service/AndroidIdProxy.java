package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.device.DeviceSpoofManager;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;


public class AndroidIdProxy extends ClassInvocationStub {
    public static final String TAG = "AndroidIdProxy";

    public AndroidIdProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getAndroidId")
    public static class GetAndroidId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return DeviceSpoofManager.getAndroidIdForCurrentUser();
            } catch (Exception e) {
                Slog.w(TAG, "AndroidId: GetAndroidId error, returning spoofed ID", e);
                return DeviceSpoofManager.getAndroidIdForCurrentUser();
            }
        }
    }

    @ProxyMethod("getString")
    public static class GetString extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "AndroidId: Handling getString call");
                Object result = method.invoke(who, args);
                
                
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    String key = (String) args[0];
                    if (key.contains("android_id") || key.contains("ANDROID_ID") || 
                        key.contains("secure_id") || key.contains("device_id")) {
                        return DeviceSpoofManager.getAndroidIdForCurrentUser();
                    }
                }
                return result;
            } catch (Exception e) {
                Slog.w(TAG, "AndroidId: GetString error, returning spoofed ID", e);
                return DeviceSpoofManager.getAndroidIdForCurrentUser();
            }
        }
    }

    @ProxyMethod("getLong")
    public static class GetLong extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "AndroidId: Handling getLong call");
                Object result = method.invoke(who, args);
                
                
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    String key = (String) args[0];
                    if (key.contains("android_id") || key.contains("ANDROID_ID") || 
                        key.contains("secure_id") || key.contains("device_id")) {
                        return generateMockAndroidIdLong();
                    }
                }
                return result;
            } catch (Exception e) {
                Slog.w(TAG, "AndroidId: GetLong error, returning spoofed long ID", e);
                return generateMockAndroidIdLong();
            }
        }
    }

    @ProxyMethod("get")
    public static class Get extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "AndroidId: Handling get call");
                Object result = method.invoke(who, args);
                
                
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    String key = (String) args[0];
                    if (key.contains("android_id") || key.contains("ANDROID_ID") || 
                        key.contains("secure_id") || key.contains("device_id")) {
                        return DeviceSpoofManager.getAndroidIdForCurrentUser();
                    }
                }
                return result;
            } catch (Exception e) {
                Slog.w(TAG, "AndroidId: Get error, returning spoofed ID", e);
                return DeviceSpoofManager.getAndroidIdForCurrentUser();
            }
        }
    }

    @ProxyMethod("read")
    public static class Read extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "AndroidId: Handling read call");
                Object result = method.invoke(who, args);
                
                
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    String key = (String) args[0];
                    if (key.contains("android_id") || key.contains("ANDROID_ID") || 
                        key.contains("secure_id") || key.contains("device_id")) {
                        return DeviceSpoofManager.getAndroidIdForCurrentUser();
                    }
                }
                return result;
            } catch (Exception e) {
                Slog.w(TAG, "AndroidId: Read error, returning spoofed ID", e);
                return DeviceSpoofManager.getAndroidIdForCurrentUser();
            }
        }
    }

    
    private static String generateMockAndroidId() {
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(Integer.toHexString((int) (Math.random() * 16)));
        }
        String mockId = sb.toString().toUpperCase();
        Slog.d(TAG, "AndroidId: Generated mock Android ID: " + mockId);
        return mockId;
    }

    private static Long generateMockAndroidIdLong() {
        long mockId = Long.parseUnsignedLong(DeviceSpoofManager.getAndroidIdForCurrentUser(), 16);
        Slog.d(TAG, "AndroidId: Generated mock Android ID long: " + mockId);
        return mockId;
    }
}
