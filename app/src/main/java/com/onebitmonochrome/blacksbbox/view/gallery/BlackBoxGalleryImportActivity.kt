package com.onebitmonochrome.blacksbbox.view.gallery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.databinding.ActivityBlackboxGalleryImportBinding
import com.onebitmonochrome.blacksbbox.util.inflate
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.base.LoadingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.media.BlackBoxMediaStore

class BlackBoxGalleryImportActivity : LoadingActivity() {

    private val viewBinding: ActivityBlackboxGalleryImportBinding by inflate()
    private var hasStartedImport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        val uris = extractSharedUris(intent)
        if (uris.isEmpty()) {
            toast(R.string.blackbox_gallery_import_empty)
            finish()
            return
        }

        chooseUserAndImport(uris)
    }

    private fun chooseUserAndImport(uris: List<Uri>) {
        if (hasStartedImport) {
            return
        }
        val shown = BlackBoxGalleryUserPicker.show(
            context = this,
            onSelected = { userId ->
                hasStartedImport = true
                importMedia(userId, uris)
            },
            onCancelled = { finish() }
        )
        if (!shown) {
            toast(R.string.blackbox_gallery_import_failed)
            finish()
        }
    }

    private fun importMedia(userId: Int, uris: List<Uri>) {
        showLoading()
        lifecycleScope.launch {
            val importedCount = withContext(Dispatchers.IO) {
                var successCount = 0
                uris.forEach { uri ->
                    runCatching {
                        BlackBoxMediaStore.importMedia(this@BlackBoxGalleryImportActivity, userId, uri, contentResolver.getType(uri))
                    }.onSuccess {
                        successCount += 1
                    }
                }
                if (successCount > 0) {
                    BlackBoxMediaStore.rescanUser(userId)
                    BlackBoxGuestGalleryInstaller.ensureInstalled(this@BlackBoxGalleryImportActivity, userId)
                }
                successCount
            }
            hideLoading()
            if (importedCount <= 0) {
                toast(R.string.blackbox_gallery_import_failed)
                finish()
                return@launch
            }
            toast(getString(R.string.blackbox_gallery_imported_count, importedCount))
            BlackBoxGalleryActivity.start(this@BlackBoxGalleryImportActivity, userId)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSharedUris(intent: Intent): List<Uri> {
        val result = linkedSetOf<Uri>()
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
}
