package top.niunaijun.blackbox.fake.service;

import android.app.PendingIntent;
import android.content.Context;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import black.android.location.BRILocationManagerStub;
import black.android.location.provider.BRProviderProperties;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.location.FakeLocationPendingIntentDispatcher;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;


public class ILocationManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ILocationManagerProxy";

    public ILocationManagerProxy() {
        super(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRILocationManagerStub.get().asInterface(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        MethodParameterUtils.replaceFirstAppPkg(args);
        
        
        String packageName = BActivityThread.getAppPackageName();
        if (packageName != null && packageName.equals("com.google.android.gms")) {
            
            if (method.getName().equals("getLastLocation") || 
                method.getName().equals("getLastKnownLocation") ||
                method.getName().equals("requestLocationUpdates")) {
                Log.w(TAG, "Blocking location request from Google Play Services to prevent crash");
                return null;
            }
        }

        // Privacy-first: if fake-location disabled, avoid accidentally falling through to the host
        // for location getters / update registration that may not have explicit @ProxyMethod hooks
        // on some OEM/framework builds.
        try {
            if (!BLocationManager.isFakeLocationEnable() && method != null) {
                String n = method.getName();
                if ("getLastLocation".equals(n) || "getLastKnownLocation".equals(n)) {
                    return null;
                }
                if ("requestLocationUpdates".equals(n) || "registerLocationListener".equals(n) || "getCurrentLocation".equals(n)) {
                    return defaultReturn(method);
                }
            }
        } catch (Throwable ignored) {
        }
        
        return super.invoke(proxy, method, args);
    }

    private static Object defaultReturn(Method method) {
        if (method == null) {
            return null;
        }
        Class<?> rt = method.getReturnType();
        if (rt == null || rt == Void.TYPE) {
            return null;
        }
        if (rt == Boolean.TYPE) {
            return true;
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
        if (rt == Short.TYPE) {
            return (short) 0;
        }
        if (rt == Byte.TYPE) {
            return (byte) 0;
        }
        if (rt == Character.TYPE) {
            return (char) 0;
        }
        return null;
    }

    private static String safeGetInterfaceDescriptor(IBinder binder) {
        if (binder == null) {
            return null;
        }
        try {
            return binder.getInterfaceDescriptor();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean looksLikeLocationListener(IBinder binder) {
        String desc = safeGetInterfaceDescriptor(binder);
        return desc != null && (desc.contains("ILocationListener") || desc.contains("location.ILocationListener"));
    }

    private static IBinder findBestBinderCandidate(Object[] args) {
        if (args == null) {
            return null;
        }
        IBinder firstBinder = null;
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            IBinder b = null;
            if (arg instanceof IInterface) {
                try {
                    b = ((IInterface) arg).asBinder();
                } catch (Throwable ignored) {
                }
            } else if (arg instanceof IBinder) {
                b = (IBinder) arg;
            }
            if (b == null) {
                continue;
            }
            if (firstBinder == null) {
                firstBinder = b;
            }
            if (looksLikeLocationListener(b)) {
                return b;
            }
        }
        return firstBinder;
    }

    private static PendingIntent findPendingIntent(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof PendingIntent) {
                return (PendingIntent) arg;
            }
        }
        return null;
    }

    @ProxyMethod("registerGnssStatusCallback")
    public static class RegisterGnssStatusCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            
            return true;
        }
    }

    @ProxyMethod("getLastLocation")
    public static class GetLastLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation loc = BLocationManager.get().getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                return loc != null ? loc.convert2SystemLocation() : null;
            }
            // Privacy-first default: do not forward host location when fake-location disabled.
            return null;
        }
    }

    @ProxyMethod("getLastKnownLocation")
    public static class GetLastKnownLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation loc = BLocationManager.get().getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                return loc != null ? loc.convert2SystemLocation() : null;
            }
            return null;
        }
    }

    @ProxyMethod("requestLocationUpdates")
    public static class RequestLocationUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                IBinder binder = findBestBinderCandidate(args);
                if (binder != null) {
                    BLocationManager.get().requestLocationUpdates(binder);
                    return defaultReturn(method);
                }

                PendingIntent pendingIntent = findPendingIntent(args);
                if (pendingIntent != null) {
                    FakeLocationPendingIntentDispatcher.register(pendingIntent);
                    return defaultReturn(method);
                }

                // Fake location enabled, but no supported callback type found.
                return defaultReturn(method);
            }
            // Privacy-first: do not register for host location updates when fake disabled.
            return defaultReturn(method);
        }
    }

    @ProxyMethod("registerLocationListener")
    public static class RegisterLocationListener extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                IBinder binder = findBestBinderCandidate(args);
                if (binder != null) {
                    BLocationManager.get().requestLocationUpdates(binder);
                    return defaultReturn(method);
                }

                PendingIntent pendingIntent = findPendingIntent(args);
                if (pendingIntent != null) {
                    FakeLocationPendingIntentDispatcher.register(pendingIntent);
                    return defaultReturn(method);
                }

                return defaultReturn(method);
            }
            return defaultReturn(method);
        }
    }

    @ProxyMethod("getCurrentLocation")
    public static class GetCurrentLocation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (!BLocationManager.isFakeLocationEnable()) {
                // Privacy-first: avoid forwarding to host.
                return defaultReturn(method);
            }

            BLocation loc = BLocationManager.get().getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
            if (loc == null) {
                return defaultReturn(method);
            }
            android.location.Location sys = loc.convert2SystemLocation();

            // Best-effort: deliver to any callback-ish binder in args (modern API).
            Object callback = null;
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof IInterface) {
                        String desc = safeGetInterfaceDescriptor(((IInterface) arg).asBinder());
                        if (desc != null && desc.contains("ILocationCallback")) {
                            callback = arg;
                            break;
                        }
                    }
                }
            }
            if (callback != null) {
                try {
                    for (Method m : callback.getClass().getMethods()) {
                        if (m == null) {
                            continue;
                        }
                        if (!"onLocation".equals(m.getName()) && !"onLocationResult".equals(m.getName()) && !"onLocationChanged".equals(m.getName())) {
                            continue;
                        }
                        Class<?>[] p = m.getParameterTypes();
                        if (p == null || p.length != 1) {
                            continue;
                        }
                        if (p[0] == android.location.Location.class) {
                            m.invoke(callback, sys);
                            return defaultReturn(method);
                        }
                        if (List.class.isAssignableFrom(p[0])) {
                            m.invoke(callback, Collections.singletonList(sys));
                            return defaultReturn(method);
                        }
                        try {
                            Class<?> sliceClass = black.android.content.pm.BRParceledListSlice.getRealClass();
                            if (sliceClass != null && p[0] == sliceClass) {
                                Object slice = ParceledListSliceCompat.create(Collections.singletonList(sys));
                                m.invoke(callback, slice);
                                return defaultReturn(method);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "getCurrentLocation callback deliver failed: " + t.getMessage());
                }
            }

            // Fallback: if this call used an ILocationListener, register it for periodic updates.
            IBinder binder = findBestBinderCandidate(args);
            if (binder != null) {
                BLocationManager.get().requestLocationUpdates(binder);
            }
            return defaultReturn(method);
        }
    }

    @ProxyMethod("removeUpdates")
    public static class RemoveUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            PendingIntent pendingIntent = findPendingIntent(args);
            if (pendingIntent != null) {
                FakeLocationPendingIntentDispatcher.unregister(pendingIntent);
            }
            IBinder binder = findBestBinderCandidate(args);
            if (binder != null) {
                BLocationManager.get().removeUpdates(binder);
            }

            if (BLocationManager.isFakeLocationEnable()) {
                return defaultReturn(method);
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied for removeUpdates, returning default");
                    return defaultReturn(method);
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getProviderProperties")
    public static class GetProviderProperties extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object providerProperties;
            try {
                providerProperties = method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied for getProviderProperties, returning null");
                    return null;
                }
                throw e;
            }
            if (BLocationManager.isFakeLocationEnable()) {
                BRProviderProperties.get(providerProperties)._set_mHasNetworkRequirement(false);
                if (BLocationManager.get().getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) == null) {
                    BRProviderProperties.get(providerProperties)._set_mHasCellRequirement(false);
                }
            }
            return providerProperties;
        }
    }

    @ProxyMethod("isLocationEnabledForUser")
    public static class IsLocationEnabledForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Virtual environment: prefer "enabled" to avoid framework/service enforcement
            // killing location-dependent SDKs when host location mode is off.
            if (BLocationManager.isFakeLocationEnable()) {
                return true;
            }
            // Privacy-first: if fake-location disabled, report disabled.
            return false;
        }
    }

    @ProxyMethod("isLocationEnabled")
    public static class IsLocationEnabled extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return true;
            }
            return false;
        }
    }

    @ProxyMethod("removeGpsStatusListener")
    public static class RemoveGpsStatusListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            
            return 0;
        }
    }

    @ProxyMethod("getBestProvider")
    public static class GetBestProvider extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return LocationManager.GPS_PROVIDER;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAllProviders")
    public static class GetAllProviders extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getProviders")
    public static class GetProviders extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("isProviderEnabled")
    public static class IsProviderEnabled extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (!BLocationManager.isFakeLocationEnable()) {
                return method.invoke(who, args);
            }
            String provider = args != null && args.length > 0 ? (String) args[0] : null;
            return Objects.equals(provider, LocationManager.GPS_PROVIDER)
                    || Objects.equals(provider, LocationManager.NETWORK_PROVIDER)
                    || Objects.equals(provider, LocationManager.PASSIVE_PROVIDER);
        }
    }

    @ProxyMethod("isProviderEnabledForUser")
    public static class isProviderEnabledForUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (!BLocationManager.isFakeLocationEnable()) {
                return method.invoke(who, args);
            }
            String provider = args != null && args.length > 0 ? (String) args[0] : null;
            return Objects.equals(provider, LocationManager.GPS_PROVIDER)
                    || Objects.equals(provider, LocationManager.NETWORK_PROVIDER)
                    || Objects.equals(provider, LocationManager.PASSIVE_PROVIDER);
        }
    }

    @ProxyMethod("setExtraLocationControllerPackageEnabled")
    public static class setExtraLocationControllerPackageEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }
}
