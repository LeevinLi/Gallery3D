package com.android.gallery3d.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AlbumDBHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "gallery3d_album.db";
    public static final String TABLE_NAME = "gallery_album";
    public static final String ORDER_BY = Columns.DATE_TAKEN + " DESC";

    private static final String SQL_CREATE_TABLE = "CREATE TABLE ";
    private static final String[][] CREATE_TABLE_ALBUM = {
            {Columns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"},
            {Columns.BUCKET_ID, "INTEGER NOT NULL UNIQUE"},
            {Columns.BUCKET_NAME, "TEXT"},
            {Columns.DATE_TAKEN, "INTEGER"},
            {Columns.STATUS, "TEXT"}
    };

    public AlbumDBHelper(Context context, String name, int version) {
        super(context, name, null, version);
    }

    public AlbumDBHelper(Context context, String name) {
        this(context, name, DATABASE_VERSION);
    }

    public AlbumDBHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    protected static void createTable(SQLiteDatabase db, String table, String[][] columns) {
        StringBuilder create = new StringBuilder(SQL_CREATE_TABLE);
        create.append(table).append('(');
        boolean first = true;
        for (String[] column : columns) {
            if (!first) {
                create.append(',');
            }
            first = false;
            for (String val : column) {
                create.append(val).append(' ');
            }
        }
        create.append(')');
        db.beginTransaction();
        try {
            db.execSQL(create.toString());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    protected static void dropTable(SQLiteDatabase db, String table) {
        db.beginTransaction();
        try {
            db.execSQL("drop table if exists " + table);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db, TABLE_NAME, CREATE_TABLE_ALBUM);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        dropTable(db, TABLE_NAME);
        onCreate(db);
    }

    /**
     * BucketEntry{
     * String bucketName;
     * int bucketId;
     * int dateTaken;
     * int status;
     * }
     */
    public static interface Columns {

        public static final String _ID = "_id";

        public static final String BUCKET_ID = "bucketId";

        public static final String BUCKET_NAME = "bucketName";

        public static final String DATE_TAKEN = "dateTaken";

        public static final String STATUS = "status";
    }
}
