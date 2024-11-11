package com.kaisar.xposed.godmode.injection.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Created by jrsen on 17-10-21.
 */

public final class FileUtils {

    public static final int S_IRWXU = 00700;
    public static final int S_IRUSR = 00400;
    public static final int S_IWUSR = 00200;
    public static final int S_IXUSR = 00100;

    public static final int S_IRWXG = 00070;
    public static final int S_IRGRP = 00040;
    public static final int S_IWGRP = 00020;
    public static final int S_IXGRP = 00010;

    public static final int S_IRWXO = 00007;
    public static final int S_IROTH = 00004;
    public static final int S_IWOTH = 00002;
    public static final int S_IXOTH = 00001;

    public static boolean canRead(String filepath) {
        return new File(filepath).canRead();
    }

    public static boolean canWrite(String filepath) {
        return new File(filepath).canWrite();
    }

    public static boolean exists(String filepath) {
        return new File(filepath).exists();
    }

    public static boolean createNewFile(String pathname, int mode) {
        try {
            File file = new File(pathname);
            return file.createNewFile() && (setPermissions(file, mode, -1, -1) == 0);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean mkdirs(String pathname, int mode) {
        File dir = new File(pathname);
        return dir.mkdirs() && (setPermissions(pathname, mode, -1, -1) == 0);
    }

    public static boolean copy(File src, File dst) {
        return copy(src.getAbsolutePath(), dst.getAbsolutePath());
    }

    public static boolean copy(String src, String dst) {
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inChannel = new FileInputStream(src).getChannel();
            outChannel = new FileOutputStream(dst).getChannel();
            return inChannel.transferTo(0, inChannel.size(), outChannel) > 0;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (inChannel != null)
                    inChannel.close();
                if (outChannel != null)
                    outChannel.close();
            } catch (IOException ignore) {
            }
        }
    }

    public static boolean copy(InputStream in, File dst) {
        try {
            return copy(in, new FileOutputStream(dst));
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public static boolean copy(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[4096];
            for (int len; ((len = in.read(buffer)) != -1); ) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return true;
        } catch (IOException ignore) {
            return false;
        }
    }

    public static boolean delete(String filePath) {
        return delete(new File(filePath));
    }

    public static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File childFile : files) {
                    if (!delete(childFile)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    public static boolean rmdir(String dirPath){
        return delete(new File(dirPath));
    }

    public static boolean writeFile(InputStream in, File file) {
        try {
            FileOutputStream out = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            for (int len; (len = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Set owner and mode of of given {@link File}.
     *
     * @param mode to apply through {@code chmod}
     * @param uid  to apply through {@code chown}, or -1 to leave unchanged
     * @param gid  to apply through {@code chown}, or -1 to leave unchanged
     * @return 0 on success, otherwise errno.
     */
    public static int setPermissions(File path, int mode, int uid, int gid) {
        return setPermissions(path.getAbsolutePath(), mode, uid, gid);
    }

    /**
     * Set owner and mode of of given path.
     *
     * @param mode to apply through {@code chmod}
     * @param uid  to apply through {@code chown}, or -1 to leave unchanged
     * @param gid  to apply through {@code chown}, or -1 to leave unchanged
     * @return 0 on success, otherwise errno.
     */
    public static int setPermissions(String path, int mode, int uid, int gid) {
        try {
            Class<?> FileUtilsClass = Class.forName("android.os.FileUtils");
            Method setPermissionsMethod = FileUtilsClass.getDeclaredMethod("setPermissions", String.class, int.class, int.class, int.class);
            return (int) setPermissionsMethod.invoke(null, path, mode, uid, gid);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static String readTextFile(String filePath, int max, String ellipsis) throws IOException {
        return readTextFile(new File(filePath), max, ellipsis);
    }

    /**
     * Read a text file into a String, optionally limiting the length.
     *
     * @param file     to read (will not seek, so things like /proc files are OK)
     * @param max      length (positive for head, negative of tail, 0 for no limit)
     * @param ellipsis to add of the file was truncated (can be null)
     * @return the contents of the file, possibly truncated
     * @throws IOException if something goes wrong reading the file
     */
    public static String readTextFile(File file, int max, String ellipsis) throws IOException {
        InputStream input = new FileInputStream(file);
        // wrapping a BufferedInputStream around it because when reading /proc with unbuffered
        // input stream, bytes read not equal to buffer size is not necessarily the correct
        // indication for EOF; but it is true for BufferedInputStream due to its implementation.
        BufferedInputStream bis = new BufferedInputStream(input);
        try {
            long size = file.length();
            if (max > 0 || (size > 0 && max == 0)) {  // "head" mode: read the first N bytes
                if (size > 0 && (max == 0 || size < max)) max = (int) size;
                byte[] data = new byte[max + 1];
                int length = bis.read(data);
                if (length <= 0) return "";
                if (length <= max) return new String(data, 0, length);
                if (ellipsis == null) return new String(data, 0, max);
                return new String(data, 0, max) + ellipsis;
            } else if (max < 0) {  // "tail" mode: keep the last N
                int len;
                boolean rolled = false;
                byte[] last = null;
                byte[] data = null;
                do {
                    if (last != null) rolled = true;
                    byte[] tmp = last;
                    last = data;
                    data = tmp;
                    if (data == null) data = new byte[-max];
                    len = bis.read(data);
                } while (len == data.length);

                if (last == null && len <= 0) return "";
                if (last == null) return new String(data, 0, len);
                if (len > 0) {
                    rolled = true;
                    System.arraycopy(last, len, last, 0, last.length - len);
                    System.arraycopy(data, 0, last, last.length - len, len);
                }
                if (ellipsis == null || !rolled) return new String(last);
                return ellipsis + new String(last);
            } else {  // "cat" mode: size unknown, read it all in streaming fashion
                ByteArrayOutputStream contents = new ByteArrayOutputStream();
                int len;
                byte[] data = new byte[1024];
                do {
                    len = bis.read(data);
                    if (len > 0) contents.write(data, 0, len);
                } while (len == data.length);
                return contents.toString();
            }
        } finally {
            bis.close();
            input.close();
        }
    }

    public static void stringToFile(File file, String string) throws IOException {
        stringToFile(file.getAbsolutePath(), string);
    }

    /**
     * Writes string to file. Basically same as "echo -n $string > $filename"
     *
     * @param filename
     * @param string
     * @throws IOException
     */
    public static void stringToFile(String filename, String string) throws IOException {
        FileWriter out = new FileWriter(filename);
        try {
            out.write(string);
        } finally {
            out.close();
        }
    }

    /**
     * 删除一个文件,如果文件是文件夹,那么会删除这个文件夹中的所有内容
     *
     * @param pFile
     *            文件
     * @throws IOException
     */
    public static void deleteFile(File pFile) {
        if (pFile == null || !pFile.exists())
            return;
        if (pFile.isDirectory())
            clearDir(pFile);
        if (!pFile.delete()) {
            System.gc();
            pFile.delete();
        }
    }

    /**
     * 清空一个文件夹
     *
     * @param pDir
     *            文件夹
     */
    public static void clearDir(File pDir) {
        if (!pDir.isDirectory())
            return;

        File[] tSubFiles = pDir.listFiles();
        if (tSubFiles != null) {
            for (File sChildFile : tSubFiles) {
                deleteFile(sChildFile);
            }
        }
    }

    /**
     * 创建一个新文件
     *
     * @param pFile
     *            要创建的文件
     * @param pReplace
     *            是否替换已经存在的文件
     * @throws IOException
     */
    public static void createNewFile(File pFile, boolean pReplace) throws IOException {
        if (pFile == null)
            return;
        if (pFile.isFile()) {
            if (!pReplace) {
                return;
            } else {
                deleteFile(pFile);
            }
        }

        pFile = pFile.getAbsoluteFile();
        File tParent = pFile.getParentFile();
        if (!tParent.isDirectory()) {
            if (!tParent.mkdirs()) {
                throw new IOException("File '" + pFile + "' could not be created");
            }
        }
        pFile.createNewFile();
    }

    /**
     * 使用给定的文件打开输出流
     * <p>
     * 如果文件不存在,将会自动创建
     * </p>
     *
     * @param pFile
     *            指定的文件
     * @return 创建的输出流
     * @throws IOException
     */
    public static FileOutputStream openOutputStream(File pFile) throws IOException {
        return openOutputStream(pFile, true);
    }

    /**
     * 从给定的文件打开输出流
     * <p>
     * 如果文件不存在,将会自动创建
     * </p>
     *
     * @param pFile
     *            指定的文件
     * @param pAppend
     *            是否以追加模式打开
     * @return 创建的输出流
     * @throws IOException
     *             可能发生的异常
     */
    public static FileOutputStream openOutputStream(File pFile, boolean pAppend) throws IOException {
        if (!pFile.isFile()) {
            createNewFile(pFile, !pAppend);
        }
        return new FileOutputStream(pFile, pAppend);
    }

    /**
     * 使用指定文件创建一个输入流
     *
     * @param pFile
     *            要打开的文件
     * @return 创建的输入流
     * @throws IOException
     *             创建输入流时发生错误
     */
    public static FileInputStream openInputStream(File pFile) throws IOException {
        if (!pFile.isFile()) {
            createNewFile(pFile, false);
        }
        return new FileInputStream(pFile);
    }

    /**
     * 将文件内容全部读取出来,并使用UTF-8编码转换为String
     *
     * @param pFile
     *            要读取的文件
     * @return 文件内容
     * @throws IOException
     *             报错
     */
    public static String readContent(File pFile) {
        return readContent(pFile, "UTF-8");
    }

    /**
     * 将文件内容全部读取出来,并使用指定编码转换为String
     *
     * @param pFile
     *            要读取的文件
     * @param pEncoding
     *            使用的转换编码
     * @return 文件内容
     * @throws IOException
     *             打开文件或读取数据时发生错误
     */
    public static String readContent(File pFile, String pEncoding) {
        InputStream tIPStream = null;
        try {
            tIPStream = openInputStream(pFile);
            return readContent(tIPStream, pEncoding);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            closeStream(tIPStream);
        }
    }

    /**
     * 将文件内容全部读取出来
     *
     * @param pFile
     *            要读取的文件
     * @return 文件内容
     * @throws IOException
     *             打开文件或读取数据时发生错误
     */
    public static byte[] readData(File pFile) {
        InputStream tIPStream = null;
        try {
            tIPStream = openInputStream(pFile);
            return readData(tIPStream);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            closeStream(tIPStream);
        }
    }

    public static void writeData(File pFile, String pData) {
        writeData(pFile, pData.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将文件内容全部读取出来
     *
     * @param pFile
     *            要读取的文件
     * @param pData
     *            写入的数据
     * @throws IOException
     *             打开文件或读取数据时发生错误
     */
    public static void writeData(File pFile, byte[] pData) {
        writeData(pFile, pData, 0, pData.length);
    }

    /**
     * 将文件内容全部读取出来
     *
     * @param pFile
     *            要读取的文件
     * @param pData
     *            写入的数据
     * @param pOffest
     *            数据偏移量
     * @param pLength
     *            写入的数据长度
     * @throws IOException
     *             打开文件或读取数据时发生错误
     */
    public static void writeData(File pFile, byte[] pData, int pOffest, int pLength) {
        OutputStream tOStream = null;
        try {
            tOStream = openOutputStream(pFile, false);
            tOStream.write(pData, pOffest, pLength);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            closeStream(tOStream);
        }
    }

    public static void copyFile(File srcFile, File destFile) {
        copyFile(srcFile, destFile, true);
    }

    /**
     * 复制文件到新位置
     * <p>
     * 如果目标文件或文件夹不存在,将会自动创建<br>
     * 如果目标文件存在,将会覆盖
     * </p>
     *
     * @param pSourceFile
     *            要被复制的文件
     * @param pDestFile
     *            复制到的新文件
     * @param pCopyFileInfo
     *            是否设置复制后的文件的修改日期与源文件相同
     * @throws IOException
     *             源文件不存在,创建新文件时发生错误,读取数据时发生错误
     */
    public static void copyFile(File pSourceFile, File pDestFile, boolean pCopyFileInfo) {
        try {
            if (pSourceFile.getCanonicalPath().equals(pDestFile.getCanonicalPath()))
                return;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        FileInputStream tIPStream = null;
        FileOutputStream tOPStream = null;
        try {
            tIPStream = new FileInputStream(pSourceFile);
            tOPStream = openOutputStream(pDestFile, false);
            copy(tIPStream, tOPStream);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            closeStream(tIPStream, tOPStream);
        }
        if (pCopyFileInfo) {
            pDestFile.setLastModified(Math.max(0, pSourceFile.lastModified()));
        }
    }
    /**
     * 关闭一个流
     *
     * @param pSteams
     *            流
     * @return 是否无报错的关闭了
     */
    public static boolean closeStream(Closeable...pSteams){
        boolean pHasError=false;
        for(Closeable sCloseable : pSteams){
            if(sCloseable!=null){
                try{
                    sCloseable.close();
                }catch(Exception exp){
                    pHasError=true;
                }
            }
        }

        return !pHasError;
    }

    /**
     * 关闭一个连接
     *
     * @param pConns
     *            连接
     * @return 连接是否无报错的关闭了
     */
    public static boolean closeStream(AutoCloseable...pConns){
        boolean pHasError=false;
        for(AutoCloseable sCloseable : pConns){
            if(sCloseable!=null){
                try{
                    sCloseable.close();
                }catch(Exception exp){
                    pHasError=true;
                }
            }
        }

        return !pHasError;
    }

    /**
     * 将流中的内容全部读取出来,并使用指定编码转换为String
     *
     * @param pIPStream
     *            输入流
     * @param pEncoding
     *            转换编码
     * @return 读取到的内容
     * @throws IOException
     *             读取数据时发生错误
     * @throws UnsupportedEncodingException
     */
    public static String readContent(InputStream pIPStream,String pEncoding) throws IOException{
        if(pEncoding==null||pEncoding.isEmpty()){
            return readContent(new InputStreamReader(pIPStream));
        }else{
            return readContent(new InputStreamReader(pIPStream,pEncoding));
        }
    }

    /**
     * 将流中的内容全部读取出来
     *
     * @param pIPSReader
     *            输入流
     * @return 读取到的内容
     * @throws IOException
     *             读取数据时发生错误
     */
    public static String readContent(InputStreamReader pIPSReader) throws IOException{
        int readCount=0;
        char[] tBuff=new char[4096];
        StringBuilder tSB=new StringBuilder();
        while((readCount=pIPSReader.read(tBuff))!=-1){
            tSB.append(tBuff,0,readCount);
        }
        return tSB.toString();
    }

    /**
     * 将流中的内容全部读取出来
     *
     * @param pIStream
     *            输入流
     * @return 读取到的内容
     * @throws IOException
     *             读取数据时发生错误
     */
    public static byte[] readData(InputStream pIStream) throws IOException{
        ByteArrayOutputStream tBAOStream=new ByteArrayOutputStream();
        copy(pIStream,tBAOStream);
        return tBAOStream.toByteArray();
    }
}
