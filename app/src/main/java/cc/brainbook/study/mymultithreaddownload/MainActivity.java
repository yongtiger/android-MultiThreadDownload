package cc.brainbook.study.mymultithreaddownload;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cc.brainbook.android.multithreaddownload.DownloadTask;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.listener.DownloadListener;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "TAG";

    /**
     * 下载文件保存目录（可选）
     *
     * 默认为系统SD卡的下载目录context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)。参考：Util.getDefaultFilesDir(Context context)
     * 用户可通过DownloadTask#setSavePath(String savePath)设置
     */
    public static final String DOWNLOAD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Downloads/";

    public TextView mTextView;

    private DownloadTask mDownloadTask;
    private MyDownloadListener mDownloadListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "============== MainActivity# onCreate()# ==============");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = findViewById(R.id.tvTextView);

        ///Android 6.0以上版本必须动态设置权限
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        } else {
            init();
        }

    }

    ///https://developer.android.com/training/permissions/requesting?hl=zh-cn#handle-response
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    init();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "============== MainActivity# onDestroy()# ==============");

        ///Activity退出后应调用下载暂停来保存下载文件信息（即断点）到数据库
        pauseDownload(mDownloadTask);

        ///避免内存泄漏
        mDownloadTask.setDownloadListener(null);

        super.onDestroy();
    }

    public void init() {
        ///实例化下载监听器对象
        mDownloadListener = new MyDownloadListener();

        ///创建下载任务类DownloadTask实例，并链式配置参数
        ///实例化DownloadTask时传入Context引用，方便操作（但要留意引起内存泄漏！）
        mDownloadTask = new DownloadTask(getApplicationContext())
                .setFileUrl("http://ljdy.tv/test/ljdy.apk")
//                .setFileName("ljdy.apk")
                .setSavePath(DOWNLOAD_PATH)
//                .setThreadCount(3)
                .setDownloadListener(mDownloadListener);

    }

    public void startDownload(View view) {
        startDownload(mDownloadTask);
    }

    public void stopDownload(View view) {
        stopDownload(mDownloadTask);
    }

    public void pauseDownload(View view) {
        pauseDownload(mDownloadTask);
    }

    private void startDownload(DownloadTask downloadTask) {
        if (downloadTask != null) {
            downloadTask.start();
        }
    }

    private void stopDownload(DownloadTask downloadTask) {
        if (downloadTask != null) {
            downloadTask.stop();
        }
    }

    private void pauseDownload(DownloadTask downloadTask) {
        if (downloadTask != null) {
            downloadTask.pause();
        }
    }

    private class MyDownloadListener implements DownloadListener {
        @Override
        public void onStateChanged(FileInfo fileInfo, List<ThreadInfo> threadInfos, DownloadState state) {
            Log.d(TAG, "MainActivity# MyDownloadListener# onStateChanged()# ---------- " + state + " ----------");

            switch (state) {
                case NEW:

                    break;
                case INITIALIZED:

                    break;
                case STARTED:

                    break;
                case PAUSED:

                    break;
                case SUCCEED:
                    ///下载文件URL
                    String fileUrl = fileInfo.getFileUrl();
                    ///下载文件名
                    String fileName = fileInfo.getFileName();
                    ///下载文件大小
                    long fileSize = fileInfo.getFileSize();
                    ///下载文件保存路径
                    String savePath = fileInfo.getSavePath();
                    ///已经下载完的总耗时（毫秒）
                    long finishedTimeMillis = fileInfo.getFinishedTimeMillis();
                    ///已经下载完的总字节数
                    long finishedBytes = fileInfo.getFinishedBytes();

                    break;
                case STOPPED:

                    break;
                case FAILED:

                    break;
                case UNKNOWN:

                    break;
            }

            Log.d(TAG, "MainActivity# MyDownloadListener# onStateChanged()# fileInfo: " + fileInfo);
            if (threadInfos != null) {
                for (ThreadInfo threadInfo : threadInfos) {
                    Log.d(TAG, "MainActivity# MyDownloadListener# onStateChanged()# threadInfos: "
                            + threadInfo.getId() + ", " + threadInfo.getState() + ", " + threadInfo.getFinishedBytes());
                }
            }
            Log.d(TAG, "MainActivity# MyDownloadListener# onStateChanged()# ------------------------------");
        }

        @Override
        public void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes) {
            Log.d(TAG, "MainActivity# onProgress()# fileInfo: " + fileInfo);
//            if (threadInfos != null) {
//                for (ThreadInfo threadInfo : threadInfos) {
//                    Log.d(TAG, "MainActivity# MyDownloadListener# onStateChanged()# threadInfos: "
//                            + threadInfo.getId() + ", " + threadInfo.getState() + ", " + threadInfo.getFinishedBytes());
//                }
//            }
            Log.d(TAG, "MainActivity# onProgress()# diffTimeMillis: " + diffTimeMillis);
            Log.d(TAG, "MainActivity# onProgress()# diffFinishedBytes: " + diffFinishedBytes);

            ///避免除0异常
            int progress = fileInfo.getFinishedBytes() == 0 ? 0 : (int) (fileInfo.getFinishedBytes() * 100 / fileInfo.getFileSize());
            long speed = diffFinishedBytes == 0 ? 0 : diffFinishedBytes / diffTimeMillis;
            Log.d(TAG, "MainActivity# onProgress()# progress, speed: " + progress + ", " + speed);

            mTextView.setText(progress + ", " + speed);
        }

        @Override
        public void onError(FileInfo fileInfo, List<ThreadInfo> threadInfos, Exception e) {
            e.printStackTrace();
            if (e.getCause() == null) {
                Log.d(TAG, "MainActivity# onError()# Message: " + e.getMessage());
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "MainActivity# onError()# Message: " + e.getMessage() + "\n" + e.getCause().getMessage());
                Toast.makeText(getApplicationContext(), e.getMessage() + "\n" + e.getCause().getMessage(), Toast.LENGTH_LONG).show();
            }

            if (e instanceof DownloadException) {
                DownloadException downloadException = (DownloadException) e;

                if (DownloadException.EXCEPTION_FILE_URL_NULL == downloadException.getCode()) {

                } else if (DownloadException.EXCEPTION_SAVE_PATH_MKDIR == downloadException.getCode()) {

                } else if (DownloadException.EXCEPTION_NETWORK_MALFORMED_URL == downloadException.getCode()) {
                    ///当URL为null或无效网络连接协议时：java.net.MalformedURLException: Protocol not found

                } else if (DownloadException.EXCEPTION_NETWORK_UNKNOWN_HOST == downloadException.getCode()) {
                    ///URL虽然以http://或https://开头、但host为空或无效host
                    ///     java.net.UnknownHostException: http://
                    ///     java.net.UnknownHostException: Unable to resolve host "aaa": No address associated with hostname

                } else if (DownloadException.EXCEPTION_NETWORK_IO_EXCEPTION == downloadException.getCode()) {
                    ///如果没有网络连接

                    ///开启Wifi网络设置页面
//                startWifiSettingsActivity();
                } else {

                }
            }
        }
    }
}
