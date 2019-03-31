package cc.brainbook.android.multithreaddownload.thread;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;
import java.util.concurrent.CyclicBarrier;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAO;
import cc.brainbook.android.multithreaddownload.handler.DownloadHandler;
import cc.brainbook.android.multithreaddownload.util.HttpDownloadUtil;
import cc.brainbook.android.multithreaddownload.util.Util;

/**
 * 下载线程
 */
public class DownloadThread extends Thread {
    private Config mConfig;
    private FileInfo mFileInfo;
    private DownloadHandler mHandler;
    private ThreadInfo mThreadInfo;
    private ThreadInfoDAO mThreadDAO;

    ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
    ///https://www.cnblogs.com/dolphin0520/p/3920397.html
    private CyclicBarrier mBarrier;

    public DownloadThread(Config config,
                          FileInfo fileInfo,
                          DownloadHandler handler,
                          ThreadInfo threadInfo,
                          ThreadInfoDAO threadDAO,
                          CyclicBarrier barrier) {
        this.mConfig = config;
        this.mFileInfo = fileInfo;
        this.mHandler = handler;
        this.mThreadInfo = threadInfo;
        this.mThreadDAO = threadDAO;
        this.mBarrier = barrier;
    }

    @Override
    public void run() {
        super.run();

        ///更新线程信息的状态：下载开始
        mThreadInfo.setState(DownloadState.STARTED);

        HttpURLConnection connection = null;
        BufferedInputStream bufferedInputStream = null;
        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        try{
            ///由下载文件的URL网址建立网络连接
            connection = HttpDownloadUtil.openConnection(mFileInfo.getFileUrl(), mConfig.connectTimeout);

            ///获得下载文件的开始位置
            long start = mThreadInfo.getStart() + mThreadInfo.getFinishedBytes();
            long end = mThreadInfo.getEnd();
            ///设置连接的下载范围
            connection.setRequestProperty("range", "bytes="+ start + "-" + end);

            ///发起网络连接
            HttpDownloadUtil.connect(connection);

            ///如果网络连接connection的响应码为206，则开始下载过程，否则抛出异常
            HttpDownloadUtil.handleResponseCode(connection, HttpURLConnection.HTTP_PARTIAL);

            ///获得网络连接的缓冲输入流对象BufferedInputStream
            bufferedInputStream = HttpDownloadUtil.getBufferedInputStream(connection);

            ///获得保存文件对象
            File saveFile = new File(mFileInfo.getSavePath(), mFileInfo.getFileName());
            ///获得保存文件的随机访问文件对象RandomAccessFile，并定位
            randomAccessFile = HttpDownloadUtil.getRandomAccessFile(saveFile, "rwd");
            HttpDownloadUtil.randomAccessFileSeek(randomAccessFile, start);
            ///由文件的输出流对象获得FileChannel对象
            channel = randomAccessFile.getChannel();
            ///必须minSdkVersion 26以上（为兼容低版本，弃用！）
//            Path savePath = Paths.get(mFileInfo.getSavePath(), mFileInfo.getFileName());
//            FileChannel channel = null;
//            try {
//                channel = FileChannel.open(savePath, READ, WRITE);
//                channel.position(start);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            ///输入流每次读取的内容（字节缓冲区）
            ///BufferedInputStream的默认缓冲区大小是8192字节。
            ///当每次读取数据量接近或远超这个值时，两者效率就没有明显差别了
            ///https://blog.csdn.net/xisuo002/article/details/78742631
            byte[] bytes = new byte[mConfig.bufferSize];
            ///每次循环读取的内容长度，如为-1表示输入流已经读取结束
            int readLength;
            while ((readLength = HttpDownloadUtil.bufferedInputStreamRead(bufferedInputStream, bytes)) != -1) {
                ///写入字节缓冲区内容到文件输出流
//                HttpDownloadUtil.randomAccessFileWrite(randomAccessFile, bytes, readLength);    ///随机访问文件对象RandomAccessFile的写操作
                HttpDownloadUtil.channelWriteByteBuffer(channel, bytes, readLength);    ///FileChannel的写操作（ByteBuffer）
//                HttpDownloadUtil.channelWriteMappedByteBuffer(channel, bytes, readLength, start);    ///FileChannel的写操作（MappedByteBuffer）///???????MappedByteBuffer没有调试通过！

                ///累计整个文件的下载进度
                mFileInfo.setFinishedBytes(mFileInfo.getFinishedBytes() + readLength);
                ///累计每个线程的下载进度
                mThreadInfo.setFinishedBytes(mThreadInfo.getFinishedBytes() + readLength);

                if (mFileInfo.getState() == DownloadState.PAUSED) {  ///暂停下载线程
                    ///更新线程信息的状态：下载暂停
                    mThreadInfo.setState(DownloadState.PAUSED);

                    ///线程信息保存到数据库
                    mThreadDAO.updateThreadInfo(mThreadInfo.getId(),
                            DownloadState.PAUSED,
                            mThreadInfo.getFinishedBytes(),
                            mFileInfo.getFinishedTimeMillis(),
                            mFileInfo.getUpdatedTimeMillis());

                    ///等待所有线程暂停后再做相应处理
                    mBarrier.await();

                    return;
                } else if (mFileInfo.getState() == DownloadState.STOPPED) {   ///停止下载线程
                    ///更新线程信息的状态：下载停止
                    mThreadInfo.setState(DownloadState.STOPPED);

                    ///线程信息保存到数据库
                    mThreadDAO.updateThreadInfo(mThreadInfo.getId(),
                            DownloadState.STOPPED,
                            mThreadInfo.getFinishedBytes(),
                            mFileInfo.getFinishedTimeMillis(),
                            mFileInfo.getUpdatedTimeMillis());

                    ///等待所有线程停止后再做相应处理
                    mBarrier.await();

                    return;
                }
            }

            ///更新线程信息的状态：下载完成
            mThreadInfo.setState(DownloadState.SUCCEED);

            ///线程信息保存到数据库
            mThreadDAO.updateThreadInfo(mThreadInfo.getId(),
                    DownloadState.SUCCEED,
                    mThreadInfo.getFinishedBytes(),
                    mFileInfo.getFinishedTimeMillis(),
                    mFileInfo.getUpdatedTimeMillis());

            ///等待所有线程完成后再做相应处理
            mBarrier.await();

        } catch (Exception e) {
            ///发送消息：下载错误
            mHandler.obtainMessage(DownloadHandler.MSG_FAILED, e).sendToTarget();
        } finally {
            ///关闭连接
            if (connection != null) {
                connection.disconnect();
            }

            ///关闭流Closeable
            ///FileChannel will close the associated RandomAccessFile as well.
            ///https://stackoverflow.com/questions/27248459/randomaccessfile-vs-nio-channel
            Util.closeIO(bufferedInputStream, /* randomAccessFile ,*/ channel);
        }
    }

}
