package com.onebitmonochrome.blacksbbox.view.install

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.base.LoadingActivity
import com.onebitmonochrome.blacksbbox.view.gallery.BlackBoxGalleryUserPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore

class ApkInstallImportActivity : LoadingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apk_install_import)

        val uris = extractApkUris(intent)
        if (uris.isEmpty()) {
            toast(R.string.apk_install_import_empty)
            finish()
            return
        }

        Log.i(TAG, "Received APK install intent: action=${intent.action} type=${intent.type} uris=${uris.joinToString()}")

        chooseUserAndInstall(uris)
    }

    private fun chooseUserAndInstall(uris: List<Uri>) {
        val shown = BlackBoxGalleryUserPicker.show(
            context = this,
            onSelected = { userId ->
                installApks(userId, uris)
            },
            onCancelled = { finish() }
        )
        if (!shown) {
            toast(R.string.apk_install_import_failed)
            finish()
        }
    }

    private fun installApks(userId: Int, uris: List<Uri>) {
        showLoading()
        lifecycleScope.launch {
            val (successCount, lastError) = withContext(Dispatchers.IO) {
                var ok = 0
                var error: String? = null
                uris.forEach { uri ->
                    runCatching {
                        Log.i(TAG, "Installing APK: userId=$userId uri=$uri")
                        val result = BlackBoxCore.get().installPackageAsUser(uri, userId)
                        if (result.success) {
                            ok += 1
                        } else {
                            error = result.msg
                            Log.w(TAG, "Install failed: userId=$userId uri=$uri msg=${result.msg}")
                        }
                    }.onFailure {
                        error = it.message
                        Log.e(TAG, "Install exception: userId=$userId uri=$uri", it)
                    }
                }
                ok to error
            }

            hideLoading()

            if (successCount <= 0) {
                if (lastError.isNullOrBlank()) {
                    toast(R.string.install_fail_no_msg)
                } else {
                    toast(getString(R.string.install_fail, lastError))
                }
                finish()
                return@launch
            }

            toast(getString(R.string.apk_install_imported_count, successCount))
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun extractApkUris(intent: Intent): List<Uri> {
        val result = linkedSetOf<Uri>()

        intent.data?.let(result::add)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(result::add)
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.forEach(result::add)
        } else {
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let(result::add)
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach(result::add)
        }

        val clipData = intent.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index)?.uri?.let(result::add)
            }
        }

        return result.toList()
    }

    companion object {
        private const val TAG = "ApkInstallImport"
    }
}
