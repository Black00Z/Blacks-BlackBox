package com.onebitmonochrome.blacksbbox.view.gallery

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.app.AppManager
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.system.user.BUserInfo

object BlackBoxGalleryUserPicker {

    fun show(
        context: Context,
        onSelected: (Int) -> Unit,
        onCancelled: (() -> Unit)? = null
    ): Boolean {
        val users = BlackBoxCore.get().users
        if (users.isEmpty()) {
            return false
        }
        val labels = users.map { labelForUser(it) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.blackbox_gallery_choose_user)
            .setItems(labels) { _, which ->
                users.getOrNull(which)?.let { onSelected(it.id) }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancelled?.invoke() }
            .setOnCancelListener { onCancelled?.invoke() }
            .show()
        return true
    }

    fun subtitleForUser(userId: Int): String {
        return AppManager.mRemarkSharedPreferences.getString("Remark$userId", null)
            ?.takeIf { it.isNotBlank() }
            ?: "User $userId"
    }

    private fun labelForUser(user: BUserInfo): String {
        val remark = AppManager.mRemarkSharedPreferences.getString("Remark${user.id}", null)
            ?.takeIf { it.isNotBlank() }
        val fallbackName = user.name?.takeIf { it.isNotBlank() } ?: "User ${user.id}"
        return if (remark != null && remark != fallbackName) {
            "$remark ($fallbackName)"
        } else {
            remark ?: fallbackName
        }
    }
}
