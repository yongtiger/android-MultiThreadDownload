package cc.brainbook.android.multithreaddownload.db;

import java.util.List;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;

public interface ThreadInfoDAO {
    long saveThreadInfo(ThreadInfo threadInfo, long created_time_millis, long updated_time_millis);

    int updateThreadInfo(long thread_id, DownloadState state, long finishedTimeBytes, long finishedTimeMillis, long updated_time_millis);

    boolean isExists(long thread_id);

    int deleteAllThreadInfos(String fileUrl, String fileName, long fileSize, String savePath);

    List<ThreadInfo> loadAllThreadsInfos(String fileUrl, String fileName, long fileSize, String savePath);
}
