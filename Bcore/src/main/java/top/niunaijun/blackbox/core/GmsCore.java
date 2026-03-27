package top.niunaijun.blackbox.core;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;


public class GmsCore {
    private static final String TAG = "GmsCore";

    private static final HashSet<String> GOOGLE_APP = new HashSet<>();
    private static final HashSet<String> GOOGLE_SERVICE = new HashSet<>();
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    static {
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        
        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public static boolean isGoogleService(String packageName) {
        return GOOGLE_SERVICE.contains(packageName);
    }

    public static boolean isGoogleAppOrService(String str) {
        return GOOGLE_APP.contains(str) || GOOGLE_SERVICE.contains(str);
    }

    private static InstallResult installPackages(Set<String> list, int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            if (blackBoxCore.isInstalled(packageName, userId)) {
                continue;
            }
            try {
                BlackBoxCore.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                
                continue;
            }
            InstallResult installResult = blackBoxCore.installPackageAsUser(packageName, userId);
            if (!installResult.success) {
                return installResult;
            }
        }
        return new InstallResult();
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            blackBoxCore.uninstallPackageAsUser(packageName, userId);
        }
    }

    public static InstallResult installGApps(int userId) {
        Set<String> googleApps = new HashSet<>();

        googleApps.addAll(GOOGLE_SERVICE);
        googleApps.addAll(GOOGLE_APP);

        InstallResult installResult = installPackages(googleApps, userId);
        if (!installResult.success) {
            uninstallGApps(userId);
            return installResult;
        }
        return installResult;
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static boolean hasGmsTraces(int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();

        Set<String> known = new HashSet<>();
        known.addAll(GOOGLE_SERVICE);
        known.addAll(GOOGLE_APP);

        for (String pkg : known) {
            try {
                if (blackBoxCore.isInstalled(pkg, userId)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            List<PackageInfo> installed = blackBoxCore.getInstalledPackages(0, userId);
            for (PackageInfo pi : installed) {
                if (pi == null || pi.packageName == null) {
                    continue;
                }
                String pkg = pi.packageName;
                if (VENDING_PKG.equals(pkg) || pkg.startsWith("com.google.android.")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    /**
     * Best-effort cleanup: clears package data and uninstalls known Google packages
     * for the given virtual user. Intended for "wipe" UX.
     */
    public static void wipeGApps(int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();

        Set<String> toRemove = new HashSet<>();
        toRemove.addAll(GOOGLE_SERVICE);
        toRemove.addAll(GOOGLE_APP);

        try {
            List<PackageInfo> installed = blackBoxCore.getInstalledPackages(0, userId);
            for (PackageInfo pi : installed) {
                if (pi == null || pi.packageName == null) {
                    continue;
                }
                String pkg = pi.packageName;
                if (VENDING_PKG.equals(pkg) || pkg.startsWith("com.google.android.")) {
                    toRemove.add(pkg);
                }
            }
        } catch (Throwable ignored) {
        }

        for (String pkg : toRemove) {
            try {
                blackBoxCore.clearPackage(pkg, userId);
            } catch (Throwable ignored) {
            }
        }

        for (String pkg : toRemove) {
            try {
                blackBoxCore.uninstallPackageAsUser(pkg, userId);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }


    public static boolean isSupportGms() {
        try {
            BlackBoxCore.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    public static boolean isInstalledGoogleService(int userId) {
        return BlackBoxCore.get().isInstalled(GMS_PKG, userId);
    }
}