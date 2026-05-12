package top.niunaijun.blackbox.fake.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.utils.Slog;

public final class FakeLocationPendingIntentDispatcher {
    private static final String TAG = "FakeLocationPI";

    // Keep this conservative: frequent enough for UX, but not a battery hog.
    private static final long TICK_MS = 1000L;
    private static final long FORCE_RESEND_MS = 3000L;

    private static final Map<PendingIntent, Record> RECORDS = new ConcurrentHashMap<>();
    private static volatile boolean sStarted = false;

    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "bbox-fake-location-pi");
            t.setDaemon(true);
            return t;
        }
    });

    private static final class Record {
        final int userId;
        final String packageName;
        boolean hasLast;
        double lastLat;
        double lastLng;
        long lastSentAt;

        Record(int userId, String packageName) {
            this.userId = userId;
            this.packageName = packageName;
        }
    }

    private FakeLocationPendingIntentDispatcher() {
    }

    public static void register(PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            return;
        }
        String pkg = BActivityThread.getAppPackageName();
        if (pkg == null) {
            return;
        }
        int userId = BActivityThread.getUserId();
        RECORDS.put(pendingIntent, new Record(userId, pkg));
        ensureStarted();
    }

    public static void unregister(PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            return;
        }
        RECORDS.remove(pendingIntent);
    }

    private static void ensureStarted() {
        if (sStarted) {
            return;
        }
        synchronized (FakeLocationPendingIntentDispatcher.class) {
            if (sStarted) {
                return;
            }
            sStarted = true;
            EXEC.scheduleAtFixedRate(FakeLocationPendingIntentDispatcher::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static void tick() {
        if (RECORDS.isEmpty()) {
            return;
        }
        Context ctx = BlackBoxCore.getContext();
        if (ctx == null) {
            return;
        }

        for (Map.Entry<PendingIntent, Record> e : RECORDS.entrySet()) {
            PendingIntent pi = e.getKey();
            Record r = e.getValue();
            if (pi == null || r == null) {
                continue;
            }

            BLocation loc;
            try {
                loc = BLocationManager.get().getLocation(r.userId, r.packageName);
            } catch (Throwable t) {
                Slog.w(TAG, "getLocation failed: " + t.getMessage());
                continue;
            }
            if (loc == null) {
                continue;
            }

            long now = System.currentTimeMillis();
            boolean changed = !r.hasLast
                    || Math.abs(loc.getLatitude() - r.lastLat) > 1e-7
                    || Math.abs(loc.getLongitude() - r.lastLng) > 1e-7;
            boolean force = (now - r.lastSentAt) >= FORCE_RESEND_MS;
            if (!changed && !force) {
                continue;
            }

            try {
                Location sys = loc.convert2SystemLocation();
                Intent fillIn = new Intent();
                fillIn.putExtra(LocationManager.KEY_LOCATION_CHANGED, sys);
                // Keep batched locations optional; many apps only read KEY_LOCATION_CHANGED.
                fillIn.putExtra(LocationManager.KEY_LOCATIONS, new Location[]{sys});
                pi.send(ctx, 0, fillIn);

                r.hasLast = true;
                r.lastLat = loc.getLatitude();
                r.lastLng = loc.getLongitude();
                r.lastSentAt = now;
            } catch (PendingIntent.CanceledException ex) {
                // App unregistered/died; drop it.
                RECORDS.remove(pi);
            } catch (Throwable t) {
                Slog.w(TAG, "PendingIntent send failed: " + t.getMessage());
            }
        }
    }
}
