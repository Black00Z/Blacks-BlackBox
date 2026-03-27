package top.niunaijun.blackbox.core.system.pm.installer;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.BzFileUtils;


public class CreatePackageExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        BzFileUtils.deleteDir(BEnvironment.getAppDir(ps.pkg.packageName));

        
        BzFileUtils.mkdirs(BEnvironment.getAppDir(ps.pkg.packageName));
        BzFileUtils.mkdirs(BEnvironment.getAppLibDir(ps.pkg.packageName));
        return 0;
    }
}
