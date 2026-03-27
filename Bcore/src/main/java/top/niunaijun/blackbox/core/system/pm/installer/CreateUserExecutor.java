package top.niunaijun.blackbox.core.system.pm.installer;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.BzFileUtils;


public class CreateUserExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        BzFileUtils.deleteDir(BEnvironment.getDataLibDir(packageName, userId));

        
        BzFileUtils.mkdirs(BEnvironment.getDataDir(packageName, userId));
        BzFileUtils.mkdirs(BEnvironment.getDataCacheDir(packageName, userId));
        BzFileUtils.mkdirs(BEnvironment.getDataFilesDir(packageName, userId));
        BzFileUtils.mkdirs(BEnvironment.getDataDatabasesDir(packageName, userId));
        BzFileUtils.mkdirs(BEnvironment.getDeDataDir(packageName, userId));








        return 0;
    }
}
