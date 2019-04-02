package cc.brainbook.android.multithreaddownload.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cc.brainbook.android.multithreaddownload.enumeration.DownloadState;
import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;

public class ThreadInfoDAOImpl implements ThreadInfoDAO {
    private DBHelper mHelper;

    public ThreadInfoDAOImpl(Context context) {
        mHelper = DBHelper.getInstance(context);
    }

    @Override
    public synchronized long saveThreadInfo(ThreadInfo threadInfo,
                                            long created_time_millis,
                                            long updated_time_millis) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();

        ///https://developer.android.com/training/data-storage/sqlite#WriteDbRow
        ///Create a new map of values, where column names are the keys
        final ContentValues values = new ContentValues();
        values.put("state", threadInfo.getState().toString());
        values.put("finished_bytes", threadInfo.getFinishedBytes());
        values.put("finished_time_millis", threadInfo.getFinishedTimeMillis());
        values.put("created_time_millis", threadInfo.getCreatedTimeMillis());
        values.put("updated_time_millis", threadInfo.getUpdatedTimeMillis());
        values.put("start", threadInfo.getStart());
        values.put("end", threadInfo.getEnd());
        values.put("file_url", threadInfo.getFileUrl());
        values.put("file_name", threadInfo.getFileName());
        values.put("file_size", threadInfo.getFileSize());
        values.put("save_path", threadInfo.getSavePath());

        ///Insert the new row, returning the primary key value of the new row
        final long newRowId = db.insert("thread_info", null, values);

        db.close();
        return newRowId;
    }

    @Override
    public synchronized int updateThreadInfo(long thread_id,
                                             DownloadState state,
                                             long finishedBytes,
                                             long finishedTimeMillis,
                                             long updatedTimeMillis) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();

        ///https://developer.android.com/training/data-storage/sqlite#UpdateDbRow
        ///New value for one column
        final ContentValues values = new ContentValues();
        values.put("state", state.toString());
        values.put("finished_bytes", finishedBytes);
        values.put("finished_time_millis", finishedTimeMillis);
        values.put("updated_time_millis", updatedTimeMillis);

        ///Which row to update, based on the title
        final String selection = "_id=?";
        final String[] selectionArgs = {thread_id+""};

        final int count = db.update(
                "thread_info",
                values,
                selection,
                selectionArgs);

        db.close();
        return count;
    }

    @Override
    public synchronized int deleteAllThreadInfos(String fileUrl,
                                                 String fileName,
                                                 long fileSize,
                                                 String savePath) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();

        ///https://developer.android.com/training/data-storage/sqlite#DeleteDbRow
        ///Define 'where' part of query.
        final String selection = "file_url=? and file_name=? and file_size=? and save_path=?";
        ///Specify arguments in placeholder order.
        final String[] selectionArgs = {fileUrl, fileName, fileSize+"", savePath};
        ///Issue SQL statement.
        final int deletedRows = db.delete("thread_info", selection, selectionArgs);

        db.close();
        return deletedRows;
    }

    @Override
    public synchronized List<ThreadInfo> loadAllThreadsInfos(String fileUrl,
                                                             String fileName,
                                                             long fileSize,
                                                             String savePath) {
        final List<ThreadInfo> list = new ArrayList<>();
        final SQLiteDatabase db = mHelper.getReadableDatabase();
        final Cursor cursor = db.rawQuery("select * from thread_info where file_url=? and file_name=? and file_size=? and save_path=?",
                new String[]{fileUrl, fileName, fileSize+"", savePath});
        while (cursor.moveToNext()) {
            final ThreadInfo threadInfo = new ThreadInfo();

            threadInfo.setState(DownloadState.getState(cursor.getString(cursor.getColumnIndex("state"))));
            threadInfo.setFinishedBytes(cursor.getLong(cursor.getColumnIndex("finished_bytes")));
            threadInfo.setFinishedTimeMillis(cursor.getLong(cursor.getColumnIndex("finished_time_millis")));
            threadInfo.setId(cursor.getInt(cursor.getColumnIndex("_id")));
            threadInfo.setStart(cursor.getLong(cursor.getColumnIndex("start")));
            threadInfo.setEnd(cursor.getLong(cursor.getColumnIndex("end")));
            threadInfo.setFileUrl(cursor.getString(cursor.getColumnIndex("file_url")));
            threadInfo.setFileName(cursor.getString(cursor.getColumnIndex("file_name")));
            threadInfo.setFileSize(Long.valueOf(cursor.getString(cursor.getColumnIndex("file_size"))));
            threadInfo.setSavePath(cursor.getString(cursor.getColumnIndex("save_path")));

            list.add(threadInfo);
        }
        cursor.close();
        db.close();
        return list;
    }

    @Override
    public boolean isExists(long thread_id) {
        final SQLiteDatabase db = mHelper.getReadableDatabase();
        final Cursor cursor = db.rawQuery("select * from thread_info where _id=?",
                new String[]{thread_id+""});
        boolean exist = cursor.moveToNext();
        cursor.close();
        db.close();
        return exist;
    }
}
