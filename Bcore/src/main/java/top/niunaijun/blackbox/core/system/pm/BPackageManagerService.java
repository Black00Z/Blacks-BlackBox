package top.niunaijun.blackbox.core.system.pm;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.GmsCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.core.system.user.BUserInfo;
import top.niunaijun.blackbox.core.system.user.BUserManagerService;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.entity.pm.InstalledPackage;
import top.niunaijun.blackbox.utils.AbiUtils;
import top.niunaijun.blackbox.utils.BzFileUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.PackageParserCompat;


import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;



public class BPackageManagerService extends IBPackageManagerService.Stub implements ISystemService {
    public static final String TAG = "BPackageManagerService";
    public static BPackageManagerService sService = new BPackageManagerService();
    private final Settings mSettings = new Settings();
    private final ComponentResolver mComponentResolver;
    private static final BUserManagerService sUserManager = BUserManagerService.get();
    private final List<PackageMonitor> mPackageMonitors = new ArrayList<>();

    final Map<String, BPackageSettings> mPackages = mSettings.mPackages;
    final Object mInstallLock = new Object();

    public static BPackageManagerService get() {
        return sService;
    }

    public BPackageManagerService() {
        mComponentResolver = new ComponentResolver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addDataScheme("package");
        BlackBoxCore.getContext()
                .registerReceiver(mPackageChangedHandler, filter);
    }

    private final BroadcastReceiver mPackageChangedHandler = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!TextUtils.isEmpty(action)) {
                if ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REMOVED".equals(action)) {
                    mSettings.scanPackage();
                }
            }
        }
    };

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        if (Objects.equals(packageName, BlackBoxCore.getHostPkg())) {
            try {
                return BlackBoxCore.getPackageManager().getApplicationInfo(packageName, flags);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        flags = updateFlags(flags, userId);
        
        synchronized (mPackages) {
            
            BPackageSettings ps = mPackages.get(packageName);
            if (ps != null) {
                BPackage p = ps.pkg;
                return PackageManagerCompat.generateApplicationInfo(p, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags, String resolvedType, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> query = queryIntentServicesInternal(
                intent, resolvedType, flags, userId);
        if (query != null) {
            if (query.size() >= 1) {
                
                
                return query.get(0);
            }
        }
        ResolveInfo samsungHealthFallback = resolveSamsungHealthServiceFallback(intent, flags, userId);
        if (samsungHealthFallback != null) {
            return samsungHealthFallback;
        }
        return null;
    }

    private ResolveInfo resolveSamsungHealthServiceFallback(Intent intent, int flags, int userId) {
        if (intent == null || intent.getComponent() != null) {
            return null;
        }
        String packageName = intent.getPackage();
        String action = intent.getAction();
        if (!TextUtils.equals("com.sec.android.app.shealth", packageName)) {
            return null;
        }
        if (!TextUtils.equals("com.samsung.android.sdk.healthdata.IHealthDataStore", action)
                && !TextUtils.equals("com.samsung.android.sdk.healthdata.IPrivilegedHealth", action)) {
            return null;
        }
        ComponentName component = new ComponentName(
                "com.sec.android.app.shealth",
                "com.samsung.android.service.health.HealthService"
        );
        ServiceInfo serviceInfo = getServiceInfo(component, flags, userId);
        if (serviceInfo == null) {
            return null;
        }
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;
        return resolveInfo;
    }

    private List<ResolveInfo> queryIntentServicesInternal(Intent intent, String resolvedType, int flags, int userId) {
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ServiceInfo si = getServiceInfo(comp, flags, userId);
            if (si != null) {
                
                
                
                
                final ResolveInfo ri = new ResolveInfo();
                ri.serviceInfo = si;
                list.add(ri);
            }
            return list;
        }

        
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            if (pkgName != null) {
                BPackageSettings bPackageSettings = mPackages.get(pkgName);
                if (bPackageSettings != null) {
                    final BPackage pkg = bPackageSettings.pkg;
                    return mComponentResolver.queryServices(intent, resolvedType, flags, pkg.services,
                            userId);
                }
            } else {
               return mComponentResolver.queryServices(intent, resolvedType, flags, userId);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags, String resolvedType, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> resolves = queryIntentActivities(intent, resolvedType, flags, userId);
        return chooseBestActivity(intent, resolvedType, flags, resolves);
    }

    @Override
    public ProviderInfo resolveContentProvider(String authority, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        return mComponentResolver.queryProvider(authority, flags, userId);
    }

    @Override
    public ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> resolves = queryIntentActivities(intent, resolvedType, flags, userId);
        return chooseBestActivity(intent, resolvedType, flags, resolves);
    }

    private ResolveInfo chooseBestActivity(Intent intent, String resolvedType,
                                           int flags, List<ResolveInfo> query) {
        if (query != null) {
            final int N = query.size();
            if (N == 1) {
                return query.get(0);
            } else if (N > 1) {
                
                
                ResolveInfo r0 = query.get(0);
                ResolveInfo r1 = query.get(1);
                
                
                if (r0.priority != r1.priority
                        || r0.preferredOrder != r1.preferredOrder
                        || r0.isDefault != r1.isDefault) {
                    return query.get(0);
                }
            }
        }
        return null;
    }

    private List<ResolveInfo> queryIntentActivities(Intent intent,
                                                    String resolvedType, int flags, int userId) {
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }

        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ActivityInfo ai = getActivity(comp, flags, userId);
            if (ai != null) {
                
                
                
                
                final ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
                return list;
            }
        }

        
        synchronized (mPackages) {
            return mComponentResolver.queryActivities(intent, resolvedType, flags, userId);
        }
    }

    @Override
    public List<ResolveInfo> queryIntentServices(
            Intent intent, int flags, int userId) {
        final String resolvedType = intent.resolveTypeIfNeeded(BlackBoxCore.getContext().getContentResolver());
        return this.queryIntentServicesInternal(intent, resolvedType, flags, userId);
    }

    private ActivityInfo getActivity(ComponentName component, int flags,
                                     int userId) {
        flags = updateFlags(flags, userId);
        synchronized (mPackages) {
            BPackage.Activity a = mComponentResolver.getActivity(component);

            if (a != null) {
                BPackageSettings ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageManagerCompat.generateActivityInfo(a, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        if (Objects.equals(packageName, BlackBoxCore.getHostPkg())) {
            try {
                return BlackBoxCore.getPackageManager().getPackageInfo(packageName, flags);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        flags = updateFlags(flags, userId);
        BPackageSettings ps = null;
        
        synchronized (mPackages) {
            
            ps = mPackages.get(packageName);
        }
        if (ps != null) {
            return PackageManagerCompat.generatePackageInfo(ps, flags, ps.readUserState(userId), userId);
        }
        return null;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            BPackage.Service s = mComponentResolver.getService(component);
            if (s != null) {
                BPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageManagerCompat.generateServiceInfo(
                        s, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            BPackage.Activity a = mComponentResolver.getReceiver(component);
            if (a != null) {
                BPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageManagerCompat.generateActivityInfo(
                        a, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            BPackage.Activity a = mComponentResolver.getActivity(component);

            if (a != null) {
                BPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageManagerCompat.generateActivityInfo(
                        a, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            BPackage.Provider p = mComponentResolver.getProvider(component);
            if (p != null) {
                BPackageSettings ps = mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageManagerCompat.generateProviderInfo(
                        p, flags, ps.readUserState(userId), userId);
            }
        }
        return null;
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        return getInstalledApplicationsListInternal(flags, userId, Binder.getCallingUid());
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        final int callingUid = Binder.getCallingUid();



        if (!sUserManager.exists(userId)) return Collections.emptyList();

        
        synchronized (mPackages) {
            ArrayList<PackageInfo> list;
            list = new ArrayList<>(mPackages.size());
            for (BPackageSettings ps : mPackages.values()) {






                PackageInfo pi = getPackageInfo(ps.pkg.packageName, flags, userId);
                if (pi != null) {
                    list.add(pi);
                }
            }
            return new ArrayList<>(list);
        }
    }

    private List<ApplicationInfo> getInstalledApplicationsListInternal(int flags, int userId,
                                                                       int callingUid) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();

        
        synchronized (mPackages) {
            ArrayList<ApplicationInfo> list;
            list = new ArrayList<>(mPackages.size());
            Collection<BPackageSettings> packageSettings = mPackages.values();
            for (BPackageSettings ps : packageSettings) {








                ApplicationInfo ai = PackageManagerCompat.generateApplicationInfo(ps.pkg, flags,
                        ps.readUserState(userId), userId);
                if (ai != null) {
                    list.add(ai);
                }
            }
            return list;
        }
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags, String resolvedType, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        final String pkgName = intent.getPackage();
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }

        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ActivityInfo ai = getActivityInfo(comp, flags, userId);
            if (ai != null) {
                
                
                
                
                final ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
            }
            return list;
        }

        
        List<ResolveInfo> result;
        synchronized (mPackages) {
            if (pkgName != null) {
                BPackageSettings bPackageSettings = mPackages.get(pkgName);
                result = null;
                if (bPackageSettings != null) {
                    final BPackage pkg = bPackageSettings.pkg;

                    result = mComponentResolver.queryActivities(
                            intent, resolvedType, flags, pkg.activities, userId);
                }
                if (result == null || result.size() == 0) {
                    
                    
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                }
                return result;
            }
            return mComponentResolver.queryActivities(intent, resolvedType, flags, userId);
        }
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags, String resolvedType, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return Collections.emptyList();

        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<>(1);
            final ActivityInfo ai = getReceiverInfo(comp, flags, userId);
            if (ai != null) {
                
                
                
                
                ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
            }
            return list;
        }

        
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            BPackageSettings bPackageSettings = mPackages.get(pkgName);
            if (bPackageSettings != null) {
                final BPackage pkg = bPackageSettings.pkg;
                return mComponentResolver.queryReceivers(
                        intent, resolvedType, flags, pkg.receivers, userId);
            } else {
                return mComponentResolver.queryReceivers(intent, resolvedType, flags, userId);
            }
        }
    }

    @Override
    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return Collections.emptyList();

        List<ProviderInfo> providers = new ArrayList<>();
        if (TextUtils.isEmpty(processName))
            return providers;
        providers.addAll(mComponentResolver.queryProviders(processName, null, flags, userId));
        return providers;
    }

    @Override
    public InstallResult installPackageAsUser(String file, InstallOption option, int userId) {
        synchronized (mInstallLock) {
            return installPackageAsUserLocked(file, option, userId);
        }
    }

    @Override
    public void uninstallPackageAsUser(String packageName, int userId) throws RemoteException {
        synchronized (mInstallLock) {
            synchronized (mPackages) {
                BPackageSettings ps = mPackages.get(packageName);
                if (ps == null)
                    return;

                if (!isInstalled(packageName, userId)) {
                    return;
                }
                boolean removeApp = ps.getUserState().size() <= 1;
                BProcessManagerService.get().killPackageAsUser(packageName, userId);
                int i = BPackageInstallerService.get().uninstallPackageAsUser(ps, removeApp, userId);
                if (i < 0) {
                    
                }

                if (removeApp) {
                    mSettings.removePackage(packageName);
                    mComponentResolver.removeAllComponents(ps.pkg);
                } else {
                    ps.removeUser(userId);
                    ps.save();
                }
                onPackageUninstalled(packageName, removeApp, userId);
            }
        }
    }

    @Override
    public void uninstallPackage(String packageName) {
        synchronized (mInstallLock) {
            synchronized (mPackages) {
                BPackageSettings ps = mPackages.get(packageName);
                if (ps == null)
                    return;
                BProcessManagerService.get().killAllByPackageName(packageName);
                    for (Integer userId : ps.getUserIds()) {
                        int i = BPackageInstallerService.get().uninstallPackageAsUser(ps, true, userId);
                        if (i < 0) {
                            continue;
                        }
                        onPackageUninstalled(packageName, true, userId);
                    }
                mSettings.removePackage(packageName);
                mComponentResolver.removeAllComponents(ps.pkg);
            }
        }
    }

    @Override
    public void clearPackage(String packageName, int userId) {
        if (!isInstalled(packageName, userId)) {
            return;
        }
        BProcessManagerService.get().killPackageAsUser(packageName, userId);
        BPackageSettings ps = mPackages.get(packageName);
        if (ps == null)
            return;
        int i = BPackageInstallerService.get().clearPackage(ps, userId);
    }

    @Override
    public void stopPackage(String packageName, int userId) {
        BProcessManagerService.get().killPackageAsUser(packageName, userId);
    }

    @Override
    public void deleteUser(int userId) throws RemoteException {
        synchronized (mPackages) {
            for (BPackageSettings ps : mPackages.values()) {
                uninstallPackageAsUser(ps.pkg.packageName, userId);
            }
        }
    }

    @Override
    public boolean isInstalled(String packageName, int userId) {
        if (!sUserManager.exists(userId)) return false;
        synchronized (mPackages) {
            BPackageSettings ps = mPackages.get(packageName);
            if (ps == null)
                return false;
            return ps.getInstalled(userId);
        }
    }

    @Override
    public List<InstalledPackage> getInstalledPackagesAsUser(int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        synchronized (mPackages) {
            List<InstalledPackage> installedPackages = new ArrayList<>();
            for (BPackageSettings ps : mPackages.values()) {
                if (ps.getInstalled(userId) && !GmsCore.isGoogleAppOrService(ps.pkg.packageName)) {
                    InstalledPackage installedPackage = new InstalledPackage();
                    installedPackage.userId = userId;
                    installedPackage.packageName = ps.pkg.packageName;
                    installedPackages.add(installedPackage);
                }
            }
            return installedPackages;
        }
    }

    @Override
    public String[] getPackagesForUid(int uid, int userId) throws RemoteException {
        if (!sUserManager.exists(userId)) return new String[]{};
        synchronized (mPackages) {
            List<String> packages = new ArrayList<>();
            for (BPackageSettings ps : mPackages.values()) {
                String packageName = ps.pkg.packageName;
                if (ps.getInstalled(userId) && getAppId(packageName) == uid) {
                    packages.add(packageName);
                }
            }
            if (packages.isEmpty()) {
                ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(getCallingPid());
                if (processByPid != null) {
                    packages.add(processByPid.getPackageName());
                }
            }
            return packages.toArray(new String[]{});
        }
    }

    private InstallResult installPackageAsUserLocked(String file, InstallOption option, int userId) {
        long l = System.currentTimeMillis();
        InstallResult result = new InstallResult();
        File apkFile = null;
        File stagedFile = null;
        File extractedDir = null;
        List<File> selectedSplits = null;
        try {
            Slog.i(TAG, "installPackageAsUserLocked: userId=" + userId
                    + " uriFile=" + option.isFlag(InstallOption.FLAG_URI_FILE)
                    + " file=" + file);
            if (!sUserManager.exists(userId)) {
                sUserManager.createUser(userId);
            }
            if (option.isFlag(InstallOption.FLAG_URI_FILE)) {
                Uri uri = Uri.parse(file);
                String displayName = resolveDisplayName(uri);
                boolean isApks = displayName != null && displayName.toLowerCase().endsWith(".apks");
                String suffix = isApks ? ".apks" : ".apk";
                stagedFile = new File(BEnvironment.getCacheDir(), UUID.randomUUID().toString() + suffix);
                InputStream inputStream = BlackBoxCore.getContext().getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    return result.installError("openInputStream returned null for uri: " + file);
                }
                BzFileUtils.copyFile(inputStream, stagedFile);
            } else {
                stagedFile = new File(file);
            }

            if (stagedFile == null) {
                return result.installError("install source is null");
            }

            if (isApksBundle(stagedFile)) {
                extractedDir = new File(BEnvironment.getCacheDir(), "apks_" + UUID.randomUUID());
                BzFileUtils.mkdirs(extractedDir);

                List<File> extractedApks = extractApksBundle(stagedFile, extractedDir);
                if (extractedApks == null || extractedApks.isEmpty()) {
                    return result.installError("APKS bundle: no APK files found");
                }

                apkFile = pickBaseApk(extractedApks);
                if (apkFile == null || !apkFile.exists()) {
                    return result.installError("APKS bundle: could not find base APK");
                }

                selectedSplits = selectSplitApks(extractedApks, apkFile, BlackBoxCore.is64Bit());
                StringBuilder splitNames = new StringBuilder();
                if (selectedSplits != null) {
                    for (File s : selectedSplits) {
                        if (s == null) continue;
                        if (splitNames.length() > 0) splitNames.append(", ");
                        splitNames.append(s.getName());
                    }
                }
                Slog.i(TAG, "APKS bundle: base=" + apkFile.getName()
                        + " splits=" + (selectedSplits != null ? selectedSplits.size() : 0)
                        + " [" + splitNames + "]");
            } else {
                apkFile = stagedFile;
            }


            PackageInfo packageArchiveInfo = BlackBoxCore.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (packageArchiveInfo == null) {
                return result.installError("getPackageArchiveInfo error.Please check whether APK is normal.");
            }

            
            String packageName = packageArchiveInfo.packageName;
            String hostPackageName = BlackBoxCore.getHostPkg();
            if (packageName.equals(hostPackageName)) {
                return result.installError("Cannot clone BlackBox app from within BlackBox. This would create infinite recursion and is not allowed for security reasons.");
            }
            
            
            if (packageName.contains("blackbox") || packageName.contains("niunaijun") || 
                packageName.contains("vspace") || packageName.contains("virtual")) {
                
                Slog.w(TAG, "Installing potentially BlackBox-related app: " + packageName + ". Proceed with caution.");
            }

            boolean support = AbiUtils.isSupport(apkFile);
            if (!support) {
                String msg = packageArchiveInfo.applicationInfo.loadLabel(BlackBoxCore.getPackageManager()) + "[" + packageArchiveInfo.packageName + "]";
                return result.installError(packageArchiveInfo.packageName,
                        msg + "\n" + (BlackBoxCore.is64Bit() ? "The box does not support 32-bit Application" : "The box does not support 64-bit Application"));
            }
            PackageParser.Package aPackage = parserApk(apkFile.getAbsolutePath());
            if (aPackage == null) {
                return result.installError("parser apk error.");
            }
            result.packageName = aPackage.packageName;

            if (selectedSplits != null && !selectedSplits.isEmpty()) {
                ArrayList<String> splitPaths = new ArrayList<>();
                ArrayList<String> splitNames = new ArrayList<>();
                for (File split : selectedSplits) {
                    if (split == null || !split.exists()) {
                        continue;
                    }
                    splitPaths.add(split.getAbsolutePath());
                    splitNames.add(stripApkExtension(split.getName()));
                }
                if (!splitPaths.isEmpty()) {
                    aPackage.applicationInfo.splitSourceDirs = splitPaths.toArray(new String[0]);
                    aPackage.applicationInfo.splitPublicSourceDirs = aPackage.applicationInfo.splitSourceDirs;
                    aPackage.applicationInfo.splitNames = splitNames.toArray(new String[0]);
                }
            }

            if (option.isFlag(InstallOption.FLAG_SYSTEM)) {
                aPackage.applicationInfo = BlackBoxCore.getPackageManager().getPackageInfo(aPackage.packageName, 0).applicationInfo;
            }
            BPackageSettings bPackageSettings = mSettings.getPackageLPw(aPackage.packageName, aPackage, option);

            
            BProcessManagerService.get().killPackageAsUser(aPackage.packageName, userId);

            int i = BPackageInstallerService.get().installPackageAsUser(bPackageSettings, userId);
            if (i < 0) {
                return result.installError("install apk error.");
            }
            synchronized (mPackages) {
                bPackageSettings.setInstalled(true, userId);
                bPackageSettings.save();
            }
            mComponentResolver.removeAllComponents(bPackageSettings.pkg);
            mComponentResolver.addAllComponents(bPackageSettings.pkg);
            mSettings.scanPackage(aPackage.packageName);
            onPackageInstalled(bPackageSettings.pkg.packageName, userId);
            return result;
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (stagedFile != null && option.isFlag(InstallOption.FLAG_URI_FILE)) {
                BzFileUtils.deleteDir(stagedFile);
            }
            if (extractedDir != null) {
                BzFileUtils.deleteDir(extractedDir);
            }
            Slog.d(TAG, "install finish: " + (System.currentTimeMillis() - l) + "ms");
        }
        return result;
    }

    private String resolveDisplayName(Uri uri) {
        if (uri == null) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = BlackBoxCore.getContext()
                    .getContentResolver()
                    .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor == null) {
                return null;
            }
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index < 0) {
                return null;
            }
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.getString(index);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static boolean isApksBundle(File file) {
        if (file == null) return false;
        String name = file.getName();
        if (name != null && name.toLowerCase().endsWith(".apks")) {
            return true;
        }
        // Fallback: some document providers strip/rename extensions. Detect by zip content.
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int apkCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e == null || e.isDirectory()) continue;
                String n = e.getName();
                if (n != null && n.toLowerCase().endsWith(".apk")) {
                    apkCount++;
                    if (apkCount >= 2) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // not zip
        }
        return false;
    }

    private static List<File> extractApksBundle(File apksFile, File outDir) throws Exception {
        ArrayList<File> extracted = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(apksFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (TextUtils.isEmpty(name) || !name.toLowerCase().endsWith(".apk")) {
                    continue;
                }
                String safeName = safeZipEntryFileName(name);
                if (TextUtils.isEmpty(safeName)) {
                    continue;
                }
                File outFile = uniqueApkFile(outDir, safeName);
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    if (inputStream == null) {
                        continue;
                    }
                    BzFileUtils.copyFile(inputStream, outFile);
                    extracted.add(outFile);
                }
            }
        }
        return extracted;
    }

    private static File pickBaseApk(List<File> apks) {
        if (apks == null || apks.isEmpty()) {
            return null;
        }
        for (File f : apks) {
            if (f == null) continue;
            String n = f.getName();
            if (n == null) continue;
            String lower = n.toLowerCase();
            if (lower.equals("base.apk")) {
                return f;
            }
            if (lower.contains("base-master") || lower.contains("base_master") || lower.endsWith("base-master.apk")) {
                return f;
            }
        }
        File best = null;
        long bestSize = -1;
        for (File f : apks) {
            if (f == null) continue;
            long size = f.length();
            if (size > bestSize) {
                bestSize = size;
                best = f;
            }
        }
        return best;
    }

    private static List<File> selectSplitApks(List<File> apks, File baseApk, boolean is64Bit) {
        if (apks == null || apks.isEmpty() || baseApk == null) {
            return Collections.emptyList();
        }
        ArrayList<File> nonBase = new ArrayList<>();
        for (File f : apks) {
            if (f == null || f.equals(baseApk)) continue;
            nonBase.add(f);
        }
        if (nonBase.isEmpty()) {
            return Collections.emptyList();
        }

        // Heuristic split selection: pick at most one ABI, one density, one language split.
        // This mirrors typical Play/AppBundle installs and avoids loading many incompatible splits.
        File abiSplit = null;
        File densitySplit = null;
        File langSplit = null;

        String[] supportedAbis = null;
        try {
            supportedAbis = android.os.Build.SUPPORTED_ABIS;
        } catch (Throwable ignored) {
        }

        String bestAbiKey = null;
        if (supportedAbis != null) {
            for (String abi : supportedAbis) {
                if (abi == null) continue;
                String a = abi.toLowerCase();
                if (a.contains("x86") || a.contains("mips")) continue;
                boolean want64 = is64Bit;
                boolean isAbi64 = a.contains("64");
                if (want64 != isAbi64) continue;
                bestAbiKey = a.replace('-', '_');
                break;
            }
        }

        int densityDpi = 0;
        try {
            densityDpi = android.content.res.Resources.getSystem().getDisplayMetrics().densityDpi;
        } catch (Throwable ignored) {
        }

        String densityKey = null;
        if (densityDpi > 0) {
            if (densityDpi >= 560) densityKey = "xxxhdpi";
            else if (densityDpi >= 400) densityKey = "xxhdpi";
            else if (densityDpi >= 280) densityKey = "xhdpi";
            else if (densityDpi >= 200) densityKey = "hdpi";
            else densityKey = "mdpi";
        }

        String lang = null;
        try {
            lang = java.util.Locale.getDefault().getLanguage();
        } catch (Throwable ignored) {
        }

        // First pass: exact matches.
        for (File f : nonBase) {
            String n = f.getName();
            if (n == null) continue;
            String lower = n.toLowerCase();
            if (lower.contains("x86") || lower.contains("x86_64") || lower.contains("mips")) continue;

            if (abiSplit == null && bestAbiKey != null && lower.contains(bestAbiKey)) {
                // Prefer "config.<abi>.apk" but tolerate other naming.
                abiSplit = f;
                continue;
            }
            if (densitySplit == null && densityKey != null && lower.contains(densityKey)) {
                densitySplit = f;
                continue;
            }
            if (langSplit == null && lang != null && !lang.isEmpty() && lower.contains("config." + lang.toLowerCase())) {
                langSplit = f;
            }
        }

        // Fallbacks: pick any matching-arch split if no exact ABI split chosen.
        if (abiSplit == null) {
            for (File f : nonBase) {
                String n = f.getName();
                if (n == null) continue;
                String lower = n.toLowerCase();
                if (lower.contains("x86") || lower.contains("x86_64") || lower.contains("mips")) continue;
                boolean isArm64 = lower.contains("arm64") || lower.contains("arm64_v8a") || lower.contains("arm64-v8a");
                boolean isArm32 = lower.contains("armeabi") || lower.contains("armeabi_v7a") || lower.contains("armeabi-v7a");
                if (isArm64 && !is64Bit) continue;
                if (isArm32 && is64Bit) continue;
                if (isArm64 || isArm32) {
                    abiSplit = f;
                    break;
                }
            }
        }

        // Fallback: choose a "good" density split (highest first) if no exact.
        if (densitySplit == null) {
            String[] pref = new String[]{"xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "ldpi"};
            for (String d : pref) {
                for (File f : nonBase) {
                    String n = f.getName();
                    if (n == null) continue;
                    String lower = n.toLowerCase();
                    if (lower.contains("config." + d)) {
                        densitySplit = f;
                        break;
                    }
                }
                if (densitySplit != null) break;
            }
        }

        // Fallback: english split.
        if (langSplit == null) {
            for (File f : nonBase) {
                String n = f.getName();
                if (n == null) continue;
                String lower = n.toLowerCase();
                if (lower.contains("config.en")) {
                    langSplit = f;
                    break;
                }
            }
        }

        ArrayList<File> result = new ArrayList<>();
        if (abiSplit != null) result.add(abiSplit);
        if (densitySplit != null && densitySplit != abiSplit) result.add(densitySplit);
        if (langSplit != null && langSplit != abiSplit && langSplit != densitySplit) result.add(langSplit);
        return result;
    }

    private static String stripApkExtension(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();
        if (lower.endsWith(".apk") && name.length() > 4) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String safeZipEntryFileName(String entryName) {
        if (entryName == null) return null;
        String n = entryName.replace('\\', '/');
        int idx = n.lastIndexOf('/');
        if (idx >= 0) {
            n = n.substring(idx + 1);
        }
        if (n.contains("..") || n.contains("/") || n.contains("\\")) {
            return null;
        }
        return n;
    }

    private static File uniqueApkFile(File dir, String fileName) {
        File f = new File(dir, fileName);
        if (!f.exists()) {
            return f;
        }
        String base = stripApkExtension(fileName);
        for (int i = 1; i <= 1000; i++) {
            File candidate = new File(dir, base + "_" + i + ".apk");
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return new File(dir, UUID.randomUUID().toString() + ".apk");
    }

    private PackageParser.Package parserApk(String file) {
        try {
            PackageParser parser = PackageParserCompat.createParser(new File(file));
            PackageParser.Package aPackage = PackageParserCompat.parsePackage(parser, new File(file), 0);
            PackageParserCompat.collectCertificates(parser, aPackage, 0);
            return aPackage;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    static String fixProcessName(String defProcessName, String processName) {
        if (processName == null) {
            return defProcessName;
        }
        return processName;
    }

    
    private int updateFlags(int flags, int userId) {
        if ((flags & (PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                | PackageManager.MATCH_DIRECT_BOOT_AWARE)) != 0) {
            
            
            
        } else {
            
            flags |= PackageManager.MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE;
        }
        return flags;
    }

    public int getAppId(String packageName) {
        BPackageSettings bPackageSettings = mPackages.get(packageName);
        if (bPackageSettings != null)
            return bPackageSettings.appId;
        return -1;
    }

    Settings getSettings() {
        return mSettings;
    }

    public void addPackageMonitor(PackageMonitor monitor) {
        mPackageMonitors.add(monitor);
    }

    public void removePackageMonitor(PackageMonitor monitor) {
        mPackageMonitors.remove(monitor);
    }

    void onPackageUninstalled(String packageName, boolean isRemove, int userId) {
        for (PackageMonitor packageMonitor : mPackageMonitors) {
            packageMonitor.onPackageUninstalled(packageName, isRemove, userId);
        }
        Slog.d(TAG, "onPackageUninstalled: " + packageName + ", userId: " + userId);
    }

    void onPackageInstalled(String packageName, int userId) {
        for (PackageMonitor packageMonitor : mPackageMonitors) {
            packageMonitor.onPackageInstalled(packageName, userId);
        }
        Slog.d(TAG, "onPackageInstalled: " + packageName + ", userId: " + userId);
    }

    public BPackageSettings getBPackageSetting(String packageName) {
        return mPackages.get(packageName);
    }

    public List<BPackageSettings> getBPackageSettings() {
        return new ArrayList<>(mPackages.values());
    }

    @Override
    public void systemReady() {
        mSettings.scanPackage();
        for (BPackageSettings value : mPackages.values()) {
            mComponentResolver.removeAllComponents(value.pkg);
            mComponentResolver.addAllComponents(value.pkg);
        }
    }
}
