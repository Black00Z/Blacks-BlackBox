package top.niunaijun.blackbox.fake.service.context.providers;

import android.os.IInterface;

import java.lang.reflect.Method;
import java.util.Set;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.compat.ContextCompat;
import top.niunaijun.blackbox.utils.Slog;
import android.os.Bundle;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;


public class ContentProviderStub extends ClassInvocationStub implements BContentProvider {
    public static final String TAG = "ContentProviderStub";
    private IInterface mBase;
    private String mAppPkg;

    public IInterface wrapper(final IInterface contentProviderProxy, final String appPkg) {
        mBase = contentProviderProxy;
        mAppPkg = appPkg;
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
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }
        
        
        String methodName = method.getName();
        
        
        
        if ("call".equals(methodName)) {
            if (args != null) {
                Class<?> attributionSourceClass = BRAttributionSource.getRealClass();
                for (Object arg : args) {
                    if (arg != null
                            && attributionSourceClass != null
                            && arg.getClass().getName().equals(attributionSourceClass.getName())) {
                        ContextCompat.fixAttributionSourceState(
                                arg,
                                mAppPkg != null ? mAppPkg : BlackBoxCore.getHostPkg(),
                                BlackBoxCore.getHostUid());
                    }
                }
            }
            AttributionSourceUtils.fixAttributionSourceInArgs(args);
            replaceCallingPackageStringsForCall(args);
            maybeLogSamsungAccountCall("before", args, null);
        } else {
            
            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof String) {
                        String strArg = (String) arg;
                        
                        if (!isSystemProviderAuthority(strArg)) {
                            
                            args[i] = mAppPkg;
                        }
                    }
                }
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
            }
        }
        
        
        methodName = method.getName();
        if (methodName.equals("query") || methodName.equals("insert") || 
            methodName.equals("update") || methodName.equals("delete") || 
            methodName.equals("bulkInsert") || methodName.equals("call")) {
            boolean samsungIsChildCall = isSamsungAccountIsChildCall(args);
            
            try {
                Object result = method.invoke(mBase, args);
                if (samsungIsChildCall) {
                    Bundle synthetic = maybeBypassSamsungChildAccountResult(result);
                    if (synthetic != null) {
                        maybeLogSamsungAccountCall("synthetic", args, synthetic);
                        return synthetic;
                    }
                }
                if ("call".equals(methodName)) {
                    maybeLogSamsungAccountCall("after", args, result);
                }
                return result;
            } catch (Throwable e) {
                
                Throwable cause = e.getCause();
                if (isUidMismatchError(cause)) {
                    Slog.w(TAG, "UID mismatch in ContentProvider call, returning safe default: " + cause.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                } else if (cause instanceof RuntimeException) {
                    String message = cause.getMessage();
                    if (message != null && (message.contains("uid") || message.contains("permission"))) {
                        Slog.w(TAG, "Permission/UID error in ContentProvider call, returning safe default: " + message);
                        return getSafeDefaultValue(methodName, method.getReturnType());
                    }
                }
                
                
                if (methodName.equals("call")) {
                    if (samsungIsChildCall) {
                        Bundle synthetic = createSamsungAdultResultBundle();
                        maybeLogSamsungAccountCall("synthetic", args, synthetic);
                        Slog.w(TAG, "Bypassing Samsung Account child-account provider failure");
                        return synthetic;
                    }
                    Slog.w(TAG, "Error in call method, returning safe default: " + e.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                }
                
                throw e.getCause();
            }
        }
        
        
        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            
            Throwable cause = e.getCause();
            if (isUidMismatchError(cause)) {
                Slog.w(TAG, "UID mismatch in " + methodName + ", returning safe default: " + cause.getMessage());
                return getSafeDefaultValue(methodName, method.getReturnType());
            }
            throw e.getCause();
        }
    }
    
    private Object getSafeDefaultValue(String methodName) {
        switch (methodName) {
            case "query":
                return null; 
            case "insert":
                return null; 
            case "update":
            case "delete":
                return 0; 
            case "bulkInsert":
                return 0; 
            case "call":
                return new Bundle(); 
            case "getType":
                return null; 
            case "openFile":
                return null; 
            case "openAssetFile":
                return null; 
            default:
                return null; 
        }
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        
        
        return authority.equals("settings") || 
               authority.equals("settings_global") || 
               authority.equals("settings_system") || 
               authority.equals("settings_secure") ||
               authority.equals("media") ||
               authority.equals("telephony") ||
               authority.startsWith("android.provider.Settings");
    }
    
    
    private boolean isUidMismatchError(Throwable error) {
        if (error == null) return false;
        
        String message = error.getMessage();
        if (message == null) return false;
        
        
        return message.contains("Calling uid") && 
               message.contains("doesn't match source uid") ||
               message.contains("uid") && 
               message.contains("permission") ||
               message.contains("SecurityException") ||
               message.contains("UID mismatch");
    }
    
    
    private Object getSafeDefaultValue(String methodName, Class<?> returnType) {
        if (returnType == null) {
            return getSafeDefaultValue(methodName);
        }
        
        
        if (returnType == String.class) {
            return "true"; 
        } else if (returnType == int.class || returnType == Integer.class) {
            return 1; 
        } else if (returnType == long.class || returnType == Long.class) {
            return 1L; 
        } else if (returnType == float.class || returnType == Float.class) {
            return 1.0f; 
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return true; 
        } else if (returnType == Bundle.class) {
            return new Bundle(); 
        }
        
        
        return getSafeDefaultValue(methodName);
    }

    private boolean isSamsungAccountIsChildCall(Object[] args) {
        if (!"com.sec.android.app.shealth".equals(mAppPkg) || args == null) {
            return false;
        }
        boolean providerMatched = false;
        boolean methodMatched = false;
        for (Object arg : args) {
            if (!(arg instanceof String)) {
                continue;
            }
            String value = (String) arg;
            if ("com.samsung.android.samsungaccount.accountmanagerprovider".equals(value)) {
                providerMatched = true;
            } else if ("isChildAccount".equals(value)) {
                methodMatched = true;
            }
        }
        return providerMatched && methodMatched;
    }

    private Bundle maybeBypassSamsungChildAccountResult(Object result) {
        if (!(result instanceof Bundle)) {
            return createSamsungAdultResultBundle();
        }
        Bundle bundle = (Bundle) result;
        if (!bundle.containsKey("result_code")) {
            return createSamsungAdultResultBundle();
        }
        int resultCode = bundle.getInt("result_code", -1);
        if (resultCode != 0) {
            return createSamsungAdultResultBundle();
        }
        return null;
    }

    private Bundle createSamsungAdultResultBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("result_code", 0);
        bundle.putString("result_message", "");
        bundle.putBoolean("is_child_account", false);
        bundle.putBoolean("isChildAccount", false);
        bundle.putBoolean("childAccountAccessError", false);
        return bundle;
    }

    private void replaceCallingPackageStringsForCall(Object[] args) {
        if (args == null || mAppPkg == null || mAppPkg.isEmpty()) {
            return;
        }
        String hostPkg = BlackBoxCore.getHostPkg();
        if (hostPkg == null || hostPkg.isEmpty() || hostPkg.equals(mAppPkg)) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (!(arg instanceof String)) {
                continue;
            }
            String value = (String) arg;
            if (hostPkg.equals(value)) {
                args[i] = mAppPkg;
            } else if ((hostPkg + ".").equals(value)) {
                args[i] = mAppPkg + ".";
            }
        }
    }

    private void maybeLogSamsungAccountCall(String stage, Object[] args, Object result) {
        if (!"com.sec.android.app.shealth".equals(mAppPkg) || args == null) {
            return;
        }
        boolean relevant = false;
        StringBuilder builder = new StringBuilder();
        builder.append("SamsungAccount call ").append(stage).append(" args=[");
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(arg);
            if (arg instanceof String) {
                String value = (String) arg;
                if (value.contains("samsungaccount") || value.contains("isChildAccount") || value.contains("sign")) {
                    relevant = true;
                }
            }
        }
        builder.append("]");
        if (result instanceof Bundle) {
            Bundle bundle = (Bundle) result;
            Set<String> keys = bundle.keySet();
            builder.append(" resultKeys=").append(keys);
            for (String key : keys) {
                builder.append(" ").append(key).append("=").append(bundle.get(key));
            }
            relevant = true;
        }
        if (relevant) {
            Slog.d(TAG, builder.toString());
        }
    }

    
    private void fixAttributionSourceUid(Object attributionSource) {
        try {
            if (attributionSource == null) return;
            
            Class<?> attributionSourceClass = attributionSource.getClass();
            
            
            try {
                java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("mUid");
                uidField.setAccessible(true);
                uidField.set(attributionSource, BlackBoxCore.getHostUid());
                Slog.d(TAG, "Fixed AttributionSource UID via field access");
            } catch (NoSuchFieldException e) {
                
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("uid");
                    uidField.setAccessible(true);
                    uidField.set(attributionSource, BlackBoxCore.getHostUid());
                    Slog.d(TAG, "Fixed AttributionSource UID via alternative field");
                } catch (NoSuchFieldException e2) {
                    
                    try {
                        java.lang.reflect.Method setUidMethod = attributionSourceClass.getDeclaredMethod("setUid", int.class);
                        setUidMethod.setAccessible(true);
                        setUidMethod.invoke(attributionSource, BlackBoxCore.getHostUid());
                        Slog.d(TAG, "Fixed AttributionSource UID via setter method");
                    } catch (Exception e3) {
                        Slog.w(TAG, "Could not fix AttributionSource UID: " + e3.getMessage());
                    }
                }
            }
            
            
            try {
                java.lang.reflect.Field packageField = attributionSourceClass.getDeclaredField("mPackageName");
                packageField.setAccessible(true);
                packageField.set(attributionSource, mAppPkg);
                Slog.d(TAG, "Fixed AttributionSource package name");
            } catch (Exception e) {
                
            }
            
        } catch (Exception e) {
            Slog.w(TAG, "Error fixing AttributionSource UID: " + e.getMessage());
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
