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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.EmptyAlbumSet;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.StringTexture;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.settings.GallerySettings;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSetSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
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
import java.util.List;

public class AlbumSetPage extends ActivityState implements SelectionManager.SelectionListener, GalleryActionBar.ClusterRunner,
        EyePosition.EyePositionListener, MediaSet.SyncListener, FileHelper.FileListener {
    public static final String KEY_MEDIA_PATH = "album-set-media-path";
    public static final String KEY_SET_TITLE = "set-title";
    public static final String KEY_SET_SUBTITLE = "set-subtitle";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";
    public static final int MSG_ACTION_CREATE_ALBUM = 13;
    public static final int MSG_ACTION_RENAME_ALBUM = 14;
    private static final String TAG = "AlbumSetPage";
    private static final int MSG_PICK_ALBUM = 1;
    private static final int MSG_ACTION_BAR_HIDE = 11;
    private static final int MSG_ACTION_BAR_SHOW = 12;
    private static final int MSG_RENAME_FOLDER_EXITS = 101;
    private static final int MSG_RENAME_FOLDER_SUCC = 102;
    private static final int MSG_RENAME_FOLDER_ERROR = 103;
    private static final int MSG_RENAME_NOT_SUPPORT_ROOT_PATH = 104;
    private static final int MSG_RENAME_FOLDER_NONE = 105;
    private static final int DATA_CACHE_SIZE = 128;
    private static final int REQUEST_DO_ANIMATION = 1;
    private static final int REQUEST_CREATE_ALBUM = 11;

    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;
    protected SelectionManager mSelectionManager;
    WeakReference<Toast> mEmptyAlbumToast = null;
    private boolean mIsActive = false;
    private SlotView mSlotView;
    private AlbumSetSlotRenderer mAlbumSetView;
    private Config.AlbumSetPage mConfig;
    private MediaSet mMediaSet;
    private String mTitle;
    private String mSubtitle;
    private boolean mShowClusterMenu;
    private GalleryActionBar mActionBar;
    private TextView tvEmptyAlbum;
    private StringTexture mEmptyText;
    private int mSelectedAction;
    private ViewPagerListener mListener;
    private AlbumSetDataLoader mAlbumSetDataAdapter;
    private boolean mGetContent;
    private boolean mGetAlbum;
    private ActionModeHandler mActionModeHandler;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private boolean mShowDetails;
    private EyePosition mEyePosition;
    private Handler mHandler;
    private FileHelper mFileHelper;
    private Future<Integer> mSyncTask = null;
    private int mLoadingBits = 0;
    private boolean mInitialSynced = false;
    private Button mCameraButton;
    private boolean mShowedEmptyToastForSelf = false;
    private String mTargetName;
    private ProgressDialog mProgressDialog;
    private boolean isLoadingRename = false;
    private boolean isDataEmpty = false;

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

            if (mShowDetails) {
                mDetailsHelper.layout(slotViewLeft, slotViewTop, slotViewRight, slotViewBottom);
            } else {
                mAlbumSetView.setHighlightItemPath(null);
            }

            mSlotView.layout(slotViewLeft, slotViewTop, slotViewRight, slotViewBottom);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            GalleryUtils.setViewPointMatrix(mMatrix, getWidth() / 2 + mX, getHeight() / 2 + mY, mZ);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            if (isDataEmpty) {
                drawEmptyAlbumText(canvas, getWidth(), getHeight());
            }
            canvas.restore();
        }
    };

    private static boolean albumShouldOpenInFilmstrip(MediaSet album) {
        int itemCount = album.getMediaItemCount();
        ArrayList<MediaItem> list = (itemCount == 1) ? album.getMediaItem(0, 1) : null;
        // open in film strip only if there's one item in the album and the item exists
        return (list != null && !list.isEmpty());
    }

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_set_background;
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
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            super.onBackPressed();
        }
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
            MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
            if (targetSet == null) return; // Content is dirty, we shall reload soon
            mSelectionManager.toggle(targetSet.getPath());
            mSlotView.invalidate();
        } else {
            // Show pressed-up animation for the single-tap.
            mAlbumSetView.setPressedIndex(slotIndex);
            mAlbumSetView.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0), FadeTexture.DURATION);
        }
    }

    public void showEmptyAlbumText() {
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

    private void drawEmptyAlbumText(GLCanvas canvas, int width, int height) {
        mEmptyText.draw(canvas, (width - mEmptyText.getWidth()) / 2, (height - mEmptyText.getHeight()) / 2);
    }

    public void hideEmptyAlbumText() {
        if (tvEmptyAlbum != null) {
            tvEmptyAlbum.setVisibility(View.GONE);
        }
        RelativeLayout galleryRoot = (RelativeLayout) mActivity.findViewById(R.id.gallery_root);
        if (galleryRoot == null || tvEmptyAlbum == null) return;
        galleryRoot.removeView(tvEmptyAlbum);
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

        MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon

        //goto emptypage if targetSet is EmptyAlbumSet
        if (targetSet instanceof EmptyAlbumSet) {
            Bundle data = new Bundle();
            data.putString(EmptyPage.KEY_SET_TITLE, targetSet.getName());
            mActivity.getStateManager().startState(EmptyPage.class, data);
            return;
        }

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
        if (mGetAlbum && targetSet.isLeafAlbum()) {
            Activity activity = mActivity;
            Intent result = new Intent().putExtra(AlbumPicker.KEY_ALBUM_PATH, targetSet.getPath().toString());
            activity.setResult(Activity.RESULT_OK, result);
            activity.finish();
        } else if (targetSet.getSubMediaSetCount() > 0) {
            data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
            mActivity.getStateManager().startStateForResult(AlbumSetPage.class, REQUEST_DO_ANIMATION, data);
        } else {
            /*
            if (!mGetContent && albumShouldOpenInFilmstrip(targetSet)) {
                data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT, mSlotView.getSlotRect(slotIndex, mRootPane));
                data.putInt(PhotoPage.KEY_INDEX_HINT, 0);
                data.putString(PhotoPage.KEY_MEDIA_SET_PATH, mediaPath);
                data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, true);
                data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, targetSet.isCameraRoll());
                mActivity.getStateManager().startStateForResult(FilmstripPage.class, AlbumPage.REQUEST_PHOTO, data);
                return;
            }
            */
            data.putString(AlbumPage.KEY_MEDIA_PATH, mediaPath);

            // We only show cluster menu in the first AlbumPage in stack
            boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
            data.putBoolean(AlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum);
            mActivity.getStateManager().startStateForResult(AlbumPage.class, REQUEST_DO_ANIMATION, data);
        }
    }

    private void onDown(int index) {
        mAlbumSetView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumSetView.setPressedIndex(-1);
        } else {
            mAlbumSetView.setPressedUp();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent || mGetAlbum) return;
        MediaSet set = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (set == null) return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(set.getPath());
        mSlotView.invalidate();
    }

    @Override
    public void doCluster(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        Bundle data = new Bundle(getData());
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, newPath);
        data.putInt(KEY_SELECTED_CLUSTER_TYPE, clusterType);
        mActivity.getStateManager().switchState(this, AlbumSetPage.class, data);
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        initializeViews();
        initializeData(data);
        Context context = mActivity.getAndroidContext();
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mGetAlbum = data.getBoolean(GalleryActivity.KEY_GET_ALBUM, false);
        mTitle = data.getString(AlbumSetPage.KEY_SET_TITLE);
        mSubtitle = data.getString(AlbumSetPage.KEY_SET_SUBTITLE);
        mEyePosition = new EyePosition(context, this);
        mDetailsSource = new MyDetailsSource();
        mActionBar = mActivity.getGalleryActionBar();
        mSelectedAction = data.getInt(AlbumSetPage.KEY_SELECTED_CLUSTER_TYPE, FilterUtils.CLUSTER_BY_ALBUM);
        mFileHelper = new FileHelper(mActivity.getAndroidContext());
        mFileHelper.setCreateListener(this);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_ALBUM: {
                        pickAlbum(message.arg1);
                        break;
                    }
                    case MSG_ACTION_BAR_HIDE: {
                        mActionBar.hide();
                        break;
                    }
                    case MSG_ACTION_BAR_SHOW: {
                        mActionBar.show();
                        break;
                    }
                    case MSG_RENAME_FOLDER_NONE: {
                        mActivity.showToast(R.string.error_unknow);
                        break;
                    }
                    case MSG_RENAME_FOLDER_ERROR: {
                        mActivity.showToast(R.string.error_unknow);
                        break;
                    }
                    case MSG_RENAME_NOT_SUPPORT_ROOT_PATH: {
                        mActivity.showToast(R.string.root_path_not_support_rename);
                        break;
                    }
                    case MSG_RENAME_FOLDER_SUCC: {
                        mActivity.showToast(R.string.done);
                        break;
                    }
                    case MSG_RENAME_FOLDER_EXITS: {
                        mActivity.showToast(R.string.file_already_exist);
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
        cleanupCameraButton();
        mActionModeHandler.destroy();
    }

    private boolean setupCameraButton() {
        if (!GalleryUtils.isCameraAvailable(mActivity)) return false;
        RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity).findViewById(R.id.gallery_root);
        if (galleryRoot == null) return false;

        mCameraButton = new Button(mActivity);
        mCameraButton.setText(R.string.camera_label);
        mCameraButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.frame_overlay_gallery_camera, 0, 0);
        mCameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                GalleryUtils.startCameraActivity(mActivity);
            }
        });
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        galleryRoot.addView(mCameraButton, lp);
        return true;
    }

    private void cleanupCameraButton() {
        if (mCameraButton == null) return;
        RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity).findViewById(R.id.gallery_root);
        if (galleryRoot == null) return;
        galleryRoot.removeView(mCameraButton);
        mCameraButton = null;
    }

    private void showCameraButton() {
        if (mCameraButton == null && !setupCameraButton()) return;
        mCameraButton.setVisibility(View.VISIBLE);
    }

    private void hideCameraButton() {
        if (mCameraButton == null) return;
        mCameraButton.setVisibility(View.GONE);
    }

    private void clearLoadingBit(int loadingBit) {
        mLoadingBits &= ~loadingBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumSetDataAdapter.size() == 0) {
                // If this is not the top of the gallery folder hierarchy,
                // tell the parent AlbumSetPage instance to handle displaying
                // the empty album toast, otherwise show it within this
                // instance
                if (mActivity.getStateManager().getStateCount() > 1) {
                    Logger.d(TAG, "clearLoadingBit - 1");
                    Intent result = new Intent();
                    result.putExtra(AlbumPage.KEY_EMPTY_ALBUM, true);
                    setStateResult(Activity.RESULT_OK, result);
                    mActivity.getStateManager().finishState(this);
                } else {
                    Logger.d(TAG, "clearLoadingBit - 2");
                    mShowedEmptyToastForSelf = true;
                    //showEmptyAlbumToast(Toast.LENGTH_LONG);
                    //showEmptyAlbumText();
                    mSlotView.invalidate();
                    //showCameraButton();
                    setDataChange(GalleryPage.POSITION_ALBUMSET, true);
                }
                return;
            }
            setDataChange(GalleryPage.POSITION_ALBUMSET, false);
        }
        // Hide the empty album toast if we are in the root instance of
        // AlbumSetPage and the album is no longer empty (for instance,
        // after a sync is completed and web albums have been synced)
        if (mShowedEmptyToastForSelf) {
            Logger.d(TAG, "clearLoadingBit - 3");
            mShowedEmptyToastForSelf = false;
            setDataChange(GalleryPage.POSITION_ALBUMSET, false);
            //hideEmptyAlbumToast();
            //hideEmptyAlbumText();
            //hideCameraButton();
        }
    }

    private void setDataChange(int position, boolean isEmpty) {
        if (mListener != null) {
            mListener.onDataChange(position, isEmpty);
        }
    }

    private void setLoadingBit(int loadingBit) {
        mLoadingBits |= loadingBit;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActive = false;

        if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        }
        mAlbumSetDataAdapter.pause();
        mAlbumSetView.pause();
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

        if (mGetAlbum) {
            setContentPane(mRootPane);
        } else {
            setContentPane2(mRootPane);
        }

        // Set the reload bit here to prevent it exit this mGalleryViewPager in clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        mAlbumSetDataAdapter.resume();

        mAlbumSetView.resume();
        mEyePosition.resume();
        mActionModeHandler.resume();
        if (mShowClusterMenu) {
            mActionBar.enableClusterMenu(mSelectedAction, this);
        }
        if (!mInitialSynced) {
            setLoadingBit(BIT_LOADING_SYNC);
            mSyncTask = mMediaSet.requestSync(AlbumSetPage.this);
        }
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(AlbumSetPage.KEY_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSetDataAdapter = new AlbumSetDataLoader(mActivity, mMediaSet, DATA_CACHE_SIZE);
        mAlbumSetDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSetView.setModel(mAlbumSetDataAdapter);
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);

        mConfig = Config.AlbumSetPage.get(mActivity);
        mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
        mAlbumSetView = new AlbumSetSlotRenderer(mActivity, mSelectionManager, mSlotView, mConfig.labelSpec, mConfig.placeholderColor);
        mEmptyText = StringTexture.newInstance(mActivity.getString(R.string.empty_album), mConfig.emptyFontSize, mConfig.emptyFontColor,
                false);
        mSlotView.setSlotRenderer(mAlbumSetView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumSetPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumSetPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumSetPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                AlbumSetPage.this.onLongTap(slotIndex);
            }

            @Override
            public void onScrollPositionChanged(int position, int total) {
                //AlbumSetPage.this.onScrollPositionChanged(position, total);
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
//        Logger.d(TAG, "onCreateActionBar");
//        Activity activity = mActivity;
//        final boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
//        MenuInflater inflater = getSupportMenuInflater();
//
//        if (mGetContent) {
//            inflater.inflate(R.menu.pickup, menu);
//            int typeBits = mData.getInt(GalleryActivity.KEY_TYPE_BITS, DataManager.INCLUDE_IMAGE);
//            mActionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
//        } else if (mGetAlbum) {
//            inflater.inflate(R.menu.pickup, menu);
//            mActionBar.setTitle(R.string.select_album);
//        } else {
//            inflater.inflate(R.menu.albumset, menu);
//            boolean wasShowingClusterMenu = mShowClusterMenu;
//            mShowClusterMenu = !inAlbum;
//            boolean selectAlbums = !inAlbum && mActionBar.getClusterTypeAction() == FilterUtils.CLUSTER_BY_ALBUM;
//            MenuItem selectItem = menu.findItem(R.id.action_select);
//            selectItem.setTitle(activity.getString(selectAlbums ? R.string.select_album : R.string.select_group));
//
//            MenuItem cameraItem = menu.findItem(R.id.action_camera);
//            cameraItem.setVisible(GalleryUtils.isCameraAvailable(activity));
//
//            FilterUtils.setupMenuItems(mActionBar, mMediaSet.getPath(), false);
//
//            Intent helpIntent = HelpUtils.getHelpIntent(activity);
//
//            MenuItem helpItem = menu.findItem(R.id.action_general_help);
//            helpItem.setVisible(helpIntent != null);
//            if (helpIntent != null) helpItem.setIntent(helpIntent);
//
//            mActionBar.setTitle(mTitle);
//            mActionBar.setSubtitle(mSubtitle);
//            if (mShowClusterMenu != wasShowingClusterMenu) {
//                if (mShowClusterMenu) {
//                    mActionBar.enableClusterMenu(mSelectedAction, this);
//                } else {
//                    mActionBar.disableClusterMenu(true);
//                }
//            }
//        }
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
            case R.id.action_details:
                if (mAlbumSetDataAdapter.size() != 0) {
                    if (mShowDetails) {
                        hideDetails();
                    } else {
                        showDetails();
                    }
                } else {
                    Toast.makeText(activity, activity.getText(R.string.no_albums_alert), Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_camera:
                GalleryUtils.startCameraActivity(activity);
                return true;
            case R.id.action_manage_offline:
                Bundle data = new Bundle();
                String mediaPath = mActivity.getDataManager().getTopSetPath(DataManager.INCLUDE_ALL);
                data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
                mActivity.getStateManager().startState(ManageCachePage.class, data);
                return true;
            case R.id.action_sync_picasa_albums:
                PicasaSource.requestSync(activity);
                return true;
            case R.id.action_settings:
                activity.startActivity(new Intent(activity, GallerySettings.class));
                return true;
            case R.id.action_album_setting:
                //startAlbumSettingPage();
                return true;
            case R.id.action_create_albums:
                //showInputDialog(MSG_ACTION_CREATE_ALBUM, R.string.create_album);
                return true;
            case R.id.action_rename:
                showInputDialog(MSG_ACTION_RENAME_ALBUM, R.string.rename);
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        if (data != null && data.getBooleanExtra(AlbumPage.KEY_EMPTY_ALBUM, false)) {
            //showEmptyAlbumToast(Toast.LENGTH_SHORT);
        }
        switch (requestCode) {
            case REQUEST_DO_ANIMATION:
                mSlotView.startRisingAnimation();
                break;
            case REQUEST_CREATE_ALBUM:
                if (data == null) return;
                Logger.d(TAG, "onStateResult - CREATE_ALBUM: " + data.toString());
                //mFileHelper.putTargetPicIntoFolder(data.getData(), mTargetName);
                break;
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
                if (mShowClusterMenu) {
                    mActionBar.enableClusterMenu(mSelectedAction, this);
                }
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
        mListener.onSelectionModeChange(mode);
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        Logger.d(TAG, "onPageSelected - path： " + path.toString() + " selected: " + selected);
        mActionModeHandler.setTitle(getSelectedString());
        mActionModeHandler.updateSupportedOperation(path, selected);
        mListener.onSelectionChange(path, selected);
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mAlbumSetView.setHighlightItemPath(null);
        mSlotView.invalidate();
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
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

    public void showInputDialog(final int msgType, final int title) {
        //get selected data
        final List<Path> selectedPaths = mSelectionManager.getSelected(false);
        //setup dialog
        View mRootView = mActivity.getLayoutInflater().inflate(R.layout.dlg_input_album_name, null);
        final EditText mEditText = (EditText) mRootView.findViewById(R.id.create_album_edittext);
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(mActivity);
        mBuilder.setTitle(mActivity.getResources().getString(title));
        mBuilder.setCancelable(true);
        mBuilder.setView(mRootView);
        mBuilder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int paramInt) {
                mTargetName = mEditText.getText().toString();
                switch (msgType) {
                    case MSG_ACTION_CREATE_ALBUM:
                        createAlbum();
                        break;
                    case MSG_ACTION_RENAME_ALBUM:
                        renameAlbum(selectedPaths);
                        break;
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

    private void createAlbum() {
        Logger.d(TAG, "createAlbum");
        if (mFileHelper.validateNewFileName(mActivity, mTargetName)) {
            // 1
            //create folder and putin default picture
            //mFileHelper.putPictureIntoFolder(mTargetName);

            // 2
            //Send action for select target picture
            //Intent i = new Intent(mActivity.getAndroidContext(), DialogPicker.class);
            //i.setAction(Intent.ACTION_GET_CONTENT);
            //i.setType(GalleryUtils.MIME_TYPE_IMAGE);
            //mActivity.startActivityForResult(i, REQUEST_CREATE_ALBUM);

            // 3
            // add empty album into database
            Logger.d(TAG, "validate name is pass");
            String folderPath = MediaSetUtils.DIRECTORY_PICTURES + File.separator + mTargetName;
            int bucketId = GalleryUtils.getBucketId(folderPath);
            boolean isUpdate = mActivity.getAlbumsManager().insert(bucketId, mTargetName, System.currentTimeMillis(), GalleryUtils.NA);
            Logger.d(TAG, "insert empty album into database - folderPath: " + folderPath + " result: " + isUpdate);
            MediaSetUtils.requestSync(mActivity.getAndroidContext());
        }
    }

    private void renameAlbum(List<Path> selectedPaths) {
        Logger.d(TAG, "renameAlbum");
        if (selectedPaths == null || selectedPaths.size() <= 0) {
            mHandler.sendEmptyMessage(MSG_RENAME_FOLDER_NONE);
            return;
        }
        isLoadingRename = true;
        showProgressDialog();
        try {
            if (mFileHelper.validateFileName(mActivity, mTargetName)) {
                DataManager mDataManager = mActivity.getDataManager();
                String parentPath = null;
                String newPath = null;
                File parentFile = null;
                File newFile = null;

                //get mediaset
                MediaSet mediaSet = mDataManager.getMediaSet(selectedPaths.get(0));
                int setSize = mediaSet.getMediaItemCount();
                List<MediaItem> datas = mediaSet.getMediaItem(0, setSize);
                List<String> filePathList = new ArrayList<>(setSize);
                String[] fileArrays = new String[setSize];

                //get parent path
                String itemPath = datas.get(0).getFilePath();
                parentPath = itemPath.substring(0, itemPath.lastIndexOf(File.separator));
                parentFile = new File(parentPath);
                newPath = parentPath.substring(0, parentPath.lastIndexOf(File.separator)) + File.separator + mTargetName;
                newFile = new File(newPath);

                //is root path
                if (GalleryUtils.isRootPath(parentPath)) {
                    Logger.d(TAG, "root path isn't support rename - path: " + parentPath);
                    mHandler.sendEmptyMessage(MSG_RENAME_NOT_SUPPORT_ROOT_PATH);
                    return;
                }

                //new file isExist
                if (newFile.exists()) {
                    Logger.d(TAG, "folder is exists - path: " + newPath);
                    mHandler.sendEmptyMessage(MSG_RENAME_FOLDER_EXITS);
                    return;
                }

                //rename
                parentFile.renameTo(newFile);

                //media sacanner for newPath
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(newFile));
                mActivity.sendBroadcast(intent);

                //media sacanner for olderPath
                for (int i = 0; i < datas.size(); i++) {
                    filePathList.add(datas.get(i).getFilePath());
                }
                filePathList.toArray(fileArrays);
                MediaScannerConnection.scanFile(mActivity.getAndroidContext(), fileArrays, null, null);

                mHandler.sendEmptyMessage(MSG_RENAME_FOLDER_SUCC);
                Logger.d(TAG, "renameAlbum succ - parentPath: " + parentPath + " newPath: " + newPath);
            }
        } catch (Exception ex) {
            Logger.e(TAG, "rename err: " + ex.getMessage());
            mHandler.sendEmptyMessage(MSG_RENAME_FOLDER_ERROR);
        } finally {
            hideProgressDialog();
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setTitle(R.string.rename);
            mProgressDialog.setMessage(mActivity.getString(R.string.pano_review_saving_indication_str));
            mProgressDialog.setIcon(R.mipmap.ic_launcher_gallery);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
        }
        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    public void setListener(ViewPagerListener listener) {
        this.mListener = listener;
    }

    /**
     * enter album setting page
     */
    public void startAlbumSettingPage() {
        Bundle data = new Bundle();
        String mediaPath = mActivity.getDataManager().getTopSetPath(DataManager.INCLUDE_SETTING_ONLY);
        data.putString(AlbumSettingPage.KEY_MEDIA_PATH, mediaPath);
        mActivity.getStateManager().startState(AlbumSettingPage.class, data);
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            clearLoadingBit(BIT_LOADING_RELOAD);
            isDataEmpty = mSlotView != null && mMediaSet.getSubMediaSetCount() <= 0;
            if (isLoadingRename) {
                isLoadingRename = false;
                hideProgressDialog();
            }
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;

        @Override
        public int size() {
            return mAlbumSetDataAdapter.size();
        }

        @Override
        public int setIndex() {
            Path id = mSelectionManager.getSelected(false).get(0);
            mIndex = mAlbumSetDataAdapter.findSet(id);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            MediaObject item = mAlbumSetDataAdapter.getMediaSet(mIndex);
            if (item != null) {
                mAlbumSetView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }

}
