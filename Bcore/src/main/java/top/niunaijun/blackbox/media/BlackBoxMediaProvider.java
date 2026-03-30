package top.niunaijun.blackbox.media;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BlackBoxMediaProvider extends ContentProvider {
    public static final String METHOD_RESCAN = "rescan";
    public static final String EXTRA_USER_ID = "user_id";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri,
                        @Nullable String[] projection,
                        @Nullable String selection,
                        @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        return BlackBoxMediaStore.query(BlackBoxMediaContract.getUserId(uri), uri, projection, selection, selectionArgs, sortOrder);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return BlackBoxMediaStore.getType(BlackBoxMediaContract.getUserId(uri), uri);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        try {
            Uri insertedUri = BlackBoxMediaStore.insert(BlackBoxMediaContract.getUserId(uri), uri, values != null ? values : new ContentValues());
            notifyMediaChange(uri);
            return insertedUri;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int deleted = BlackBoxMediaStore.delete(BlackBoxMediaContract.getUserId(uri), uri, selection, selectionArgs);
        if (deleted > 0) {
            notifyMediaChange(uri);
        }
        return deleted;
    }

    @Override
    public int update(@NonNull Uri uri,
                      @Nullable ContentValues values,
                      @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        int updated = BlackBoxMediaStore.update(BlackBoxMediaContract.getUserId(uri), uri, values != null ? values : new ContentValues(), selection, selectionArgs);
        if (updated > 0) {
            notifyMediaChange(uri);
        }
        return updated;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
        try {
            return BlackBoxMediaStore.openFile(BlackBoxMediaContract.getUserId(uri), uri, mode);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (METHOD_RESCAN.equals(method)) {
            int userId = extras != null ? extras.getInt(EXTRA_USER_ID, 0) : 0;
            BlackBoxMediaStore.rescanUser(userId);
            Bundle result = new Bundle();
            result.putBoolean("success", true);
            return result;
        }
        return super.call(method, arg, extras);
    }

    private void notifyMediaChange(Uri privateUri) {
        if (getContext() == null) {
            return;
        }
        getContext().getContentResolver().notifyChange(privateUri, null);
        Uri publicUri = BlackBoxMediaContract.toPublicUri(privateUri);
        getContext().getContentResolver().notifyChange(publicUri, null);
        getContext().getContentResolver().notifyChange(MediaStore.Files.getContentUri("external"), null);
        getContext().getContentResolver().notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null);
        getContext().getContentResolver().notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
    }
}
