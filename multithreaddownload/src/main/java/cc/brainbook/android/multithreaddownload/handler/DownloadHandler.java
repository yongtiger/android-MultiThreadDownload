package cc.brainbook.android.multithreaddownload.handler;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;

import cc.brainbook.android.multithreaddownload.DownloadTask;
import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAO;
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

    private DownloadTask mDownloadTask;
    private FileInfo mFileInfo;
    private DownloadListener mDownloadListener;
    private ThreadInfoDAO mThreadDAO;

    public DownloadHandler(DownloadTask downloadTask,
                           FileInfo fileInfo,
                           DownloadListener downloadListener,
                           ThreadInfoDAO threadDAO) {
        this.mDownloadTask = downloadTask;
        this.mFileInfo = fileInfo;
        this.mDownloadListener = downloadListener;
        this.mThreadDAO = threadDAO;
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        switch (msg.what) {
            case MSG_PROGRESS:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_PROGRESS");

                ///进度的回调接口
                if (mDownloadListener != null) {
                    if (mFileInfo.getState() == DownloadState.STARTED) {
                        long diffTimeMillis = ((long[]) msg.obj)[0];
                        long diffFinishedBytes = ((long[]) msg.obj)[1];
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos, diffTimeMillis, diffFinishedBytes);
                    } else {
                        ///修正进度更新显示的下载速度为0
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                    }
                }

                break;
            case MSG_INITIALIZED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_INITIALIZED");

                ///停止定时器
                mDownloadTask.stopTimer();

                ///修正进度更新显示的下载速度为0
                if (mDownloadListener != null) {
                    mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                }

                ///更新文件信息的状态：初始化结束（INITIALIZED）
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：初始化结束（INITIALIZED）");
                mFileInfo.setState(DownloadState.INITIALIZED);

                ///状态变化的回调接口：初始化结束（INITIALIZED）
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.INITIALIZED);
                }

                break;
            case MSG_STARTED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STARTED");

                ///更新文件信息的状态：下载开始（STARTED）
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：下载开始（STARTED）");
                mFileInfo.setState(DownloadState.STARTED);

                ///状态变化的回调接口：下载开始（STARTED）
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STARTED);
                }

                break;
            case MSG_PAUSED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_PAUSED");

                ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]可以取消定时器Timer
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# barrier: 可以取消定时器Timer");
                mDownloadTask.mayStopTimer = true;

                ///更新文件信息的状态：下载暂停（PAUSED）
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：下载暂停（PAUSED）");
                mFileInfo.setState(DownloadState.PAUSED);

                ///状态变化的回调接口：下载暂停（PAUSED）
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.PAUSED);
                }

                break;
            case MSG_SUCCEED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_SUCCEED");

                ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]可以取消定时器Timer
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 可以取消定时器Timer");
                mDownloadTask.mayStopTimer = true;

                ///修正进度更新显示的下载速度为0
                if (mDownloadListener != null) {
                    mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                }

                ///更新文件信息的状态：下载成功（SUCCEED）
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：下载成功（SUCCEED）");
                mFileInfo.setState(DownloadState.SUCCEED);

                ///状态变化的回调接口：下载成功（SUCCEED）
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.SUCCEED);
                }

                break;
            case MSG_FAILED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_FAILED");

                ///停止定时器
                mDownloadTask.stopTimer();

                ///错误的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onError(mFileInfo, mDownloadTask.mThreadInfos, (Exception) msg.obj);
                }

                ///更新文件信息的状态：下载错误（FAILED）
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：下载错误（FAILED）");
                mFileInfo.setState(DownloadState.FAILED);

                ///状态变化的回调接口：下载失败（FAILED）
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.FAILED);
                }

                break;
            case MSG_STOPPED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STOPPED");

                ///删除数据库中下载文件的所有线程信息
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STOPPED: 删除下载线程");
                mThreadDAO.deleteAllThreadInfos(mFileInfo.getFileUrl(), mFileInfo.getFileName(), mFileInfo.getFileSize(), mFileInfo.getSavePath());

                ///删除下载文件
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# fileInfo: 删除下载文件");
                new File(mFileInfo.getSavePath() + mFileInfo.getFileName()).delete();

                ///[修正下载完成（成功/失败/停止）后重新开始下载]
                ///重置文件信息的已经完成的总耗时（毫秒）、总字节数
                mFileInfo.setFinishedTimeMillis(0);
                mFileInfo.setFinishedBytes(0);

                ///清空线程信息集合（innerStart()中不必访问数据库！而如为null则需从数据库加载）
                mDownloadTask.mThreadInfos.clear();

                ///停止定时器
                mDownloadTask.stopTimer();

                ///修正进度更新显示的下载速度为0
                if (mDownloadListener != null) {
                    mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                }

                ///更新文件信息的状态：下载停止（STOPPED）
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# 更新文件信息的状态：下载停止（STOPPED）");
                mFileInfo.setState(DownloadState.STOPPED);

                ///状态变化的回调接口：下载停止（STOPPED）
                if (mDownloadListener != null) {
                    mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STOPPED);
                }

                break;
        }
    }

}
