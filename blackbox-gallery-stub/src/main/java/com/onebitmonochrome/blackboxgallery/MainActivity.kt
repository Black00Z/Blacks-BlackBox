package com.onebitmonochrome.blackboxgallery

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.onebitmonochrome.blackboxgallery.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = GalleryMediaAdapter { item ->
        previewLauncher.launch(PreviewActivity.intentFor(this, item))
    }
    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadMedia()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter
        binding.refreshButton.setOnClickListener { loadMedia() }
        loadMedia()
    }

    override fun onResume() {
        super.onResume()
        loadMedia()
    }

    private fun loadMedia() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                queryMedia()
            }
            adapter.submit(items)
            binding.emptyView.visibility =
                if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun queryMedia(): List<GalleryMediaItem> {
        val items = mutableListOf<GalleryMediaItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC, ${MediaStore.Files.FileColumns._ID} DESC"
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val mediaType = cursor.getInt(mediaTypeIndex)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                items += GalleryMediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(baseUri, id),
                    displayName = cursor.getString(nameIndex) ?: "media_$id",
                    mimeType = cursor.getString(mimeIndex),
                    isVideo = isVideo
                )
            }
        }
        return items
    }
}
