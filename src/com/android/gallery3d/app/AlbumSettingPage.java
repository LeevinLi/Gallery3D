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

package com.android.gallery3d.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.settings.GallerySettings;
import com.android.gallery3d.ui.AlbumSettingSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class AlbumSettingPage extends ActivityState implements EyePosition.EyePositionListener, MediaSet.SyncListener {

    public static final String KEY_MEDIA_PATH = "album-setting-media-path";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";

    private static final String TAG = "AlbumSettingPage";

    private static final int MSG_PICK_ALBUM = 1;
    private static final int DATA_CACHE_SIZE = 256;
    private static final int REQUEST_DO_ANIMATION = 1;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;
    WeakReference<Toast> mEmptyAlbumToast = null;
    private boolean mIsActive = false;
    private SlotView mSlotView;
    private AlbumSettingSlotRenderer mAlbumSettingView;
    private Config.AlbumSettingPage mConfig;
    private MediaSet mMediaSet;
    private GalleryActionBar mActionBar;
    private TextView tvEmptyAlbum;
    private int mSelectedAction;
    private AlbumSettingDataLoader mAlbumSettingDataAdapter;
    private EyePosition mEyePosition;
    private Handler mHandler;
    private ProgressDialog mProgressDialog;
    private boolean isLoading = false;
    // The eyes' position of the user, the origin is at the center of the
    // device and the unit is in pixels.
    private float mX;
    private float mY;
    private float mZ;
    private final GLView mRootPane = new GLView() {
        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            mEyePosition.resetPosition();

            int paddingLeft;
            int paddingBottom;
            int paddingRight;
            int paddingTop;
            int slotViewTop;

            int statusBarHeight = GalleryUtils.getStatusBarHeight(mActivity);

            if (right - left > bottom - top) {
                paddingTop = mConfig.paddingTopLand;
                paddingBottom = mConfig.paddingBottomLand;
                paddingRight = mConfig.paddingRightLand;
                paddingLeft = mConfig.paddingLeftLand;
                slotViewTop = mActivity.getGalleryActionBar().getHeight() + paddingTop;
            } else {
                paddingTop = mConfig.paddingTop;
                paddingBottom = mConfig.paddingBottom;
                paddingRight = mConfig.paddingRight;
                paddingLeft = mConfig.paddingLeft;
                slotViewTop = mActivity.getGalleryActionBar().getHeight() + paddingTop + statusBarHeight;
            }

            int slotViewBottom = bottom - top - paddingBottom;
            int slotViewRight = right - left - paddingRight;
            int slotViewLeft = paddingLeft;

            mAlbumSettingView.setHighlightItemPath(null);
            mSlotView.layout(slotViewLeft, slotViewTop, slotViewRight, slotViewBottom);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            GalleryUtils.setViewPointMatrix(mMatrix, getWidth() / 2 + mX, getHeight() / 2 + mY, mZ);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            canvas.restore();
        }
    };
    private Future<Integer> mSyncTask = null;
    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;
    private boolean mShowedEmptyToastForSelf = false;

    private static boolean albumShouldOpenInFilmstrip(MediaSet album) {
        int itemCount = album.getMediaItemCount();
        ArrayList<MediaItem> list = (itemCount == 1) ? album.getMediaItem(0, 1) : null;
        // open in film strip only if there's one item in the album and the item exists
        return (list != null && !list.isEmpty());
    }

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_setting_background;
    }

    @Override
    public void onEyePositionChanged(float x, float y, float z) {
        mRootPane.lockRendering();
        mX = x;
        mY = y;
        mZ = z;
        mRootPane.unlockRendering();
        mRootPane.invalidate();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private void getSlotCenter(int slotIndex, int center[]) {
        Rect offset = new Rect();
        mRootPane.getBoundsOf(mSlotView, offset);
        Rect r = mSlotView.getSlotRect(slotIndex);
        int scrollX = mSlotView.getScrollX();
        int scrollY = mSlotView.getScrollY();
        center[0] = offset.left + (r.left + r.right) / 2 - scrollX;
        center[1] = offset.top + (r.top + r.bottom) / 2 - scrollY;
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setTitle(R.string.camera_menu_settings_label);
            mProgressDialog.setMessage(mActivity.getString(R.string.pano_review_saving_indication_str));
            mProgressDialog.setIcon(R.mipmap.ic_launcher_gallery);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            //mProgressDialog.setMax(maxSize);
            mProgressDialog.setCancelable(false);
        }
        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    public void onSingleTapUp(int slotIndex) {
        if (!mIsActive) return;
        // Show pressed-up animation for the single-tap.
        mAlbumSettingView.setPressedIndex(slotIndex);
        mAlbumSettingView.setPressedUp();
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0), FadeTexture.DURATION);
    }

    private void showEmptyAlbumToast(int toastLength) {
        Toast toast;
        if (mEmptyAlbumToast != null) {
            toast = mEmptyAlbumToast.get();
            if (toast != null) {
                toast.show();
                return;
            }
        }
        toast = Toast.makeText(mActivity, R.string.empty_album, toastLength);
        mEmptyAlbumToast = new WeakReference<Toast>(toast);
        toast.show();
    }

    private void showEmptyAlbumText() {
        RelativeLayout galleryRoot = (RelativeLayout) mActivity.findViewById(R.id.gallery_root);
        if (galleryRoot == null) return;
        if (tvEmptyAlbum == null) {
            tvEmptyAlbum = new TextView(mActivity);
            tvEmptyAlbum.setText(R.string.empty_album);
            tvEmptyAlbum.setTextColor(mActivity.getColor(R.color.empty_text_color));
            tvEmptyAlbum.setGravity(Gravity.CENTER);
            tvEmptyAlbum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            galleryRoot.addView(tvEmptyAlbum, lp);
        }
        tvEmptyAlbum.setVisibility(View.VISIBLE);
    }

    private void hideEmptyAlbumText() {
        if (tvEmptyAlbum != null) {
            tvEmptyAlbum.setVisibility(View.GONE);
        }
        RelativeLayout galleryRoot = (RelativeLayout) mActivity.findViewById(R.id.gallery_root);
        if (galleryRoot == null || tvEmptyAlbum == null) return;
        galleryRoot.removeView(tvEmptyAlbum);
    }

    private void hideEmptyAlbumToast() {
        if (mEmptyAlbumToast != null) {
            Toast toast = mEmptyAlbumToast.get();
            if (toast != null) toast.cancel();
        }
    }

    private void pickAlbum(int slotIndex) {
        if (!mIsActive) return;

        MediaSet targetSet = mAlbumSettingDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon
        if (targetSet.getTotalMediaItemCount() == 0) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
            return;
        }
        hideEmptyAlbumToast();

        String mediaPath = targetSet.getPath().toString();

        Bundle data = new Bundle(getData());
        int[] center = new int[2];
        getSlotCenter(slotIndex, center);
        data.putIntArray(AlbumPage.KEY_SET_CENTER, center);
        if (targetSet.getSubMediaSetCount() > 0) {
            data.putString(AlbumSettingPage.KEY_MEDIA_PATH, mediaPath);
            mActivity.getStateManager().startStateForResult(AlbumSettingPage.class, REQUEST_DO_ANIMATION, data);
        } else {
            int status = targetSet.getStatus();
            showProgressDialog();
            isLoading = true;
            Bundle bundle = new Bundle();
            if (MediaSet.ALBUM_FLAG_HIDDEN == status) {
                Logger.d(TAG, "pick album: " + targetSet.getName() + " status: " + status + " -> " + MediaSet.ALBUM_FLAG_SHOW);
                bundle.putInt(GalleryUtils.EXTRA_ALBUM_STATUS, MediaSet.ALBUM_FLAG_SHOW);
            } else if (MediaSet.ALBUM_FLAG_SHOW == status) {
                Logger.d(TAG, "pick album: " + targetSet.getName() + " status: " + status + " -> " + MediaSet.ALBUM_FLAG_HIDDEN);
                bundle.putInt(GalleryUtils.EXTRA_ALBUM_STATUS, MediaSet.ALBUM_FLAG_HIDDEN);
            }
            mActivity.getDataManager().update(targetSet.getPath(), bundle);
        }
    }

    private void onDown(int index) {
        mAlbumSettingView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumSettingView.setPressedIndex(-1);
        } else {
            mAlbumSettingView.setPressedUp();
        }
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        initializeViews();
        initializeData(data);
        Context context = mActivity.getAndroidContext();
        mEyePosition = new EyePosition(context, this);
        mActionBar = mActivity.getGalleryActionBar();
        mSelectedAction = data.getInt(AlbumSettingPage.KEY_SELECTED_CLUSTER_TYPE, FilterUtils.CLUSTER_BY_ALBUM);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_ALBUM: {
                        pickAlbum(message.arg1);
                        break;
                    }
                    default:
                        throw new AssertionError(message.what);
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void clearLoadingBit(int loadingBit) {
        mLoadingBits &= ~loadingBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumSettingDataAdapter.size() == 0) {
                // If this is not the top of the gallery folder hierarchy,
                // tell the parent AlbumSetPage instance to handle displaying
                // the empty album toast, otherwise show it within this
                // instance
//                if (mActivity.getStateManager().getStateCount() > 1) {
//                    Logger.d(TAG, "clearLoadingBit - 1");
//                    Intent result = new Intent();
//                    result.putExtra(AlbumPage.KEY_EMPTY_ALBUM, true);
//                    setStateResult(Activity.RESULT_OK, result);
//                    mActivity.getStateManager().finishState(this);
//                } else {
                Logger.d(TAG, "clearLoadingBit - 2");
                mShowedEmptyToastForSelf = true;
                //showEmptyAlbumToast(Toast.LENGTH_LONG);
                showEmptyAlbumText();
                mSlotView.invalidate();
//                }
                return;
            }
        }
        // Hide the empty album toast if we are in the root instance of
        // AlbumSetPage and the album is no longer empty (for instance,
        // after a sync is completed and web albums have been synced)
        if (mShowedEmptyToastForSelf) {
            Logger.d(TAG, "clearLoadingBit - 3");
            mShowedEmptyToastForSelf = false;
            //hideEmptyAlbumToast();
            hideEmptyAlbumText();
        }
    }

    private void setLoadingBit(int loadingBit) {
        mLoadingBits |= loadingBit;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;
        mAlbumSettingDataAdapter.pause();
        mAlbumSettingView.pause();
        mEyePosition.pause();
        DetailsHelper.pause();
        // Call disableClusterMenu to avoid receiving callback after paused.
        // Don't hide menu here otherwise the list menu will disappear earlier than
        // the action bar, which is janky and unwanted behavior.
        mActionBar.disableClusterMenu(false);
        if (mSyncTask != null) {
            mSyncTask.cancel();
            mSyncTask = null;
            clearLoadingBit(BIT_LOADING_SYNC);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsActive = true;

        setContentPane(mRootPane);

        // Set the reload bit here to prevent it exit this mGalleryViewPager in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        mAlbumSettingDataAdapter.resume();

        mAlbumSettingView.resume();
        mEyePosition.resume();

        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(AlbumSettingPage.this);
        }
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(AlbumSettingPage.KEY_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        mAlbumSettingDataAdapter = new AlbumSettingDataLoader(mActivity, mMediaSet, DATA_CACHE_SIZE);
        mAlbumSettingDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSettingView.setModel(mAlbumSettingDataAdapter);
    }

    private void initializeViews() {
        mConfig = Config.AlbumSettingPage.get(mActivity);
        mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
        mAlbumSettingView = new AlbumSettingSlotRenderer(mActivity, null, mSlotView,
                mConfig.labelSpec, mConfig.placeholderColor);
        mSlotView.setSlotRenderer(mAlbumSettingView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumSettingPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumSettingPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumSettingPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                //AlbumSettingPage.this.onLongTap(slotIndex);
            }
        });
        mRootPane.addComponent(mSlotView);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Logger.d(TAG, "onCreateActionBar");
        //MenuInflater inflater = getSupportMenuInflater();
        //inflater.inflate(R.menu.album_setting, menu);
        mActionBar.setTitle(R.string.album_setting);
        return super.onCreateActionBar(menu);
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        Activity activity = mActivity;
        switch (item.getItemId()) {
            case R.id.action_cancel:
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
                return true;
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(activity);
                return true;
            }
            case R.id.action_manage_offline: {
                Bundle data = new Bundle();
                String mediaPath = mActivity.getDataManager().getTopSetPath(DataManager.INCLUDE_ALL);
                data.putString(AlbumSettingPage.KEY_MEDIA_PATH, mediaPath);
                mActivity.getStateManager().startState(ManageCachePage.class, data);
                return true;
            }
            case R.id.action_sync_picasa_albums: {
                PicasaSource.requestSync(activity);
                return true;
            }
            case R.id.action_settings: {
                activity.startActivity(new Intent(activity, GallerySettings.class));
                return true;
            }
            case R.id.action_create_albums:

                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        if (data != null && data.getBooleanExtra(AlbumPage.KEY_EMPTY_ALBUM, false)) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
        }
        switch (requestCode) {
            case REQUEST_DO_ANIMATION: {
                mSlotView.startRisingAnimation();
            }
        }
    }

    @Override
    public void onSyncDone(final MediaSet mediaSet, final int resultCode) {
        if (resultCode == MediaSet.SYNC_RESULT_ERROR) {
            Logger.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName()) + " result=" + resultCode);
        }
        ((Activity) mActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GLRoot root = mActivity.getGLRoot();
                root.lockRenderThread();
                try {
                    if (resultCode == MediaSet.SYNC_RESULT_SUCCESS) {
                        mInitialSynced = true;
                    }
                    clearLoadingBit(BIT_LOADING_SYNC);
                    if (resultCode == MediaSet.SYNC_RESULT_ERROR && mIsActive) {
                        Logger.w(TAG, "failed to load album set");
                    }
                } finally {
                    root.unlockRenderThread();
                }
            }
        });
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            clearLoadingBit(BIT_LOADING_RELOAD);
            if (isLoading) {
                hideProgressDialog();
                isLoading = false;
            }
        }
    }
}
