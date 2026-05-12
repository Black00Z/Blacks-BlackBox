package top.niunaijun.blackbox.core.system.pm.installer;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.BzFileUtils;
import top.niunaijun.blackbox.utils.NativeUtils;
import top.niunaijun.blackbox.utils.Slog;


public class CopyExecutor implements Executor {
    private static final String TAG = "CopyExecutor";

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                NativeUtils.copyNativeLib(new File(ps.pkg.baseCodePath), BEnvironment.getAppLibDir(ps.pkg.packageName));
                if (ps.pkg.applicationInfo != null && ps.pkg.applicationInfo.splitSourceDirs != null) {
                    for (String split : ps.pkg.applicationInfo.splitSourceDirs) {
                        if (split == null || split.length() == 0) {
                            continue;
                        }
                        try {
                            NativeUtils.copyNativeLib(new File(split), BEnvironment.getAppLibDir(ps.pkg.packageName));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(InstallOption.FLAG_STORAGE)) {
            
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = BEnvironment.getBaseApkDir(ps.pkg.packageName);
            try {
                if (option.isFlag(InstallOption.FLAG_URI_FILE)) {
                    boolean b = BzFileUtils.renameTo(origFile, newFile);
                    if (!b) {
                        BzFileUtils.copyFile(origFile, newFile);
                    }
                } else {
                    BzFileUtils.copyFile(origFile, newFile);
                }
                newFile.setReadOnly();
                
                ps.pkg.baseCodePath = newFile.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }

            if (ps.pkg.applicationInfo != null && ps.pkg.applicationInfo.splitSourceDirs != null
                    && ps.pkg.applicationInfo.splitSourceDirs.length > 0) {
                try {
                    File splitDir = BEnvironment.getSplitApkDir(ps.pkg.packageName);
                    BzFileUtils.mkdirs(splitDir);

                    ArrayList<String> newSplitPaths = new ArrayList<>();
                    for (String split : ps.pkg.applicationInfo.splitSourceDirs) {
                        if (split == null || split.length() == 0) {
                            continue;
                        }
                        File splitFile = new File(split);
                        if (!splitFile.exists()) {
                            Slog.w(TAG, "split missing, skip: " + split);
                            continue;
                        }
                        File copied = new File(splitDir, splitFile.getName());
                        try {
                            BzFileUtils.copyFile(splitFile, copied);
                            copied.setReadOnly();
                            newSplitPaths.add(copied.getAbsolutePath());
                        } catch (IOException ignored) {
                        }
                    }

                    if (!newSplitPaths.isEmpty()) {
                        ps.pkg.applicationInfo.splitSourceDirs = newSplitPaths.toArray(new String[0]);
                        ps.pkg.applicationInfo.splitPublicSourceDirs = ps.pkg.applicationInfo.splitSourceDirs;
                        Slog.i(TAG, "copied splits: pkg=" + ps.pkg.packageName + " count=" + newSplitPaths.size());
                    }
                } catch (Throwable ignored) {
                }
            }
        } else if (option.isFlag(InstallOption.FLAG_SYSTEM)) {
            
        }
        return 0;
    }
}
