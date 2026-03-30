package top.niunaijun.blackbox.media;

import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;

public final class BlackBoxMediaContract {
    public static final String PUBLIC_AUTHORITY = "media";
    public static final String PRIVATE_AUTHORITY_SUFFIX = ".blackbox.MediaProvider";
    public static final String USER_ID_QUERY_PARAM = "bbx_user_id";
    public static final String EXTERNAL_VOLUME = "external";

    public static final String ROOT_IMAGES = "DCIM/BlackBoxGallery/";
    public static final String ROOT_VIDEOS = "Movies/BlackBoxGallery/";

    public static final String TABLE_MEDIA = "media_entries";
    public static final String TABLE_SCAN_STATE = "scan_state";

    public static final String COL_ID = "_id";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_MEDIA_TYPE = MediaStore.Files.FileColumns.MEDIA_TYPE;
    public static final String COL_DISPLAY_NAME = MediaStore.MediaColumns.DISPLAY_NAME;
    public static final String COL_TITLE = MediaStore.MediaColumns.TITLE;
    public static final String COL_MIME_TYPE = MediaStore.MediaColumns.MIME_TYPE;
    public static final String COL_SIZE = MediaStore.MediaColumns.SIZE;
    public static final String COL_DATE_ADDED = MediaStore.MediaColumns.DATE_ADDED;
    public static final String COL_DATE_MODIFIED = MediaStore.MediaColumns.DATE_MODIFIED;
    public static final String COL_DATE_TAKEN = MediaStore.MediaColumns.DATE_TAKEN;
    public static final String COL_RELATIVE_PATH = MediaStore.MediaColumns.RELATIVE_PATH;
    public static final String COL_BUCKET_DISPLAY_NAME = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME;
    public static final String COL_BUCKET_ID = MediaStore.MediaColumns.BUCKET_ID;
    public static final String COL_WIDTH = MediaStore.MediaColumns.WIDTH;
    public static final String COL_HEIGHT = MediaStore.MediaColumns.HEIGHT;
    public static final String COL_DURATION = MediaStore.MediaColumns.DURATION;
    public static final String COL_IS_PENDING = MediaStore.MediaColumns.IS_PENDING;
    public static final String COL_DATA = MediaStore.MediaColumns.DATA;
    public static final String COL_CANONICAL_PATH = "canonical_path";
    public static final String COL_ROOT_PATH = "root_path";

    public static final String STATE_COL_USER_ID = "user_id";
    public static final String STATE_COL_ROOT_PATH = "root_path";
    public static final String STATE_COL_ROOT_LAST_MODIFIED = "root_last_modified";
    public static final String STATE_COL_FILE_COUNT = "file_count";
    public static final String STATE_COL_MAX_MODIFIED = "max_modified";

    private BlackBoxMediaContract() {
    }

    public static String getPrivateAuthority() {
        return BlackBoxCore.getHostPkg() + PRIVATE_AUTHORITY_SUFFIX;
    }

    public static Uri toPrivateUri(Uri uri, int userId) {
        if (uri == null) {
            return null;
        }
        return uri.buildUpon()
                .authority(getPrivateAuthority())
                .appendQueryParameter(USER_ID_QUERY_PARAM, String.valueOf(userId))
                .build();
    }

    public static Uri toPublicUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        Uri.Builder builder = uri.buildUpon().authority(PUBLIC_AUTHORITY).clearQuery();
        for (String name : uri.getQueryParameterNames()) {
            if (USER_ID_QUERY_PARAM.equals(name)) {
                continue;
            }
            for (String value : uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value);
            }
        }
        return builder.build();
    }

    public static int getUserId(Uri uri) {
        if (uri == null) {
            return 0;
        }
        String userId = uri.getQueryParameter(USER_ID_QUERY_PARAM);
        if (TextUtils.isEmpty(userId)) {
            return 0;
        }
        try {
            return Integer.parseInt(userId);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static int mediaTypeForMime(String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
        }
        if (mimeType.startsWith("image/")) {
            return MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        }
        if (mimeType.startsWith("video/")) {
            return MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        }
        return MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
    }

    public static String inferMimeType(File file) {
        if (file == null) {
            return null;
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (TextUtils.isEmpty(extension)) {
            int dotIndex = file.getName().lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < file.getName().length() - 1) {
                extension = file.getName().substring(dotIndex + 1);
            }
        }
        if (TextUtils.isEmpty(extension)) {
            return null;
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    }

    public static String defaultRelativePathForMediaType(int mediaType) {
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            return ROOT_VIDEOS;
        }
        return ROOT_IMAGES;
    }

    public static boolean isSupportedMime(String mimeType) {
        return mediaTypeForMime(mimeType) != MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
    }

    public static boolean isImagesUri(Uri uri) {
        return uri != null
                && uri.getPathSegments().size() >= 3
                && EXTERNAL_VOLUME.equals(uri.getPathSegments().get(0))
                && "images".equals(uri.getPathSegments().get(1))
                && "media".equals(uri.getPathSegments().get(2));
    }

    public static boolean isVideosUri(Uri uri) {
        return uri != null
                && uri.getPathSegments().size() >= 3
                && EXTERNAL_VOLUME.equals(uri.getPathSegments().get(0))
                && "video".equals(uri.getPathSegments().get(1))
                && "media".equals(uri.getPathSegments().get(2));
    }

    public static boolean isFilesUri(Uri uri) {
        return uri != null
                && uri.getPathSegments().size() >= 2
                && EXTERNAL_VOLUME.equals(uri.getPathSegments().get(0))
                && "file".equals(uri.getPathSegments().get(1));
    }

    public static long getIdFromUri(Uri uri) {
        if (uri == null) {
            return -1L;
        }
        try {
            return Long.parseLong(uri.getLastPathSegment());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    public static Uri buildPublicCollectionUri(int mediaType) {
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        return MediaStore.Files.getContentUri(EXTERNAL_VOLUME);
    }

    public static Uri buildPrivateCollectionUri(int mediaType, int userId) {
        return toPrivateUri(buildPublicCollectionUri(mediaType), userId);
    }

    public static Uri buildPrivateItemUri(int mediaType, long id, int userId) {
        return Uri.withAppendedPath(buildPrivateCollectionUri(mediaType, userId), String.valueOf(id));
    }

    public static Uri buildPublicItemUri(int mediaType, long id) {
        return Uri.withAppendedPath(buildPublicCollectionUri(mediaType), String.valueOf(id));
    }
}
