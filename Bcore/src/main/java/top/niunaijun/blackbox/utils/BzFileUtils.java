package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.system.Os;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BzFileUtils {

    private static final int IO_BUFFER_SIZE = 8192;

    public static int count(File file) {
        if (file == null || !file.exists()) {
            return -1;
        }
        if (file.isFile()) {
            return 1;
        }
        if (!file.isDirectory()) {
            return 0;
        }
        String[] children = file.list();
        return children == null ? 0 : children.length;
    }

    public static String getFilenameExt(String filename) {
        if (filename == null) {
            return "";
        }
        int dotPos = filename.lastIndexOf('.');
        return dotPos < 0 ? "" : filename.substring(dotPos + 1);
    }

    public static File changeExt(File f, String targetExt) {
        if (f == null) {
            return null;
        }
        String path = f.getAbsolutePath();
        String ext = getFilenameExt(path);
        if (targetExt != null && targetExt.equals(ext)) {
            return f;
        }

        int lastDot = path.lastIndexOf('.');
        String base = lastDot > 0 ? path.substring(0, lastDot) : path;
        String outPath = (targetExt == null || targetExt.isEmpty())
                ? base
                : base + "." + targetExt;
        return new File(outPath);
    }

    public static boolean renameTo(File origFile, File newFile) {
        return origFile != null && newFile != null && origFile.renameTo(newFile);
    }

    public static String readToString(String fileName) throws IOException {
        try (InputStream input = new FileInputStream(fileName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pump(input, out);
            return out.toString();
        }
    }

    public static Parcel readToParcel(File file) throws IOException {
        Parcel in = Parcel.obtain();
        byte[] bytes = toByteArray(file);
        in.unmarshall(bytes, 0, bytes.length);
        in.setDataPosition(0);
        return in;
    }

    
    public static void chmod(String path, int mode) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.chmod(path, mode);
                return;
            } catch (Exception e) {
                
            }
        }

        File file = new File(path);
        String cmd = "chmod ";
        if (file.isDirectory()) {
            cmd += " -R ";
        }
        String cmode = String.format("%o", mode);
        Runtime.getRuntime().exec(cmd + cmode + " " + path).waitFor();
    }

    public static void createSymlink(String oldPath, String newPath) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.link(oldPath, newPath);
                return;
            } catch (Throwable e) {
                
            }
        }
        Runtime.getRuntime().exec("ln -s " + oldPath + " " + newPath).waitFor();
    }

    public static boolean isSymlink(File file) throws IOException {
        if (file == null)
            throw new NullPointerException("File must not be null");
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = file.getParentFile().getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    public static void writeParcelToFile(Parcel p, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(p.marshall());
        }
    }

    public static void writeParcelToOutput(Parcel p, FileOutputStream fos) throws IOException {
        fos.write(p.marshall());
    }

    public static byte[] toByteArray(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return toByteArray(in);
        }
    }

    public static byte[] toByteArray(InputStream inStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pump(inStream, out);
        return out.toByteArray();
    }

    public static int deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }

        int deleted = 0;
        if (dir.isDirectory() && !isDirectorySymlinkQuietly(dir)) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleted += deleteDir(child);
                }
            }
        }

        if (dir.delete()) {
            deleted++;
        }
        return deleted;
    }

    public static int deleteDir(String dir) {
        return deleteDir(new File(dir));
    }

    public static void writeToFile(InputStream dataIns, File target) throws IOException {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            pump(dataIns, output);
        }
    }

    public static void writeToFile(byte[] data, File target) throws IOException {
        try (ReadableByteChannel src = Channels.newChannel(new ByteArrayInputStream(data));
             FileOutputStream fo = new FileOutputStream(target);
             FileChannel out = fo.getChannel()) {
            long transferred = 0L;
            while (transferred < data.length) {
                transferred += out.transferFrom(src, transferred, data.length - transferred);
            }
        }
    }

    public static void copyFile(InputStream inputStream, File target) {
        if (inputStream == null || target == null) {
            return;
        }
        try (OutputStream output = new FileOutputStream(target)) {
            pump(inputStream, output);
            output.flush();
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(inputStream);
        }
    }

    public static void copyFile(File source, File target) throws IOException {
        if (source == null || target == null) {
            return;
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target);
             FileChannel src = in.getChannel();
             FileChannel dst = out.getChannel()) {
            long size = src.size();
            long pos = 0L;
            while (pos < size) {
                long copied = src.transferTo(pos, size - pos, dst);
                if (copied <= 0) {
                    break;
                }
                pos += copied;
            }
        }
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static int peekInt(byte[] bytes, int value, ByteOrder endian) {
        int v2;
        int v0;
        if (endian == ByteOrder.BIG_ENDIAN) {
            v0 = value + 1;
            v2 = v0 + 1;
            v0 = (bytes[v0] & 255) << 16 | (bytes[value] & 255) << 24 | (bytes[v2] & 255) << 8 | bytes[v2 + 1] & 255;
        } else {
            v0 = value + 1;
            v2 = v0 + 1;
            v0 = (bytes[v0] & 255) << 8 | bytes[value] & 255 | (bytes[v2] & 255) << 16 | (bytes[v2 + 1] & 255) << 24;
        }

        return v0;
    }

    private static void pump(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[IO_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static boolean isDirectorySymlinkQuietly(File dir) {
        try {
            return isSymlink(dir);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isValidExtFilenameChar(char c) {
        switch (c) {
            case '\0':
            case '/':
                return false;
            default:
                return true;
        }
    }

    
    public static boolean isValidExtFilename(String name) {
        return (name != null) && name.equals(buildValidExtFilename(name));
    }

    
    public static String buildValidExtFilename(String name) {
        if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) {
            return "(invalid)";
        }
        final StringBuilder res = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (isValidExtFilenameChar(c)) {
                res.append(c);
            } else {
                res.append('_');
            }
        }
        return res.toString();
    }

    public static void mkdirs(File path) {
        if (path != null && !path.exists()) {
            path.mkdirs();
        }
    }

    public static void mkdirs(String path) {
        mkdirs(new File(path));
    }

    public static boolean isExist(String path) {
        return new File(path).exists();
    }

    public static boolean canRead(String path) {
        return new File(path).canRead();
    }

    public interface FileMode {
        int MODE_ISUID = 04000;
        int MODE_ISGID = 02000;
        int MODE_ISVTX = 01000;
        int MODE_IRUSR = 00400;
        int MODE_IWUSR = 00200;
        int MODE_IXUSR = 00100;
        int MODE_IRGRP = 00040;
        int MODE_IWGRP = 00020;
        int MODE_IXGRP = 00010;
        int MODE_IROTH = 00004;
        int MODE_IWOTH = 00002;
        int MODE_IXOTH = 00001;

        int MODE_755 = MODE_IRUSR | MODE_IWUSR | MODE_IXUSR
                | MODE_IRGRP | MODE_IXGRP
                | MODE_IROTH | MODE_IXOTH;
    }

    
    public static class FileLock {
        private static FileLock singleton;
        private final Map<String, FileLockCount> refCounts = new ConcurrentHashMap<String, FileLockCount>();

        public static FileLock getInstance() {
            if (singleton == null) {
                singleton = new FileLock();
            }
            return singleton;
        }

        private int incRef(String filePath, java.nio.channels.FileLock fileLock, RandomAccessFile raf, FileChannel channel) {
            FileLockCount existing = refCounts.get(filePath);
            if (existing != null) {
                int prev = existing.mRefCount;
                existing.mRefCount = prev + 1;
                return prev;
            }
            refCounts.put(filePath, new FileLockCount(fileLock, 1, raf, channel));
            return 1;
        }

        private int decRef(String filePath) {
            FileLockCount existing = refCounts.get(filePath);
            if (existing == null) {
                return 0;
            }
            int next = existing.mRefCount - 1;
            existing.mRefCount = next;
            if (next <= 0) {
                refCounts.remove(filePath);
            }
            return next;
        }

        public boolean LockExclusive(File targetFile) {

            if (targetFile == null) {
                return false;
            }
            try {
                File lockFile = new File(targetFile.getParentFile().getAbsolutePath().concat("/lock"));
                if (!lockFile.exists()) {
                    lockFile.createNewFile();
                }
                RandomAccessFile randomAccessFile = new RandomAccessFile(lockFile.getAbsolutePath(), "rw");
                FileChannel channel = randomAccessFile.getChannel();
                java.nio.channels.FileLock lock = channel.lock();
                if (!lock.isValid()) {
                    return false;
                }
                incRef(lockFile.getAbsolutePath(), lock, randomAccessFile, channel);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        
        public void unLock(File targetFile) {

            File lockFile = new File(targetFile.getParentFile().getAbsolutePath().concat("/lock"));
            if (!lockFile.exists()) {
                return;
            }
            if (this.refCounts.containsKey(lockFile.getAbsolutePath())) {
                FileLockCount fileLockCount = this.refCounts.get(lockFile.getAbsolutePath());
                if (fileLockCount != null) {
                    java.nio.channels.FileLock fileLock = fileLockCount.mFileLock;
                    RandomAccessFile randomAccessFile = fileLockCount.fOs;
                    FileChannel fileChannel = fileLockCount.fChannel;
                    try {
                        if (decRef(lockFile.getAbsolutePath()) <= 0) {
                            if (fileLock != null && fileLock.isValid()) {
                                fileLock.release();
                            }
                            if (randomAccessFile != null) {
                                randomAccessFile.close();
                            }
                            if (fileChannel != null) {
                                fileChannel.close();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private class FileLockCount {
            FileChannel fChannel;
            RandomAccessFile fOs;
            java.nio.channels.FileLock mFileLock;
            int mRefCount;

            FileLockCount(java.nio.channels.FileLock fileLock, int mRefCount, RandomAccessFile fOs,
                          FileChannel fChannel) {
                this.mFileLock = fileLock;
                this.mRefCount = mRefCount;
                this.fOs = fOs;
                this.fChannel = fChannel;
            }
        }
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}
