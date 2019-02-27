package cc.brainbook.android.multithreadhttpdownload.config;

public class Config {
    /**
     * 下载线程的数量（缺省为1）
     *
     * 注意：建议不要太大，取值范围不超过50！否则系统不再分配线程，造成其余下载线程仍处于初始化状态而不能进入运行状态。
     */
    public int threadCount = 1;

    /**
     * 下载进度的更新周期（缺省为1秒）
     */
    public int progressInterval = 1000;

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
    public int bufferSize = 1024 * 4;

}
