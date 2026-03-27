package com.onebitmonochrome.blacksbbox.view.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.onebitmonochrome.blacksbbox.data.AppsRepository


@Suppress("UNCHECKED_CAST")
class ListFactory(private val appsRepository: AppsRepository) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ListViewModel(appsRepository) as T
    }
}