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

package com.android.gallery3d.util;

import android.content.Context;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.android.gallery3d.data.LocalPanoramasAlbumSet;
import com.android.gallery3d.data.LocalVideoAlbumSet;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;

import java.util.Comparator;

public class MediaSetUtils {
    public static final Comparator<MediaSet> NAME_COMPARATOR = new NameComparator();

    public static final int CAMERA_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.CAMERA);
    public static final int CAMERA_VIDEO_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.CAMERA_VIDEO);
    public static final int DOWNLOAD_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.DOWNLOAD);
    public static final int EDITED_ONLINE_PHOTOS_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.EDITED_ONLINE_PHOTOS);
    public static final int IMPORTED_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.IMPORTED);
    public static final int SNAPSHOT_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.SCREENSHOTS);
    public static final int PANORAMA_BUCKET_ID = GalleryUtils.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/" + BucketNames.PANORAMAS);
    public static final String DIRECTORY_PICTURES = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES).getAbsolutePath();

    private static final Path[] CAMERA_PATHS = {
            Path.fromString("/local/all/" + CAMERA_BUCKET_ID),
            Path.fromString("/local/image/" + CAMERA_BUCKET_ID),
            Path.fromString("/local/video/" + CAMERA_BUCKET_ID)};
    private static final int[] SYSTEM_BUCKETS = {
            CAMERA_BUCKET_ID,
            SNAPSHOT_BUCKET_ID};
    private static final Path[] SYSTEM_PATHS = {
            Path.fromString(LocalPanoramasAlbumSet.LOCAL_ALL_PANORAMAS_PATH),
            Path.fromString(LocalVideoAlbumSet.LOCAL_ALL_VIDEO_PATH)};

    private static final int[] SYSTEM_ALBUM_NAME = {
            R.string.folder_camera,
            R.string.folder_screenshot,
            R.string.folder_panorama,
            R.string.folder_video};

    private static final String[] SUPPORT_VIDEO_TYPE = {"video/mp4", "video/mpeg4", "video/3gpp", "video/3gpp2"};

    public static boolean isCameraSource(Path path) {
        return CAMERA_PATHS[0] == path || CAMERA_PATHS[1] == path || CAMERA_PATHS[2] == path;
    }

    /**
     * whether system album by bucket
     * {CAMERA,SNAPSHOT}
     *
     * @param bucketId
     * @return
     */
    public static boolean isSystemAlbum(int bucketId) {
        return SYSTEM_BUCKETS[0] == bucketId || SYSTEM_BUCKETS[1] == bucketId;
    }

    /**
     * whether system album by path
     * {PANORAMAS,VIDEO}
     *
     * @param path
     * @return
     */
    public static boolean isSystemAlbum(Path path) {
        return SYSTEM_PATHS[0] == path || SYSTEM_PATHS[1] == path;
    }

    /**
     * whether system album name
     *
     * @param res
     * @param name
     * @return
     */
    public static boolean isSysAlbumName(final Resources res, String name) {
        if (res == null || TextUtils.isEmpty(name)) {
            return false;
        }
        for (int i = 0; i < SYSTEM_ALBUM_NAME.length; i++) {
            //Logger.d("MediaSetUtils", "isSysAlbumName: " + res.getString(SYSTEM_ALBUM_NAME[i]) + " name " + name);
            if (res.getString(SYSTEM_ALBUM_NAME[i]).equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * is supported video type by mimetype
     *
     * @param mimeType
     * @return
     */
    public static boolean isVideoTypeSupported(String mimeType) {
        for (String type : SUPPORT_VIDEO_TYPE) {
            if (type.equalsIgnoreCase(mimeType)) return true;
        }
        return false;
    }

    /**
     * sync data
     *
     * @param context
     */
    public static void requestSync(Context context) {
        MediaScannerConnection.scanFile(context, new String[]{DIRECTORY_PICTURES}, null, null);
    }

    // Sort MediaSets by name
    public static class NameComparator implements Comparator<MediaSet> {
        @Override
        public int compare(MediaSet set1, MediaSet set2) {
            int result = set1.getName().compareToIgnoreCase(set2.getName());
            if (result != 0) return result;
            return set1.getPath().toString().compareTo(set2.getPath().toString());
        }
    }
}
