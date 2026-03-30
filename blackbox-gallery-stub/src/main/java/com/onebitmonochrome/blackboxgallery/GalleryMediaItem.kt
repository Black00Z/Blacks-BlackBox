package com.onebitmonochrome.blackboxgallery

import android.net.Uri

data class GalleryMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val isVideo: Boolean
)
