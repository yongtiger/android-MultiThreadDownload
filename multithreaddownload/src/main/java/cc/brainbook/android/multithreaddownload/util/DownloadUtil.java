package cc.brainbook.android.multithreaddownload.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAO;
import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;

public class DownloadUtil {

    /**
     * 创建下载空占位文件
     *
     * 注意：可以不要创建占位文件（下载文件大小将实时增长）
     *
     * @param savePath
     * @param fileName
     * @param fileSize
     */
    public static void createEmptySaveFile(String savePath, String fileName, long fileSize) {
        ///获得保存文件
        File saveFile = new File(savePath, fileName);

        ///如果保存文件存在则删除
        if (saveFile.exists()) {
            if (!saveFile.delete()) {
                throw new DownloadException(DownloadException.EXCEPTION_FILE_DELETE_EXCEPTION, "The file cannot be deleted: " + saveFile);
            }
        }

        ///创建下载空文件
        RandomAccessFile randomAccessFile = HttpDownloadUtil.getRandomAccessFile(saveFile, "rwd");
        HttpDownloadUtil.randomAccessFileSetLength(randomAccessFile, fileSize);

        ///关闭流Closeable
        Util.closeIO(randomAccessFile);
    }

    /**
     * 从线程信息集合获取下载状态
     *
     * 如果threadInfos为null，则返回INITIALIZED
     *
     * @param threadInfos
     * @return     如果threadInfos为null，则返回初始化状态（INITIALIZED）
     *              如果threadInfos为空，则返回停止状态（STOPPED）
     *              遍历所有线程信息，如果存在停止状态，则说明文件信息的状态是停止状态
     *              如果存在初始化失败状态，则说明文件信息的状态是初始化失败状态（INIT_FAILED）
     *              如果存在下载失败状态，则说明文件信息的状态是下载失败状态（DOWNLOAD_FAILED）
     *              如果存在暂停状态，则说明文件信息的状态是暂停状态（PAUSED）
     *              否则就应该是成功状态（SUCCEED）
     */
    public static DownloadState getStateFromThreadInfos(List<ThreadInfo> threadInfos) {
        if (threadInfos == null) {
            return DownloadState.INITIALIZED;
        } else if (threadInfos.isEmpty()) {
            return DownloadState.STOPPED;
        }

        DownloadState state = null;
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo.getState() == DownloadState.STOPPED) {
                state = DownloadState.STOPPED;
                break;
            }
        }
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo.getState() == DownloadState.INIT_FAILED) {
                state = DownloadState.INIT_FAILED;
                break;
            }
        }
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo.getState() == DownloadState.DOWNLOAD_FAILED) {
                state = DownloadState.DOWNLOAD_FAILED;
                break;
            }
        }
        if (state == null) {
            for (ThreadInfo threadInfo : threadInfos) {
                if (threadInfo.getState() == DownloadState.PAUSED) {
                    state = DownloadState.PAUSED;
                    break;
                }
            }
        }
        if (state == null) {
            state = DownloadState.SUCCEED;
        }

        return state;
    }

    /**
     * 根据线程数量创建线程信息，并添加到线程信息集合中
     *
     * @param fileInfo
     * @param threadCount
     * @param threadInfoDAO
     * @return
     */
    public static List<ThreadInfo> createToThreadInfos(FileInfo fileInfo, int threadCount, ThreadInfoDAO threadInfoDAO) {
        List<ThreadInfo> threadInfos = new ArrayList<>();

        ///获得每个线程的长度
        long length = fileInfo.getFileSize() / threadCount;

        ///遍历每个线程
        for (int i = 0; i < threadCount; i++) {
            ///创建线程信息
            ThreadInfo threadInfo = new ThreadInfo (
                    DownloadState.NEW,
                    0,
                    0,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    0,
                    i * length,
                    (i + 1) * length - 1,
                    fileInfo.getFileUrl(),
                    fileInfo.getFileName(),
                    fileInfo.getFileSize(),
                    fileInfo.getSavePath());

            ///处理最后一个线程（可能存在除不尽的情况）
            if (i == threadCount - 1) {
                threadInfo.setEnd(fileInfo.getFileSize() - 1);
            }

            ///设置线程信息的状态为初始化
            threadInfo.setState(DownloadState.INITIALIZED);

            ///向数据库插入线程信息
            long threadId = threadInfoDAO.saveThreadInfo(threadInfo, System.currentTimeMillis(), System.currentTimeMillis());
            threadInfo.setId(threadId);

            ///添加到线程信息集合中
            threadInfos.add(threadInfo);
        }

        return threadInfos;
    }

}
