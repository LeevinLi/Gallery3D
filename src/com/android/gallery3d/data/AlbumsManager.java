package com.android.gallery3d.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.Logger;

import java.util.ArrayList;
import java.util.List;


public class AlbumsManager {

    public static final String[] PROJECTION_BUCKET = {
            AlbumDBHelper.Columns._ID,                //0
            AlbumDBHelper.Columns.BUCKET_ID,          //1
            AlbumDBHelper.Columns.BUCKET_NAME,        //2
            AlbumDBHelper.Columns.DATE_TAKEN,         //3
            AlbumDBHelper.Columns.STATUS};            //4

    private static final String TAG = "AlbumsManager";
    private static final int INDEX_ID = 0;
    private static final int INDEX_BUCKET_ID = 1;
    private static final int INDEX_BUCKET_NAME = 2;
    private static final int INDEX_DATE_TAKEN = 3;
    private static final int INDEX_STATUS = 4;
    private final AlbumDBHelper dbHelper;
    private SQLiteDatabase database = null;

    public AlbumsManager(Context context) {
        dbHelper = new AlbumDBHelper(context);
    }

    public void open() {
        Logger.d(TAG, "open");
        try {
            if (database == null) {
                database = dbHelper.getWritableDatabase();
            }
        } catch (SQLiteException e) {
            Log.w(TAG, "could not open database" + e.getMessage());
        }
    }

    private void close() {
        if (database != null) {
            database.close();
        }
    }

    public void destroy() {
        Logger.d(TAG, "destroy");
        if (database != null && database.isOpen()) {
            database.close();
            database = null;
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    public boolean insert(int bucketID, String bucketName, long datataken, String status) {
        Logger.d(TAG, "insert name: " + bucketName);
        boolean ret = true;
        ContentValues val = new ContentValues();
        val.put(AlbumDBHelper.Columns.BUCKET_ID, bucketID);
        val.put(AlbumDBHelper.Columns.BUCKET_NAME, bucketName);
        val.put(AlbumDBHelper.Columns.DATE_TAKEN, datataken);
        val.put(AlbumDBHelper.Columns.STATUS, status);
        try {
            open();
            database.beginTransaction();
            ret = (-1 != database.insert(AlbumDBHelper.TABLE_NAME, null, val));
            database.setTransactionSuccessful();
        } catch (Exception ex) {
            Logger.e(TAG, "insert err: " + ex.getMessage());
            return false;
        } finally {
            database.endTransaction();
            //close();
            return ret;
        }
    }

    public boolean delete(int bucketID) {
        Logger.d(TAG, "delete bucketID: " + bucketID);
        boolean ret = true;
        String where = AlbumDBHelper.Columns.BUCKET_ID + " = ?";
        try {
            open();
            database.beginTransaction();
            int result = database.delete(AlbumDBHelper.TABLE_NAME, where, new String[]{String.valueOf(bucketID)});
            Logger.d(TAG, "delete result-id : " + result);
            ret = result > 0;
            database.setTransactionSuccessful();
        } catch (Exception ex) {
            Logger.e(TAG, "delete err: " + ex.getMessage());
            return false;
        } finally {
            database.endTransaction();
            //close();
            return ret;
        }
    }

    public boolean delete(String bucketName) {
        Logger.d(TAG, "delete name: " + bucketName);
        boolean ret = true;
        String where = AlbumDBHelper.Columns.BUCKET_NAME + " = ?";
        try {
            open();
            database.beginTransaction();
            int result = database.delete(AlbumDBHelper.TABLE_NAME, where, new String[]{bucketName});
            Logger.d(TAG, "delete result-name : " + result);
            ret = result > 0;
            database.setTransactionSuccessful();
        } catch (Exception ex) {
            Logger.e(TAG, "delete err: " + ex.getMessage());
            return false;
        } finally {
            database.endTransaction();
            //close();
            return ret;
        }
    }

    public boolean exists(String bucketName) {
        Logger.d(TAG, "exists name: " + bucketName);
        boolean ret = false;
        String where = AlbumDBHelper.Columns.BUCKET_NAME + " = ?";
        Cursor cursor = null;
        try {
            open();
            database.beginTransaction();
            cursor = database.query(AlbumDBHelper.TABLE_NAME, PROJECTION_BUCKET,
                    where, new String[]{bucketName}, null, null, null, "1");
            if (cursor != null && cursor.moveToNext()) {
                ret = true;
            }
            database.setTransactionSuccessful();
        } catch (Exception ex) {
            Logger.e(TAG, "exists err: " + ex.getMessage());
            ret = false;
        } finally {
            database.endTransaction();
            //close();
            return ret;
        }
    }

    public List<BucketHelper.BucketEntry> loadBucketEntriesFromAlbumTable() {
        Logger.d(TAG, "loadBucketEntriesFromAlbumTable");
        List<BucketHelper.BucketEntry> datas = new ArrayList<>();
        BucketHelper.BucketEntry bucketEntry = null;
        Cursor cursor = null;
        try {
            open();
            database.beginTransaction();
            cursor = database.query(AlbumDBHelper.TABLE_NAME, PROJECTION_BUCKET, null, null, null, null, AlbumDBHelper.ORDER_BY);
            if (cursor == null) {
                Logger.w(TAG, "cannot open gallery album database or data is empty");
                return null;
            }
            while (cursor.moveToNext()) {
                int bucketId = cursor.getInt(INDEX_BUCKET_ID);
                String bucketName = cursor.getString(INDEX_BUCKET_NAME);
                int dateTaken = cursor.getInt(INDEX_DATE_TAKEN);
                int status = cursor.getInt(INDEX_STATUS);
                bucketEntry = new BucketHelper.BucketEntry(bucketId, bucketName, status);
                bucketEntry.dateTaken = dateTaken;
                datas.add(bucketEntry);
            }
            database.setTransactionSuccessful();
            return datas;
        } catch (Exception ex) {
            Logger.e(TAG, "loadBucketEntriesFromAlbumTable err: " + ex.getMessage());
            return null;
        } finally {
            database.endTransaction();
            Utils.closeSilently(cursor);
            //close();
        }
    }


//
//    public ArrayList<FilterUserPresetRepresentation> getAllUserPresets() {
//        ArrayList<FilterUserPresetRepresentation> ret =
//                new ArrayList<FilterUserPresetRepresentation>();
//
//        Cursor c = null;
//        database.beginTransaction();
//        try {
//            c = database.query(FilterStackDBHelper.FilterStack.TABLE,
//                    new String[]{FilterStackDBHelper.FilterStack._ID,
//                            FilterStackDBHelper.FilterStack.STACK_ID,
//                            FilterStackDBHelper.FilterStack.FILTER_STACK},
//                    null, null, null, null, null, null);
//            if (c != null) {
//                boolean loopCheck = c.moveToFirst();
//                while (loopCheck) {
//                    int id = c.getInt(0);
//                    String name = (c.isNull(1)) ? null : c.getString(1);
//                    byte[] b = (c.isNull(2)) ? null : c.getBlob(2);
//                    String json = new String(b);
//
//                    ImagePreset preset = new ImagePreset();
//                    preset.readJsonFromString(json);
//                    FilterUserPresetRepresentation representation =
//                            new FilterUserPresetRepresentation(name, preset, id);
//                    ret.add(representation);
//                    loopCheck = c.moveToNext();
//                }
//            }
//            database.setTransactionSuccessful();
//        } finally {
//            if (c != null) {
//                c.close();
//            }
//            database.endTransaction();
//        }
//
//        return ret;
//    }
//
//    public List<Pair<String, byte[]>> getAllStacks() {
//        List<Pair<String, byte[]>> ret = new ArrayList<Pair<String, byte[]>>();
//        Cursor c = null;
//        database.beginTransaction();
//        try {
//            c = database.query(FilterStackDBHelper.FilterStack.TABLE,
//                    new String[]{FilterStackDBHelper.FilterStack.STACK_ID, FilterStackDBHelper.FilterStack.FILTER_STACK},
//                    null, null, null, null, null, null);
//            if (c != null) {
//                boolean loopCheck = c.moveToFirst();
//                while (loopCheck) {
//                    String name = (c.isNull(0)) ? null : c.getString(0);
//                    byte[] b = (c.isNull(1)) ? null : c.getBlob(1);
//                    ret.add(new Pair<String, byte[]>(name, b));
//                    loopCheck = c.moveToNext();
//                }
//            }
//            database.setTransactionSuccessful();
//        } finally {
//            if (c != null) {
//                c.close();
//            }
//            database.endTransaction();
//        }
//        if (ret.size() <= 0) {
//            return null;
//        }
//        return ret;
//    }
}
