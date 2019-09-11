package com.android.gallery3d.data;


import java.util.ArrayList;

public class EmptyAlbumSet extends MediaSet {

    private static final String TAG = "EmptyAlbumSet";
    private final MediaItem mItem;
    private final String mName;
    private final String mBucketId;

    public EmptyAlbumSet(Path path, String name, MediaItem item) {
        super(path, nextVersionNumber());
        mBucketId = path.getSuffix();
        mItem = item;
        mName = name;
    }

    @Override
    public int getMediaItemCount() {
        return 0;
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        ArrayList<MediaItem> result = new ArrayList<MediaItem>();

        // If [start, start+count) contains the index 0, return the item.
        if (start <= 0 && start + count > 0) {
            result.add(mItem);
        }
        return result;
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_DELETE;
    }

    public MediaItem getItem() {
        return mItem;
    }

    @Override
    public void delete() {
        super.delete();
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public long reload() {
        return mDataVersion;
    }
}
