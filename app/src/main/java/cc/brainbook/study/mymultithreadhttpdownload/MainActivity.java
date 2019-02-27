package cc.brainbook.study.mymultithreadhttpdownload;

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

import java.io.File;
import java.util.List;

import cc.brainbook.android.multithreadhttpdownload.DownloadTask;
import cc.brainbook.android.multithreadhttpdownload.bean.FileInfo;
import cc.brainbook.android.multithreadhttpdownload.bean.ThreadInfo;
import cc.brainbook.android.multithreadhttpdownload.interfaces.DownloadEvent;
import cc.brainbook.android.multithreadhttpdownload.interfaces.OnProgressListener;

public class MainActivity extends AppCompatActivity implements DownloadEvent {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "============== MainActivity# onCreate(): ==============");

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
        Log.d(TAG, "============== MainActivity# onDestroy(): ==============");

        ///Activity退出后应调用下载暂停来保存下载文件信息（即断点）到数据库
        pauseDownload(mDownloadTask);

        ///避免内存泄漏
        mDownloadTask.setDownloadEvent(null);
        mDownloadTask.setOnProgressListener(null);

        super.onDestroy();
    }

    public void init() {
        ///创建下载任务类DownloadTask实例，并链式配置参数
        ///实例化DownloadTask时传入Context引用，方便操作（但要留意引起内存泄漏！）
//        mDownloadTask = new DownloadTask(getApplicationContext())
//                .setFileUrl("http://23.237.10.182/ljdy_v1.0.1.apk")
//                .setFileName("ljdy_v1.0.1.apk")
//                .setSavePath(DOWNLOAD_PATH)
//                .setThreadCount(1)
//                .setOnProgressListener(new OnProgressListener() {   ///设置进度监听（可选）
//                    @Override
//                    public void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes) {
//                        Log.d(TAG, "MainActivity# onProgress()# fileInfo: " + fileInfo);
//                        for (ThreadInfo threadInfo : threadInfos) {
//                            Log.d(TAG, "MainActivity# onProgress()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
//                            Log.d(TAG, "MainActivity# onProgress()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
//                        }
//                        Log.d(TAG, "MainActivity# onProgress()# diffTimeMillis: " + diffTimeMillis);
//                        Log.d(TAG, "MainActivity# onProgress()# diffFinishedBytes: " + diffFinishedBytes);
//                        ///避免除0异常
//                        int progress = fileInfo.getFinishedBytes() == 0 ? 0 : (int) (fileInfo.getFinishedBytes() * 100 / fileInfo.getFileSize());
//                        long speed = diffFinishedBytes == 0 ? 0 : diffFinishedBytes / diffTimeMillis;
//                        Log.d(TAG, "MainActivity# onProgress()# progress, speed: " + progress + ", " + speed);
//
//                        mTextView.setText(progress + ", " + speed);
//                    }
//                })
//                .setDownloadEvent(this);
//        mDownloadTask = new DownloadTask(getApplicationContext())
//                .setFileUrl("http://23.237.10.182/smqq.info.rar")
//                .setFileName("smqq.info.rar")
//                .setSavePath(DOWNLOAD_PATH)
//                .setThreadCount(5)
//                .setOnProgressListener(new OnProgressListener() {   ///设置进度监听（可选）
//                    @Override
//                    public void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes) {
//                        Log.d(TAG, "MainActivity# onProgress()# fileInfo: " + fileInfo);
//                        for (ThreadInfo threadInfo : threadInfos) {
//                            Log.d(TAG, "MainActivity# onProgress()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
//                            Log.d(TAG, "MainActivity# onProgress()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
//                        }
//                        Log.d(TAG, "MainActivity# onProgress()# diffTimeMillis: " + diffTimeMillis);
//                        Log.d(TAG, "MainActivity# onProgress()# diffFinishedBytes: " + diffFinishedBytes);
//                        ///避免除0异常
//                        int progress = fileInfo.getFinishedBytes() == 0 ? 0 : (int) (fileInfo.getFinishedBytes() * 100 / fileInfo.getFileSize());
//                        long speed = diffFinishedBytes == 0 ? 0 : diffFinishedBytes / diffTimeMillis;
//                        Log.d(TAG, "MainActivity# onProgress()# progress, speed: " + progress + ", " + speed);
//
//                        mTextView.setText(progress + ", " + speed);
//                    }
//                })
//                .setDownloadEvent(this);
        mDownloadTask = new DownloadTask(getApplicationContext())
                .setFileUrl("http://23.237.10.182/bbs.rar")
                .setFileName("bbs.rar")
                .setSavePath(DOWNLOAD_PATH)
                .setThreadCount(10)
                .setOnProgressListener(new OnProgressListener() {   ///设置进度监听（可选）
                    @Override
                    public void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes) {
                        Log.d(TAG, "MainActivity# onProgress()# fileInfo: " + fileInfo);
                        for (ThreadInfo threadInfo : threadInfos) {
                            Log.d(TAG, "MainActivity# onProgress()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
                            Log.d(TAG, "MainActivity# onProgress()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
                        }
                        Log.d(TAG, "MainActivity# onProgress()# diffTimeMillis: " + diffTimeMillis);
                        Log.d(TAG, "MainActivity# onProgress()# diffFinishedBytes: " + diffFinishedBytes);
                        ///避免除0异常
                        int progress = fileInfo.getFinishedBytes() == 0 ? 0 : (int) (fileInfo.getFinishedBytes() * 100 / fileInfo.getFileSize());
                        long speed = diffFinishedBytes == 0 ? 0 : diffFinishedBytes / diffTimeMillis;
                        Log.d(TAG, "MainActivity# onProgress()# progress, speed: " + progress + ", " + speed);

                        mTextView.setText(progress + ", " + speed);
                    }
                })
                .setDownloadEvent(this);

        ///必须初始化，否则不能执行下载开始、暂停等操作！
        mDownloadTask.init();
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
        ///（可选）下载启动方式一：按钮点击事件中运行DownloadTask.start()
        ///优点：无需实现回调事件接口，代码简单
        ///缺点：代码执行不连续，比如DownloadTask的new()和init()在onCreate()，而start()在按钮中
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

    /* ----------- [实现下载事件接口DownloadEvent] ----------- */
    @Override
    public void onInit(FileInfo fileInfo, List<ThreadInfo> threadInfos) {
        Log.d(TAG, "MainActivity# onInit()# fileInfo: " + fileInfo);

        for (ThreadInfo threadInfo : threadInfos) {
            Log.d(TAG, "MainActivity# onInit()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
            Log.d(TAG, "MainActivity# onInit()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
        }

        ///（可选）下载启动方式二：初始化完成的事件接口中运行DownloadTask.start()
        ///优点：代码执行连续。可放在一个方法或代码块中（以后方便移植到RxJava）
//        mDownloadTask.start();
    }

    @Override
    public void onStart(FileInfo fileInfo, List<ThreadInfo> threadInfos) {
        Log.d(TAG, "MainActivity# onStart()# fileInfo: " + fileInfo);

        for (ThreadInfo threadInfo : threadInfos) {
            Log.d(TAG, "MainActivity# onStart()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
            Log.d(TAG, "MainActivity# onStart()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
        }
    }

    @Override
    public void onPause(FileInfo fileInfo, List<ThreadInfo> threadInfos) {
        Log.d(TAG, "MainActivity# onPause()# fileInfo: " + fileInfo);

        for (ThreadInfo threadInfo : threadInfos) {
            Log.d(TAG, "MainActivity# onPause()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
            Log.d(TAG, "MainActivity# onPause()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
        }
    }

    @Override
    public void onStop(FileInfo fileInfo, List<ThreadInfo> threadInfos) {
        Log.d(TAG, "MainActivity# onStop()# fileInfo: " + fileInfo);

        for (ThreadInfo threadInfo : threadInfos) {
            Log.d(TAG, "MainActivity# onStop()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
            Log.d(TAG, "MainActivity# onStop()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
        }

        ///删除下载文件
        Log.d(TAG, "MainActivity# onStop()# fileInfo: 删除下载文件");
        new File(fileInfo.getSavePath() + fileInfo.getFileName()).delete();
    }

    @Override
    public void onComplete(FileInfo fileInfo, List<ThreadInfo> threadInfos) {
        Log.d(TAG, "MainActivity# onComplete()# fileInfo: " + fileInfo);

        for (ThreadInfo threadInfo : threadInfos) {
            Log.d(TAG, "MainActivity# onComplete()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getStatus());
            Log.d(TAG, "MainActivity# onComplete()# threadInfos(" + threadInfo.getId() +"): " + threadInfo.getFinishedBytes());
        }

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
    }

}
