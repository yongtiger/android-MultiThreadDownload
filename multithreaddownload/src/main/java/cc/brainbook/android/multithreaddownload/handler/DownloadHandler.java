package cc.brainbook.android.multithreaddownload.handler;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;

import cc.brainbook.android.multithreaddownload.DownloadTask;
import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.db.ThreadDAO;
import cc.brainbook.android.multithreaddownload.listener.DownloadListener;

import static cc.brainbook.android.multithreaddownload.BuildConfig.DEBUG;

public class DownloadHandler extends Handler {
    private static final String TAG = "TAG";

    public static final int MSG_PROGRESS = 100;
    ///以下与DownloadState的状态对应
    public static final int MSG_FAILED = -1;
    public static final int MSG_INITIALIZED = 0;
    public static final int MSG_STARTED = 1;
    public static final int MSG_PAUSED = 2;
    public static final int MSG_COMPLETED = 3;
    public static final int MSG_STOPPED = 4;
    public static final int MSG_WAITING_FOR_NETWORK = 5;

    private DownloadTask mDownloadTask;
    private FileInfo mFileInfo;
    private DownloadListener mDownloadListener;
    private ThreadDAO mThreadDAO;

    public DownloadHandler(DownloadTask downloadTask,
                           FileInfo fileInfo,
                           DownloadListener downloadListener,
                           ThreadDAO threadDAO
    ) {
        this.mDownloadTask = downloadTask;
        this.mFileInfo = fileInfo;
        this.mDownloadListener = downloadListener;
        this.mThreadDAO = threadDAO;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PROGRESS:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_PROGRESS");

                ///下载进度回调接口DownloadEvent
                if (mDownloadListener != null) {
                    if (mFileInfo.getState() != DownloadState.STARTED) {
                        ///下载文件状态为运行时需要修正进度更新显示的下载速度为0
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                    } else {
                        long diffTimeMillis = ((long[]) msg.obj)[0];
                        long diffFinishedBytes = ((long[]) msg.obj)[1];
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos, diffTimeMillis, diffFinishedBytes);
                    }
                }

                break;
            case MSG_FAILED:
                ///更新下载文件状态：下载错误
                if (DEBUG) Log.d(TAG, "更新下载文件状态：mFileInfo.setState(FileInfo.FILE_STATUS_ERROR)");
                mFileInfo.setState(DownloadState.FAILED);

                ///下载错误回调接口DownloadEvent
                if (mDownloadListener != null) {
                    mDownloadListener.onError(mFileInfo, mDownloadTask.mThreadInfos, (Exception) msg.obj);
                }

                break;
            case MSG_INITIALIZED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_INITIALIZED");

                ///下载事件接口DownloadEvent
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.INITIALIZED);
                }

                ///开始下载
                mDownloadTask.start();

                break;
            case MSG_STARTED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_STARTED");

                ///下载事件接口DownloadEvent
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STARTED);
                }

                break;
            case MSG_PAUSED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_PAUSED");

                ///下载事件接口DownloadEvent
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.PAUSED);
                }

                break;
            case MSG_COMPLETED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_COMPLETED");

                ///下载完成回调接口DownloadEvent
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.COMPLETED);
                }

                break;
            case MSG_STOPPED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_STOPPED");

                ///删除数据库中文件的所有线程信息
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage(): msg.what = MSG_STOPPED: 删除线程信息");
                mThreadDAO.deleteAllThread(mFileInfo.getFileUrl(), mFileInfo.getFileName(), mFileInfo.getFileSize(), mFileInfo.getSavePath());

                ///删除下载文件
                Log.d(TAG, "MainActivity# onStop()# fileInfo: 删除下载文件");
                new File(mFileInfo.getSavePath() + mFileInfo.getFileName()).delete();

                ///下载停止回调接口DownloadCallback
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STOPPED);
                }

                break;
            case MSG_WAITING_FOR_NETWORK:
                ///更新下载文件状态：下载错误
                if (DEBUG) Log.d(TAG, "更新下载文件状态：mFileInfo.setState(FileInfo.WAITING_FOR_NETWORK)");
                mFileInfo.setState(DownloadState.WAITING_FOR_NETWORK);

                // todo ...


                break;
        }
        super.handleMessage(msg);
    }

}
