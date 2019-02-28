package cc.brainbook.android.multithreaddownload.db;

import java.util.List;

import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;

public interface ThreadDAO {
    long insertThread(ThreadInfo threadInfo);

    int updateThread(long thread_id, long finished);

    boolean isExists(long thread_id);

    int deleteAllThread(String fileUrl, String fileName, long fileSize, String savePath);

    List<ThreadInfo> getAllThreads(String fileUrl, String fileName, long fileSize, String savePath);
}
