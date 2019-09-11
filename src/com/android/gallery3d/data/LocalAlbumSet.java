/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.data;

import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.data.BucketHelper.BucketEntry;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.Logger;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// LocalAlbumSet lists all image or video albums in the local storage.
// The path should be "/local/image", "local/video" or "/local/all"
public class LocalAlbumSet extends MediaSet implements FutureListener<ArrayList<MediaSet>> {
    public static final Path PATH_ALL = Path.fromString("/local/all");
    public static final Path PATH_IMAGE = Path.fromString("/local/image");
    public static final Path PATH_VIDEO = Path.fromString("/local/video");
    private static final String TAG = "LocalAlbumSet";
    private static final Uri[] mWatchUris = {Images.Media.EXTERNAL_CONTENT_URI, Video.Media.EXTERNAL_CONTENT_URI};

    private final GalleryApp mApplication;
    private final int mType;
    private final int mSourceType;
    private final ChangeNotifier mNotifier;
    private final String mName;
    private final Handler mHandler;
    private ArrayList<MediaSet> mAlbums = new ArrayList<MediaSet>();
    private boolean mIsLoading;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mLoadBuffer;

    public LocalAlbumSet(Path path, GalleryApp application, int sourceType) {
        super(path, nextVersionNumber());
        mApplication = application;
        mHandler = new Handler(application.getMainLooper());
        mType = getTypeFromPath(path);
        mSourceType = sourceType;
        mName = application.getResources().getString(R.string.set_label_local_albums);
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
    }

    private static int getTypeFromPath(Path path) {
        String name[] = path.split();
        if (name.length < 2) {
            throw new IllegalArgumentException(path.toString());
        }
        return getTypeFromString(name[1]);
    }

    private static int findBucket(BucketEntry entries[], int bucketId) {
        for (int i = 0, n = entries.length; i < n; ++i) {
            if (entries[i].bucketId == bucketId) return i;
        }
        return -1;
    }

    // Circular shift the array range from a[i] to a[j] (inclusive). That is,
    // a[i] -> a[i+1] -> a[i+2] -> ... -> a[j], and a[j] -> a[i]
    private static <T> void circularShiftRight(T[] array, int i, int j) {
        T temp = array[j];
        for (int k = j; k > i; k--) {
            array[k] = array[k - 1];
        }
        array[i] = temp;
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mName;
    }

    private MediaSet getLocalAlbum(DataManager manager, int type, Path parent, int id, String name, int status) {
        synchronized (DataManager.LOCK) {
            Path path = parent.getChild(id);
            if (mSourceType != LocalSource.LOCAL_SETTING_ALBUMSET) {
                MediaObject object = manager.peekMediaObject(path);
                if (object != null) return (MediaSet) object;
            }
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return new LocalAlbum(path, mApplication, id, true, name, status);
                case MEDIA_TYPE_VIDEO:
                    return new LocalAlbum(path, mApplication, id, false, name, status);
                case MEDIA_TYPE_ALL:
                    Comparator<MediaItem> comp = DataManager.sDateTakenComparator;
                    return new LocalMergeAlbum(path, comp, new MediaSet[]{
                            getLocalAlbum(manager, MEDIA_TYPE_IMAGE, PATH_IMAGE, id, name, status),
                            getLocalAlbum(manager, MEDIA_TYPE_VIDEO, PATH_VIDEO, id, name, status)}, id);
            }
            throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    @Override
    // synchronized on this function for
    //   1. Prevent calling reload() concurrently.
    //   2. Prevent calling onFutureDone() and reload() concurrently
    public synchronized long reload() {
        if (mNotifier.isDirty()) {
            if (mLoadTask != null) mLoadTask.cancel();
            mIsLoading = true;
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), this);
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    @Override
    public synchronized void onFutureDone(Future<ArrayList<MediaSet>> future) {
        if (mLoadTask != future) return; // ignore, wait for the latest task
        mLoadBuffer = future.get();
        mIsLoading = false;
        if (mLoadBuffer == null) mLoadBuffer = new ArrayList<MediaSet>();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyContentChanged();
            }
        });
    }

    // For debug only. Fake there is a ContentObserver.onChange() event.
    void fakeChange() {
        mNotifier.fakeChange();
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        @SuppressWarnings("unchecked")
        public ArrayList<MediaSet> run(JobContext jc) {
            // Note: it will be faster if we only select media_type and bucket_id.
            //       need to test the performance if that is worth

            BucketEntry[] entries = BucketHelper.loadBucketEntries(jc, mApplication.getContentResolver(), mType);

            List<BucketEntry> entryList = null;
            try {
                entryList = mApplication.getAlbumsManager().loadBucketEntriesFromAlbumTable();
            } catch (Exception ex) {
                Logger.d(TAG, "empty album load err: " + ex.getMessage());
            }

            if (jc.isCancelled()) return null;

            ArrayList<MediaSet> albums = new ArrayList<MediaSet>();
            DataManager dataManager = mApplication.getDataManager();

            int offset = 0;
            // Move camera and download bucket to the front, while keeping the
            // order of others.
            int index = findBucket(entries, MediaSetUtils.CAMERA_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            /*
            index = findBucket(entries, MediaSetUtils.CAMERA_VIDEO_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.PANORAMA_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            */
            index = findBucket(entries, MediaSetUtils.SNAPSHOT_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }

            if (mSourceType == LocalSource.LOCAL_SETTING_ALBUMSET) {
                //Album setting page{
                //1.isn't show system album
                //2.isn't show empty album
                for (BucketEntry entry : entries) {
                    if (!MediaSetUtils.isSystemAlbum(entry.bucketId)) {
                        //Logger.d(TAG, "AlbumsLoader - run - name: " + entry.bucketName + " status: " + entry.status);
                        MediaSet album = getLocalAlbum(dataManager, mType, mPath, entry.bucketId, entry.bucketName, entry.status);
                        albums.add(album);
                    }
                }
                Logger.d(TAG, "AlbumsLoader - setting - size: " + albums.size());
            } else if (mSourceType == LocalSource.LOCAL_SELECTION_ALBUMSET) {
                //Album selection page{
                //1.isn't show system album
                for (BucketEntry entry : entries) {
                    if (!MediaSetUtils.isSystemAlbum(entry.bucketId)) {
                        //Logger.d(TAG, "AlbumsLoader - run - name: " + entry.bucketName + " status: " + entry.status);
                        MediaSet album = getLocalAlbum(dataManager, mType, mPath, entry.bucketId, entry.bucketName, entry.status);
                        albums.add(album);
                    }
                }

                //add empty from database
                if (entryList != null && entryList.size() > 0) {
                    for (int i = 0; i < entryList.size(); i++) {
                        BucketEntry entry = entryList.get(i);
                        MediaSet emp = new EmptyAlbumSet(Path.fromString("/local/empty/" + entry.bucketId), entry.bucketName,
                                new EmptyAlbumImage(LocalSource.EMPTY_ALBUM_PATH, mApplication));
                        albums.add(i, emp);
                    }
                }
                Logger.d(TAG, "AlbumsLoader - selection - size: " + albums.size());
            } else if (mSourceType == LocalSource.LOCAL_ALL_ALBUMSET) {
                //Time line page
                //1.isn't show hidden album
                for (BucketEntry entry : entries) {
                    int status = entry.status;
                    if (status == MediaSet.ALBUM_FLAG_SHOW) {
                        MediaSet album = getLocalAlbum(dataManager, mType, mPath, entry.bucketId, entry.bucketName, status);
                        albums.add(album);
                    }
                }
                Logger.d(TAG, "AlbumsLoader - local all - size: " + albums.size());
            } else if (mSourceType == LocalSource.LOCAL_CUSTOM_ALBUMSET) {
                //Album set page
                //1.isn't show hidden album
                int position = 0;
                for (BucketEntry entry : entries) {
                    int status = entry.status;
                    if (status == MediaSet.ALBUM_FLAG_SHOW) {
                        //Logger.d(TAG, "AlbumsLoader - run - name: " + entry.bucketName + " status: " + status);
                        MediaSet album = getLocalAlbum(dataManager, mType, mPath, entry.bucketId, entry.bucketName, status);
                        albums.add(album);
                        if (MediaSetUtils.isSystemAlbum(entry.bucketId)) {
                            position++;
                        }
                    }
                }

                Logger.d(TAG, "sys album count: " + position);
                //add 'video' and 'panoramas' album set
                MediaSet videoMediaSet = dataManager.getMediaSet(LocalVideoAlbumSet.LOCAL_ALL_VIDEO_PATH);
                MediaSet panoramasMediaSet = dataManager.getMediaSet(LocalPanoramasAlbumSet.LOCAL_ALL_PANORAMAS_PATH);

                boolean isContainVideos = false;
                if (videoMediaSet != null && videoMediaSet.getMediaItemCount() > 0) {
                    Logger.d(TAG, "load video album");
                    isContainVideos = true;
                    albums.add(1, videoMediaSet);
                    position++;
                }
                if (panoramasMediaSet != null && panoramasMediaSet.getMediaItemCount() > 0) {
                    Logger.d(TAG, "load panoramas album");
                    albums.add(isContainVideos ? 2 : 1, panoramasMediaSet);
                    position++;
                }

                //add 'empty' album from database
                if (entryList != null && entryList.size() > 0) {
                    Logger.d(TAG, "empty album size: " + entryList.size() + " sys album count: " + position);
                    for (int i = 0; i < entryList.size(); i++) {
                        BucketEntry entry = entryList.get(i);
                        MediaSet emp = new EmptyAlbumSet(Path.fromString("/local/empty/" + entry.bucketId), entry.bucketName,
                                new EmptyAlbumImage(LocalSource.EMPTY_ALBUM_PATH, mApplication));
                        albums.add(position + i, emp);
                    }
                }
                Logger.d(TAG, "AlbumsLoader - local custom - size: " + albums.size());
            }
            return albums;
        }
    }
}
