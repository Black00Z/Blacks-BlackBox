package top.niunaijun.blackbox.core.system.pm.installer;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.BzFileUtils;


public class RemoveUserExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        
        BzFileUtils.deleteDir(BEnvironment.getDataDir(packageName, userId));
        BzFileUtils.deleteDir(BEnvironment.getDeDataDir(packageName, userId));
        BzFileUtils.deleteDir(BEnvironment.getExternalDataDir(packageName, userId));
        return 0;
    }
}
