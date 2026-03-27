package com.onebitmonochrome.blacksbbox.data

import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.BlackBoxCore
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.app.AppManager
import com.onebitmonochrome.blacksbbox.bean.GmsBean
import com.onebitmonochrome.blacksbbox.bean.GmsInstallBean
import com.onebitmonochrome.blacksbbox.util.getString


class GmsRepository {


    fun getGmsInstalledList(mInstalledLiveData: MutableLiveData<List<GmsBean>>) {
        val userList = arrayListOf<GmsBean>()

        BlackBoxCore.get().users.forEach {
            val userId = it.id
            val userName =
                AppManager.mRemarkSharedPreferences.getString("Remark$userId", "User $userId") ?: ""
            val isInstalled = BlackBoxCore.get().isInstallGms(userId)
            val hasTraces = BlackBoxCore.get().hasGmsTraces(userId)
            val bean = GmsBean(userId, userName, isInstalled, hasTraces)
            userList.add(bean)
        }

        mInstalledLiveData.postValue(userList)
    }

    fun installGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        val installResult = BlackBoxCore.get().installGms(userID)

        val installedNow = BlackBoxCore.get().isInstallGms(userID)
        val hasTracesNow = BlackBoxCore.get().hasGmsTraces(userID)

        val result = if (installResult.success) {
            getString(R.string.install_success)
        } else {
            getString(R.string.install_fail, installResult.msg)
        }

        val bean = GmsInstallBean(userID, installResult.success, result, installedNow, hasTracesNow)
        mUpdateInstalledLiveData.postValue(bean)
    }

    fun uninstallGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        var isSuccess = false
        if (BlackBoxCore.get().isInstallGms(userID)) {
            isSuccess = BlackBoxCore.get().uninstallGms(userID)
        }

        val installedNow = BlackBoxCore.get().isInstallGms(userID)
        val hasTracesNow = BlackBoxCore.get().hasGmsTraces(userID)

        val result = if (isSuccess) {
            getString(R.string.uninstall_success)
        } else {
            getString(R.string.uninstall_fail)
        }

        val bean = GmsInstallBean(userID, isSuccess, result, installedNow, hasTracesNow)

        mUpdateInstalledLiveData.postValue(bean)
    }

    fun wipeGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        val isSuccess = BlackBoxCore.get().wipeGms(userID)

        val installedNow = BlackBoxCore.get().isInstallGms(userID)
        val hasTracesNow = BlackBoxCore.get().hasGmsTraces(userID)

        val result = if (isSuccess) {
            getString(R.string.uninstall_success)
        } else {
            getString(R.string.uninstall_fail)
        }

        val bean = GmsInstallBean(userID, isSuccess, result, installedNow, hasTracesNow)
        mUpdateInstalledLiveData.postValue(bean)
    }
}