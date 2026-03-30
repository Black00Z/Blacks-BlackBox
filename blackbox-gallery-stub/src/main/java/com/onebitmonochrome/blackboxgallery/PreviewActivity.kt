package com.onebitmonochrome.blackboxgallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
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

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_IS_VIDEO = "extra_is_video"

        fun intentFor(context: Context, item: GalleryMediaItem): Intent {
            return Intent(context, PreviewActivity::class.java)
                .putExtra(EXTRA_URI, item.uri.toString())
                .putExtra(EXTRA_NAME, item.displayName)
                .putExtra(EXTRA_IS_VIDEO, item.isVideo)
        }
    }
}
