package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Logger;
import com.android.gallery3d.util.MediaSetUtils;

import java.util.ArrayList;

public class LocalPanoramasAlbumSet extends MediaSet {
    public static final String LOCAL_ALL_PANORAMAS_PATH = "/local/panoramas/all";
    private static final String TAG = "LocalPanoramasAlbumSet";
    private static final String[] COUNT_PROJECTION = {"count(*)"};
    private static final int INVALID_COUNT = 0;
    private final String mName;
    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final Uri mBaseUri;
    private int mCachedCount = INVALID_COUNT;
    private String mWhereClause;
    private String mOrderClause;
    private String[] mProjection;
    private Path mItemPath;

    private ChangeNotifier mNotifier;

    public LocalPanoramasAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mName = application.getResources().getString(R.string.folder_panorama);
        mOrderClause = ImageColumns.DATE_TAKEN + " DESC, " + ImageColumns._ID + " DESC";
        mBaseUri = Images.Media.EXTERNAL_CONTENT_URI;
        mWhereClause = ImageColumns.BUCKET_ID + " = ? AND " + ImageColumns.TITLE + " LIKE ?";
        mProjection = LocalImage.PROJECTION;
        mItemPath = LocalImage.ITEM_PATH;

        mNotifier = new ChangeNotifier(this, mBaseUri, application);
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor, DataManager dataManager, GalleryApp app) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new LocalImage(path, app, cursor);
            } else {
                item.updateContent(cursor);
            }
            return item;
        }
    }

    @Override
    public Uri getContentUri() {
        return mBaseUri;
    }

    @Override
    public String getName() {
        return mApplication.getResources().getString(R.string.folder_panorama);
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon().appendQueryParameter("limit", start + "," + count).build();
        ArrayList<MediaItem> list = new ArrayList<MediaItem>();
        GalleryUtils.assertNotInRenderThread();
        Cursor cursor = mResolver.query(uri, mProjection, mWhereClause,
                new String[]{String.valueOf(MediaSetUtils.CAMERA_BUCKET_ID), GalleryUtils.PANORAMAS_TITLE}, mOrderClause);
        if (cursor == null) {
            Logger.w(TAG, "query fail: " + uri);
            return list;
        }

        try {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column
                Path childPath = mItemPath.getChild(id);
                MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager, mApplication);
                list.add(item);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_DELETE | SUPPORT_INFO;
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
        }
        return mDataVersion;
    }

    @Override
    public void delete() {
        GalleryUtils.assertNotInRenderThread();
        mResolver.delete(mBaseUri, mWhereClause,
                new String[]{String.valueOf(MediaSetUtils.CAMERA_BUCKET_ID), GalleryUtils.PANORAMAS_TITLE});
        //MediaSetUtils.requestSync(mApplication.getAndroidContext());
    }

    @Override
    public int getMediaItemCount() {
        //if (mCachedCount == INVALID_COUNT) {
        Cursor cursor = mResolver.query(mBaseUri, COUNT_PROJECTION, mWhereClause,
                new String[]{String.valueOf(MediaSetUtils.CAMERA_BUCKET_ID), GalleryUtils.PANORAMAS_TITLE}, null);
        if (cursor == null) {
            Logger.w(TAG, "query fail");
            return 0;
        }
        try {
            Utils.assertTrue(cursor.moveToNext());
            mCachedCount = cursor.getInt(0);
        } finally {
            cursor.close();
        }
        //}
        return mCachedCount;
    }
}
