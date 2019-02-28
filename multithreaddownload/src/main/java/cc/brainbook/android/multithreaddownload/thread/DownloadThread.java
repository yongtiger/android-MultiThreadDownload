package cc.brainbook.android.multithreaddownload.thread;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import cc.brainbook.android.multithreaddownload.DownloadTask;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.db.ThreadDAO;
import cc.brainbook.android.multithreaddownload.util.HttpDownloadUtil;
import cc.brainbook.android.multithreaddownload.util.Util;

public class DownloadThread extends Thread {
    private static final String TAG = "TAG";

    private DownloadTask mDownloadTask;
    private Config mConfig;
    private FileInfo mFileInfo;
    private ThreadInfo mThreadInfo;
    private ThreadDAO mThreadDAO;
    private CyclicBarrier mBarrierPause;
    private CyclicBarrier mBarrierComplete;

    public DownloadThread(DownloadTask downloadTask,
                          Config config,
                          FileInfo fileInfo,
                          ThreadInfo threadInfo,
                          ThreadDAO threadDAO,
                          CyclicBarrier barrierPause,
                          CyclicBarrier barrierComplete
    ) {
        this.mDownloadTask = downloadTask;
        this.mConfig = config;
        this.mFileInfo = fileInfo;
        this.mThreadInfo = threadInfo;
        this.mThreadDAO = threadDAO;
        this.mBarrierPause = barrierPause;
        this.mBarrierComplete = barrierComplete;
    }

    @Override
    public void run() {
        super.run();

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
            ///
            randomAccessFile = HttpDownloadUtil.getRandomAccessFile(saveFile);
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
                HttpDownloadUtil.channelWrite(channel, bytes, readLength);    ///FileChannel的写操作

                ///累计整个文件的下载进度
                mFileInfo.setFinishedBytes(mFileInfo.getFinishedBytes() + readLength);
                ///累计每个线程的下载进度
                mThreadInfo.setFinishedBytes(mThreadInfo.getFinishedBytes() + readLength);

                Log.d(TAG, "DownloadThread# run(): Thread name(" + Thread.currentThread().getName() + "), " + mThreadInfo.getStart() + " - " + mThreadInfo.getEnd() + ", Finished: " +mThreadInfo.getFinishedBytes());

                if (mFileInfo.getStatus() == FileInfo.FILE_STATUS_PAUSE) {  ///暂停下载线程
                    Log.d(TAG, "DownloadThread# run(): Thread name(" + Thread.currentThread().getName() + "), Status: THREAD_STATUS_PAUSE");
                    mThreadInfo.setStatus(ThreadInfo.THREAD_STATUS_PAUSE);

                    ///下载线程信息保存到数据库
                    Log.d(TAG, "DownloadThread# run(): Thread name(" + Thread.currentThread().getName() + "), mThreadDAO.updateThread(" + mThreadInfo.getId() + ", " + mThreadInfo.getFinishedBytes() + ")");
                    mThreadDAO.updateThread(mThreadInfo.getId(), mThreadInfo.getFinishedBytes());

                    ///判断所有下载线程是否暂停，并做相应处理
                    ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
                    ///https://www.cnblogs.com/dolphin0520/p/3920397.html
                    mBarrierPause.await();

                    return;
                } else if (mFileInfo.getStatus() == FileInfo.FILE_STATUS_STOP) {   ///停止下载线程
                    Log.d(TAG, "DownloadThread# run(): Thread name(" + Thread.currentThread().getName() + "), Status: FILE_STATUS_STOP");
                    mThreadInfo.setStatus(ThreadInfo.THREAD_STATUS_STOP);

                    return;
                }
            }

            ///更新线程状态：下载完成
            mThreadInfo.setStatus(ThreadInfo.THREAD_STATUS_COMPLETE);

            ///下载线程信息保存到数据库
            Log.d(TAG, "DownloadThread# run(): Thread name(" + Thread.currentThread().getName() + "), mThreadDAO.updateThread(" + mThreadInfo.getId() + ", " + mThreadInfo.getFinishedBytes() + ")");
            mThreadDAO.updateThread(mThreadInfo.getId(), mThreadInfo.getFinishedBytes());

            ///判断所有下载线程是否完成，并做相应处理
            ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
            ///https://www.cnblogs.com/dolphin0520/p/3920397.html
            mBarrierComplete.await();

        } catch (BrokenBarrierException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
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
