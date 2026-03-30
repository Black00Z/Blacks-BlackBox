package com.onebitmonochrome.blacksbbox.view.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.onebitmonochrome.blacksbbox.databinding.ItemBlackboxGalleryMediaBinding
import java.io.File
import top.niunaijun.blackbox.media.BlackBoxMediaEntry

class BlackBoxGalleryAdapter(
    private val onClicked: (BlackBoxMediaEntry) -> Unit
) : RecyclerView.Adapter<BlackBoxGalleryAdapter.MediaViewHolder>() {

    private val items = mutableListOf<BlackBoxMediaEntry>()

    fun submit(entries: List<BlackBoxMediaEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemBlackboxGalleryMediaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position], onClicked)
    }

    override fun getItemCount(): Int = items.size

    class MediaViewHolder(
        private val binding: ItemBlackboxGalleryMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: BlackBoxMediaEntry, onClicked: (BlackBoxMediaEntry) -> Unit) {
            binding.fileNameView.text = entry.displayName
            binding.videoBadge.visibility = if (entry.isVideo) android.view.View.VISIBLE else android.view.View.GONE
            binding.thumbnailView.setImageBitmap(loadThumbnail(entry))
            binding.root.setOnClickListener { onClicked(entry) }
        }

        private fun loadThumbnail(entry: BlackBoxMediaEntry): Bitmap? {
            val file = File(entry.filePath)
            if (!file.exists()) {
                return null
            }
            return if (entry.isVideo) {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 320, 320)
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
        }

        private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
            var sampleSize = 1
            var currentWidth = width
            var currentHeight = height
            while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
                currentWidth /= 2
                currentHeight /= 2
                sampleSize *= 2
            }
            return sampleSize.coerceAtLeast(1)
        }
    }
}
