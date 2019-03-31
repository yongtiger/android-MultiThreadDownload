package cc.brainbook.android.multithreaddownload.listener;

///\com\amazonaws\mobileconnectors\s3\transferutility\DownloadListener.java
import java.util.List;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;

/**
 * Listener interface for download state and progress changes.
 * All callbacks will be invoked on the main thread.
 */
public interface DownloadListener {
    /**
     * 状态变化的事件
     *
     * @param fileInfo
     * @param threadInfos
     * @param state
     */
    void onStateChanged(FileInfo fileInfo, List<ThreadInfo> threadInfos, DownloadState state);

    /**
     * 进度的监听回调方法
     *
     * @param fileInfo              文件信息，比如用fileInfo.getFinishedTimeMillis()获取实时的耗时（暂停期间不计！）
     * @param threadInfos           线程信息（比如，可实现分段详细显示下载进度条）
     * @param diffTimeMillis        配合文件信息获取下载网速speed
     * @param diffFinishedBytes     配合文件信息获取下载进度progress
     */
    void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes);

    /**
     * 错误的事件
     *
     * @param fileInfo
     * @param threadInfos
     * @param e
     */
    void onError(FileInfo fileInfo, List<ThreadInfo> threadInfos, Exception e);
}
