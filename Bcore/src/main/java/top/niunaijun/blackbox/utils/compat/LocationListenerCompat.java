package top.niunaijun.blackbox.utils.compat;

import android.location.Location;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import black.android.content.pm.BRParceledListSlice;
import top.niunaijun.blackbox.utils.Slog;

public final class LocationListenerCompat {
    private static final String TAG = "LocationListenerCompat";

    private static final int MODE_NONE = 0;
    private static final int MODE_LOCATION = 1;
    private static final int MODE_LIST = 2;
    private static final int MODE_SLICE = 3;

    private static final ConcurrentHashMap<Class<?>, Dispatch> CACHE = new ConcurrentHashMap<>();

    private static final class Dispatch {
        final Method method;
        final int mode;
        final boolean needsCompleteCallback;

        Dispatch(Method method, int mode, boolean needsCompleteCallback) {
            this.method = method;
            this.mode = mode;
            this.needsCompleteCallback = needsCompleteCallback;
        }
    }

    private LocationListenerCompat() {
    }

    public static void dispatchLocationChanged(Object locationListener, Location location) {
        if (locationListener == null || location == null) {
            return;
        }

        Dispatch dispatch = CACHE.get(locationListener.getClass());
        if (dispatch == null) {
            dispatch = buildDispatch(locationListener.getClass());
            CACHE.put(locationListener.getClass(), dispatch);
        }

        if (dispatch.mode == MODE_NONE || dispatch.method == null) {
            return;
        }

        try {
            switch (dispatch.mode) {
                case MODE_LOCATION:
                    if (dispatch.needsCompleteCallback) {
                        dispatch.method.invoke(locationListener, location, null);
                    } else {
                        dispatch.method.invoke(locationListener, location);
                    }
                    return;
                case MODE_LIST: {
                    List<Location> list = Collections.singletonList(location);
                    if (dispatch.needsCompleteCallback) {
                        dispatch.method.invoke(locationListener, list, null);
                    } else {
                        dispatch.method.invoke(locationListener, list);
                    }
                    return;
                }
                case MODE_SLICE: {
                    Object slice = ParceledListSliceCompat.create(Collections.singletonList(location));
                    if (dispatch.needsCompleteCallback) {
                        dispatch.method.invoke(locationListener, slice, null);
                    } else {
                        dispatch.method.invoke(locationListener, slice);
                    }
                    return;
                }
                default:
                    return;
            }
        } catch (Throwable t) {
            Slog.w(TAG, "dispatchLocationChanged failed: " + t.getMessage());
        }
    }

    private static Dispatch buildDispatch(Class<?> listenerClass) {
        try {
            Dispatch found = findDispatch(listenerClass.getMethods());
            if (found != null) {
                return found;
            }
        } catch (Throwable ignored) {
        }

        try {
            Dispatch found = findDispatch(listenerClass.getDeclaredMethods());
            if (found != null) {
                return found;
            }
        } catch (Throwable ignored) {
        }

        return new Dispatch(null, MODE_NONE, false);
    }

    private static Dispatch findDispatch(Method[] methods) {
        if (methods == null) {
            return null;
        }

        // Prefer the most specific signature first.
        Method locationMethod = null;
        Method listMethod = null;
        Method sliceMethod = null;
        boolean locationNeedsCallback = false;
        boolean listNeedsCallback = false;
        boolean sliceNeedsCallback = false;

        Class<?> sliceClass = null;
        try {
            sliceClass = BRParceledListSlice.getRealClass();
        } catch (Throwable ignored) {
        }

        for (Method m : methods) {
            if (m == null) {
                continue;
            }
            if (!"onLocationChanged".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p == null || (p.length != 1 && p.length != 2)) {
                continue;
            }

            boolean needsCallback = (p.length == 2);
            if (p[0] == Location.class) {
                locationMethod = m;
                locationNeedsCallback = needsCallback;
                continue;
            }
            if (sliceClass != null && p[0] == sliceClass) {
                sliceMethod = m;
                sliceNeedsCallback = needsCallback;
                continue;
            }
            if (List.class.isAssignableFrom(p[0])) {
                listMethod = m;
                listNeedsCallback = needsCallback;
            }
        }

        if (locationMethod != null) {
            locationMethod.setAccessible(true);
            return new Dispatch(locationMethod, MODE_LOCATION, locationNeedsCallback);
        }
        if (sliceMethod != null) {
            sliceMethod.setAccessible(true);
            return new Dispatch(sliceMethod, MODE_SLICE, sliceNeedsCallback);
        }
        if (listMethod != null) {
            listMethod.setAccessible(true);
            return new Dispatch(listMethod, MODE_LIST, listNeedsCallback);
        }

        return null;
    }
}
