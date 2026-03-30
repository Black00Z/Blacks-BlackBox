package com.onebitmonochrome.blackboxgallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.onebitmonochrome.blackboxgallery.databinding.ItemMediaBinding

class GalleryMediaAdapter(
    private val onClicked: (GalleryMediaItem) -> Unit
) : RecyclerView.Adapter<GalleryMediaAdapter.MediaViewHolder>() {

    private val items = mutableListOf<GalleryMediaItem>()

    fun submit(newItems: List<GalleryMediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        return MediaViewHolder(
            ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position], onClicked)
    }

    override fun getItemCount(): Int = items.size

    class MediaViewHolder(
        private val binding: ItemMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GalleryMediaItem, onClicked: (GalleryMediaItem) -> Unit) {
            binding.fileNameView.text = item.displayName
            binding.videoBadge.visibility = if (item.isVideo) android.view.View.VISIBLE else android.view.View.GONE
            if (item.isVideo) {
                binding.thumbnailView.setImageResource(R.drawable.ic_video_placeholder)
            } else {
                binding.thumbnailView.setImageBitmap(loadBitmap(item))
            }
            binding.root.setOnClickListener { onClicked(item) }
        }

        private fun loadBitmap(item: GalleryMediaItem): Bitmap? {
            return binding.root.context.contentResolver.openInputStream(item.uri)?.use { stream ->
                val bytes = stream.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 320, 320)
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
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
