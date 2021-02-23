package cc.brainbook.android.multithreaddownload;

import android.content.Context;
import android.text.TextUtils;

import java.net.HttpURLConnection;

import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.util.HttpDownloadUtil;

/**
 * 初始化线程
 *
 * 由网络连接获得文件名、文件长度
 */
public class InitThread extends Thread {
    private Context mContext;
    private Config mConfig;
    private FileInfo mFileInfo;
    private DownloadHandler mHandler;
    private boolean isStart;

    InitThread (Context context,
                Config config,
                FileInfo fileInfo,
                DownloadHandler handler,
                boolean isStart) {
        this.mContext = context;
        this.mConfig = config;
        this.mFileInfo = fileInfo;
        this.mHandler = handler;
        this.isStart = isStart;
    }

    @Override
    public void run() {
        super.run();

        HttpURLConnection connection = null;
        try {
            ///由下载文件的URL网址建立网络连接
            connection = HttpDownloadUtil.openConnection(mFileInfo.getFileUrl(), mConfig.connectTimeout);

            ///进行网络连接
            HttpDownloadUtil.connect(connection);

            ///处理网络连接的响应码，如果网络连接connection的响应码为200，则开始下载过程，否则抛出异常
            HttpDownloadUtil.handleResponseCode(connection, HttpURLConnection.HTTP_OK);

            ///由网络连接获得文件名
            if (TextUtils.isEmpty(mFileInfo.getFileName())) {
                mFileInfo.setFileName(HttpDownloadUtil.getUrlFileName(connection));
            }

            ///由网络连接获得文件长度
            if (mFileInfo.getFileSize() <= 0) {
                ///注意：connection.getContentLength()最大为2GB，使用connection.getHeaderField("Content-Length")可以突破2GB限制
                ///http://szuwest.github.io/tag/android-download.html
//            mFileInfo.setFileSize(connection.getContentLength());
                mFileInfo.setFileSize(Long.parseLong(connection.getHeaderField("Content-Length")));
                if (mFileInfo.getFileSize() <= 0) {
                    throw new DownloadException(DownloadException.EXCEPTION_FILE_DELETE_EXCEPTION,
                            mContext.getString(R.string.msg_the_ile_size_is_not_valid, mFileInfo.getFileSize()));
                }
            }

        } catch (Exception e) {
            ///发送消息：下载错误
            mHandler.obtainMessage(DownloadHandler.MSG_INIT_FAILED, e).sendToTarget();
            return;
        } finally {
            ///关闭连接
            if (connection != null) {
                connection.disconnect();
            }
        }

        ///发送消息：初始化完成
        mHandler.obtainMessage(DownloadHandler.MSG_INITIALIZED, isStart).sendToTarget();
    }

}
