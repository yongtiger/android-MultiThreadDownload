package cc.brainbook.android.multithreaddownload.thread;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;

import cc.brainbook.android.multithreaddownload.DownloadTask;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.db.ThreadDAO;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.handler.DownloadHandler;
import cc.brainbook.android.multithreaddownload.util.HttpDownloadUtil;
import cc.brainbook.android.multithreaddownload.util.Util;

import static cc.brainbook.android.multithreaddownload.BuildConfig.DEBUG;

public class InitThread extends Thread {
    private static final String TAG = "TAG";

    private Context mContext;
    private Config mConfig;
    private DownloadTask mDownloadTask;
    private FileInfo mFileInfo;
    private DownloadHandler mHandler;
    private ThreadDAO mThreadDAO;

    public InitThread (Context context,
                      DownloadTask downloadTask,
                      Config config,
                      FileInfo fileInfo,
                      DownloadHandler handler,
                      ThreadDAO threadDAO
    ) {
        this.mContext = context;
        this.mDownloadTask = downloadTask;
        this.mConfig = config;
        this.mFileInfo = fileInfo;
        this.mHandler = handler;
        this.mThreadDAO = threadDAO;
    }

    @Override
    public void run() {
        if (DEBUG) Log.d(TAG, "InitThread# run()# ");
        super.run();


        /* ----------- 重置mFileInfo的已经下载完的总耗时（毫秒） ----------- */
        ///注意：只计算本次下载初始化之后的总耗时，如果数据库已存线程信息，也重新计算，不累计（[//////??????以后改进]）
        if (DEBUG) Log.d(TAG, "InitThread# run()# 重置mFileInfo的FinishedTimeMillis为0");
        mFileInfo.setFinishedTimeMillis(0);


        /* ----------- 检验文件网址URL、保存目录 ----------- */
        if (TextUtils.isEmpty(mFileInfo.getFileUrl())) {
            ///更新下载文件状态：下载错误
            mFileInfo.setStatus(FileInfo.FILE_STATUS_ERROR);
            mHandler.obtainMessage(DownloadHandler.MSG_ERROR,
                    new DownloadException(DownloadException.EXCEPTION_FILE_URL_NULL, "The file url cannot be null."))
                    .sendToTarget();
            return;
        }
        if (TextUtils.isEmpty(mFileInfo.getSavePath())) {
            mFileInfo.setSavePath(Util.getDefaultFilesDirPath(mContext));
        } else {
            if (!Util.mkdirs(mFileInfo.getSavePath())) {
                ///更新下载文件状态：下载错误
                mFileInfo.setStatus(FileInfo.FILE_STATUS_ERROR);
                mHandler.obtainMessage(DownloadHandler.MSG_ERROR,
                        new DownloadException(DownloadException.EXCEPTION_SAVE_PATH_MKDIR, "The file save path cannot be made: " + mFileInfo.getSavePath()))
                        .sendToTarget();
                return;
            }
        }

        HttpURLConnection connection = null;
        RandomAccessFile randomAccessFile = null;
        try {
            /* ----------- 由网络连接获得文件名、文件长度 ----------- */
            ///由下载文件的URL网址建立网络连接
            connection = HttpDownloadUtil.openConnection(mFileInfo.getFileUrl(), mConfig.connectTimeout);

            ///处理网络连接的响应码，如果网络连接connection的响应码为200，则开始下载过程，否则抛出异常
            HttpDownloadUtil.handleResponseCode(connection, HttpURLConnection.HTTP_OK);

            ///由网络连接获得文件名
            if (mFileInfo.getFileName().isEmpty()) {
                mFileInfo.setFileName(HttpDownloadUtil.getUrlFileName(connection));
            }

            ///由网络连接获得文件长度（建议用long类型，int类型最大为2GB）
            mFileInfo.setFileSize(connection.getContentLength());

            ///创建下载空文件
            File saveFile = new File(mFileInfo.getSavePath(), mFileInfo.getFileName());

            ///如果保存文件存在则删除
            if (saveFile.exists()) {
                if (saveFile.delete());
            }

            randomAccessFile = HttpDownloadUtil.getRandomAccessFile(saveFile);
            HttpDownloadUtil.randomAccessFileSetLength(randomAccessFile, mFileInfo.getFileSize());
            if (DEBUG) Log.d(TAG, "InitThread# run()# 创建下载空文件成功: " + saveFile.getName());

        } catch (DownloadException downloadException) {
            ///更新下载文件状态：下载错误
            mFileInfo.setStatus(FileInfo.FILE_STATUS_ERROR);
            mHandler.obtainMessage(DownloadHandler.MSG_ERROR, downloadException).sendToTarget();
            return;
        } finally {
            ///关闭连接
            if (connection != null) {
                connection.disconnect();
            }

            ///关闭流Closeable
            Util.closeIO(randomAccessFile);
        }



        /* ----------- 获得下载文件的所有线程信息集合 ----------- */
        ///从数据库获得下载文件的所有线程信息集合
        mDownloadTask.mThreadInfos = mThreadDAO.getAllThreads(
                mFileInfo.getFileUrl(),
                mFileInfo.getFileName(),
                mFileInfo.getFileSize(),
                mFileInfo.getSavePath()
        );

        if (mDownloadTask.mThreadInfos.size() == 0) { ///如果数据库中没有
            if (DEBUG) Log.d(TAG, "InitThread# run()# threadInfos.size() == 0");

            ///重置mFileInfo的下载完的总字节数
            if (DEBUG) Log.d(TAG, "InitThread# run()# 重置mFileInfo的FinishedBytes为0");
            mFileInfo.setFinishedBytes(0);

            ///获得每个线程的长度
            long length = mFileInfo.getFileSize() / mConfig.threadCount;

            ///遍历每个线程
            for (int i = 0; i < mConfig.threadCount; i++) {
                ///创建线程信息
                ThreadInfo threadInfo = new ThreadInfo (
                        ThreadInfo.THREAD_STATUS_NEW,
                        0,
                        0,
                        i * length,
                        (i + 1) * length - 1,
                        mFileInfo.getFileUrl(),
                        mFileInfo.getFileName(),
                        mFileInfo.getFileSize(),
                        mFileInfo.getSavePath()
                );

                ///处理最后一个线程（可能存在除不尽的情况）
                if (i == mConfig.threadCount - 1) {
                    threadInfo.setEnd(mFileInfo.getFileSize() - 1);
                }

                ///向数据库插入线程信息
                long threadId = mThreadDAO.insertThread(threadInfo);
                threadInfo.setId(threadId);

                ///设置线程信息的状态为THREAD_STATUS_INIT
                threadInfo.setStatus(ThreadInfo.THREAD_STATUS_INIT);

                ///添加到线程信息集合中
                mDownloadTask.mThreadInfos.add(threadInfo);
            }
        } else {
            ///重置mFileInfo的下载完的总字节数
            for (ThreadInfo threadInfo : mDownloadTask.mThreadInfos) {
                if (threadInfo.getEnd() - threadInfo.getStart() + 1 == threadInfo.getFinishedBytes()) {
                    threadInfo.setStatus(ThreadInfo.THREAD_STATUS_COMPLETE);
                } else {
                    threadInfo.setStatus(ThreadInfo.THREAD_STATUS_PAUSE);
                }
                mFileInfo.setFinishedBytes(mFileInfo.getFinishedBytes() + threadInfo.getFinishedBytes());
            }

            if (mFileInfo.getFinishedBytes() == mFileInfo.getFileSize()) {
                mFileInfo.setStatus(FileInfo.FILE_STATUS_COMPLETE);
            } else {
                mFileInfo.setStatus(FileInfo.FILE_STATUS_PAUSE);
            }
        }


        /* ----------- 发送消息：始化线程结束 ----------- */
        if (DEBUG) Log.d(TAG, "InitThread# run()# ------- 发送消息：始化线程结束 -------");
        mHandler.obtainMessage(DownloadHandler.MSG_INIT).sendToTarget();
    }
}
