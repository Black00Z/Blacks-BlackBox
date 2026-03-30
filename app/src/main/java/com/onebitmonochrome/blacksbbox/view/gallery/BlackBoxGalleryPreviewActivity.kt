package com.onebitmonochrome.blacksbbox.view.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import androidx.lifecycle.lifecycleScope
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.databinding.ActivityBlackboxGalleryPreviewBinding
import com.onebitmonochrome.blacksbbox.util.inflate
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.base.LoadingActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.media.BlackBoxMediaEntry
import top.niunaijun.blackbox.media.BlackBoxMediaStore

class BlackBoxGalleryPreviewActivity : LoadingActivity() {

    private val viewBinding: ActivityBlackboxGalleryPreviewBinding by inflate()
    private val userId by lazy { intent.getIntExtra(EXTRA_USER_ID, 0) }
    private val mediaId by lazy { intent.getLongExtra(EXTRA_MEDIA_ID, -1L) }
    private var currentEntry: BlackBoxMediaEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.blackbox_gallery_title, true)
        initActions()
        loadEntry()
    }

    private fun initActions() {
        viewBinding.openGuestGalleryButton.setOnClickListener {
            if (!BlackBoxGuestGalleryInstaller.launch(this, userId)) {
                toast(R.string.blackbox_gallery_open_guest_failed)
            }
        }
        viewBinding.deleteButton.setOnClickListener {
            val entry = currentEntry ?: return@setOnClickListener
            showLoading()
            lifecycleScope.launch {
                val deleted = withContext(Dispatchers.IO) {
                    BlackBoxMediaStore.delete(userId, entry.privateUri, null, null) > 0
                }
                hideLoading()
                if (deleted) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    toast(R.string.blackbox_gallery_delete_failed)
                }
            }
        }
    }

    private fun loadEntry() {
        showLoading()
        lifecycleScope.launch {
            val entry = withContext(Dispatchers.IO) {
                BlackBoxMediaStore.getEntry(userId, mediaId)
            }
            hideLoading()
            if (entry == null) {
                toast(R.string.blackbox_gallery_item_missing)
                finish()
                return@launch
            }
            currentEntry = entry
            bindEntry(entry)
        }
    }

    private fun bindEntry(entry: BlackBoxMediaEntry) {
        viewBinding.toolbarLayout.toolbar.subtitle = BlackBoxGalleryUserPicker.subtitleForUser(userId)
        if (entry.isVideo) {
            viewBinding.imageView.visibility = android.view.View.GONE
            viewBinding.videoView.visibility = android.view.View.VISIBLE
            viewBinding.videoView.setMediaController(MediaController(this).also { controller ->
                controller.setAnchorView(viewBinding.videoView)
            })
            viewBinding.videoView.setVideoURI(Uri.fromFile(File(entry.filePath)))
            viewBinding.videoView.start()
        } else {
            viewBinding.videoView.stopPlayback()
            viewBinding.videoView.visibility = android.view.View.GONE
            viewBinding.imageView.visibility = android.view.View.VISIBLE
            viewBinding.imageView.setImageURI(Uri.fromFile(File(entry.filePath)))
        }
    }

    companion object {
        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_MEDIA_ID = "extra_media_id"

        fun intentFor(context: Context, userId: Int, mediaId: Long): Intent {
            return Intent(context, BlackBoxGalleryPreviewActivity::class.java)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_MEDIA_ID, mediaId)
        }
    }
}
