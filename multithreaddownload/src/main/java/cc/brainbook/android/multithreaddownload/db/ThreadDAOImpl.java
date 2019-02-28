package cc.brainbook.android.multithreaddownload.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cc.brainbook.android.multithreaddownload.bean.ThreadInfo;

public class ThreadDAOImpl implements ThreadDAO {
    private DBHelper mHelper;

    public ThreadDAOImpl(Context context) {
        mHelper = DBHelper.getInstance(context);
    }

    @Override
    public synchronized long insertThread(ThreadInfo threadInfo) {
        SQLiteDatabase db = mHelper.getWritableDatabase();

        ///https://developer.android.com/training/data-storage/sqlite#WriteDbRow
        ///Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put("file_url", threadInfo.getFileUrl());
        values.put("file_name", threadInfo.getFileName());
        values.put("file_size", threadInfo.getFileSize());
        values.put("save_path", threadInfo.getSavePath());
        values.put("start", threadInfo.getStart());
        values.put("end", threadInfo.getEnd());
        values.put("finished", threadInfo.getFinishedBytes());

        ///Insert the new row, returning the primary key value of the new row
        long newRowId = db.insert("thread_info", null, values);

        db.close();
        return newRowId;
    }

    @Override
    public synchronized int updateThread(long thread_id, long finished) {
        SQLiteDatabase db = mHelper.getWritableDatabase();

        ///https://developer.android.com/training/data-storage/sqlite#UpdateDbRow
        ///New value for one column
        ContentValues values = new ContentValues();
        values.put("finished", finished);

        ///Which row to update, based on the title
        String selection = "_id=?";
        String[] selectionArgs = {thread_id+""};

        int count = db.update(
                "thread_info",
                values,
                selection,
                selectionArgs);

        db.close();
        return count;
    }

    @Override
    public synchronized int deleteAllThread(String fileUrl, String fileName, long fileSize, String savePath) {
        SQLiteDatabase db = mHelper.getWritableDatabase();

        ///https://developer.android.com/training/data-storage/sqlite#DeleteDbRow
        ///Define 'where' part of query.
        String selection = "file_url=? and file_name=? and file_size=? and save_path=?";
        ///Specify arguments in placeholder order.
        String[] selectionArgs = {fileUrl, fileName, fileSize+"", savePath};
        ///Issue SQL statement.
        int deletedRows = db.delete("thread_info", selection, selectionArgs);

        db.close();
        return deletedRows;
    }

    @Override
    public List<ThreadInfo> getAllThreads(String fileUrl, String fileName, long fileSize, String savePath) {
        List<ThreadInfo> list = new ArrayList<>();
        SQLiteDatabase db = mHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("select * from thread_info where file_url=? and file_name=? and file_size=? and save_path=?",
                new String[]{fileUrl, fileName, fileSize+"", savePath});
        while (cursor.moveToNext()) {
            ThreadInfo threadInfo = new ThreadInfo();
            threadInfo.setId(cursor.getInt(cursor.getColumnIndex("_id")));
            threadInfo.setFileUrl(cursor.getString(cursor.getColumnIndex("file_url")));
            threadInfo.setFileName(cursor.getString(cursor.getColumnIndex("file_name")));
            threadInfo.setFileSize(Long.valueOf(cursor.getString(cursor.getColumnIndex("file_size"))));
            threadInfo.setSavePath(cursor.getString(cursor.getColumnIndex("save_path")));
            threadInfo.setStart(cursor.getLong(cursor.getColumnIndex("start")));
            threadInfo.setEnd(cursor.getLong(cursor.getColumnIndex("end")));
            threadInfo.setFinishedBytes(cursor.getLong(cursor.getColumnIndex("finished")));
            list.add(threadInfo);
        }
        cursor.close();
        db.close();
        return list;
    }

    @Override
    public boolean isExists(long thread_id) {
        SQLiteDatabase db = mHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("select * from thread_info where _id=?",
                new String[]{thread_id+""});
        boolean exist = cursor.moveToNext();
        cursor.close();
        db.close();
        return exist;
    }
}
