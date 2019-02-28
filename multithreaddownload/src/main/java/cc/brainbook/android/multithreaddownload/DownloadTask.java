package cc.brainbook.android.multithreaddownload;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.db.ThreadDAO;
import cc.brainbook.android.multithreaddownload.db.ThreadDAOImpl;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.handler.DownloadHandler;
import cc.brainbook.android.multithreaddownload.interfaces.DownloadEvent;
import cc.brainbook.android.multithreaddownload.interfaces.OnProgressListener;
import cc.brainbook.android.multithreaddownload.thread.DownloadThread;
import cc.brainbook.android.multithreaddownload.thread.InitThread;

/**
 * 多线程可断点续传的下载任务类DownloadTask（使用Android原生HttpURLConnection）
 *
 *
 * 特点：
 *
 * 1）多线程
 * 采用的线程池是java提供的四中线程池中的缓存线程池Executors.newCachedThreadPool()，特点是如果现有线程没有可用的，
 * 则创建一个新线程并添加到池中，如果有线程可用，则复用现有的线程。如果60 秒钟未被使用的线程则会被回收。
 * 因此，长时间保持空闲的线程池不会使用任何内存资源。
 * 用户可通过DownloadTask#setThreadCount(int threadCount)设置
 * 注意：建议不要太大，取值范围不超过50！否则系统不再分配线程，造成其余下载线程仍处于初始化状态而不能进入运行状态
 *
 * 2）断点续传
 * 下载运行时按暂停或关闭Activity退出应用，自动保存断点，下次点击开始下载按钮（或开启Activity后点击开始下载按钮）自动从断点处继续下载
 * 当点击停止按钮，文件下载信息（即断点）从数据库删除。用户也可以覆写DownloadEvent#onStop(FileInfo fileInfo, List<ThreadInfo> threadInfos)删除下载文件
 * 注意：只保留下载暂停时的文件下载信息（即断点）到数据库[//////??????有待改进：任何运行状态期间按照定时器周期保存断点（影响效率！）]
 * 注意：Activity退出后应调用下载暂停来保存下载文件信息（即断点）到数据库
 *
 * 3）链式set方法设置
 *
 * 4）丰富的下载监听器参数
 * 如获取下载进度progress和下载网速speed，获取实时的下载耗时（暂停期间不计！），也可实现分段详细显示下载进度条
 *
 *
 * 使用：
 * 1）创建下载任务类DownloadTask实例，并链式set方法设置参数
 * mDownloadTask = new DownloadTask(getApplicationContext());
 * 1.1）实例化DownloadTask时传入Context引用，方便操作（但要留意引起内存泄漏！）
 * 1.2）配置下载文件的网址（必选）
 * 可通过DownloadTask#setFileUrl(String fileUrl)设置
 * 1.3）配置下载文件名（可选）
 * 可通过DownloadTask#setFileName(String fileName)设置
 * 如果用户不配置，则尝试从下载连接connection中获得下载文件的文件名HttpDownloadUtil.getUrlFileName(HttpURLConnection connection)
 * 考虑到下载文件网址中不一定含有文件名，所有不考虑从网址中获取！
 * 1.4）下载文件保存目录（可选）
 * 默认为系统SD卡的下载目录context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)，参考：Util.getDefaultFilesDir(Context context)
 * 可通过DownloadTask#setSavePath(String savePath)设置
 *
 * 2）设置进度监听（可选）
 * fileInfo用于获取下载进度progress和下载网速speed
 *      ///避免除0异常
 *      int progress = fileInfo.getFinishedBytes() == 0 ? 0 : (int) (fileInfo.getFinishedBytes() * 100 / fileInfo.getFileSize());
 *      long speed = diffFinishedBytes == 0 ? 0 : diffFinishedBytes / diffTimeMillis;
 * 也可用fileInfo.getFinishedTimeMillis()获取实时的下载耗时（暂停期间不计！）
 * threadInfos提供了全部下载线程信息（比如，可实现分段详细显示下载进度条）
 *
 * 3）下载事件接口DownloadEvent（可选）
 *      void onInit(FileInfo fileInfo, List<ThreadInfo> threadInfos)
 *      void onStart(FileInfo fileInfo, List<ThreadInfo> threadInfos)
 *      void onPause(FileInfo fileInfo, List<ThreadInfo> threadInfos)
 *      void onStop(FileInfo fileInfo, List<ThreadInfo> threadInfos)
 *      void onComplete(FileInfo fileInfo, List<ThreadInfo> threadInfos)
 *
 * 4）下载启动方式
 * 4.1）方式一：按钮点击事件中运行DownloadTask.start()
 * 优点：无需实现回调事件接口，代码简单
 * 缺点：代码执行不连续，比如DownloadTask的new()和init()在onCreate()，而start()在按钮中
 * 4.2）方式二：初始化完成的事件接口中运行DownloadTask.start()
 * 优点：代码执行连续。可放在一个方法或代码块中（以后方便移植到RxJava）
 *
 */
public class DownloadTask {
    private static final String TAG = "TAG";

    /**
     * 线程池
     */
    private static ExecutorService sExecutorService = Executors.newCachedThreadPool();

    /**
     * 持有Activity的引用
     *
     * 注意：可能带来的内存泄漏问题！
     * 当Activity退出后，而子线程仍继续运行，此时如果GC，因为子线程仍持有Activity的引用mContext，导致Activity无法被回收，就会发生内存泄漏！
     * 通用解决方式：在子线程设置停止标志（并且声明为volatile），Activity退出时置位该标志使得子线程终止运行。
     *
     * https://blog.csdn.net/changlei_shennan/article/details/44039905
     */
    private Context mContext;

    private FileInfo mFileInfo;
    private Config mConfig = new Config();
    private DownloadHandler mHandler;
    private ThreadDAO mThreadDAO;

    private long startTimeMillis;
    private long currentTimeMillis;
    private long currentFinishedBytes;

    /**
     * [FIX BUG# 下载完成后取消定时器，进度更新显示99%]
     * 分析：下载完成到取消定时器期间，应该再运行一次定时任务去更新进度
     * 解决：加入成员变量mayStopTimer来控制定时器的取消
     */
    private boolean mayStopTimer;
    private Timer mTimer;

    /**
     * 下载文件的所有线程信息集合
     *
     * 注意：设置为public允许InitThread类访问
     * 因为mThreadInfos是在InitThread类赋值，所以无法通过传参引用！
     */
    public List<ThreadInfo> mThreadInfos;

    public DownloadTask(Context context) {
        mContext = context;
        mFileInfo = new FileInfo();
        mThreadDAO = new ThreadDAOImpl(mContext);
    }


    /* ------------ 链式配置 ----------- */
    public DownloadTask setFileUrl(String fileUrl) {
        mFileInfo.setFileUrl(fileUrl);
        return this;
    }
    public DownloadTask setFileName(String fileName) {
        mFileInfo.setFileName(fileName);
        return this;
    }
    public DownloadTask setSavePath(String savePath) {
        mFileInfo.setSavePath(savePath);
        return this;
    }
    public DownloadTask setConnectTimeout(int connectTimeout) {
        mConfig.connectTimeout = connectTimeout;
        return this;
    }
    public DownloadTask setBufferSize(int bufferSize) {
        mConfig.bufferSize = bufferSize;
        return this;
    }
    public DownloadTask setProgressInterval(int progressInterval) {
        mConfig.progressInterval = progressInterval;
        return this;
    }
    private OnProgressListener mOnProgressListener;
    public DownloadTask setOnProgressListener(OnProgressListener onProgressListener) {
        mOnProgressListener = onProgressListener;
        return this;
    }
    private DownloadEvent mDownloadEvent;
    public DownloadTask setDownloadEvent(DownloadEvent downloadEvent) {
        mDownloadEvent = downloadEvent;
        return this;
    }
    public DownloadTask setThreadCount(int threadCount) {
        mConfig.threadCount = threadCount;
        return this;
    }
    /* ------------ 链式配置 ----------- */


    /**
     * 初始化
     *
     * 注意：必须初始化，否则不能执行下载开始、暂停等操作！
     */
    public void init() {
        Log.d(TAG, "DownloadTask# init(): ");
        if (mFileInfo.getStatus() == FileInfo.FILE_STATUS_NEW) {
            ///更新下载文件状态：下载初始化
            mFileInfo.setStatus(FileInfo.FILE_STATUS_INIT);

            ///创建Handler对象
            ///注意：Handler对象不能在构造中创建！因为mDownloadEvent、mOnProgressListener参数尚未准备好
            mHandler = new DownloadHandler (
                    this,
                    mFileInfo,
                    mDownloadEvent,
                    mOnProgressListener,
                    mThreadDAO
            );

            ///启动初始化线程
            InitThread initThread = new InitThread (
                    mContext,
                    this,
                    mConfig,
                    mFileInfo,
                    mHandler,
                    mThreadDAO
            );

            ///线程池
//            initThread.start();
            sExecutorService.execute(initThread);
        }
    }

    /**
     * 开始下载
     */
    public void start() {
        Log.d(TAG, "DownloadTask# start(): ");

        switch (mFileInfo.getStatus()) {
            case FileInfo.FILE_STATUS_NEW:
                throw new DownloadException(DownloadException.EXCEPTION_NO_INIT, "DownloadTask Init expected.");

            case FileInfo.FILE_STATUS_INIT:
                ///执行下载过程
                innerStart();

                break;
            case FileInfo.FILE_STATUS_START:

                break;
            case FileInfo.FILE_STATUS_PAUSE:    ///下载暂停后重新开始下载

                ///[FIX BUG#pause后立即start所引起的重复启动问题]运行start()时会同时检测mTimer是否为null的条件
                if (mTimer != null) {
                    return;
                }

                ///执行下载过程
                innerStart();

                break;
            case FileInfo.FILE_STATUS_STOP:

                break;
            case FileInfo.FILE_STATUS_COMPLETE: ///下载完成后重新开始下载

                ///[修正下载完成后重新开始下载]
                ///重置已经下载完的总耗时（毫秒）、总下载字节数
                mFileInfo.setFinishedTimeMillis(0);
                mFileInfo.setFinishedBytes(0);
                ///重置所有下载线程信息的下载字节数
                for (ThreadInfo threadInfo : mThreadInfos) {
                    threadInfo.setStatus(ThreadInfo.THREAD_STATUS_INIT);
                    threadInfo.setFinishedBytes(0);
                }

                ///执行下载过程
                innerStart();

                break;
        }
    }

    private void innerStart() {
        ///下载线程信息集合必须存在且不为空
        if (mThreadInfos.isEmpty()) {
            return;
        }

        ///遍历下载线程信息集合，找出所有未完成的下载线程信息
        ArrayList<ThreadInfo> notCompleteThreadInfos = new ArrayList<>();
        for (ThreadInfo threadInfo : mThreadInfos) {
            if (threadInfo.getStatus() != ThreadInfo.THREAD_STATUS_COMPLETE) {
                notCompleteThreadInfos.add(threadInfo);
            }
        }

        ///如果下载线程全部完成
        if (notCompleteThreadInfos.isEmpty()) {
            ///更新下载文件状态：下载完成
            Log.d(TAG, "DownloadTask# innerStart(): 更新下载文件状态：mFileInfo.setStatus(FileInfo.FILE_STATUS_COMPLETE)");
            mFileInfo.setStatus(FileInfo.FILE_STATUS_COMPLETE);

            ///发送消息：下载完成
            Log.d(TAG, "DownloadTask# innerStart(): ------- 发送消息：下载完成 -------");
            mHandler.obtainMessage(DownloadHandler.MSG_COMPLETE).sendToTarget();

            return;
        }

        ///[FIX BUG: 暂停或完成后出现多次重复的消息通知！]
        ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
        ///https://www.cnblogs.com/dolphin0520/p/3920397.html
        CyclicBarrier barrierPause  = new CyclicBarrier(notCompleteThreadInfos.size(), new Runnable() {
            @Override
            public void run() {
                ///发送消息：下载暂停
                Log.d(TAG, "DownloadTask# innerStart(): CyclicBarrier barrierPause:  ------- 发送消息：下载暂停 -------");
                mHandler.obtainMessage(DownloadHandler.MSG_PAUSE).sendToTarget();

                ///[FIX BUG# 下载完成后取消定时器，进度更新显示99%]可以取消定时器Timer
                Log.d(TAG, "DownloadTask# innerStart(): CyclicBarrier barrierPause: 可以取消定时器Timer");
                mayStopTimer = true;
            }
        });
        CyclicBarrier barrierComplete  = new CyclicBarrier(notCompleteThreadInfos.size(), new Runnable() {
            @Override
            public void run() {
                ///更新下载文件状态：下载完成
                mFileInfo.setStatus(FileInfo.FILE_STATUS_COMPLETE);

                ///[FIX BUG# 下载完成后取消定时器，进度更新显示99%]可以取消定时器Timer
                Log.d(TAG, "DownloadTask# innerStart(): CyclicBarrier barrierComplete: 可以取消定时器Timer");
                mayStopTimer = true;

                ///发送消息：下载完成
                Log.d(TAG, "DownloadTask# innerStart(): CyclicBarrier barrierComplete:  ------- 发送消息：下载完成 -------");
                mHandler.obtainMessage(DownloadHandler.MSG_COMPLETE).sendToTarget();
            }
        });

        ///遍历所有未完成的下载线程信息集合，逐个启动下载线程
        for (ThreadInfo threadInfo : notCompleteThreadInfos) {
            DownloadThread downloadThread = new DownloadThread (
                    this,
                    mConfig,
                    mFileInfo,
                    threadInfo,
                    mThreadDAO,
                    barrierPause,
                    barrierComplete
            );
            ///线程池
//            downloadThread.start();
            sExecutorService.execute(downloadThread);

            threadInfo.setStatus(ThreadInfo.THREAD_STATUS_START);
        }

        ///更新下载文件状态：下载开始
        mFileInfo.setStatus(FileInfo.FILE_STATUS_START);

        ///设置下载开始时间
        startTimeMillis = System.currentTimeMillis();

        ///控制更新下载进度的周期
        currentTimeMillis = System.currentTimeMillis();
        currentFinishedBytes = mFileInfo.getFinishedBytes();

        ///启动定时器Timer
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ///更新已经下载完的总耗时（毫秒）
                mFileInfo.setFinishedTimeMillis(mFileInfo.getFinishedTimeMillis() + System.currentTimeMillis() - startTimeMillis);

                if (mOnProgressListener != null) {
                    ///发送消息：更新下载进度
                    Log.d(TAG, "DownloadTask# mTimer.schedule() #run() ------- 发送消息：更新进度 -------");
                    long diffTimeMillis = System.currentTimeMillis() - currentTimeMillis;   ///下载进度的耗时（毫秒）
                    currentTimeMillis = System.currentTimeMillis();
                    long diffFinishedBytes = mFileInfo.getFinishedBytes() - currentFinishedBytes;  ///下载进度的下载字节数
                    currentFinishedBytes = mFileInfo.getFinishedBytes();
                    mHandler.obtainMessage(DownloadHandler.MSG_PROGRESS, new long[]{diffTimeMillis, diffFinishedBytes}).sendToTarget();
                }

                ///[FIX BUG# 下载完成后取消定时器，进度更新显示99%]
                if (mayStopTimer) {
                    Log.d(TAG, "DownloadTask# mTimer.schedule() #run() ------- 取消定时器 -------");
                    mayStopTimer = false;
                    mTimer.cancel();
                    mTimer = null;  ///[FIX BUG# pause后立即start所引起的重复启动问题]解决方法：在运行start()时会同时检测mTimer是否为null的条件
                }
            }
        }, 1000, mConfig.progressInterval);

        ///发送消息：下载开始
        Log.d(TAG, "DownloadTask# innerStart(): ------- 发送消息：下载开始 -------");
        if (mHandler != null) {
            mHandler.obtainMessage(DownloadHandler.MSG_START).sendToTarget();
        }
    }

    /**
     * 暂停下载
     */
    public void pause() {
        Log.d(TAG, "DownloadTask# pause(): ");

        switch (mFileInfo.getStatus()) {
            case FileInfo.FILE_STATUS_NEW:
                throw new DownloadException(DownloadException.EXCEPTION_NO_INIT, "DownloadTask Init expected.");

            case FileInfo.FILE_STATUS_INIT:

                break;
            case FileInfo.FILE_STATUS_START:
                ///更新下载文件状态：下载暂停
                Log.d(TAG, "DownloadTask# pause(): 更新下载文件状态：mFileInfo.setStatus(FileInfo.FILE_STATUS_PAUSE)");
                mFileInfo.setStatus(FileInfo.FILE_STATUS_PAUSE);

                break;
            case FileInfo.FILE_STATUS_PAUSE:

                break;
            case FileInfo.FILE_STATUS_STOP:

                break;
            case FileInfo.FILE_STATUS_COMPLETE:

                break;
        }
    }

    /**
     * 停止下载
     */
    public void stop() {
        Log.d(TAG, "DownloadTask# stop(): ");

        switch (mFileInfo.getStatus()) {
            case FileInfo.FILE_STATUS_NEW:
                throw new DownloadException(DownloadException.EXCEPTION_NO_INIT, "DownloadTask Init expected.");

            case FileInfo.FILE_STATUS_INIT:

                break;
            case FileInfo.FILE_STATUS_START:
                innerStop();

                break;
            case FileInfo.FILE_STATUS_PAUSE:
                innerStop();

                break;
            case FileInfo.FILE_STATUS_STOP:

                break;
            case FileInfo.FILE_STATUS_COMPLETE:
                innerStop();

                break;
        }
    }

    private void innerStop() {
        ///更新下载文件状态：下载停止
        Log.d(TAG, "DownloadTask# innerStop(): 更新下载文件状态：mFileInfo.setStatus(FileInfo.FILE_STATUS_STOP)");
        mFileInfo.setStatus(FileInfo.FILE_STATUS_STOP);

        ///[FIX BUG# 下载完成后取消定时器，进度更新显示99%]可以取消定时器Timer
        Log.d(TAG, "DownloadTask# innerStop(): 可以取消定时器Timer");
        mayStopTimer = true;

        ///发送消息：下载停止
        Log.d(TAG, "DownloadTask# innerStop(): ------- 发送消息：下载停止 -------");
        mHandler.obtainMessage(DownloadHandler.MSG_STOP).sendToTarget();
    }

}
