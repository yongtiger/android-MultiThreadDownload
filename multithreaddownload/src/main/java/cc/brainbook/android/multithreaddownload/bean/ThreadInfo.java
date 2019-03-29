package cc.brainbook.android.multithreaddownload.bean;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;

public class ThreadInfo {
    /**
     * 本线程的状态标志
     *
     * 注意：只用到其中的暂停和完成两个状态
     */
    private DownloadState state;

    /**
     * 本线程已经下载的字节数
     */
    private long finishedBytes;

    private long id;
    private long start;
    private long end;

    ///以下四个决定了下载文件的唯一性，即ID
    private String fileUrl;
    private String fileName;
    private long fileSize;
    private String savePath;

    public ThreadInfo() {}

    public ThreadInfo(DownloadState state,
                      long finishedBytes,
                      long id,
                      long start,
                      long end,
                      String fileUrl,
                      String fileName,
                      long fileSize,
                      String savePath
    ) {
        this.state = state;
        this.finishedBytes = finishedBytes;
        this.id = id;
        this.start = start;
        this.end = end;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.savePath = savePath;
    }

    public DownloadState getState() {
        return state;
    }

    public void setState(DownloadState state) {
        this.state = state;
    }

    public long getFinishedBytes() {
        return finishedBytes;
    }

    public void setFinishedBytes(long finishedBytes) {
        this.finishedBytes = finishedBytes;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
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
        return "ThreadInfo{" +
                "state=" + state +
                ", finishedBytes=" + finishedBytes +
                ", id=" + id +
                ", start=" + start +
                ", end=" + end +
                ", fileUrl='" + fileUrl + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", savePath='" + savePath + '\'' +
                '}';
    }
}
