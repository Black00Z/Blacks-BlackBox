package top.niunaijun.blackbox.media;

import android.net.Uri;
import android.provider.MediaStore;

public class BlackBoxMediaEntry {
    private final long id;
    private final int userId;
    private final int mediaType;
    private final String displayName;
    private final String title;
    private final String mimeType;
    private final long size;
    private final long dateAdded;
    private final long dateModified;
    private final long dateTaken;
    private final String relativePath;
    private final String bucketDisplayName;
    private final int bucketId;
    private final int width;
    private final int height;
    private final long duration;
    private final int isPending;
    private final String filePath;
    private final String canonicalPath;
    private final String rootPath;

    public BlackBoxMediaEntry(long id,
                              int userId,
                              int mediaType,
                              String displayName,
                              String title,
                              String mimeType,
                              long size,
                              long dateAdded,
                              long dateModified,
                              long dateTaken,
                              String relativePath,
                              String bucketDisplayName,
                              int bucketId,
                              int width,
                              int height,
                              long duration,
                              int isPending,
                              String filePath,
                              String canonicalPath,
                              String rootPath) {
        this.id = id;
        this.userId = userId;
        this.mediaType = mediaType;
        this.displayName = displayName;
        this.title = title;
        this.mimeType = mimeType;
        this.size = size;
        this.dateAdded = dateAdded;
        this.dateModified = dateModified;
        this.dateTaken = dateTaken;
        this.relativePath = relativePath;
        this.bucketDisplayName = bucketDisplayName;
        this.bucketId = bucketId;
        this.width = width;
        this.height = height;
        this.duration = duration;
        this.isPending = isPending;
        this.filePath = filePath;
        this.canonicalPath = canonicalPath;
        this.rootPath = rootPath;
    }

    public long getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public int getMediaType() {
        return mediaType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTitle() {
        return title;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public long getDateModified() {
        return dateModified;
    }

    public long getDateTaken() {
        return dateTaken;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getBucketDisplayName() {
        return bucketDisplayName;
    }

    public int getBucketId() {
        return bucketId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getDuration() {
        return duration;
    }

    public int getIsPending() {
        return isPending;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getCanonicalPath() {
        return canonicalPath;
    }

    public String getRootPath() {
        return rootPath;
    }

    public boolean isVideo() {
        return mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
    }

    public Uri getPublicUri() {
        return BlackBoxMediaContract.buildPublicItemUri(mediaType, id);
    }

    public Uri getPrivateUri() {
        return BlackBoxMediaContract.buildPrivateItemUri(mediaType, id, userId);
    }
}
