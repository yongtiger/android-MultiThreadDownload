package cc.brainbook.study.mymultithreaddownload.application;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ///[LeakCanary] A memory leak detection library. https://github.com/square/leakcanary
        if (LeakCanary.isInAnalyzerProcess(this)) {
            /// 用来进行过滤操作，如果当前的进程是用来给LeakCanary 进行堆分析的则return，否则会执行LeakCanary的install方法。
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
        // Normal app init code...

    }
}