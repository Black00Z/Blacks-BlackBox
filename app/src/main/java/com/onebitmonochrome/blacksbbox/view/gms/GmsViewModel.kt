package com.onebitmonochrome.blacksbbox.view.gms

import androidx.lifecycle.MutableLiveData
import com.onebitmonochrome.blacksbbox.bean.GmsBean
import com.onebitmonochrome.blacksbbox.bean.GmsInstallBean
import com.onebitmonochrome.blacksbbox.data.GmsRepository
import com.onebitmonochrome.blacksbbox.view.base.BaseViewModel


class GmsViewModel(private val mRepo: GmsRepository) : BaseViewModel() {

    val mInstalledLiveData = MutableLiveData<List<GmsBean>>()

    val mUpdateInstalledLiveData = MutableLiveData<GmsInstallBean>()

    fun getInstalledUser() {
        launchOnUI {
            mRepo.getGmsInstalledList(mInstalledLiveData)
        }
    }

    fun installGms(userID: Int) {
        launchOnUI {
            mRepo.installGms(userID,mUpdateInstalledLiveData)
        }
    }

    fun uninstallGms(userID: Int) {
        launchOnUI {
            mRepo.uninstallGms(userID,mUpdateInstalledLiveData)
        }
    }

    fun wipeGms(userID: Int) {
        launchOnUI {
            mRepo.wipeGms(userID, mUpdateInstalledLiveData)
        }
    }
}