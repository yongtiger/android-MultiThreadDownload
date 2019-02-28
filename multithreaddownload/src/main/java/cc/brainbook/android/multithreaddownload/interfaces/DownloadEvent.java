package cc.brainbook.android.multithreaddownload.interfaces;

import java.util.List;

import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;

/**
 * 下载事件接口
 */
public interface DownloadEvent {
    /**
     * 下载初始化的事件
     *
     * @param fileInfo
     * @param threadInfos
     */
    void onInit(FileInfo fileInfo, List<ThreadInfo> threadInfos);

    /**
     * 下载开始的事件
     *
     * @param fileInfo
     */
    void onStart(FileInfo fileInfo, List<ThreadInfo> threadInfos);

    /**
     * 下载暂停的事件
     *
     * @param fileInfo
     * @param threadInfos
     */
    void onPause(FileInfo fileInfo, List<ThreadInfo> threadInfos);

    /**
     * 下载停止的事件
     *
     * @param fileInfo
     * @param threadInfos
     */
    void onStop(FileInfo fileInfo, List<ThreadInfo> threadInfos);

    /**
     * 下载完成的事件
     *
     * @param fileInfo
     * @param threadInfos
     */
    void onComplete(FileInfo fileInfo, List<ThreadInfo> threadInfos);

}
