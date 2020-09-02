package cc.brainbook.android.multithreaddownload.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "download.db";
    private static final int VERSION = 1;
    private static final String SQL_CREATE ="create table thread_info(_id integer primary key AUTOINCREMENT," +
            "state, finished_bytes long, finished_time_millis long, created_time_millis long, updated_time_millis long, " +
            "file_url text, file_name text, " + "file_size long, save_path text, start long, end long)";
    private static final String SQL_DROP ="drop table if exists thread_info";

    // 单例模式：
    // 1、饿汉方式（以空间换时间，资源浪费！且无法传参！但线程安全），DBHelper创建时要求传入Context参数，所以放弃！（暂不考虑创建对象后另外调用方法传递参数）
    // 但效率不高！会出现调用静态成员同时对instance进行实例化的情况
//    public class Singleton{
//        //类加载时就初始化，且定义为static final静态常量
//        private static final Singleton instance = new Singleton();
//
//        private Singleton(){}
//
//        public static Singleton getInstance(){
//            return instance;
//        }
//    }

    // 2、懒汉方式：同步方法（并不高效）
    // 虽然做到了线程安全,并且解决了多实例的问题,但是它并不高效.因为在任何时候只能有一个线程调用 getInstance()方法.
    // 但是同步操作只需要在第一次调用时才被需要,即第一次创建单例实例对象时.这就引出了双重检验锁.
//    // 构造方法为private
//    private DBHelper(@Nullable Context context) {
//        super(context, DB_NAME, null, VERSION);
//    }
//    // 私有静态实例
//    private static DBHelper sDBHelper;
//    // 公有静态方法：获得实例（可传入参数！）
//    // 保证线程安全
//    public static synchronized DBHelper getInstance(@Nullable Context context) {
//        if (sDBHelper == null) {
//            sDBHelper = new DBHelper(context);
//        }
//        return sDBHelper;
//    }

    // 上面代码中参数的赋值是在单例的创建里面进行的，这里是因为违背了单一职责原则。修改方法：只需要将context从单例的创建中分离出来
    ///https://blog.csdn.net/li_huorong/article/details/49804955
    // 但不适合本例！因为构造中就需要传入参数context
//    // 构造方法为private
//    private DBHelper(@Nullable Context context) {
//        super(context, DB_NAME, null, VERSION);
//    }
//    // 私有静态实例
//    private static DBHelper sDBHelper;
//    // 公有静态方法：获得实例（可传入参数！）
//    // 保证线程安全
//    public static synchronized DBHelper getInstance(@Nullable Context context) {
//        if (sDBHelper == null) {
//            sDBHelper = new DBHelper();
//        }
//        mContext = context;
//        return sDBHelper;
//    }
//    //注意：Context不能为static！容易造成内存泄漏！
//    private static Context mContext;
//    private DBHelper() {
//        this(mContext);
//    }

    // 3、懒汉方式：双重检验锁（复杂又隐含Java版本问题）
    // 使用 volatile使得JVM对该代码的优化丧失，影响性能！
    // 特别注意在 Java 5 以前的版本使用了 volatile 的双检锁还是有问题的。其原因是 Java 5 以前的 JMM （Java 内存模型）是存在缺陷的，即时将变量声明成 volatile 也不能完全避免重排序，主要是 volatile 变量前后的代码仍然存在重排序问题。这个 volatile 屏蔽重排序的问题在 Java 5 中才得以修复，所以在这之后才可以放心使用 volatile。
    // 相信你不会喜欢这种复杂又隐含问题的方式，当然我们有更好的实现线程安全的单例模式的办法
    // 构造方法为private
    private DBHelper(@Nullable Context context) {
        super(context, DB_NAME, null, VERSION);
    }
    // 私有静态实例
    // https://blog.csdn.net/imobama/article/details/81093394
    // 使用 volatile 的主要原因是其一个特性：禁止指令重排序优化。 instance = new Singleton()这句代码并非一个原子性操作,实际上在JVM里大概做了3件事
    private volatile static DBHelper sDBHelper;
    // 公有静态方法：获得实例（可传入参数！）
    // 保证线程安全
    public static DBHelper getInstance(@Nullable Context context) {
        if (sDBHelper == null) {
            synchronized (DBHelper.class) {
                if (sDBHelper == null) {
                    sDBHelper = new DBHelper(context);
                }
            }
        }
        return sDBHelper;
    }

    // 4、懒汉方式之静态内部类
    // 注意：Context不能为static！容易造成内存泄漏！所以不适合本例
//    private static String s;
//    private static int i;
//    private SonB() {
//        super(s, i);
//    }
//    private static class LoadSonB {
//        private static final SonB SONB_INTANCE = new SonB();
//    }
//    public static SonB getInstance(String s1,int i1){
//        s=s1;
//        i=i1;
//        return LoadSonB.SONB_INTANCE;
//    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DROP);
        db.execSQL(SQL_CREATE);
    }
}
