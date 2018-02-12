package com.snatik.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import com.snatik.storage.helpers.ImmutablePair;
import com.snatik.storage.helpers.SizeUnit;
import com.snatik.storage.security.SecurityUtil;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Cipher;

/**
 * Common class for internal and external storage implementations
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
public class Storage {

    private static final String TAG = "Storage";

    private final Context mContext;
    private EncryptConfiguration mConfiguration;

    public Storage(Context context) {
        mContext = context;
    }

    public void setEncryptConfiguration(EncryptConfiguration configuration) {
        mConfiguration = configuration;
    }

    public String getExternalStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public String getExternalStorageDirectory(String publicDirectory) {
        return Environment.getExternalStoragePublicDirectory(publicDirectory).getAbsolutePath();
    }

    public String getInternalRootDirectory() {
        return Environment.getRootDirectory().getAbsolutePath();
    }

    public String getInternalFilesDirectory() {
        return mContext.getFilesDir().getAbsolutePath();
    }

    public String getInternalCacheDirectory() {
        return mContext.getCacheDir().getAbsolutePath();
    }

    public static boolean isExternalWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public boolean createDirectory(String path) {
        File directory = new File(path);
        if (directory.exists()) {
            Log.w(TAG, "Directory '" + path + "' already exists");
            return false;
        }
        return directory.mkdirs();
    }

    public boolean createDirectory(String path, boolean override) {

        // Check if directory exists. If yes, then delete all directory
        if (override && isDirectoryExists(path)) {
            deleteDirectory(path);
        }

        // Create new directory
        return createDirectory(path);
    }

    public boolean deleteDirectory(String path) {
        return deleteDirectoryImpl(path);
    }

    public boolean isDirectoryExists(String path) {
        return new File(path).exists();
    }

    public boolean createFile(String path, String content) {
        return createFile(path, content.getBytes());
    }

    public boolean createFile(String path, Storable storable) {
        return createFile(path, storable.getBytes());
    }

    public boolean createFile(String path, byte[] content) {
        try {
            OutputStream stream = new FileOutputStream(new File(path));

            // encrypt if needed
            if (mConfiguration != null && mConfiguration.isEncrypted()) {
                content = encrypt(content, Cipher.ENCRYPT_MODE);
            }

            stream.write(content);
            stream.flush();
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed create file", e);
            return false;
        }
        return true;
    }

    public boolean createFile(String path, Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return createFile(path, byteArray);
    }

    public boolean deleteFile(String path) {
        File file = new File(path);
        return file.delete();
    }

    public boolean isFileExist(String path) {
        return new File(path).exists();
    }

    public byte[] readFile(String path) {
        final FileInputStream stream;
        try {
            stream = new FileInputStream(new File(path));
            return readFile(stream);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to read file to input stream", e);
            return null;
        }
    }

    public String readTextFile(String path) {
        byte[] bytes = readFile(path);
        return new String(bytes);
    }

    public void appendFile(String path, String content) {
        appendFile(path, content.getBytes());
    }

    public void appendFile(String path, byte[] bytes) {
        if (!isFileExist(path)) {
            Log.w(TAG, "Impossible to append content, because such file doesn't exist");
            return;
        }

        try {
            FileOutputStream stream = new FileOutputStream(new File(path), true);
            stream.write(bytes);
            stream.write(System.getProperty("line.separator").getBytes());
            stream.flush();
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to append content to file", e);
        }
    }

    public List<File> getNestedFiles(String path) {
        File file = new File(path);
        List<File> out = new ArrayList<File>();
        getDirectoryFilesImpl(file, out);
        return out;
    }

    public List<File> getFiles(String dir) {
        return getFiles(dir, null);
    }

    public List<File> getFiles(String dir, final String matchRegex) {
        File file = new File(dir);
        File[] files = null;
        if (matchRegex != null) {
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String fileName) {
                    return fileName.matches(matchRegex);
                }
            };
            files = file.listFiles(filter);
        } else {
            files = file.listFiles();
        }
        return files != null ? Arrays.asList(files) : null;
    }

    public File getFile(String path) {
        return new File(path);
    }

    public boolean rename(String fromPath, String toPath) {
        File file = getFile(fromPath);
        File newFile = new File(toPath);
        return file.renameTo(newFile);
    }

    public double getSize(File file, SizeUnit unit) {
        long length = file.length();
        return (double) length / (double) unit.inBytes();
    }

    public String getReadableSize(File file) {
        long length = file.length();
        return SizeUnit.readableSizeUnit(length);
    }

    public long getFreeSpace(String dir, SizeUnit sizeUnit) {
        StatFs statFs = new StatFs(dir);
        long availableBlocks;
        long blockSize;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            availableBlocks = statFs.getAvailableBlocks();
            blockSize = statFs.getBlockSize();
        } else {
            availableBlocks = statFs.getAvailableBlocksLong();
            blockSize = statFs.getBlockSizeLong();
        }
        long freeBytes = availableBlocks * blockSize;
        return freeBytes / sizeUnit.inBytes();
    }

    public long getUsedSpace(String dir, SizeUnit sizeUnit) {
        StatFs statFs = new StatFs(dir);
        long availableBlocks;
        long blockSize;
        long totalBlocks;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            availableBlocks = statFs.getAvailableBlocks();
            blockSize = statFs.getBlockSize();
            totalBlocks = statFs.getBlockCount();
        } else {
            availableBlocks = statFs.getAvailableBlocksLong();
            blockSize = statFs.getBlockSizeLong();
            totalBlocks = statFs.getBlockCountLong();
        }
        long usedBytes = totalBlocks * blockSize - availableBlocks * blockSize;
        return usedBytes / sizeUnit.inBytes();
    }

    public boolean copy(String fromPath, String toPath) {
        File file = getFile(fromPath);
        if (!file.isFile()) {
            return false;
        }

        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        try {
            inStream = new FileInputStream(file);
            outStream = new FileOutputStream(new File(toPath));
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (Exception e) {
            Log.e(TAG, "Failed copy", e);
            return false;
        } finally {
            closeSilently(inStream);
            closeSilently(outStream);
        }
        return true;
    }

    public boolean move(String fromPath, String toPath) {
        if (copy(fromPath, toPath)) {
            return getFile(fromPath).delete();
        }
        return false;
    }

    protected byte[] readFile(final FileInputStream stream) {
        class Reader extends Thread {
            byte[] array = null;
        }

        Reader reader = new Reader() {
            public void run() {
                LinkedList<ImmutablePair<byte[], Integer>> chunks = new LinkedList<ImmutablePair<byte[], Integer>>();

                // read the file and build chunks
                int size = 0;
                int globalSize = 0;
                do {
                    try {
                        int chunkSize = mConfiguration != null ? mConfiguration.getChuckSize() : 8192;
                        // read chunk
                        byte[] buffer = new byte[chunkSize];
                        size = stream.read(buffer, 0, chunkSize);
                        if (size > 0) {
                            globalSize += size;

                            // add chunk to list
                            chunks.add(new ImmutablePair<byte[], Integer>(buffer, size));
                        }
                    } catch (Exception e) {
                        // very bad
                    }
                } while (size > 0);

                try {
                    stream.close();
                } catch (Exception e) {
                    // very bad
                }

                array = new byte[globalSize];

                // append all chunks to one array
                int offset = 0;
                for (ImmutablePair<byte[], Integer> chunk : chunks) {
                    // flush chunk to array
                    System.arraycopy(chunk.element1, 0, array, offset, chunk.element2);
                    offset += chunk.element2;
                }
            }

            ;
        };

        reader.start();
        try {
            reader.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed on reading file from storage while the locking Thread", e);
            return null;
        }

        if (mConfiguration != null && mConfiguration.isEncrypted()) {
            return encrypt(reader.array, Cipher.DECRYPT_MODE);
        } else {
            return reader.array;
        }
    }

    /**
     * Encrypt or Descrypt the content. <br>
     *
     * @param content        The content to encrypt or descrypt.
     * @param encryptionMode Use: {@link Cipher#ENCRYPT_MODE} or
     *                       {@link Cipher#DECRYPT_MODE}
     * @return
     */
    private synchronized byte[] encrypt(byte[] content, int encryptionMode) {
        final byte[] secretKey = mConfiguration.getSecretKey();
        final byte[] ivx = mConfiguration.getIvParameter();
        return SecurityUtil.encrypt(content, encryptionMode, secretKey, ivx);
    }

    /**
     * Delete the directory and all sub content.
     *
     * @param path The absolute directory path. For example:
     *             <i>mnt/sdcard/NewFolder/</i>.
     * @return <code>True</code> if the directory was deleted, otherwise return
     * <code>False</code>
     */
    private boolean deleteDirectoryImpl(String path) {
        File directory = new File(path);

        // If the directory exists then delete
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files == null) {
                return true;
            }
            // Run on all sub files and folders and delete them
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectoryImpl(files[i].getAbsolutePath());
                } else {
                    files[i].delete();
                }
            }
        }
        return directory.delete();
    }

    /**
     * Get all files under the directory
     *
     * @param directory
     * @param out
     * @return
     */
    private void getDirectoryFilesImpl(File directory, List<File> out) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files == null) {
                return;
            } else {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        getDirectoryFilesImpl(files[i], out);
                    } else {
                        out.add(files[i]);
                    }
                }
            }
        }
    }

    private void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }
}
