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
    public static final int MSG_INITIALIZED = 0;
    public static final int MSG_STARTED = 1;
    public static final int MSG_PAUSED = 2;
    public static final int MSG_SUCCEED = 3;
    public static final int MSG_FAILED = 4;
    public static final int MSG_STOPPED = 5;
    public static final int MSG_WAITING_FOR_NETWORK = 6;

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
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_PROGRESS");

                ///进度的回调接口
                if (mDownloadListener != null) {
                    if (mFileInfo.getState() != DownloadState.STARTED) {
                        ///文件信息状态为运行时需要修正进度更新显示的下载速度为0
                        ///注意：必须提早更新状态！，否则timer停止后，不再更新进度，也就无法修正下载速度为0了！
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                    } else {
                        long diffTimeMillis = ((long[]) msg.obj)[0];
                        long diffFinishedBytes = ((long[]) msg.obj)[1];
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos, diffTimeMillis, diffFinishedBytes);
                    }
                }

                break;
            case MSG_FAILED:
//                ///更新文件信息的状态：下载错误
//                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：mFileInfo.setState(DownloadState.FAILED)");
//                mFileInfo.setState(DownloadState.FAILED);？？？？？？？？？？？

                ///失败的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onError(mFileInfo, mDownloadTask.mThreadInfos, (Exception) msg.obj);
                }

                break;
            case MSG_INITIALIZED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_INITIALIZED");

                ///状态变化的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.INITIALIZED);
                }

                ///开始下载
                mDownloadTask.start();

                break;
            case MSG_STARTED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STARTED");

                ///下载开始的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STARTED);
                }

                break;
            case MSG_PAUSED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_PAUSED");

                ///下载暂停的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.PAUSED);
                }

                break;
            case MSG_SUCCEED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_SUCCEED");

                ///下载成功的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.SUCCEED);
                }

                break;
            case MSG_STOPPED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STOPPED");

                ///删除数据库中下载文件的所有线程信息
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STOPPED: 删除下载线程");
                mThreadDAO.deleteAllThread(mFileInfo.getFileUrl(), mFileInfo.getFileName(), mFileInfo.getFileSize(), mFileInfo.getSavePath());

                ///删除下载文件
                Log.d(TAG, "MainActivity# onStop()# fileInfo: 删除下载文件");
                new File(mFileInfo.getSavePath() + mFileInfo.getFileName()).delete();

                ///下载停止的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STOPPED);
                }

                break;
            case MSG_WAITING_FOR_NETWORK:
//                ///更新文件信息的状态：下载错误
//                if (DEBUG) Log.d(TAG, "更新文件信息的状态：mFileInfo.setState(FileInfo.WAITING_FOR_NETWORK)");
//                mFileInfo.setState(DownloadState.WAITING_FOR_NETWORK);？？？？？？？？？？？？？？

                // todo ...


                break;
        }
        super.handleMessage(msg);
    }

}
