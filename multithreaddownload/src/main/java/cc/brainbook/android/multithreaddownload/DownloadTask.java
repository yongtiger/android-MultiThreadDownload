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

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.db.ThreadDAO;
import cc.brainbook.android.multithreaddownload.db.ThreadDAOImpl;
import cc.brainbook.android.multithreaddownload.handler.DownloadHandler;
import cc.brainbook.android.multithreaddownload.listener.DownloadListener;
import cc.brainbook.android.multithreaddownload.thread.DownloadThread;
import cc.brainbook.android.multithreaddownload.thread.InitThread;

import static cc.brainbook.android.multithreaddownload.BuildConfig.DEBUG;

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
 * 用户可通过DownloadTask#setThreadCount(int threadCount)设置，设置下载线程数量以后，系统会优化调整最终获得的下载线程数量
 * 以保证每个线程下载的文件长度不少于MINIMUM_DOWNLOAD_PART_SIZE（5MB），极端情况下，如果文件总长度小于5MB，则只分配一个线程？？？？？？
 * 注意：建议不要太大，取值范围不超过50！否则系统可能不再分配线程，造成其余下载线程仍处于初始化状态而不能进入运行状态
 *
 * 2）断点续传
 * 下载运行时按暂停或关闭Activity时，自动保存断点，下次点击开始下载按钮（或开启Activity后点击开始下载按钮）自动从断点处继续下载
 * 当点击停止按钮，线程信息（即断点）从数据库删除，同时删除下载文件
 * 注意：Activity销毁后应在onDestroy()调用下载暂停来保存线程信息（即断点）到数据库
 *
 * 3）断网恢复后自动续传？？？？？？？？？？
 * 内部设置广播接收器来监听网络状态的变化，
 *
 * 4）可以设置只允许Wifi网络连接时下载、是否允许漫游网络连接时下载？？？？？？？？？？？？
 *
 * 5）链式set方法设置
 *
 * 6）丰富的下载监听器参数
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
 * 注意：考虑到下载文件网址中不一定含有文件名，所有不考虑从网址中获取！
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
 * threadInfos提供了全部线程信息（比如，可实现分段详细显示下载进度条）
 *
 * 3）下载事件接口DownloadListener（可选）
 *      void onStateChanged(FileInfo fileInfo, List<ThreadInfo> threadInfos, DownloadState state);
 *      void onProgress(FileInfo fileInfo, List<ThreadInfo> threadInfos, long diffTimeMillis, long diffFinishedBytes);
 *      void onError(FileInfo fileInfo, List<ThreadInfo> threadInfos, Exception e);
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
     * 当Activity关闭后，而子线程仍继续运行，此时如果GC，因为子线程仍持有Activity的引用mContext，导致Activity无法被回收，就会发生内存泄漏！
     * 通用解决方式：在子线程设置停止标志（并且声明为volatile），Activity关闭时置位该标志使得子线程终止运行。
     *
     * https://blog.csdn.net/changlei_shennan/article/details/44039905
     */
    private Context mContext;

    private FileInfo mFileInfo;
    private Config mConfig = new Config();
    private DownloadHandler mHandler;
    private ThreadDAO mThreadDAO;
    private DownloadListener mDownloadListener;

    private long startTimeMillis;
    private long currentTimeMillis;
    private long currentFinishedBytes;

    ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]
    ///分析：下载完成（成功/失败/停止）或暂停到取消定时器期间，应该再运行一次定时任务去更新进度
    ///解决：加入成员变量mayStopTimer来控制定时器的取消
    /**
     * 可以停止定时器
     */
    private boolean mayStopTimer;
    private Timer mTimer;

    /**
     * 线程信息集合
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
    public DownloadTask setThreadCount(int threadCount) {
        mConfig.threadCount = threadCount;
        return this;
    }
    public DownloadTask setDownloadListener(DownloadListener onProgressListener) {
        mDownloadListener = onProgressListener;
        return this;
    }
    /* ------------ 链式配置 ----------- */


    /**
     * 开始下载
     */
    public void start() {
        if (DEBUG) Log.d(TAG, "DownloadTask# start()# ");

        switch (mFileInfo.getState()) {
            case FAILED:   ///下载失败后重新开始下载

                ///[修正下载完成（成功/失败/停止）后重新开始下载]
                ///重置文件信息的已经完成的总耗时（毫秒）、总字节数
                mFileInfo.setFinishedTimeMillis(0);
                mFileInfo.setFinishedBytes(0);

                ///执行下载过程
                innerStart();

                ///初始化过程
                init();

                break;
            case NEW:
                ///初始化过程
                init();

                break;
            case INITIALIZED:
                ///执行下载过程
                innerStart();

                break;
            case STARTED:

                break;
            case PAUSED:    ///下载暂停后重新开始下载

                ///[FIX BUG#pause后立即start所引起的重复启动问题]运行start()时会同时检测mTimer是否为null的条件
                if (mTimer != null) {
                    return;
                }

                ///执行下载过程
                innerStart();

                break;
            case STOPPED:   ///下载停止后重新开始下载

                ///[修正下载完成（成功/失败/停止）后重新开始下载]
                ///重置文件信息的已经完成的总耗时（毫秒）、总字节数
                mFileInfo.setFinishedTimeMillis(0);
                mFileInfo.setFinishedBytes(0);

                ///初始化过程
                init();

                break;
            case SUCCEED:   ///下载成功后重新开始下载

                ///[修正下载完成（成功/失败/停止）后重新开始下载]
                ///重置文件信息的已经完成的总耗时（毫秒）、总字节数
                mFileInfo.setFinishedTimeMillis(0);
                mFileInfo.setFinishedBytes(0);

                ///初始化过程
                init();

                break;
        }
    }

    private void init() {
        if (DEBUG) Log.d(TAG, "DownloadTask# init()# ");

        ///创建Handler对象
        ///注意：Handler对象不能在构造中创建！因为mDownloadEvent、mOnProgressListener参数尚未准备好
        mHandler = new DownloadHandler (
                this,
                mFileInfo,
                mDownloadListener,
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
//        initThread.start();
        sExecutorService.execute(initThread);
    }

    private void innerStart() {
        ///线程信息集合必须存在且不为空
        if (mThreadInfos == null || mThreadInfos.isEmpty()) {
            return;
        }

        ///遍历线程信息集合，找出所有暂停的线程信息，准备续传（只有暂停状态才能续传！）
        final ArrayList<ThreadInfo> pausedThreadInfos = new ArrayList<>();
        for (ThreadInfo threadInfo : mThreadInfos) {
            if (threadInfo.getState() == DownloadState.PAUSED) {
                pausedThreadInfos.add(threadInfo);
            }
        }

//        ///如果没有暂停的线程信息？？？？？？？？？？？？？？？？？
//        if (pausedThreadInfos.isEmpty()) {
//            ///更新文件信息的状态：下载成功
//            ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
//            if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# 更新文件信息的状态：mFileInfo.setState(DownloadState.SUCCEED)");
//            mFileInfo.setState(DownloadState.SUCCEED);
//
//            ///发送消息：下载成功
//            if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# ------- 发送消息：下载成功 -------");
//            mHandler.obtainMessage(DownloadHandler.MSG_SUCCEED).sendToTarget();
//
//            return;
//        }

        ///[FIX BUG: 完成（成功/失败/停止）暂停后出现多次重复的消息通知！]
        ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
        ///https://www.cnblogs.com/dolphin0520/p/3920397.html
        CyclicBarrier barrier = new CyclicBarrier(pausedThreadInfos.size(), new Runnable() {
            @Override
            public void run() {
                ///遍历所有线程信息，如果存在停止状态，则说明文件信息的状态是停止状态
                ///否则如果存在暂停状态，则说明文件信息的状态是暂停状态
                ///否则就应该是成功状态
                DownloadState state = null;
                for (ThreadInfo threadInfo : pausedThreadInfos) {
                    if (threadInfo.getState() == DownloadState.STOPPED) {
                        state = DownloadState.STOPPED;
                        break;
                    }
                }
                if (state == null) {
                    for (ThreadInfo threadInfo : pausedThreadInfos) {
                        if (threadInfo.getState() == DownloadState.PAUSED) {
                            state = DownloadState.PAUSED;
                            break;
                        }
                    }
                }
                if (state == null) {
                    state = DownloadState.SUCCEED;
                }

                if (state == DownloadState.STOPPED) {
                    ///发送消息：下载停止
                    if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# barrier: ------- 发送消息：下载停止 -------");
                    mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();

                } else if (state == DownloadState.PAUSED) {
                    ///发送消息：下载暂停
                    if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# barrier: ------- 发送消息：下载暂停 -------");
                    mHandler.obtainMessage(DownloadHandler.MSG_PAUSED).sendToTarget();

                    ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
                    synchronized (LOCK) {
                        if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# barrier: LOCK.notify() ............................");
                        LOCK.notify();
                    }

                } else {    ///DownloadState.SUCCEED
                    ///更新文件信息的状态：下载成功
                    if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# 更新文件信息的状态：mFileInfo.setState(DownloadState.SUCCEED)");
                    mFileInfo.setState(DownloadState.SUCCEED);

                    ///发送消息：下载成功
                    if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# barrier: ------- 发送消息：下载成功 -------");
                    mHandler.obtainMessage(DownloadHandler.MSG_SUCCEED).sendToTarget();
                }

                ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]可以取消定时器Timer
                if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# barrier: 可以取消定时器Timer");
                mayStopTimer = true;
            }
        });

        ///遍历所有暂停的线程信息集合，逐个启动线程
        for (ThreadInfo threadInfo : pausedThreadInfos) {
            DownloadThread downloadThread = new DownloadThread (
                    this,
                    mConfig,
                    mFileInfo,
                    mHandler,
                    threadInfo,
                    mThreadDAO,
                    barrier
            );
            ///线程池
//            downloadThread.start();
            sExecutorService.execute(downloadThread);
        }

        ///设置下载开始时间
        startTimeMillis = System.currentTimeMillis();

        ///控制更新进度的周期
        currentTimeMillis = System.currentTimeMillis();
        currentFinishedBytes = mFileInfo.getFinishedBytes();

        ///启动定时器Timer
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ///更新文件信息的已经完成的总耗时（毫秒）
                mFileInfo.setFinishedTimeMillis(mFileInfo.getFinishedTimeMillis() + System.currentTimeMillis() - startTimeMillis);

                if (mDownloadListener != null) {
                    ///发送消息：更新进度
                    if (DEBUG) Log.d(TAG, "DownloadTask# mTimer.schedule()# run()# ------- 发送消息：更新进度 -------");
                    long diffTimeMillis = System.currentTimeMillis() - currentTimeMillis;   ///下载进度的耗时（毫秒）
                    currentTimeMillis = System.currentTimeMillis();
                    long diffFinishedBytes = mFileInfo.getFinishedBytes() - currentFinishedBytes;  ///下载进度的下载字节数
                    currentFinishedBytes = mFileInfo.getFinishedBytes();
                    mHandler.obtainMessage(DownloadHandler.MSG_PROGRESS, new long[]{diffTimeMillis, diffFinishedBytes}).sendToTarget();
                }

                ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]
                if (mayStopTimer) {
                    if (DEBUG) Log.d(TAG, "DownloadTask# mTimer.schedule()# run()# ------- 取消定时器 -------");
                    mayStopTimer = false;
                    mTimer.cancel();
                    mTimer = null;  ///[FIX BUG# pause后立即start所引起的重复启动问题]解决方法：在运行start()时会同时检测mTimer是否为null的条件
                }
            }
        }, 1000, mConfig.progressInterval);

        ///更新文件信息的状态：下载开始
        mFileInfo.setState(DownloadState.STARTED);

        ///发送消息：下载开始
        if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# ------- 发送消息：下载开始 -------");
        mHandler.obtainMessage(DownloadHandler.MSG_STARTED).sendToTarget();
    }

    ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
    /**
     * Lock to synchronize access.
     */
    private static final Object LOCK = new Object();

    /**
     * 暂停下载
     */
    public void pause() {
        if (DEBUG) Log.d(TAG, "DownloadTask# pause()# ");

        switch (mFileInfo.getState()) {
            case FAILED:

                break;
            case NEW:

                break;
            case INITIALIZED:

                break;
            case STARTED:
                ///更新文件信息的状态：下载暂停
                ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
                if (DEBUG) Log.d(TAG, "DownloadTask# pause()# 更新文件信息的状态：mFileInfo.setState(DownloadState.PAUSED)");
                mFileInfo.setState(DownloadState.PAUSED);

                ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
                synchronized (LOCK) {
                    try {
                        if (DEBUG) Log.d(TAG, "DownloadTask# pause()# wait ............................");
                        LOCK.wait();
                        if (DEBUG) Log.d(TAG, "DownloadTask# pause()# notified ............................");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                break;
            case PAUSED:

                break;
            case STOPPED:

                break;
            case SUCCEED:

                break;
        }
    }

    /**
     * 停止下载
     */
    public void stop() {
        if (DEBUG) Log.d(TAG, "DownloadTask# stop()# ");

        switch (mFileInfo.getState()) {
            case FAILED:

                break;
            case NEW:

                break;
            case INITIALIZED:

                break;
            case STARTED:
                innerStop();

                break;
            case PAUSED:
                innerStop();

                break;
            case STOPPED:

                break;
            case SUCCEED:
                innerStop();

                break;
        }
    }

    private void innerStop() {
        ///更新文件信息的状态：下载停止
        ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
        if (DEBUG) Log.d(TAG, "DownloadTask# innerStop()# 更新文件信息的状态：mFileInfo.setState(DownloadState.STOPPED)");
        mFileInfo.setState(DownloadState.STOPPED);
    }

}
