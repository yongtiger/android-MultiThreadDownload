package cc.brainbook.android.multithreadhttpdownload.interfaces;

import java.util.List;

import cc.brainbook.android.multithreadhttpdownload.bean.FileInfo;
import cc.brainbook.android.multithreadhttpdownload.bean.ThreadInfo;

/**
 * 下载进度监听器
 */
public interface OnProgressListener {
    /**
     * 下载进度的监听回调方法
     *
     * @param fileInfo              提供下载文件基本信息，比如用fileInfo.getFinishedTimeMillis()获取实时的下载耗时（暂停期间不计！）
     * @param threadInfos           提供了全部下载线程信息（比如，可实现分段详细显示下载进度条）
     * @param diffTimeMillis        配合fileInfo获取下载网速speed
     * @param diffFinishedBytes     配合fileInfo获取下载进度progress
     */
    void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes);

}
