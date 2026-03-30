package com.onebitmonochrome.blacksbbox.view.gallery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.databinding.ActivityBlackboxGalleryBinding
import com.onebitmonochrome.blacksbbox.util.inflate
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.base.LoadingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.media.BlackBoxMediaStore

class BlackBoxGalleryActivity : LoadingActivity() {

    private val viewBinding: ActivityBlackboxGalleryBinding by inflate()
    private val adapter = BlackBoxGalleryAdapter { entry ->
        previewLauncher.launch(intentForEntry(entry.id))
    }
    private val userId by lazy { currentUserID() }
    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadMedia(forceRescan = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.blackbox_gallery_title, true)
        viewBinding.toolbarLayout.toolbar.subtitle = BlackBoxGalleryUserPicker.subtitleForUser(userId)
        viewBinding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        viewBinding.recyclerView.adapter = adapter
        viewBinding.refreshButton.setOnClickListener { loadMedia(forceRescan = true) }
        viewBinding.openGuestGalleryButton.setOnClickListener {
            if (!BlackBoxGuestGalleryInstaller.launch(this, userId)) {
                toast(R.string.blackbox_gallery_open_guest_failed)
            }
        }
        loadMedia(forceRescan = true)
    }

    override fun onResume() {
        super.onResume()
        loadMedia(forceRescan = false)
    }

    private fun loadMedia(forceRescan: Boolean) {
        if (forceRescan) {
            showLoading()
        }
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                if (forceRescan) {
                    BlackBoxMediaStore.rescanUser(userId)
                }
                BlackBoxMediaStore.listMediaEntries(userId)
            }
            if (forceRescan) {
                hideLoading()
            }
            adapter.submit(entries)
            viewBinding.emptyView.visibility =
                if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun intentForEntry(mediaId: Long): Intent {
        return BlackBoxGalleryPreviewActivity.intentFor(this, userId, mediaId)
    }

    companion object {
        fun start(context: Context, userId: Int) {
            context.startActivity(
                Intent(context, BlackBoxGalleryActivity::class.java)
                    .putExtra("userID", userId)
            )
        }
    }
}
