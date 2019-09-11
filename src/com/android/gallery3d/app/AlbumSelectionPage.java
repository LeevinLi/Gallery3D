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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.EmptyAlbumSet;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSelectionSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.FileHelper;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Logger;
import com.android.gallery3d.util.MediaSetUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class AlbumSelectionPage extends ActivityState implements SelectionManager.SelectionListener,
        EyePosition.EyePositionListener, MediaSet.SyncListener, FileHelper.FileListener {

    public static final String KEY_MEDIA_PATH = "album-selection-media-path";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";
    public static final String KEY_SELECTED_MEDIA = "selected-media";
    public static final String KEY_AUTO_SELECT_ALL = "auto-select-all";
    public static final String KEY_RETURN_FILE_PATH = "return-file-path";
    public static final String KEY_RETURN_IS_EMPTY_ALBUM = "return-is-empty-album";

    private static final String TAG = "AlbumSelectionPage";

    private static final int MSG_PICK_ALBUM = 1;
    private static final int DATA_CACHE_SIZE = 256;
    private static final int REQUEST_DO_ANIMATION = 1;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;
    protected SelectionManager mSelectionManager;
    WeakReference<Toast> mEmptyAlbumToast = null;
    private boolean mIsActive = false;
    private SlotView mSlotView;
    private TextView tvEmptyAlbum;
    private AlbumSelectionSlotRenderer mAlbumSelectionView;
    private Config.AlbumSelectionPage mConfig;
    private MediaSet mMediaSet;
    private String mCurrentFilePath;
    private boolean mIsEmptyMediaSet;
    private GalleryActionBar mActionBar;
    private AlbumSelectionDataLoader mAlbumSelectionDataAdapter;
    private ActionModeHandler mActionModeHandler;
    private EyePosition mEyePosition;
    private Handler mHandler;
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

            mAlbumSelectionView.setHighlightItemPath(null);

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
    private FileHelper mFileHelper;
    private ArrayList<Path> mPaths;

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_selection_background;
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
        if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            super.onBackPressed();
        }
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

    public void onSingleTapUp(int slotIndex) {
        if (!mIsActive) return;

        if (mSelectionManager.inSelectionMode()) {
            MediaSet targetSet = mAlbumSelectionDataAdapter.getMediaSet(slotIndex);
            if (targetSet == null) return; // Content is dirty, we shall reload soon
            mSelectionManager.toggle(targetSet.getPath());
            mSlotView.invalidate();
        } else {
            // Show pressed-up animation for the single-tap.
            mAlbumSelectionView.setPressedIndex(slotIndex);
            mAlbumSelectionView.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0), FadeTexture.DURATION);
        }
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

    private void hideEmptyAlbumToast() {
        if (mEmptyAlbumToast != null) {
            Toast toast = mEmptyAlbumToast.get();
            if (toast != null) toast.cancel();
        }
    }

    private void pickAlbum(int slotIndex) {
        if (!mIsActive) return;

        MediaSet targetSet = mAlbumSelectionDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon
        /*
        if (targetSet.getTotalMediaItemCount() == 0) {
            showEmptyAlbumToast(Toast.LENGTH_SHORT);
            return;
        }
        hideEmptyAlbumToast();
        */
        if (targetSet instanceof EmptyAlbumSet) {
            mCurrentFilePath = MediaSetUtils.DIRECTORY_PICTURES + File.separator + targetSet.getName();
            mIsEmptyMediaSet = true;
        } else {
            mCurrentFilePath = targetSet.getAbsoluteFilePath();
            mIsEmptyMediaSet = false;
        }

        Logger.d(TAG, "pickAlbum - selected path: " + mCurrentFilePath);
        setResult();
    }

    private void onDown(int index) {
        mAlbumSelectionView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumSelectionView.setPressedIndex(-1);
        } else {
            mAlbumSelectionView.setPressedUp();
        }
    }

    public void onLongTap(int slotIndex) {
        // if (mGetContent || mGetAlbum) return;
        MediaSet set = mAlbumSelectionDataAdapter.getMediaSet(slotIndex);
        if (set == null) return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(set.getPath());
        mSlotView.invalidate();
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        initializeViews();
        initializeData(data);
        Context context = mActivity.getAndroidContext();
        mEyePosition = new EyePosition(context, this);
        mActionBar = mActivity.getGalleryActionBar();
        mFileHelper = new FileHelper(mActivity.getAndroidContext());
        mFileHelper.setCreateListener(this);

        mPaths = (ArrayList<Path>) data.getSerializable(KEY_SELECTED_MEDIA);
        if (data.getBoolean(KEY_AUTO_SELECT_ALL)) {
            mSelectionManager.selectAll();
        }

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
        mActionModeHandler.destroy();
    }

    private void clearLoadingBit(int loadingBit) {
        mLoadingBits &= ~loadingBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumSelectionDataAdapter.size() == 0) {
                // If this is not the top of the gallery folder hierarchy,
                // tell the parent AlbumSetPage instance to handle displaying
                // the empty album toast, otherwise show it within this
                // instance
                /*
                if (mActivity.getStateManager().getStateCount() > 1) {
                    Logger.d(TAG, "clearLoadingBit - 1");
                    Intent result = new Intent();
                    result.putExtra(AlbumPage.KEY_EMPTY_ALBUM, true);
                    setStateResult(Activity.RESULT_OK, result);
                    mActivity.getStateManager().finishState(this);
                } else {
                */
                Logger.d(TAG, "clearLoadingBit - 2");
                mShowedEmptyToastForSelf = true;
                //showEmptyAlbumToast(Toast.LENGTH_LONG);
                showEmptyAlbumText();
                mSlotView.invalidate();
                //}
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

    private void setLoadingBit(int loadingBit) {
        mLoadingBits |= loadingBit;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;
        mAlbumSelectionDataAdapter.pause();
        mAlbumSelectionView.pause();
        mActionModeHandler.pause();
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
        mAlbumSelectionDataAdapter.resume();

        mAlbumSelectionView.resume();
        mEyePosition.resume();
        mActionModeHandler.resume();

        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(AlbumSelectionPage.this);
        }
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(AlbumSelectionPage.KEY_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSelectionDataAdapter = new AlbumSelectionDataLoader(mActivity, mMediaSet, DATA_CACHE_SIZE);
        mAlbumSelectionDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSelectionView.setModel(mAlbumSelectionDataAdapter);
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);

        mConfig = Config.AlbumSelectionPage.get(mActivity);
        mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
        mAlbumSelectionView = new AlbumSelectionSlotRenderer(mActivity, mSelectionManager, mSlotView, mConfig.labelSpec, mConfig
                .placeholderColor);
        mSlotView.setSlotRenderer(mAlbumSelectionView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumSelectionPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumSelectionPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumSelectionPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                //AlbumSelectionPage.this.onLongTap(slotIndex);
            }
        });

        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }
        });
        mRootPane.addComponent(mSlotView);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Logger.d(TAG, "onCreateActionBar");
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.selection, menu);
        mActionBar.setTitle(R.string.move_to_album);
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
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_new_album:
                Logger.d(TAG, "action_new_album");
                createAlbum();
                return true;
            default:
                return false;
        }
    }

    private void setResult() {
        Intent result = new Intent();
        result.putExtra(KEY_RETURN_FILE_PATH, mCurrentFilePath);
        result.putExtra(KEY_RETURN_IS_EMPTY_ALBUM, mIsEmptyMediaSet);
        result.putExtra(KEY_SELECTED_MEDIA, mPaths);
        setStateResult(Activity.RESULT_OK, result);
        mActivity.getStateManager().finishState(this);
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

    private String getSelectedString() {
        int count = mSelectionManager.getSelectedCount();
        int action = mActionBar.getClusterTypeAction();
        int string = action == FilterUtils.CLUSTER_BY_ALBUM ? R.plurals.number_of_albums_selected : R.plurals.number_of_groups_selected;
        String format = mActivity.getResources().getQuantityString(string, count);
        return String.format(format, count);
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionBar.disableClusterMenu(true);
                mActionModeHandler.startActionMode();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionModeHandler.finishActionMode();
                mRootPane.invalidate();
                mActivity.invalidateOptionsMenu();
                break;
            }
            case SelectionManager.SELECT_ALL_MODE: {
                mActionModeHandler.updateSupportedOperation();
                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        Logger.d(TAG, "onPageSelected - path： " + path.toString() + " selected: " + selected);
        mActionModeHandler.setTitle(getSelectedString());
        mActionModeHandler.updateSupportedOperation(path, selected);
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

    public void createAlbum() {
        View mRootView = mActivity.getLayoutInflater().inflate(R.layout.dlg_input_album_name, null);
        final EditText mEditText = (EditText) mRootView.findViewById(R.id.create_album_edittext);
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(mActivity);
        mBuilder.setTitle(mActivity.getResources().getString(R.string.create_album));
        mBuilder.setCancelable(true);
        mBuilder.setView(mRootView);
        mBuilder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int paramInt) {
                String mName = mEditText.getText().toString();
                if (mFileHelper.validateNewFileName(mActivity, mName)) {
                    Logger.d(TAG, "validate name is pass");
                    String folderPath = MediaSetUtils.DIRECTORY_PICTURES + File.separator + mName;
                    int bucketId = GalleryUtils.getBucketId(folderPath);
                    boolean isUpdate = mActivity.getAlbumsManager().insert(bucketId, mName, System.currentTimeMillis(), GalleryUtils.NA);
                    Logger.d(TAG, "insert empty album into database - folderPath: " + folderPath + " result: " + isUpdate);
                    MediaSetUtils.requestSync(mActivity.getAndroidContext());
                    //mFileHelper.putPictureIntoFolder(mName);
                }
            }
        });
        mBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int paramInt) {
                dialog.dismiss();
            }
        });
        mBuilder.create().show();
    }

    @Override
    public void onCreateResult(int result) {
        switch (result) {
            case FileHelper.ERROR_FILE_NAME_IS_NOT_EMPTY:
                mActivity.showToast(R.string.validate_album_name_is_not_empty);
                break;
            case FileHelper.ERROR_FILE_NAME_ALREADY_EXISTS:
                mActivity.showToast(R.string.validate_album_name_already_exists);
                break;
            case FileHelper.ERROR_FILE_NAME_CONTAINS_ILLEGAL_CHAR:
                mActivity.showToast(R.string.validate_album_name_contains_illegal_characters);
                break;
            case FileHelper.ERROR_FILE_NAME_TOO_LONG:
                mActivity.showToast(R.string.validate_album_name_too_long);
                break;
            case FileHelper.ERROR_FILE_NAME_UNKOWN:
                mActivity.showToast(R.string.error_unknow);
                break;
            case FileHelper.CREATE_ALBUM_SUCC:
                mActivity.showToast(R.string.create_album_success);
                break;
        }
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            clearLoadingBit(BIT_LOADING_RELOAD);
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;

        @Override
        public int size() {
            return mAlbumSelectionDataAdapter.size();
        }

        @Override
        public int setIndex() {
            Path id = mSelectionManager.getSelected(false).get(0);
            mIndex = mAlbumSelectionDataAdapter.findSet(id);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            MediaObject item = mAlbumSelectionDataAdapter.getMediaSet(mIndex);
            if (item != null) {
                mAlbumSelectionView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }
}
