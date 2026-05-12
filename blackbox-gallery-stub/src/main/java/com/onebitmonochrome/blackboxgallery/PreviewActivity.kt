package com.onebitmonochrome.blackboxgallery

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.onebitmonochrome.blackboxgallery.databinding.ActivityPreviewBinding

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private lateinit var mediaUri: Uri
    private var isVideo: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        mediaUri = Uri.parse(intent.getStringExtra(EXTRA_URI))
        isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        supportActionBar?.title = intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.app_name)

        bindMedia()
        binding.shareButton.setOnClickListener {
            shareInsideBlackBox()
        }
        binding.deleteButton.setOnClickListener {
            val deleted = contentResolver.delete(mediaUri, null, null) > 0
            if (deleted) {
                setResult(RESULT_OK)
                finish()
            } else {
                Snackbar.make(binding.root, R.string.delete_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindMedia() {
        if (isVideo) {
            binding.imageView.visibility = android.view.View.GONE
            binding.videoView.visibility = android.view.View.VISIBLE
            binding.videoView.setMediaController(MediaController(this).also { controller ->
                controller.setAnchorView(binding.videoView)
            })
            binding.videoView.setVideoURI(mediaUri)
            binding.videoView.start()
        } else {
            binding.videoView.stopPlayback()
            binding.videoView.visibility = android.view.View.GONE
            binding.imageView.visibility = android.view.View.VISIBLE
            binding.imageView.setImageURI(mediaUri)
        }
    }

    private fun shareInsideBlackBox() {
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
            ?: if (isVideo) "video/*" else "image/*"
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, mediaUri)
            clipData = ClipData.newUri(contentResolver, "media", mediaUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val targets = packageManager.queryIntentActivities(baseIntent, 0)
            .filter { it.activityInfo?.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (targets.isEmpty()) {
            Snackbar.make(binding.root, R.string.share_no_targets, Snackbar.LENGTH_SHORT).show()
            return
        }

        val labels = targets.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.share_to_app)
            .setItems(labels) { _, which ->
                startShareTarget(baseIntent, targets[which])
            }
            .show()
    }

    private fun startShareTarget(baseIntent: Intent, target: ResolveInfo) {
        val info = target.activityInfo ?: return
        val shareIntent = Intent(baseIntent).apply {
            setClassName(info.packageName, info.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, R.string.share_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_IS_VIDEO = "extra_is_video"
        private const val EXTRA_MIME_TYPE = "extra_mime_type"

        fun intentFor(context: Context, item: GalleryMediaItem): Intent {
            return Intent(context, PreviewActivity::class.java)
                .putExtra(EXTRA_URI, item.uri.toString())
                .putExtra(EXTRA_NAME, item.displayName)
                .putExtra(EXTRA_IS_VIDEO, item.isVideo)
                .putExtra(EXTRA_MIME_TYPE, item.mimeType)
        }
    }
}
