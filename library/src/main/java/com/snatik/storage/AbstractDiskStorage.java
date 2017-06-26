package com.snatik.storage;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.StatFs;
import android.util.Log;

import com.snatik.storage.helpers.ImmutablePair;
import com.snatik.storage.helpers.OrderType;
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Cipher;

/**
 * Common class for internal and external storage implementations
 *
 * @author Roman Kushnarenko - sromku (sromku@gmail.com)
 */
abstract class AbstractDiskStorage implements Storage {

    protected static final String UTF_8 = "UTF-8";
    private static final String TAG = "AbstractDiskStorage";

    AbstractDiskStorage() {
    }

    protected SimpleStorageConfiguration getConfiguration() {
        return SimpleStorage.getConfiguration();
    }

    @Override
    public boolean createDirectory(String name) {
        String path = buildPath(name);
        File directory = new File(path);
        if (directory.exists()) {
            Log.w(TAG, "Directory '" + name + "' already exists");
            return false;
        }
        return directory.mkdirs();
    }

    @Override
    public boolean createDirectory(String name, boolean override) {

        // Check if directory exists. If yes, then delete all directory
        if (override && isDirectoryExists(name)) {
            deleteDirectory(name);
        }

        // Create new directory
        return createDirectory(name);
    }

    @Override
    public boolean deleteDirectory(String name) {
        String path = buildPath(name);
        return deleteDirectoryImpl(path);
    }

    @Override
    public boolean isDirectoryExists(String name) {
        String path = buildPath(name);
        return new File(path).exists();
    }

    @Override
    public boolean createFile(String directoryName, String fileName, String content) {
        return createFile(directoryName, fileName, content.getBytes());
    }

    @Override
    public boolean createFile(String directoryName, String fileName, Storable storable) {
        return createFile(directoryName, fileName, storable.getBytes());
    }

    @Override
    public boolean createFile(String directoryName, String fileName, byte[] content) {
        String path = buildPath(directoryName, fileName);
        try {
            OutputStream stream = new FileOutputStream(new File(path));

            // encrypt if needed
            if (getConfiguration().isEncrypted()) {
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

    @Override
    public boolean createFile(String directoryName, String fileName, Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return createFile(directoryName, fileName, byteArray);
    }

    @Override
    public boolean deleteFile(String directoryName, String fileName) {
        String path = buildPath(directoryName, fileName);
        File file = new File(path);
        return file.delete();
    }

    @Override
    public boolean isFileExist(String directoryName, String fileName) {
        String path = buildPath(directoryName, fileName);
        return new File(path).exists();
    }

    @Override
    public byte[] readFile(String directoryName, String fileName) {
        String path = buildPath(directoryName, fileName);
        final FileInputStream stream;
        try {
            stream = new FileInputStream(new File(path));
            return readFile(stream);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to read file to input stream", e);
            return null;
        }
    }

    @Override
    public String readTextFile(String directoryName, String fileName) {
        byte[] bytes = readFile(directoryName, fileName);
        return new String(bytes);
    }

    @Override
    public void appendFile(String directoryName, String fileName, String content) {
        appendFile(directoryName, fileName, content.getBytes());
    }

    @Override
    public void appendFile(String directoryName, String fileName, byte[] bytes) {
        if (!isFileExist(directoryName, fileName)) {
            Log.w(TAG, "Impossible to append content, because such file doesn't exist");
            return;
        }

        try {
            String path = buildPath(directoryName, fileName);
            FileOutputStream stream = new FileOutputStream(new File(path), true);
            stream.write(bytes);
            stream.write(System.getProperty("line.separator").getBytes());
            stream.flush();
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to append content to file", e);
        }
    }

    @Override
    public List<File> getNestedFiles(String directoryName) {
        String buildPath = buildPath(directoryName);
        File file = new File(buildPath);
        List<File> out = new ArrayList<File>();
        getDirectoryFilesImpl(file, out);
        return out;
    }

    @Override
    public List<File> getFiles(String directoryName, final String matchRegex) {
        String buildPath = buildPath(directoryName);
        File file = new File(buildPath);
        List<File> out;
        if (matchRegex != null) {
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String fileName) {
                    return fileName.matches(matchRegex);
                }
            };
            out = Arrays.asList(file.listFiles(filter));
        } else {
            out = Arrays.asList(file.listFiles());
        }
        return out;
    }

    @Override
    public List<File> getFiles(String directoryName, OrderType orderType) {
        List<File> files = getFiles(directoryName, (String) null);
        Collections.sort(files, orderType.getComparator());
        return files;
    }

    @Override
    public File getFile(String name) {
        String path = buildPath(name);
        File file = new File(path);
        return file;
    }

    @Override
    public File getFile(String directoryName, String fileName) {
        String path = buildPath(directoryName, fileName);
        return new File(path);
    }

    @Override
    public void rename(File file, String newName) {
        String name = file.getName();
        String newFullName = file.getAbsolutePath().replaceAll(name, newName);
        File newFile = new File(newFullName);
        file.renameTo(newFile);
    }

    @Override
    public double getSize(File file, SizeUnit unit) {
        long length = file.length();
        return (double) length / (double) unit.inBytes();
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @Override
    public long getFreeSpace(SizeUnit sizeUnit) {
        String path = buildAbsolutePath();
        StatFs statFs = new StatFs(path);
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

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @Override
    public long getUsedSpace(SizeUnit sizeUnit) {
        String path = buildAbsolutePath();
        StatFs statFs = new StatFs(path);
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

    @Override
    public void copy(File file, String directoryName, String fileName) {
        if (!file.isFile()) {
            return;
        }

        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        try {
            inStream = new FileInputStream(file);
            outStream = new FileOutputStream(new File(buildPath(directoryName, fileName)));
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (Exception e) {
            Log.e(TAG, "Failed copy", e);
        } finally {
            closeQuietly(inStream);
            closeQuietly(outStream);
        }
    }

    @Override
    public void move(File file, String directoryName, String fileName) {
        copy(file, directoryName, fileName);
        file.delete();
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
                        int chunkSize = getConfiguration().getChuckSize();
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

        if (getConfiguration().isEncrypted()) {
            return encrypt(reader.array, Cipher.DECRYPT_MODE);
        } else {
            return reader.array;
        }
    }

    protected abstract String buildAbsolutePath();

    protected abstract String buildPath(String name);

    protected abstract String buildPath(String directoryName, String fileName);

    /**
     * Encrypt or Descrypt the content. <br>
     *
     * @param content        The content to encrypt or descrypt.
     * @param encryptionMode Use: {@link Cipher#ENCRYPT_MODE} or
     *                       {@link Cipher#DECRYPT_MODE}
     * @return
     */
    protected synchronized byte[] encrypt(byte[] content, int encryptionMode) {
        final byte[] secretKey = getConfiguration().getSecretKey();
        final byte[] ivx = getConfiguration().getIvParameter();
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

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }
}
