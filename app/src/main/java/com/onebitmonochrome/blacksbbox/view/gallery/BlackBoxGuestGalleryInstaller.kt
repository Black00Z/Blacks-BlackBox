package com.onebitmonochrome.blacksbbox.view.gallery

import android.content.Context
import java.io.File
import top.niunaijun.blackbox.BlackBoxCore

object BlackBoxGuestGalleryInstaller {
    private const val PACKAGE_NAME = "com.onebitmonochrome.blackboxgallery"
    private const val ASSET_NAME = "blackbox-gallery-stub.apk"

    fun ensureInstalled(context: Context, userId: Int): Boolean {
        val blackBoxCore = BlackBoxCore.get()
        if (blackBoxCore.isInstalled(PACKAGE_NAME, userId)) {
            return true
        }
        val tempApk = File(context.cacheDir, ASSET_NAME)
        return try {
            context.assets.open(ASSET_NAME).use { input ->
                tempApk.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val result = blackBoxCore.installPackageAsUser(tempApk, userId)
            result.success
        } catch (_: Exception) {
            false
        } finally {
            tempApk.delete()
        }
    }

    fun launch(context: Context, userId: Int): Boolean {
        if (!ensureInstalled(context, userId)) {
            return false
        }
        return BlackBoxCore.get().launchApk(PACKAGE_NAME, userId)
    }
}
