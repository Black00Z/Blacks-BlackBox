package top.niunaijun.blackbox.utils.compat;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;

public class BzContentProviderCompat {

    public static Bundle call(Context context, Uri uri, String method, String arg, Bundle extras, int retryCount) throws IllegalAccessException {
        if (VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return context.getContentResolver().call(uri, method, arg, extras);
        }

        ProviderClientLease lease = ProviderClientLease.acquireUri(context, uri, retryCount);
        if (lease == null) {
            throw new IllegalAccessException("Failed to acquire provider client for " + uri);
        }
        try {
            return lease.client.call(method, arg, extras);
        } catch (RemoteException e) {
            throw new IllegalAccessException("Provider call failed: " + e.getMessage());
        } finally {
            lease.close();
        }
    }

    public static ContentProviderClient acquireContentProviderClientRetry(Context context, Uri uri, int retryCount) {
        ProviderClientLease lease = ProviderClientLease.acquireUri(context, uri, retryCount);
        return lease == null ? null : lease.detach();
    }

    public static ContentProviderClient acquireContentProviderClientRetry(Context context, String name, int retryCount) {
        ProviderClientLease lease = ProviderClientLease.acquireName(context, name, retryCount);
        return lease == null ? null : lease.detach();
    }

    private static final class ProviderClientLease implements AutoCloseable {
        private final ContentProviderClient client;
        private boolean detached;

        private ProviderClientLease(ContentProviderClient client) {
            this.client = client;
        }

        static ProviderClientLease acquireUri(Context context, Uri uri, int retryCount) {
            return acquire(context, uri, null, retryCount);
        }

        static ProviderClientLease acquireName(Context context, String name, int retryCount) {
            return acquire(context, null, name, retryCount);
        }

        private static ProviderClientLease acquire(Context context, Uri uri, String name, int retryCount) {
            final int attempts = Math.max(0, retryCount) + 1;
            final long deadlineUptimeMs = SystemClock.uptimeMillis() + 2000L;

            for (int attempt = 0; attempt < attempts; attempt++) {
                ContentProviderClient client = tryAcquireClient(context, uri, name);
                if (client != null) {
                    return new ProviderClientLease(client);
                }

                if (attempt + 1 >= attempts) {
                    break;
                }
                if (SystemClock.uptimeMillis() >= deadlineUptimeMs) {
                    break;
                }
                SystemClock.sleep(200L);
            }
            return null;
        }

        private static ContentProviderClient tryAcquireClient(Context context, Uri uri, String name) {
            try {
                if (VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    if (uri != null) {
                        return context.getContentResolver().acquireUnstableContentProviderClient(uri);
                    }
                    return context.getContentResolver().acquireUnstableContentProviderClient(name);
                }
                if (uri != null) {
                    return context.getContentResolver().acquireContentProviderClient(uri);
                }
                return context.getContentResolver().acquireContentProviderClient(name);
            } catch (SecurityException ignored) {
                return null;
            }
        }

        ContentProviderClient detach() {
            detached = true;
            return client;
        }

        @Override
        public void close() {
            if (detached || client == null) {
                return;
            }
            try {
                if (VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    client.close();
                } else {
                    client.release();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}