package com.onebitmonochrome.blacksbbox.view.fake

import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.entity.location.BLocation
import com.onebitmonochrome.blacksbbox.bean.FakeLocationBean
import com.onebitmonochrome.blacksbbox.data.FakeLocationRepository
import com.onebitmonochrome.blacksbbox.view.base.BaseViewModel


class FakeLocationViewModel(private val mRepo: FakeLocationRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<FakeLocationBean>>()


    fun getInstallAppList(userID: Int) {
        launchOnUI {
            mRepo.getInstalledAppList(userID, appsLiveData)
        }
    }

    fun setPattern(userId: Int, pkg: String, pattern: Int) {
        launchOnUI {
            mRepo.setPattern(userId, pkg, pattern)
        }
    }

    fun setLocation(userId: Int, pkg: String, location: BLocation) {
        launchOnUI {
            mRepo.setLocation(userId, pkg, location)
        }
    }

}