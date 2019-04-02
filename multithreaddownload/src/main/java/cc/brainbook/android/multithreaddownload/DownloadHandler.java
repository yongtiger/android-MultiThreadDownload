package cc.brainbook.android.multithreaddownload;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAO;
import cc.brainbook.android.multithreaddownload.listener.DownloadListener;
import cc.brainbook.android.multithreaddownload.util.DownloadUtil;

import static cc.brainbook.android.multithreaddownload.BuildConfig.DEBUG;

public class DownloadHandler extends Handler {
    private static final String TAG = "TAG";

    static final int MSG_PROGRESS = 100;
    ///以下与DownloadState的状态对应
    static final int MSG_INITIALIZED = 1;
    static final int MSG_STARTED = 2;
    static final int MSG_PAUSED = 3;
    static final int MSG_STOPPED = 4;
    static final int MSG_SUCCEED = 5;
    static final int MSG_INIT_FAILED = 6;
    static final int MSG_DOWNLOAD_FAILED = 7;

    private Config mConfig;
    private DownloadTask mDownloadTask;
    private FileInfo mFileInfo;
    private DownloadListener mDownloadListener;
    private ThreadInfoDAO mThreadDAO;

    DownloadHandler(Config config,
                           DownloadTask downloadTask,
                           FileInfo fileInfo,
                           DownloadListener downloadListener,
                           ThreadInfoDAO threadDAO) {
        this.mConfig = config;
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

                ///更新进度
                if (mDownloadListener != null) {
                    if (mFileInfo.getState() == DownloadState.STARTED) {
                        final long diffTimeMillis = ((long[]) msg.obj)[0];
                        final long diffFinishedBytes = ((long[]) msg.obj)[1];
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos, diffTimeMillis, diffFinishedBytes);
                    } else {
                        ///修正进度更新显示的下载速度为0
                        mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
                    }
                }

                break;
            case MSG_INITIALIZED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_INITIALIZED");

                ///从数据库获得所有线程信息
                mDownloadTask.mThreadInfos = mThreadDAO.loadAllThreadsInfos(
                        mFileInfo.getFileUrl(),
                        mFileInfo.getFileName(),
                        mFileInfo.getFileSize(),
                        mFileInfo.getSavePath());

                if (!mDownloadTask.mThreadInfos.isEmpty()) {
                    ///重置文件信息的下载完成的总字节数、总耗时
                    setFileInfoFinished();

                    DownloadState state = DownloadUtil.getStateFromThreadInfos(mDownloadTask.mThreadInfos);
                    if (DownloadState.INITIALIZED == state) {
                        ///继续
                    } else if (DownloadState.PAUSED == state) {
                        ///更改状态为下载暂停（PAUSED）
                        changeStateToPaused(true);

                        ///是否初始化后立即开始下载
                        if (msg.obj != null && (boolean) msg.obj) {
                            mDownloadTask.start();
                        }

                        return;
                    } else if (DownloadState.SUCCEED == state) {
                        ///更改状态为下载成功（SUCCEED）
                        changeStateToSucceed(true);

                        return;
                    } else if (DownloadState.INIT_FAILED == state) {
                        ///更改状态为初始化失败（INIT_FAILED）
                        changeStateToInitFailed();

                        return;
                    } else if (DownloadState.DOWNLOAD_FAILED == state) {
                        ///更改状态为下载失败（DOWNLOAD_FAILED）
                        changeStateToDownloadFailed();

                        return;
                    }
                }

                ///插入数据库记录前，再次确保删除下载文件的所有线程信息
                mThreadDAO.deleteAllThreadInfos(mFileInfo.getFileUrl(),
                        mFileInfo.getFileName(),
                        mFileInfo.getFileSize(),
                        mFileInfo.getSavePath());

                ///根据线程数量创建线程信息，并添加到线程信息集合中
                mDownloadTask.mThreadInfos = DownloadUtil.createToThreadInfos(mFileInfo, mConfig.threadCount, mThreadDAO);

                ///更改状态为初始化完成（INITIALIZED）
                changeStateToInitialized();

                ///是否初始化后立即开始下载
                if (msg.obj != null && (boolean) msg.obj) {
                    mDownloadTask.start();
                }

                break;
            case MSG_STARTED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STARTED");

                ///更改状态为下载开始（STARTED）
                changeStateToStarted();

                break;
            case MSG_PAUSED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_PAUSED");

                ///更改状态为下载暂停（PAUSED）
                changeStateToPaused(false);

                break;
            case MSG_STOPPED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_STOPPED");

                ///重置下载
                mDownloadTask.reset();

                ///更改状态为下载停止（STOPPED）
                changeStateToStopped();

                break;
            case MSG_SUCCEED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_SUCCEED");

                ///更改状态为下载成功（SUCCEED）
                changeStateToSucceed(false);

                break;
            case MSG_INIT_FAILED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_INIT_FAILED");

                ///错误的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onError(mFileInfo, mDownloadTask.mThreadInfos, (Exception) msg.obj);
                }

                ///更改状态为下载失败（DOWNLOAD_FAILED）
                changeStateToInitFailed();

                break;
            case MSG_DOWNLOAD_FAILED:
                if (DEBUG) Log.d(TAG, "DownloadHandler# handleMessage()# msg.what = MSG_DOWNLOAD_FAILED");

                ///停止定时器
                mDownloadTask.stopTimer();

                ///错误的回调接口
                if (mDownloadListener != null) {
                    mDownloadListener.onError(mFileInfo, mDownloadTask.mThreadInfos, (Exception) msg.obj);
                }

                ///更改状态为下载失败（DOWNLOAD_FAILED）
                changeStateToDownloadFailed();

                break;
        }
    }

    /**
     * 更改状态为初始化完成（INITIALIZED）
     */
    private void changeStateToInitialized() {
        ///更新文件信息的状态：初始化结束（INITIALIZED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToInitialized()# 更改状态为初始化完成（INITIALIZED）");
        mFileInfo.setState(DownloadState.INITIALIZED);

        ///修正进度更新显示的下载速度为0
        if (mDownloadListener != null) {
            mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
        }

        ///状态变化的回调接口：初始化结束（INITIALIZED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.INITIALIZED);
        }
    }

    /**
     * 更改状态为下载开始（STARTED）
     */
    private void changeStateToStarted() {
        ///更新文件信息的状态：下载开始（STARTED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToStarted()# 更改状态为下载开始（STARTED）");
        mFileInfo.setState(DownloadState.STARTED);

        ///状态变化的回调接口：下载开始（STARTED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STARTED);
        }
    }

    /**
     * 更改状态为下载暂停（PAUSED）
     *
     * @param isAtOnce  是否让定时器再运行一次进度更新（解决进度更新显示99%的问题）
     */
    private void changeStateToPaused(boolean isAtOnce) {
        ///更新文件信息的状态：下载暂停（PAUSED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToPaused()# 更改状态为下载暂停（PAUSED）");
        mFileInfo.setState(DownloadState.PAUSED);

        if (isAtOnce) {
            ///修正进度更新显示的下载速度为0
            if (mDownloadListener != null) {
                mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
            }
        } else {
            ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]可以取消定时器Timer
            if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToPaused()# 可以取消定时器Timer");
            mDownloadTask.mayStopTimer = true;
        }

        ///状态变化的回调接口：下载暂停（PAUSED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.PAUSED);
        }
    }

    /**
     * 更改状态为下载停止（STOPPED）
     */
    private void changeStateToStopped() {
        ///更新文件信息的状态：下载停止（STOPPED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToStopped()# 更改状态为下载停止（STOPPED）");
        mFileInfo.setState(DownloadState.STOPPED);

        ///修正进度更新显示的下载速度为0
        if (mDownloadListener != null) {
            mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
        }

        ///状态变化的回调接口：下载停止（STOPPED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.STOPPED);
        }
    }

    /**
     * 更改状态为下载成功（SUCCEED）
     *
     * @param isAtOnce  是否让定时器再运行一次进度更新（解决进度更新显示99%的问题）
     */
    private void changeStateToSucceed(boolean isAtOnce) {
        ///更新文件信息的状态：下载成功（SUCCEED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToSucceed()# 更改状态为下载成功（SUCCEED）");
        mFileInfo.setState(DownloadState.SUCCEED);

        if (isAtOnce) {
            ///修正进度更新显示的下载速度为0
            if (mDownloadListener != null) {
                mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
            }
        } else {
            ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]可以取消定时器Timer
            if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToSucceed()# 可以取消定时器Timer");
            mDownloadTask.mayStopTimer = true;
        }

        ///状态变化的回调接口：下载成功（SUCCEED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.SUCCEED);
        }
    }

    /**
     * 更改状态为初始化失败（INIT_FAILED）
     */
    private void changeStateToInitFailed() {
        ///更新文件信息的状态：初始化失败（INIT_FAILED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToInitFailed()# 更改状态为初始化失败（INIT_FAILED）");
        mFileInfo.setState(DownloadState.INIT_FAILED);

        ///状态变化的回调接口：初始化失败（INIT_FAILED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.INIT_FAILED);
        }
    }

    /**
     * 更改状态为下载失败（DOWNLOAD_FAILED）
     */
    private void changeStateToDownloadFailed() {
        ///更新文件信息的状态：下载失败（DOWNLOAD_FAILED）
        if (DEBUG) Log.d(TAG, "DownloadHandler# changeStateToInitFailed()# 更改状态为下载失败（DOWNLOAD_FAILED）");
        mFileInfo.setState(DownloadState.DOWNLOAD_FAILED);

        ///修正进度更新显示的下载速度为0
        if (mDownloadListener != null) {
            mDownloadListener.onProgress(mFileInfo, mDownloadTask.mThreadInfos,0, 0);
        }

        ///状态变化的回调接口：下载失败（DOWNLOAD_FAILED）
        if (mDownloadListener != null) {
            mDownloadListener.onStateChanged(mFileInfo, mDownloadTask.mThreadInfos, DownloadState.DOWNLOAD_FAILED);
        }
    }

    /**
     * 重置下载文件的下载完的总字节数、总耗时
     */
    private void setFileInfoFinished() {
        ///[修正下载完成（成功/失败/停止）后重新开始下载]
        ///重置文件信息的已经完成的总耗时（毫秒）、总字节数
        mFileInfo.setFinishedTimeMillis(0);
        mFileInfo.setFinishedBytes(0);

        for (ThreadInfo threadInfo : mDownloadTask.mThreadInfos) {
            mFileInfo.setFinishedBytes(mFileInfo.getFinishedBytes() + threadInfo.getFinishedBytes());

            ///注意：threadInfo数据库中保存的是mFileInfo的FinishedTimeMillis（没有做累计！）
            ///可以近似认为各线程的开始时间相同，但结束时间不同，所以取最长的
            if (threadInfo.getFinishedTimeMillis() > mFileInfo.getFinishedTimeMillis()) {
                mFileInfo.setFinishedTimeMillis(threadInfo.getFinishedTimeMillis());
            }
        }
    }

}
