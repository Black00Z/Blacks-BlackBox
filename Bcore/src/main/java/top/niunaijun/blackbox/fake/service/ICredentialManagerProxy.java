package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;

public class ICredentialManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ICredentialManagerProxy";
    public static final String CREDENTIAL_SERVICE = "credential";

    public ICredentialManagerProxy() {
        super(BRServiceManager.get().getService(CREDENTIAL_SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(CREDENTIAL_SERVICE);
            if (binder == null) return null;
            Class<?> stubClass = Class.forName("android.credentials.ICredentialManager$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable e) {
            Slog.d(TAG, "getWho error: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (BRServiceManager.get().getService(CREDENTIAL_SERVICE) != null) {
            replaceSystemService(CREDENTIAL_SERVICE);
            Slog.d(TAG, "Hooked CredentialManagerService");
        } else {
            Slog.d(TAG, "Skipping CredentialManagerService hook (service not found)");
        }
    }

    @Override
    public boolean isBadEnv() {
        IBinder binder = BRServiceManager.get().getService(CREDENTIAL_SERVICE);
        return binder != null && binder != this;
    }

    @ProxyMethod("executeGetCredential")
    public static class ExecuteGetCredential extends MethodHook {
        private static final String PERM_MARKER = "CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS";

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (who == null) {
                return defaultReturnValue(method);
            }

            sanitizeArgs(args);

            try {
                return method.invoke(who, args);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getTargetException();
                if (isAllowedProvidersSecurityException(cause)) {
                    Slog.w(TAG, "Swallowing CredentialManager SecurityException (" + PERM_MARKER + ") to prevent app crash");

                    Object callback = findGetCredentialCallback(args);
                    if (callback != null) {
                        trySendCallbackError(callback, String.valueOf(cause.getMessage()));
                    }

                    return defaultReturnValue(method);
                }
                throw cause;
            }
        }

        private static boolean isAllowedProvidersSecurityException(Throwable t) {
            if (!(t instanceof SecurityException)) return false;
            String msg = t.getMessage();
            return msg != null && msg.contains(PERM_MARKER);
        }

        private static void sanitizeArgs(Object[] args) {
            if (args == null) return;

            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) continue;

                if (arg instanceof String) {
                    String pkg = (String) arg;
                    if (pkg != null && !pkg.equals(BlackBoxCore.getHostPkg()) && looksLikePackageName(pkg)) {
                        args[i] = BlackBoxCore.getHostPkg();
                    }
                    continue;
                }

                if (arg instanceof List) {
                    List<?> list = (List<?>) arg;
                    if (!list.isEmpty() && looksLikePackageNameList(list)) {
                        args[i] = Collections.emptyList();
                    }
                    continue;
                }

                String className = arg.getClass().getName();
                if (className.startsWith("android.credentials.") && className.contains("GetCredentialRequest")) {
                    clearAllowedProvidersFields(arg);
                }
            }
        }

        private static boolean looksLikePackageName(String s) {
            return s.indexOf('.') > 0 && s.indexOf(' ') < 0;
        }

        private static boolean looksLikePackageNameList(List<?> list) {
            for (Object o : list) {
                if (!(o instanceof String)) return false;
                if (!looksLikePackageName((String) o)) return false;
            }
            return true;
        }

        private static void clearAllowedProvidersFields(Object request) {
            Class<?> c = request.getClass();
            while (c != null && c != Object.class) {
                try {
                    for (Field f : c.getDeclaredFields()) {
                        String name = f.getName();
                        if (name == null) continue;
                        if (!name.toLowerCase().contains("allowed")) continue;
                        if (!List.class.isAssignableFrom(f.getType())) continue;

                        f.setAccessible(true);
                        f.set(request, Collections.emptyList());
                    }
                } catch (Throwable ignored) {
                }
                c = c.getSuperclass();
            }
        }

        private static Object findGetCredentialCallback(Object[] args) {
            if (args == null) return null;
            for (Object arg : args) {
                if (arg == null) continue;
                Class<?> c = arg.getClass();
                for (Class<?> iface : c.getInterfaces()) {
                    if (iface == null) continue;
                    String name = iface.getName();
                    if (name == null) continue;
                    if (name.equals("android.credentials.IGetCredentialCallback")) {
                        return arg;
                    }
                }
                if (c.getName().contains("IGetCredentialCallback")) {
                    return arg;
                }
            }
            return null;
        }

        private static void trySendCallbackError(Object callback, String message) {
            try {
                for (Method m : callback.getClass().getMethods()) {
                    if (!"onError".equals(m.getName())) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 2 && pt[0] == String.class && pt[1] == String.class) {
                        m.invoke(callback, "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL", message);
                        return;
                    }
                    if (pt.length == 2 && pt[0] == String.class && CharSequence.class.isAssignableFrom(pt[1])) {
                        m.invoke(callback, "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL", message);
                        return;
                    }
                }
            } catch (Throwable e) {
                Slog.d(TAG, "Failed to report CredentialManager error to callback: " + e.getMessage());
            }
        }

        private static Object defaultReturnValue(Method method) {
            Class<?> rt = method.getReturnType();
            if (rt == void.class) return null;
            if (!rt.isPrimitive()) return null;
            if (rt == boolean.class) return false;
            if (rt == byte.class) return (byte) 0;
            if (rt == short.class) return (short) 0;
            if (rt == int.class) return 0;
            if (rt == long.class) return 0L;
            if (rt == float.class) return 0f;
            if (rt == double.class) return 0d;
            if (rt == char.class) return (char) 0;
            return null;
        }
    }
}
