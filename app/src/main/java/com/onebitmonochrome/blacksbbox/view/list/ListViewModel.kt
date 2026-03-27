package com.onebitmonochrome.blacksbbox.view.list

import androidx.lifecycle.MutableLiveData
import com.onebitmonochrome.blacksbbox.bean.InstalledAppBean
import com.onebitmonochrome.blacksbbox.data.AppsRepository
import com.onebitmonochrome.blacksbbox.view.base.BaseViewModel


class ListViewModel(private val repo: AppsRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<InstalledAppBean>>()

    val loadingLiveData = MutableLiveData<Boolean>()

    fun previewInstalledList() {
        launchOnUI { repo.previewInstallList() }
    }

    fun getInstallAppList(userID: Int) {
        launchOnUI { repo.getInstalledAppList(userID, loadingLiveData, appsLiveData) }
    }
}
