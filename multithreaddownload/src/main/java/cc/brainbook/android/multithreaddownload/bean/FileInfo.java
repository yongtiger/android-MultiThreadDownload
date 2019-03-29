package cc.brainbook.android.multithreaddownload.bean;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;

/**
 * 文件信息
 */
public class FileInfo {
    /**
     * 状态标志
     *
     * 注意：考虑到多线程访问，必须声明为volatile（是一种轻量级的synchronized）
     *
     * https://www.jianshu.com/p/31e5ab16935f
     * https://blog.csdn.net/changlei_shennan/article/details/44039905
     */
    private volatile DownloadState state;

    /**
     * 已经完成的总耗时（毫秒）
     *
     * 注意：考虑到断点续传，在停止状态不计算在内！
     */
    private long finishedTimeMillis;

    /**
     * 已经完成的总字节数
     *
     * 注意：考虑到多线程访问，必须声明为volatile（是一种轻量级的synchronized）
     *
     * https://www.jianshu.com/p/31e5ab16935f
     * https://blog.csdn.net/changlei_shennan/article/details/44039905
     */
    private volatile long finishedBytes;

    /**
     * 以下四个决定了下载文件的唯一性，即ID
     */
    private String fileUrl;
    private String fileName;
    private long fileSize;
    private String savePath;

    public FileInfo() {
        state = DownloadState.NEW;
    }

    public DownloadState getState() {
        return state;
    }

    public void setState(DownloadState state) {
        this.state = state;
    }

    public long getFinishedTimeMillis() {
        return finishedTimeMillis;
    }

    public void setFinishedTimeMillis(long finishedTimeMillis) {
        this.finishedTimeMillis = finishedTimeMillis;
    }

    public long getFinishedBytes() {
        return finishedBytes;
    }

    public void setFinishedBytes(long finishedBytes) {
        this.finishedBytes = finishedBytes;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "state=" + state +
                ", finishedTimeMillis=" + finishedTimeMillis +
                ", finishedBytes=" + finishedBytes +
                ", fileUrl='" + fileUrl + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", savePath='" + savePath + '\'' +
                '}';
    }
}
