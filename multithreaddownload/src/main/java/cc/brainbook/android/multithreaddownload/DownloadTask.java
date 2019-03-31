package cc.brainbook.android.multithreaddownload;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
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
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAO;
import cc.brainbook.android.multithreaddownload.db.ThreadInfoDAOImpl;
import cc.brainbook.android.multithreaddownload.exception.DownloadException;
import cc.brainbook.android.multithreaddownload.handler.DownloadHandler;
import cc.brainbook.android.multithreaddownload.listener.DownloadListener;
import cc.brainbook.android.multithreaddownload.thread.DownloadThread;
import cc.brainbook.android.multithreaddownload.thread.InitThread;
import cc.brainbook.android.multithreaddownload.util.HttpDownloadUtil;
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
 * 以保证每个线程下载的文件长度不少于MINIMUM_DOWNLOAD_PART_SIZE（5MB），极端情况下，如果文件总长度小于5MB，则只分配一个线程？？？？？？
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
    private ThreadInfoDAO mThreadDAO;
    private DownloadListener mDownloadListener;

    private long currentTimeMillis;
    private long currentFinishedBytes;

    ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
    /**
     * Lock to synchronize access.
     */
    private static final Object LOCK = new Object();

    ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]
    ///分析：下载完成（成功/失败/停止）或暂停到取消定时器期间，应该再运行一次定时任务去更新进度
    ///解决：加入成员变量mayStopTimer来控制定时器的取消
    /**
     * 定时器、可以停止定时器的标识
     */
    private Timer mTimer;
    public boolean mayStopTimer;    ///public供DownloadHandler访问

    /**
     * 线程信息集合
     */
    public List<ThreadInfo> mThreadInfos;   ///public供DownloadHandler访问

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
    public DownloadTask setDownloadListener(DownloadListener onProgressListener) {
        mDownloadListener = onProgressListener;
        return this;
    }
    /* ------------ 链式配置 ----------- */


    /**
     * 开始下载
     */
    public void start() {
        if (DEBUG) Log.d(TAG, "DownloadTask# start()# mFileInfo.getState(): " + mFileInfo.getState());

        switch (mFileInfo.getState()) {
            case FAILED:        ///允许：下载失败（FAILED）后开始下载start()？？？？？？？？？？？？？？？？？？

                break;
            case NEW:           ///允许：新创建下载任务对象（NEW）后开始下载start()
                ///初始化过程
                init();

                break;
            case INITIALIZED:   ///允许：新创建下载任务对象后初始化（INITIALIZED）后开始下载start()
                ///执行下载过程
                innerStart();

                break;
            case STARTED:       ///禁止：下载开始（STARTED）后开始下载start()？？？？？？？？？？？？？？？？？？

                break;
            case PAUSED:        ///允许：下载暂停（PAUSED）后开始下载start()
                ///[FIX BUG#pause后立即start所引起的重复启动问题]运行start()时会同时检测mTimer是否为null的条件
                if (mTimer != null) {
                    return;
                }

                ///执行下载过程
                innerStart();

                break;
            case STOPPED:       ///允许：下载停止（STOPPED）后开始下载start()？？？？？？？？？？？？？？？？？？
                ///执行下载过程
                innerStart();

                break;
            case SUCCEED:       ///允许：下载成功（SUCCEED）后开始下载start()
                ///同步发送消息：下载停止
                mHandler.handleMessage(mHandler.obtainMessage(DownloadHandler.MSG_STOPPED));

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
            case FAILED:        ///禁止：下载失败（FAILED）后暂停下载pause()？？？？？？？？？？？？？？？？？？

                break;
            case NEW:           ///禁止：新创建下载任务对象（NEW）后暂停下载pause()

                break;
            case INITIALIZED:   ///禁止：新创建下载任务对象后初始化（INITIALIZED）后暂停下载pause()

                break;
            case STARTED:       ///允许：下载开始（STARTED）后暂停下载pause()？？？？？？？？？？？？？？？？？？
                ///更新文件信息的状态：下载暂停
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
            case PAUSED:        ///禁止：下载暂停（PAUSED）后暂停下载pause()

                break;
            case STOPPED:       ///禁止：下载停止（STOPPED）后暂停下载pause()？？？？？？？？？？？？？？？？？？

                break;
            case SUCCEED:       ///禁止：下载成功（SUCCEED）后暂停下载pause()？？？？？？？？？？？？？？？？？？

                break;
        }
    }

    /**
     * 停止下载
     */
    public void stop() {
        if (DEBUG) Log.d(TAG, "DownloadTask# stop()# mFileInfo.getState(): " + mFileInfo.getState());

        switch (mFileInfo.getState()) {
            case FAILED:        ///允许：下载失败（FAILED）后停止下载stop()？？？？？？？？？？？？？？？？？？
                // todo ...
                break;
            case NEW:           ///禁止：新创建下载任务对象（NEW）后停止下载stop()

                break;
            case INITIALIZED:   ///禁止：新创建下载任务对象后初始化（INITIALIZED）后停止下载stop()

                break;
            case STARTED:       ///允许：下载开始（STARTED）后停止下载stop()？？？？？？？？？？？？？？？？？？
                ///更新文件信息的状态：下载停止
                ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
                mFileInfo.setState(DownloadState.STOPPED);

                break;
            case PAUSED:        ///允许：下载暂停（PAUSED）后停止下载stop()？？？？？？？？？？？？？？？？？？
                ///发送消息：下载停止
                mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();

                break;
            case STOPPED:       ///禁止：下载停止（STOPPED）后停止下载stop()？？？？？？？？？？？？？？？？？？

                break;
            case SUCCEED:       ///允许：下载成功（SUCCEED）后停止下载stop()？？？？？？？？？？？？？？？？？？
                // todo ...
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
                mThreadDAO);


        /* ----------- 检验文件网址URL、保存目录 ----------- */
        if (TextUtils.isEmpty(mFileInfo.getFileUrl())) {
            ///发送消息：下载错误
            mHandler.obtainMessage(DownloadHandler.MSG_FAILED,
                    new DownloadException(DownloadException.EXCEPTION_FILE_URL_NULL, "The file url cannot be null."))
                    .sendToTarget();
            return;
        }
        if (TextUtils.isEmpty(mFileInfo.getSavePath())) {
            mFileInfo.setSavePath(Util.getDefaultFilesDirPath(mContext));
        } else {
            if (!Util.mkdirs(mFileInfo.getSavePath())) {
                ///发送消息：下载失败
                mHandler.obtainMessage(DownloadHandler.MSG_FAILED,
                        new DownloadException(DownloadException.EXCEPTION_SAVE_PATH_MKDIR, "The file save path cannot be made: " + mFileInfo.getSavePath()))
                        .sendToTarget();
                return;
            }
        }

        ///如果没有文件名或文件大小，则启动初始化线程由网络连接获得文件名、文件长度
        if (TextUtils.isEmpty(mFileInfo.getFileName()) || mFileInfo.getFileSize() <= 0) {
            ///启动初始化线程
            InitThread initThread = new InitThread (
                    mConfig,
                    mFileInfo,
                    mHandler);

            ///线程池
//            initThread.start();
            sExecutorService.execute(initThread);

        } else {
            ///发送消息：下载初始化
            mHandler.obtainMessage(DownloadHandler.MSG_INITIALIZED).sendToTarget();
        }
    }

    private void innerStart() {
        ///如果mThreadInfos为null，则从数据库加载线程信息集合
        ///注意：mThreadInfos.clear()清空后（不为null），则不必从数据库加载
        if (mThreadInfos == null) {
            ///从数据库获得所有线程信息
            mThreadInfos = mThreadDAO.loadAllThreadsInfos(
                    mFileInfo.getFileUrl(),
                    mFileInfo.getFileName(),
                    mFileInfo.getFileSize(),
                    mFileInfo.getSavePath());
        }

        if (mThreadInfos.isEmpty()) { ///如果mThreadInfos为空
            ///插入数据库记录前，再次确保删除下载文件的所有线程信息
            mThreadDAO.deleteAllThreadInfos(mFileInfo.getFileUrl(), mFileInfo.getFileName(), mFileInfo.getFileSize(), mFileInfo.getSavePath());

            ///根据线程数量创建线程信息，并添加到线程信息集合中
            createToThreadInfos(mConfig.threadCount);

        } else if (DownloadState.INITIALIZED == mFileInfo.getState()){    ///否则如果mThreadInfos有数据，且文件信息状态为INITIALIZED
            ///重置文件信息的下载完成的总字节数、总耗时
            setFileInfoFinished();
        }

        ///找出所有未成功的线程信息（除了成功以为任何状态）添加到线程信息集合
        final ArrayList<ThreadInfo> unCompleteThreadInfos = new ArrayList<>();
        for (ThreadInfo threadInfo : mThreadInfos) {
            if (DownloadState.SUCCEED != threadInfo.getState()) {
                unCompleteThreadInfos.add(threadInfo);
            }
        }

        ///如果没有未成功的线程信息
        if (unCompleteThreadInfos.isEmpty()) {
            ///发送消息：下载成功
            mHandler.obtainMessage(DownloadHandler.MSG_SUCCEED).sendToTarget();
            return;
        }

        ///注意：不必一定要用setLength()创建占位文件！///？？？？？？猜想：也许影响速度！
        ///创建下载空文件成功
        if (DownloadState.INITIALIZED == mFileInfo.getState()
                || DownloadState.SUCCEED == mFileInfo.getState()
                || DownloadState.STOPPED == mFileInfo.getState()) {
            ///获得保存文件
            File saveFile = new File(mFileInfo.getSavePath(), mFileInfo.getFileName());

            ///如果保存文件存在则删除
            if (saveFile.exists()) {
                if (!saveFile.delete()) {
                    throw new DownloadException(DownloadException.EXCEPTION_FILE_DELETE_EXCEPTION, "The file cannot be deleted: " + saveFile);
                }
            }

            ///创建下载空文件
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = HttpDownloadUtil.getRandomAccessFile(saveFile, "rwd");
                HttpDownloadUtil.randomAccessFileSetLength(randomAccessFile, mFileInfo.getFileSize());
            } catch (Exception e) {
                ///发送消息：下载错误
                mHandler.obtainMessage(DownloadHandler.MSG_FAILED, e).sendToTarget();
                return;
            } finally {
                ///关闭流Closeable
                Util.closeIO(randomAccessFile);
            }
        }

        ///更新文件信息的状态：下载开始
        ///注意：start/pause/stop尽量提早设置状态（所以不放在Handler中），避免短时间内连续点击造成的重复操作！
        mFileInfo.setState(DownloadState.STARTED);

        ///[FIX BUG: 完成（成功/失败/停止）暂停后出现多次重复的消息通知！]
        ///[CyclicBarrier]实现让一组线程等待至某个状态之后再全部同时执行
        ///https://www.cnblogs.com/dolphin0520/p/3920397.html
        CyclicBarrier barrier = new CyclicBarrier(unCompleteThreadInfos.size(), new Runnable() {
            @Override
            public void run() {
                ///遍历所有线程信息，如果存在停止状态，则说明文件信息的状态是停止状态
                ///否则如果存在暂停状态，则说明文件信息的状态是暂停状态
                ///否则就应该是成功状态
                DownloadState state = null;
                for (ThreadInfo threadInfo : unCompleteThreadInfos) {
                    if (threadInfo.getState() == DownloadState.STOPPED) {
                        state = DownloadState.STOPPED;
                        break;
                    }
                }
                if (state == null) {
                    for (ThreadInfo threadInfo : unCompleteThreadInfos) {
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
                    mHandler.obtainMessage(DownloadHandler.MSG_STOPPED).sendToTarget();
                } else if (state == DownloadState.PAUSED) {
                    ///发送消息：下载暂停
                    mHandler.obtainMessage(DownloadHandler.MSG_PAUSED).sendToTarget();

                    ///[FIX#等待所有下载线程全部暂停之后，再暂停，否则会产生内存泄漏！]
                    synchronized (LOCK) {
                        if (DEBUG) Log.d(TAG, "DownloadTask# innerStart()# barrier: LOCK.notify() ............................");
                        LOCK.notify();
                    }

                } else {    ///DownloadState.SUCCEED
                    ///发送消息：下载成功
                    mHandler.obtainMessage(DownloadHandler.MSG_SUCCEED).sendToTarget();
                }
            }
        });

        ///遍历所有暂停的线程信息集合，逐个启动线程
        for (ThreadInfo threadInfo : unCompleteThreadInfos) {
            DownloadThread downloadThread = new DownloadThread (
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

        ///控制更新进度的周期
        currentTimeMillis = System.currentTimeMillis();
        currentFinishedBytes = mFileInfo.getFinishedBytes();

        ///启动定时器Timer
        mayStopTimer = false;
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ///发送消息：更新进度
                if (DEBUG) Log.d(TAG, "DownloadTask# mTimer.schedule()# run()# ------- 触发定时器 -------");

                long diffTimeMillis = System.currentTimeMillis() - currentTimeMillis;   ///下载进度的耗时（毫秒）
                currentTimeMillis = System.currentTimeMillis();
                long diffFinishedBytes = mFileInfo.getFinishedBytes() - currentFinishedBytes;  ///下载进度的下载字节数
                currentFinishedBytes = mFileInfo.getFinishedBytes();
                mHandler.obtainMessage(DownloadHandler.MSG_PROGRESS, new long[]{diffTimeMillis, diffFinishedBytes}).sendToTarget();

                ///更新文件信息的已经完成的总耗时（毫秒）
                mFileInfo.setFinishedTimeMillis(mFileInfo.getFinishedTimeMillis() + diffTimeMillis);

                ///[FIX BUG# 下载完成（成功/失败/停止）或暂停后取消定时器，进度更新显示99%]
                if (mayStopTimer) {
                    if (DEBUG) Log.d(TAG, "DownloadTask# mTimer.schedule()# run()# ------- 取消定时器 -------");

                    mayStopTimer = false;
                    mTimer.cancel();
                    mTimer = null;  ///[FIX BUG# pause后立即start所引起的重复启动问题]解决方法：在运行start()时会同时检测mTimer是否为null的条件
                }
            }
        }, Config.progressDelay, mConfig.progressInterval);

        ///发送消息：下载开始
        mHandler.obtainMessage(DownloadHandler.MSG_STARTED).sendToTarget();
    }

    /**
     * 根据线程数量创建线程信息，并添加到线程信息集合中
     *
     * @param threadCount
     */
    private void createToThreadInfos(int threadCount) {
        ///获得每个线程的长度
        long length = mFileInfo.getFileSize() / threadCount;

        ///遍历每个线程
        for (int i = 0; i < threadCount; i++) {
            ///创建线程信息
            ThreadInfo threadInfo = new ThreadInfo (
                    DownloadState.NEW,
                    0,
                    0,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    0,
                    i * length,
                    (i + 1) * length - 1,
                    mFileInfo.getFileUrl(),
                    mFileInfo.getFileName(),
                    mFileInfo.getFileSize(),
                    mFileInfo.getSavePath());

            ///处理最后一个线程（可能存在除不尽的情况）
            if (i == threadCount - 1) {
                threadInfo.setEnd(mFileInfo.getFileSize() - 1);
            }

            ///设置线程信息的状态为初始化
            threadInfo.setState(DownloadState.INITIALIZED);

            ///向数据库插入线程信息
            long threadId = mThreadDAO.saveThreadInfo(threadInfo, System.currentTimeMillis(), System.currentTimeMillis());
            threadInfo.setId(threadId);

            ///添加到线程信息集合中
            mThreadInfos.add(threadInfo);
        }
    }

    /**
     * 重置下载文件的下载完的总字节数、总耗时
     */
    private void setFileInfoFinished() {
        for (ThreadInfo threadInfo : mThreadInfos) {
            mFileInfo.setFinishedBytes(mFileInfo.getFinishedBytes() + threadInfo.getFinishedBytes());

            ///注意：threadInfo数据库中保存的是mFileInfo的FinishedTimeMillis（没有做累计！）
            ///这里近似认为各线程的开始时间相同，但结束时间不同，所以取最长的
            if (threadInfo.getFinishedTimeMillis() > mFileInfo.getFinishedTimeMillis()) {
                mFileInfo.setFinishedTimeMillis(threadInfo.getFinishedTimeMillis());
            }
        }
    }

}
