package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Logger;
import java.util.ArrayList;

public class LocalVideoAlbumSet extends MediaSet {
    public static final String LOCAL_ALL_VIDEO_PATH = "/local/video/all";
    private static final String TAG = "LocalVideoAlbumSet";
    private static final String[] COUNT_PROJECTION = {"count(*)"};
    private static final int INVALID_COUNT = 0;
    private final String mName;
    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final Uri mBaseUri;
    private int mCachedCount = INVALID_COUNT;
    private String mOrderClause;
    private String[] mProjection;
    private Path mItemPath;

    private ChangeNotifier mNotifier;

    public LocalVideoAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mName = application.getResources().getString(R.string.folder_video);
        mOrderClause = VideoColumns.DATE_TAKEN + " DESC, " + VideoColumns._ID + " DESC";
        mBaseUri = Video.Media.EXTERNAL_CONTENT_URI;
        mProjection = LocalVideo.PROJECTION;
        mItemPath = LocalVideo.ITEM_PATH;

        mNotifier = new ChangeNotifier(this, mBaseUri, application);
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor, DataManager dataManager, GalleryApp app) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                item = new LocalVideo(path, app, cursor);
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
        return mName;
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon().appendQueryParameter("limit", start + "," + count).build();
        ArrayList<MediaItem> list = new ArrayList<MediaItem>();
        GalleryUtils.assertNotInRenderThread();
        Cursor cursor = mResolver.query(uri, mProjection, null, null, mOrderClause);
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
        mResolver.delete(mBaseUri, null, null);
        //MediaSetUtils.requestSync(mApplication.getAndroidContext());
    }

    @Override
    public int getMediaItemCount() {
        //if (mCachedCount == INVALID_COUNT) {
        Cursor cursor = mResolver.query(mBaseUri, COUNT_PROJECTION, null, null, null);
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
