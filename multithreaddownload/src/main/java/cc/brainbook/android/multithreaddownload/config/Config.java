package cc.brainbook.android.multithreaddownload.config;

public class Config {
    /** Kilobytes */
    public static final int KB = 1024;

    /** Megabytes */
    public static final int MB = 1024 * KB;

    /** Gigabytes */
    public static final long GB = 1024 * MB;

    /** The maximum allowed parts in a multipart download. */
    public static final int MAXIMUM_DOWNLOAD_PARTS = 100;

    /**
     * Default minimum part size for download parts. Anything below this will use
     * a single download
     */
    public static final int MINIMUM_DOWNLOAD_PART_SIZE = 5 * MB;

    /** Default encoding used for text data */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * 网络连接超时（缺省为10秒）
     */
    public int connectTimeout = 10000;

    /**
     * 缓冲区大小（缺省为1k字节）
     *
     * 注意：BufferedInputStream的默认缓冲区大小是8192字节，
     * 当每次读取数据量接近或远超这个值时，两者效率就没有明显差别了。
     * https://blog.csdn.net/xisuo002/article/details/78742631
     */
    public int bufferSize = 16 * KB;

    /**
     * 下载线程的数量（缺省为1）
     *
     * 注意：建议不要太大，取值范围不超过50！否则系统不再分配线程，造成其余下载线程仍处于初始化状态而不能进入运行状态。
     */
    public int threadCount = 1;

    /**
     * 下载进度的更新周期（缺省为1秒）、定时器延时
     */
    public int progressInterval = 1000;
    public static final long progressDelay = 1000;

}
