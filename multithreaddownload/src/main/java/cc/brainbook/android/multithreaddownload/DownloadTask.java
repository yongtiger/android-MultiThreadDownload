package cc.brainbook.android.multithreaddownload;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.FileInfo;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;
import cc.brainbook.android.multithreaddownload.config.Config;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAO;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAOImpl;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.listener.DownloadListener;
import cc.brainbook.android.multithreaddownload.util.DownloadUtil;
import cc.brainbook.android.multithreaddownload.util.Util;

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
 * 以保证每个线程下载的文件长度不少于MINIMUM_DOWNLOAD_PART_SIZE（5MB），极端情况下，如果文件总长度小于5MB，则只分配一个线程
 * 注意：建议不要太大，取值范围不超过50！否则系统可能不再分配线程，造成其余下载线程仍处于初始化状态而不能进入运行状态
 *
 * 2）断点续传
 * 下载运行时按暂停或关闭Activity时，自动保存断点，下次点击开始下载按钮（或开启Activity后点击开始下载按钮）自动从断点处继续下载
 * 当点击停止按钮，线程信息（即断点）从数据库删除，同时删除下载文件
 * 注意：Activity销毁后应在onDestroy()调用下载暂停来保存线程信息（即断点）到数据库
 *
 * 3）链式set方法设置
 *
 * 4）丰富的下载监听器参数
 * 如获取下载进度progress和下载网速speed，获取实时的下载耗时，也可实现分段详细显示下载进度条
 *
 * 5）使用Handler状态机方式，方便修改状态变化逻辑，比如：
 *      初始化后可以立即下载
 *      暂停/成功/错误状态时，可以停止下载进行清空数据库下载记录、下载文件等
 *      成功后可以清空后再重新下载
 *      。。。
 *
 * 6）可以断网后自动恢复下载
 * 如果下载过程中断开网络连接，抛出异常DownloadException.EXCEPTION_NETWORK_FILE_IO_EXCEPTION
 * 用户自行编写触发再次连接的代码（比如轮询、或监听网络状态变化）
 *
 * 7）采用相比其它定时器（Timer、Handler+Thread）高效的Handler+Runnable实现定时查询和更新下载进度
 *
 * 8）优化了每个线程的长度至少为MINIMUM_DOWNLOAD_PART_SIZE，最多下载线程数量为MAXIMUM_DOWNLOAD_PARTS
 *
 * 9）消除了内存泄漏
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
 * 默认为应用的外部下载目录context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)，如果系统无SD卡返回应用的文件目录getFilesDir()
 * 参考：Util.getDefaultFilesDir(Context context)
 * 可通过DownloadTask#setSavePath(String savePath)设置
 * 1.5）初始化（必须）
 * 可选择是否初始化后立即下载
 *
 * 2）设置进度监听（可选）
 * fileInfo用于获取下载进度progress和下载网速speed
 *      ///避免除0异常
 *      int progress = fileInfo.getFinishedBytes() == 0 ? 0 : (int) (fileInfo.getFinishedBytes() * 100 / fileInfo.getFileSize());
 *      long speed = diffFinishedBytes == 0 ? 0 : diffFinishedBytes / diffTimeMillis;
 * 也可用fileInfo.getFinishedTimeMillis()获取实时的下载耗时
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

    ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
    /**
     * Lock to synchronize access.
     */
    private static final Object LOCK = new Object();

    /**
     * 线程池
     */
    private static final ExecutorService sExecutorService = Executors.newCachedThreadPool();

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
    private ThreadInfoDAO mThreadDAO;
    private DownloadListener mDownloadListener;

    /**
     * 线程信息集合
     */
    List<ThreadInfo> mThreadInfos;

    public DownloadTask(Context context) {
        mContext = context;
        mFileInfo = new FileInfo();
        mThreadDAO = new ThreadInfoDAOImpl(mContext);
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
    public DownloadTask setDownloadListener(DownloadListener downloadListener) {
        mDownloadListener = downloadListener;
        return this;
    }
    /* ------------ 链式配置 ----------- */


    /**
     * 初始化
     *
     * 创建Handler对象、检查文件网址URL和下载文件保存目录，并且
     * 检查下载文件的文件名和大小，如果下载文件没有文件名或文件大小
     * 则启动初始化线程由网络连接获得文件名和文件大小
     *
     * @param isStart   是否初始化后立即开始下载
     */
    public void init(boolean isStart) {
        if (DEBUG) Log.d(TAG, "DownloadTask# init()# ");

        ///创建Handler对象
        ///注意：Handler对象不能在构造中创建！因为mDownloadEvent、mOnProgressListener参数尚未准备好
        if (mHandler == null) {
            mHandler = new DownloadHandler (
                    mConfig,
                    this,
                    mFileInfo,
                    mDownloadListener,
                    mThreadDAO);

        }

        ///检查文件网址URL
        ///如果为null或空字符串则报错后退出
        if (TextUtils.isEmpty(mFileInfo.getFileUrl())) {
            ///发送消息：下载错误
            mHandler.obtainMessage(DownloadHandler.MSG_INIT_FAILED,
                    new DownloadException(DownloadException.EXCEPTION_FILE_URL_NULL, "The file url cannot be null."))
                    .sendToTarget();
            return;
        }

        ///检查下载文件保存目录
        ///如果为null或空字符串则获得缺省的下载目录，否则创建文件下载目录
        if (TextUtils.isEmpty(mFileInfo.getSavePath())) {
            mFileInfo.setSavePath(Util.getDefaultFilesDirPath(mContext));
        } else {
            if (!Util.mkdirs(mFileInfo.getSavePath())) {
                ///发送消息：下载失败
                mHandler.obtainMessage(DownloadHandler.MSG_INIT_FAILED,
                        new DownloadException(DownloadException.EXCEPTION_FILE_MKDIR_EXCEPTION, "The file save path cannot be made: " + mFileInfo.getSavePath()))
                        .sendToTarget();
                return;
            } else if (!Util.isCanWrite(mFileInfo.getSavePath())) {
                ///发送消息：下载失败
                mHandler.obtainMessage(DownloadHandler.MSG_INIT_FAILED,
                        new DownloadException(DownloadException.EXCEPTION_FILE_WRITE_EXCEPTION, "The file save path is not writable: " + mFileInfo.getSavePath()))
                        .sendToTarget();
                return;
            }
        }

        ///检查下载文件的文件名和大小
        ///仅当下载文件没有文件名或文件大小，才启动初始化线程由网络连接获得文件名和文件大小
        if (TextUtils.isEmpty(mFileInfo.getFileName()) || mFileInfo.getFileSize() <= 0) {
            ///启动初始化线程
            final InitThread initThread = new InitThread (
                    mConfig,
                    mFileInfo,
                    mHandler,
                    isStart);

            ///线程池
//            initThread.start();
            sExecutorService.execute(initThread);
        } else {
            ///发送消息：初始化完成
            mHandler.obtainMessage(DownloadHandler.MSG_INITIALIZED, isStart).sendToTarget();
        }
    }

    /**
     * 重置下载
     *
     * 删除数据库中下载文件的所有线程信息、删除下载文件、重置文件信息的已经完成的总耗时（毫秒）、总字节数、清空线程信息集合
     * 重置后可以重新设置各参数、初始化等操作
     */
    public void reset() {
        if (DEBUG) Log.d(TAG, "DownloadTask# reset()# ");

        ///停止定时器
        stopTimer();

        ///清空线程信息集合（innerStart()中不必访问数据库！而如为null则需从数据库加载）
        mThreadInfos.clear();

        ///删除数据库中下载文件的所有线程信息
        mThreadDAO.deleteAllThreadInfos(mFileInfo.getFileUrl(), mFileInfo.getFileName(), mFileInfo.getFileSize(), mFileInfo.getSavePath());

        ///删除下载文件
        new File(mFileInfo.getSavePath() + mFileInfo.getFileName()).delete();

        ///[修正下载完成（成功/失败/停止）后重新开始下载]
        ///重置文件信息的已经完成的总耗时（毫秒）、总字节数
        mFileInfo.setFinishedTimeMillis(0);
        mFileInfo.setFinishedBytes(0);

        ///更新文件信息的状态
        mFileInfo.setState(DownloadState.NEW);
    }

    /**
     * 开始下载
     */
    public void start() {
        if (DEBUG) Log.d(TAG, "DownloadTask# start()# mFileInfo.getState(): " + mFileInfo.getState());

        switch (mFileInfo.getState()) {
            case NEW:           ///创建下载任务对象（NEW）后开始下载start()
                ///初始化
                init(true);

                break;
            case INITIALIZED:   ///初始化（INITIALIZED）后开始下载start()
                ///创建下载空占位文件
                DownloadUtil.createEmptySaveFile(mFileInfo.getSavePath(), mFileInfo.getFileName(), mFileInfo.getFileSize());

                ///执行下载过程
                innerStart();

                break;
            case STARTED:       ///下载开始（STARTED）后开始下载start()
                ///忽略
                break;
            case PAUSED:        ///下载暂停（PAUSED）后开始下载start()
                ///[FIX BUG#pause后立即start所引起的重复启动问题]运行start()时会同时检测mTimer是否为null的条件
                if (mTimerHandler != null) {
                    return;
                }

                ///执行下载过程
                innerStart();

                break;
            case STOPPED:       ///下载停止（STOPPED）后开始下载start()
                ///同步发送消息：初始化
                mHandler.handleMessage(mHandler.obtainMessage(DownloadHandler.MSG_INITIALIZED));

                ///创建下载空占位文件
                DownloadUtil.createEmptySaveFile(mFileInfo.getSavePath(), mFileInfo.getFileName(), mFileInfo.getFileSize());

                ///执行下载过程
                innerStart();

                break;
            case SUCCEED:       ///下载成功（SUCCEED）后开始下载start()
                ///重置下载
                reset();

                ///同步发送消息：初始化
                mHandler.handleMessage(mHandler.obtainMessage(DownloadHandler.MSG_INITIALIZED));

                ///创建下载空占位文件
                DownloadUtil.createEmptySaveFile(mFileInfo.getSavePath(), mFileInfo.getFileName(), mFileInfo.getFileSize());

                ///执行下载过程
                innerStart();

                break;
            case INIT_FAILED:           ///初始化失败（INIT_FAILED）后开始下载start()
                ///初始化
                init(true);

                break;
            case DOWNLOAD_FAILED:        ///下载失败（DOWNLOAD_FAILED）后开始下载start()
                ///执行下载过程
                innerStart();

                break;
        }
    }

    /**
     * 暂停下载
     */
    public void pause() {
        if (DEBUG) Log.d(TAG, "DownloadTask# pause()# mFileInfo.getState(): " + mFileInfo.getState());

        switch (mFileInfo.getState()) {
            case NEW:           ///创建下载任务对象（NEW）后暂停下载pause()
                ///忽略
                break;
            case INITIALIZED:   ///初始化（INITIALIZED）后暂停下载pause()
                ///忽略
                break;
            case STARTED:       ///下载开始（STARTED）后暂停下载pause()
                ///更新文件信息的状态：下载暂停
                mFileInfo.setState(DownloadState.PAUSED);

                ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
                synchronized (LOCK) {
                    try {
                        if (DEBUG) Log.d(TAG, "DownloadTask# pause()# LOCK.wait ............................");
                        LOCK.wait();
                        if (DEBUG) Log.d(TAG, "DownloadTask# pause()# notified ............................");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                break;
            case PAUSED:        ///下载暂停（PAUSED）后暂停下载pause()
                ///忽略
                break;
            case STOPPED:       ///下载停止（STOPPED）后暂停下载pause()
                ///忽略
                break;
            case SUCCEED:       ///下载成功（SUCCEED）后暂停下载pause()
                ///忽略
                break;
            case INIT_FAILED:        ///初始化失败（INIT_FAILED）后暂停下载pause()
                ///忽略
                break;
            case DOWNLOAD_FAILED:        ///下载失败（DOWNLOAD_FAILED）后暂停下载pause()
                ///忽略
                break;
        }
    }

    /**
     * 停止下载
     */
    public void stop() {
        if (DEBUG) Log.d(TAG, "DownloadTask# stop()# mFileInfo.getState(): " + mFileInfo.getState());

        switch (mFileInfo.getState()) {
            case NEW:           ///创建下载任务对象（NEW）后停止下载stop()
                ///忽略
                break;
            case INITIALIZED:   ///初始化（INITIALIZED）后停止下载stop()
                ///忽略
                break;
            case STARTED:       ///下载开始（STARTED）后停止下载stop()
                ///更新文件信息的状态：下载停止
                ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
                mFileInfo.setState(DownloadState.STOPPED);

                break;
            case PAUSED:        ///下载暂停（PAUSED）后停止下载stop()
                ///发送消息：下载停止
                mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();

                break;
            case STOPPED:       ///下载停止（STOPPED）后停止下载stop()
                ///忽略
                break;
            case SUCCEED:       ///下载成功（SUCCEED）后停止下载stop()
                ///发送消息：下载停止
                mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();

                break;
            case INIT_FAILED:       ///初始化失败（INIT_FAILED）后暂停下载pause()
                ///发送消息：下载停止
                mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();

                break;
            case DOWNLOAD_FAILED:   ///下载失败（DOWNLOAD_FAILED）后停止下载stop()
                ///发送消息：下载停止
                mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();

                break;
        }
    }

    private void innerStart() {
        ///更新文件信息的状态：下载开始
        ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
        mFileInfo.setState(DownloadState.STARTED);

        ///启动下载线程
        startDownloadThreads();

        ///启动定时器更新进度
        startTimer();

        ///发送消息：下载开始
        mHandler.obtainMessage(DownloadHandler.MSG_STARTED).sendToTarget();
    }

    private void startDownloadThreads() {
        ///找出所有未成功的线程信息（除了成功以为任何状态）添加到线程信息集合
        final ArrayList<ThreadInfo> unCompleteThreadInfos = new ArrayList<>();
        for (ThreadInfo threadInfo : mThreadInfos) {
            if (DownloadState.SUCCEED != threadInfo.getState()) {
                unCompleteThreadInfos.add(threadInfo);
            }
        }

        ///[FIX BUG: 完成（成功/失败/停止）暂停后出现多次重复的消息通知！]
        ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
        ///https://www.cnblogs.com/dolphin0520/p/3920397.html
        final CyclicBarrier barrier = new CyclicBarrier(unCompleteThreadInfos.size(), new Runnable() {
            @Override
            public void run() {
                ///遍历所有线程信息，如果存在停止状态，则说明文件信息的状态是停止状态
                ///否则如果存在暂停状态，则说明文件信息的状态是暂停状态
                ///否则就应该是成功状态
                DownloadState state = DownloadUtil.getStateFromThreadInfos(unCompleteThreadInfos);
                if (state == DownloadState.PAUSED) {
                    ///发送消息：下载暂停
                    mHandler.obtainMessage(DownloadHandler.MSG_PAUSED).sendToTarget();

                    ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
                    synchronized (LOCK) {
                        if (DEBUG) Log.d(TAG, "DownloadTask# startDownloadThreads()# barrier: LOCK.notify() ............................");
                        LOCK.notify();
                    }
                } else if (state == DownloadState.STOPPED) {
                    ///发送消息：下载停止
                    mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();
                } else if (state == DownloadState.SUCCEED) {
                    ///发送消息：下载成功
                    mHandler.obtainMessage(DownloadHandler.MSG_SUCCEED).sendToTarget();
                }
            }
        });

        ///遍历所有暂停的线程信息集合，逐个启动线程
        for (ThreadInfo threadInfo : unCompleteThreadInfos) {
            final DownloadThread downloadThread = new DownloadThread (
                    mConfig,
                    mFileInfo,
                    mHandler,
                    threadInfo,
                    mThreadDAO,
                    barrier);

            ///线程池
//            downloadThread.start();
            sExecutorService.execute(downloadThread);
        }
    }

    ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]
    ///分析：下载完成（成功/失败/停止）或暂停到取消定时器期间，应该再运行一次定时任务去更新进度
    ///解决：加入可以停止定时器的标识mayStopTimer来控制定时器的取消
    /**
     * 可以停止定时器的标识
     */
    boolean mayStopTimer;

    /**
     * 定时器
     */
    private long currentTimeMillis;
    private long currentFinishedBytes;
    private Handler mTimerHandler;
    private Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "DownloadTask# mTimer.schedule()# run()# ------- 触发定时器 -------");

            ///发送消息：更新进度
            final long diffTimeMillis = System.currentTimeMillis() - currentTimeMillis;   ///下载进度的耗时（毫秒）
            currentTimeMillis = System.currentTimeMillis();
            final long diffFinishedBytes = mFileInfo.getFinishedBytes() - currentFinishedBytes;  ///下载进度的下载字节数
            currentFinishedBytes = mFileInfo.getFinishedBytes();
            mHandler.obtainMessage(DownloadHandler.MSG_PROGRESS, new long[]{diffTimeMillis, diffFinishedBytes}).sendToTarget();

            ///累计文件信息的已经完成的总耗时（毫秒）
            mFileInfo.setFinishedTimeMillis(mFileInfo.getFinishedTimeMillis() + diffTimeMillis);

            ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]
            if (mayStopTimer) {
                ///停止定时器
                stopTimer();
            } else {
                mTimerHandler.postDelayed(mTimerRunnable, mConfig.progressInterval);
            }
        }
    };

    /**
     * 启动定时器
     */
    void startTimer() {
        if (DEBUG) Log.d(TAG, "DownloadTask# startTimer()# ------- 启动定时器 -------");

        ///控制更新进度的周期
        currentTimeMillis = System.currentTimeMillis();
        currentFinishedBytes = mFileInfo.getFinishedBytes();

        ///设置可以停止定时器的标识为false
        mayStopTimer = false;

        ///启动定时器Timer
        if (mTimerHandler == null) {
            ///https://stackoverflow.com/questions/3875184/cant-create-handler-inside-thread-that-has-not-called-looper-prepare
            mTimerHandler = new Handler(Looper.getMainLooper());
        }
        mTimerHandler.postDelayed(mTimerRunnable, mConfig.progressInterval);
    }

    /**
     * 停止定时器
     */
    void stopTimer() {
        if (DEBUG) Log.d(TAG, "DownloadTask# stopTimer()# ------- 停止定时器 -------");

        if (mTimerHandler != null) {
            mTimerHandler.removeCallbacks(mTimerRunnable);
            mTimerHandler = null;  ///[FIX BUG# pause后立即start所引起的重复启动问题]解决方法：在运行start()时会同时检测mTimer是否为null的条件
        }
    }

}
