package cc.brainbook.android.multithreaddownload.util;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import cc.brainbook.android.multithreaddownload.exception.DownloadException;

import static java.nio.ByteBuffer.wrap;

public class HttpDownloadUtil {


    /* ---------------- 网络连接---------------- */
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
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_MALFORMED_URL, "new URL(fileUrl)# java.net.MalformedURLException: Protocol not found", e);
        }

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept-Encoding", "identity");
        } catch (UnknownHostException e) {
            ///URL虽然以http://或https://开头、但host为空或无效host
            ///     java.net.UnknownHostException: http://
            ///     java.net.UnknownHostException: Unable to resolve host "aaa": No address associated with hostname
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_UNKNOWN_HOST, "url.openConnection()# java.net.UnknownHostException", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_IO_EXCEPTION, "url.openConnection()# java.io.IOException", e);
        }

        try {
            connection.setRequestMethod(requestMethod);
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_PROTOCOL_EXCEPTION, "connection.setRequestMethod(requestMethod)# java.net.ProtocolException", e);
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
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_IO_EXCEPTION, "connection.connect()# java.io.IOException", e);
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
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_IO_EXCEPTION, "connection.getResponseCode()# java.io.IOException", e);
        }

        if (code != responseCode) {
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_RESPONSE_CODE_EXCEPTION, "The connection response code is unexpected: " + code);
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
        if (connection == null) return filename;

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
            URL url = connection.getURL();
            String path = "";
            if (url != null) {
                path = url.getPath();
            }
            if (path.length() > 0) {
                filename = new File(path).getName();
            }
        }
        if (filename.length() == 0) {
            throw new DownloadException(DownloadException.EXCEPTION_FILE_NAME_NULL, "The file name cannot be null.");
        }
        return filename;
    }


    /* ---------------- 文件读写 ---------------- */

    /**
     * 获得网络连接的输入流对象InputStream
     *
     * @param connection
     * @return
     */
    public static InputStream getInputStream(HttpURLConnection connection) {
        ///获得网络连接connection的输入流对象
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_IO_EXCEPTION, "connection.getInputStream()# java.io.IOException", e);
        }

        return inputStream;
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
        InputStream inputStream = getInputStream(connection);

        ///由输入流对象创建缓冲输入流对象（比inputStream效率要高）
        return new BufferedInputStream(inputStream);
    }

    /**
     * 缓冲输入流对象BufferedInputStream的读操作
     *
     * 注意：缓冲输入流比inputStream效率要高
     * https://blog.csdn.net/hfreeman2008/article/details/49174499
     *
     * 注意：当输入流对象为网络连接获得的时候，IOException为断网异常，所以特别用EXCEPTION_NETWORK_FILE_IO_EXCEPTION
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
//            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "bufferedInputStream.read(bytes)# java.io.IOException", e);
            throw new DownloadException(DownloadException.EXCEPTION_NETWORK_FILE_IO_EXCEPTION, "bufferedInputStream.read(bytes)# java.io.IOException", e);
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
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "bufferedOutputStream.write(bytes, 0, readLength)# java.io.IOException", e);
        }
    }

    /**
     * 获得随机访问文件对象RandomAccessFile
     *
     * @param saveFile
     * @return
     */
    public static RandomAccessFile getRandomAccessFile(File saveFile, String mode) {
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(saveFile, mode);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_NOT_FOUND, "new RandomAccessFile(saveFile, \"rwd\")# java.io.FileNotFoundException", e);
        }
        return raf;
    }

    /**
     * 设置随机访问文件对象RandomAccessFile的文件长度
     *
     * 注意：不必一定要用setLength()创建占位文件！///??????猜想：也许影响速度！
     *
     * @param randomAccessFile
     * @param length
     */
    public static void randomAccessFileSetLength(RandomAccessFile randomAccessFile, long length) {
        try {
            randomAccessFile.setLength(length);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "randomAccessFile.setLength(length)# java.io.IOException", e);
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
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "randomAccessFile.seek(start)# java.io.IOException", e);
        }
    }

    /**
     * 随机访问文件对象RandomAccessFile的分段读操作
     *
     * @param randomAccessFile
     * @param bytes
     * @param readLength
     */
    public static void randomAccessFileRead(RandomAccessFile randomAccessFile, byte[] bytes, int readLength) {
        try {
            randomAccessFile.read(bytes, 0, readLength);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "randomAccessFile.read(bytes, 0, readLength)# java.io.IOException", e);
        }
    }

    /**
     * 随机访问文件对象RandomAccessFile的分段写操作
     *
     * @param randomAccessFile
     * @param bytes
     * @param writeLength
     */
    public static void randomAccessFileWrite(RandomAccessFile randomAccessFile, byte[] bytes, int writeLength) {
        try {
            randomAccessFile.write(bytes, 0, writeLength);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "randomAccessFile.write(bytes, 0, writeLength)# java.io.IOException", e);
        }
    }

    /**
     * FileChannel的读操作（ByteBuffer）
     *
     * @param channel
     * @param bytes
     * @param readLength
     */
    public static void channelReadByteBuffer(FileChannel channel, byte[] bytes, int readLength) {
        ByteBuffer buf = wrap(bytes, 0, readLength);
        try {
            channel.read(buf);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "FileChannel.read(ByteBuffer)# java.io.IOException", e);
        }
    }

    /**
     * FileChannel的写操作（ByteBuffer）
     *
     * @param channel
     * @param bytes
     * @param writeLength
     */
    public static void channelWriteByteBuffer(FileChannel channel, byte[] bytes, int writeLength) {
        ByteBuffer buffer = wrap(bytes, 0, writeLength);
        try {
            channel.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "FileChannel.write(ByteBuffer)# java.io.IOException", e);
        }
    }

    ///???????MappedByteBuffer没有调试通过！
    /**
     * FileChannel的读操作（MappedByteBuffer）
     *
     * Note:  There are some limitation with MappedByteBuffer.
     * It has size limit 2GB and it can result in page fault if requested page is not in memory.
     *
     * @param channel
     * @param bytes
     * @param readLength
     */
    public static void channelReadMappedByteBuffer(FileChannel channel, byte[] bytes, int readLength) {
        try {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, readLength);
            buffer.get(bytes);
//            channel.read(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "FileChannel.read(MappedByteBuffer)# java.io.IOException", e);
        }
    }

    /**
     * FileChannel的写操作（MappedByteBuffer）
     *
     * Note:  There are some limitation with MappedByteBuffer.
     * It has size limit 2GB and it can result in page fault if requested page is not in memory.
     *
     * @param channel
     * @param bytes
     * @param writeLength
     */
    public static void channelWriteMappedByteBuffer(FileChannel channel, byte[] bytes, int writeLength, long start) {
        try {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, start, writeLength);
            buffer.put(bytes);
//            channel.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DownloadException(DownloadException.EXCEPTION_FILE_IO_EXCEPTION, "FileChannel.write(MappedByteBuffer)# java.io.IOException", e);
        }
    }
}

