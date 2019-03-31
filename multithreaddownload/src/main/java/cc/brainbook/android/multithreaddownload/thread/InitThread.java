package cc.brainbook.android.multithreaddownload.thread;

import android.text.TextUtils;

import java.net.HttpURLConnection;

import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.handler.DownloadHandler;
import cc.brainbook.android.multithreaddownload.util.HttpDownloadUtil;

/**
 * 初始化线程
 *
 * 由网络连接获得文件名、文件长度
 */
public class InitThread extends Thread {
    private Config mConfig;
    private FileInfo mFileInfo;
    private DownloadHandler mHandler;

    public InitThread (Config config,
                      FileInfo fileInfo,
                      DownloadHandler handler) {
        this.mConfig = config;
        this.mFileInfo = fileInfo;
        this.mHandler = handler;
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

            ///由网络连接获得文件长度（建议用long类型，int类型最大为2GB）
            mFileInfo.setFileSize(connection.getContentLength());
            if (mFileInfo.getFileSize() <= 0) {
                throw new DownloadException(DownloadException.EXCEPTION_FILE_DELETE_EXCEPTION, "The file size is not valid: " + mFileInfo.getFileSize());
            }
        } catch (Exception e) {
            ///发送消息：下载错误
            mHandler.obtainMessage(DownloadHandler.MSG_FAILED, e).sendToTarget();
            return;
        } finally {
            ///关闭连接
            if (connection != null) {
                connection.disconnect();
            }
        }

        ///发送消息：下载初始化
        mHandler.obtainMessage(DownloadHandler.MSG_INITIALIZED).sendToTarget();
    }

}
