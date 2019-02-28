package cc.brainbook.android.multithreaddownload.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import cc.brainbook.android.multithreaddownload.exception.DownloadException;

public class HttpDownloadUtil extends Thread {

    /**
     * 由下载文件的URL网址建立网络连接
     *
     * @param fileUrl
     * @param connectTimeout
     * @return
     */
    public static HttpURLConnection openConnection(String fileUrl, int connectTimeout) {
        return openConnection(fileUrl, "GET", connectTimeout);
    }
    /**
     * 由下载文件的URL网址建立网络连接
     *
     * @param fileUrl
     * @param connectTimeout
     * @throws MalformedURLException
     * @throws IOException
     */
    public static HttpURLConnection openConnection(String fileUrl, String requestMethod, int connectTimeout) {
        URL url;
        try {
            url = new URL(fileUrl);
        } catch (MalformedURLException e) {
            ///当URL为null或无效网络连接协议时：java.net.MalformedURLException: Protocol not found
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_MALFORMED_URL, "The protocol is not found.", e);
        }

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
        } catch (UnknownHostException e) {
            ///URL虽然以http://或https://开头、但host为空或无效host
            ///     java.net.UnknownHostException: http://
            ///     java.net.UnknownHostException: Unable to resolve host "aaa": No address associated with hostname
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_UNKNOWN_HOST, "The host is unknown.", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }

        try {
            connection.setRequestMethod(requestMethod);
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_PROTOCOL_EXCEPTION, "ProtocolException expected.", e);
        }

        connection.setConnectTimeout(connectTimeout);

        return connection;
    }

    /**
     * 发起网络连接
     *
     * Operations that depend on being connected, like getInputStream, getOutputStream, etc, will implicitly perform the connection, if necessary.
     * https://stackoverflow.com/questions/16122999/java-urlconnection-when-do-i-need-to-use-the-connect-method
     *
     * @param connection
     */
    public static void connect(HttpURLConnection connection) {
        try {
            ///Operations that depend on being connected, like getInputStream, getOutputStream, etc, will implicitly perform the connection, if necessary.
            ///https://stackoverflow.com/questions/16122999/java-urlconnection-when-do-i-need-to-use-the-connect-method
            connection.connect();
        } catch (IOException e) {
            ///当没有网络链接
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
    }

    /**
     * 处理网络连接的响应码
     *
     * 如果网络连接的响应码等于给定的响应码则继续运行，否则抛出异常
     *
     * @param connection
     * @param responseCode
     */
    public static void handleResponseCode(HttpURLConnection connection, int responseCode) {
        int code;
        try {
            code = connection.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }

        if (code != responseCode) {
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "The connection response code is " + code);
        }
    }

    /**
     * 由网络连接获得文件名
     *
     * @param connection
     * @return
     */
    public static String getUrlFileName(HttpURLConnection connection) {
        String filename = "";
        String disposition = connection.getHeaderField("Content-Disposition");
        if (disposition != null) {
            // extracts file name from header field
            int index = disposition.indexOf("filename=");
            if (index > 0) {
                filename = disposition.substring(index + 10,
                        disposition.length() - 1);
            }
        }
        if (filename.length() == 0) {
            String path = connection.getURL().getPath();
            filename = new File(path).getName();
        }
        if (filename.isEmpty()) {
            throw new DownloadException(DownloadException.EXCEPTION_FILE_NAME_NULL, "The file name cannot be null.");
        }
        return filename;
    }

    /**
     * 获得保存文件的输出流对象FileOutputStream
     *
     * @param saveFile
     * @return
     */
    public static FileOutputStream getFileOutputStream(File saveFile) {
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(saveFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_NOT_FOUND, "The file is not found.", e);
        }
        return fileOutputStream;
    }

    /**
     * 获得网络连接的缓冲输入流对象BufferedInputStream
     *
     * 注意：缓冲输入流对象比inputStream效率要高
     * https://blog.csdn.net/hfreeman2008/article/details/49174499
     *
     * @param connection
     * @return
     */
    public static BufferedInputStream getBufferedInputStream(HttpURLConnection connection) {
        ///获得网络连接connection的输入流对象
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
        ///由输入流对象创建缓冲输入流对象（比inputStream效率要高）
        return new BufferedInputStream(inputStream);
    }

    /**
     * 缓冲输入流对象BufferedInputStream的读操作
     *
     * 注意：缓冲输入流比inputStream效率要高
     * https://blog.csdn.net/hfreeman2008/article/details/49174499
     *
     * @param bufferedInputStream
     * @param bytes
     * @return
     */
    public static int bufferedInputStreamRead(BufferedInputStream bufferedInputStream, byte[] bytes) {
        int result;
        try {
            result = bufferedInputStream.read(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
        return result;
    }

    /**
     * 缓冲输出流对象BufferedOutputStream的写操作
     *
     * 注意：缓冲输出流比inputStream效率要高
     * https://blog.csdn.net/hfreeman2008/article/details/49174499
     *
     * @param bufferedOutputStream
     * @param bytes
     * @param readLength
     */
    public static void bufferedOutputStreamWrite(BufferedOutputStream bufferedOutputStream, byte[] bytes, int readLength) {
        try {
            bufferedOutputStream.write(bytes, 0, readLength);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
    }

    /**
     * 获得保存文件的随机访问文件对象RandomAccessFile
     *
     * @param saveFile
     * @return
     */
    public static RandomAccessFile getRandomAccessFile(File saveFile) {
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(saveFile, "rwd");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_NOT_FOUND, "The file is not found.", e);
        }
        return raf;
    }

    /**
     * 设置随机访问文件对象RandomAccessFile的文件长度
     *
     * @param randomAccessFile
     * @param length
     */
    public static void randomAccessFileSetLength(RandomAccessFile randomAccessFile, long length) {
        try {
            randomAccessFile.setLength(length);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
    }


    /**
     * 随机访问文件对象RandomAccessFile的seek操作
     *
     * @param randomAccessFile
     * @param start
     * @return
     */
    public static void randomAccessFileSeek(RandomAccessFile randomAccessFile, long start) {
        try {
            randomAccessFile.seek(start);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
    }

    /**
     * 随机访问文件对象RandomAccessFile的写操作
     *
     * @param randomAccessFile
     * @param bytes
     * @param readLength
     */
    public static void randomAccessFileWrite(RandomAccessFile randomAccessFile, byte[] bytes, int readLength) {
        try {
            randomAccessFile.write(bytes, 0, readLength);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
    }

    /**
     * FileChannel的写操作
     *
     * @param channel
     * @param bytes
     * @param readLength
     */
    public static void channelWrite(FileChannel channel, byte[] bytes, int readLength) {
        ///Wrap a byte array into a buffer
        ByteBuffer buf = ByteBuffer.wrap(bytes, 0, readLength);
        try {
            channel.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IOException expected.", e);
        }
    }
}
