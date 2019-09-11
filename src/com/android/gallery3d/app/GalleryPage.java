package com.android.gallery3d.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.gallery3d.R;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.GalleryViewPager;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Logger;

import java.util.ArrayList;

public class GalleryPage extends ActivityState implements GalleryViewPager.OnPageChangeListener,
        ViewPagerListener, GalleryActionBar.TabSelectedListenter {

    public static final String TAG = "GalleryPage";

    public static final String KEY_MEDIA_PATH = "gallery-media-path";

    public static final int POSITION_TIMELINE = 0;
    public static final int POSITION_ALBUMSET = 1;

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_PHOTO = 2;
    private static final int REQUEST_DO_ANIMATION = 3;

    private StateWallper mState = new StateWallper();
    private GalleryViewPager mGalleryViewPager;
    private GalleryActionBar mActionBar;
    private boolean mGetContent;
    private boolean mGetAlbum;
    private boolean mInSelectionMode;
    private final GLView mRootPane = new GLView() {
        protected void onLayout(boolean changeSize, int left, int top, int right, int bottom) {
            int slotViewTop = mActionBar.getHeight();
            int slotViewBottom = bottom - top;
            if (mInSelectionMode) {
                slotViewBottom -= slotViewTop;
            }
            int slotViewRight = right - left;
            mGalleryViewPager.layout(0, 0, slotViewRight, slotViewBottom);
        }

        protected void render(GLCanvas canvas) {
            super.render(canvas);
        }

        protected boolean dispatchTouchEvent(android.view.MotionEvent event) {
            return super.dispatchTouchEvent(event);
        }
    };
    private TimeLinePage mTimeLinePage;
    private AlbumSetPage mAlbumSetPage;
    private String[] clazzNames = new String[]{
            "com.android.gallery3d.app.TimeLinePage",
            "com.android.gallery3d.app.AlbumSetPage"};
    private Path mMediaSetPath;
    private Menu mMenu = null;
    private int mPosition = POSITION_TIMELINE;

    protected int getBackgroundColorId() {
        return R.color.default_background;
    }

    @Override
    protected void onCreate(Bundle data, Bundle storedState) {
        super.onCreate(data, storedState);
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mGetAlbum = data.getBoolean(GalleryActivity.KEY_GET_ALBUM, false);
        try {
            initStates(data);
            initializeData(data);
            mActionBar = mActivity.getGalleryActionBar();
            mActionBar.setTabSelectedListenter(this);
            mState.onCreate(data, storedState);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        mGalleryViewPager = new GalleryViewPager(mActivity);
        mGalleryViewPager.addPageChangeListener(this);
        for (int i = 0; i < mState.size(); i++) {
            GLView v = mState.getState(i).getContentPane();
            mGalleryViewPager.addComponent(v);
        }
        mRootPane.addComponent(mGalleryViewPager);

        Logger.d(TAG, "onCreate - end");
    }

    private void initializeData(Bundle data) {
        String mediaPath = data.getString(TimeLinePage.KEY_MEDIA_PATH);
        mMediaSetPath = Path.fromString(FilterUtils.switchClusterPath(mediaPath, FilterUtils.CLUSTER_BY_TIME));
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Logger.d(TAG, "onCreateActionBar");
        mMenu = menu;
        mActionBar.showActionTab();
        MenuInflater inflator = getSupportMenuInflater();
        if (mGetContent || mGetAlbum) {
            inflator.inflate(R.menu.pickup, mMenu);
            //int typeBits = mData.getInt(GalleryActivity.KEY_TYPE_BITS, DataManager.INCLUDE_IMAGE);
            //mActionBar.setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        } else {
            inflator.inflate(R.menu.gallery_page, mMenu);
            mMenu.findItem(R.id.action_camera).setVisible(GalleryUtils.isAnyCameraAvailable(mActivity));
            updateMenuItemVisible(mPosition);
        }
        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onUpPressed();
                return true;
            }
            case R.id.action_cancel:
                mActivity.getStateManager().finishState(this);
                return true;
            case R.id.action_select:
                Logger.d(TAG, "action_select");
                mTimeLinePage.enterSelectionMode();
                return true;
            case R.id.action_slideshow: {
                Logger.d(TAG, "action_slideshow");
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH, mMediaSetPath.toString());
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(SlideshowPage.class, REQUEST_SLIDESHOW, data);
                return true;
            }
            case R.id.action_create_albums: {
                Logger.d(TAG, "action_create_albums");
                mAlbumSetPage.showInputDialog(AlbumSetPage.MSG_ACTION_CREATE_ALBUM, R.string.create_album);
                return true;
            }
            case R.id.action_album_setting: {
                Logger.d(TAG, "action_album_setting");
                if (GalleryUtils.SUPPORT_SETTING_HIDDEN) {
                    mAlbumSetPage.startAlbumSettingPage();
                } else {
                    mActivity.showToast(R.string.album_setting);
                }
                return true;
            }
            case R.id.action_camera: {
                Logger.d(TAG, "action_camera");
                GalleryUtils.startCameraActivity(mActivity);
                return true;
            }
            default:
                return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Logger.d(TAG, "onPause");
        mGalleryViewPager.removePageChangeListener(this);
        mState.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGalleryViewPager.addPageChangeListener(this);
        mState.onResume();
        setContentPane(mRootPane);
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        mState.onStateResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_SLIDESHOW: {
                // data could be null, if there is no images in the album
                if (data == null) return;
                //mFocusIndex = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
                //mSlotView.setCenterIndex(mFocusIndex);
                break;
            }
            case REQUEST_PHOTO: {
                if (data == null) return;
                //mFocusIndex = data.getIntExtra(PhotoPage.KEY_RETURN_INDEX_HINT, 0);
                //mSlotView.makeSlotVisible(mFocusIndex);
                break;
            }
            case REQUEST_DO_ANIMATION: {
                //mSlotView.startRisingAnimation();
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mState.onDestroy();
    }

    public void initStates(Bundle data) throws InstantiationException, IllegalAccessException {
        for (String name : clazzNames) {
            try {
                Class c = Class.forName(name);
                if (c == TimeLinePage.class) {
                    Logger.d(TAG, "initStates - load timeLinePage");
                    Bundle timeLineBundle = new Bundle();
                    timeLineBundle.putBoolean(GalleryActivity.KEY_GET_CONTENT, mGetContent);
                    String mediaPath = data.getString(TimeLinePage.KEY_MEDIA_PATH);
                    //cluster by time
                    mediaPath = FilterUtils.switchClusterPath(mediaPath, FilterUtils.CLUSTER_BY_TIME);
                    timeLineBundle.putString(TimeLinePage.KEY_MEDIA_PATH, mediaPath);
                    //setup params
                    mTimeLinePage = TimeLinePage.class.newInstance();
                    mTimeLinePage.initialize(mActivity, timeLineBundle);
                    mTimeLinePage.onCreate(timeLineBundle, null);
                    mTimeLinePage.onResume();
                    mTimeLinePage.setListener(this);
                    mState.addState(mTimeLinePage);
                }
                if (c == AlbumSetPage.class) {
                    Logger.d(TAG, "initStates - load albumSetPage");
                    Bundle AlbumSetBundle = new Bundle();
                    AlbumSetBundle.putBoolean(GalleryActivity.KEY_GET_CONTENT, mGetContent);
                    AlbumSetBundle.putString(AlbumSetPage.KEY_MEDIA_PATH, data.getString(AlbumSetPage.KEY_MEDIA_PATH));

                    //setup params
                    mAlbumSetPage = AlbumSetPage.class.newInstance();
                    mAlbumSetPage.initialize(mActivity, AlbumSetBundle);
                    mAlbumSetPage.onCreate(AlbumSetBundle, null);
                    mAlbumSetPage.onResume();
                    mAlbumSetPage.setListener(this);
                    mState.addState(mAlbumSetPage);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                Logger.e(TAG, "initStates - err: " + e.getMessage());
            }
        }
        Logger.d(TAG, "initStates - end");
    }

    @Override
    public void onTabSelected(int position) {
        Logger.d(TAG, "onTabSelected - position: " + position);
        mPosition = position;
        mGalleryViewPager.setSelectedTab(position);
        mActionBar.selectTab(position);
        updateMenuItemVisible(mPosition);
    }

    @Override
    public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        Logger.d(TAG, "onPageSelected - position： " + position);
        mPosition = position;
        mActionBar.selectTab(position);
        updateMenuItemVisible(mPosition);
    }

    @Override
    public void onPageScrollStateChanged(final int state) {
        //Logger.d(TAG, "onPageScrollStateChanged - state： " + state);
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                Logger.d(TAG, "onSelectionModeChange - mode： ENTER");
                mInSelectionMode = true;
                mGalleryViewPager.setIsunableToDrag(true);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                Logger.d(TAG, "onSelectionModeChange - mode： LEAVE");
                mInSelectionMode = false;
                mGalleryViewPager.setIsunableToDrag(false);
                mRootPane.invalidate();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        Logger.d(TAG, "onSelectionChange - path： " + path.toString() + " selected: " + selected);
        // TODO Auto-generated method stub
    }

    @Override
    public void onDataChange(int params, boolean isEmpty) {
        Logger.d(TAG, "onDataChange - params： " + params + " isEmpty: " + isEmpty);
        updateMenuItemEnabled(params, !isEmpty);
    }

    private void onUpPressed() {
        if (false) {
            GalleryUtils.startGalleryActivity(mActivity);
        } else {
            super.onBackPressed();
        }
    }

    private void updateMenuItemVisible(int position) {
        if (mMenu == null) return;
        switch (position) {
            case POSITION_TIMELINE:
                setMenuItemVisible(mMenu.findItem(R.id.action_select), true);
                setMenuItemVisible(mMenu.findItem(R.id.action_slideshow), true);
                setMenuItemVisible(mMenu.findItem(R.id.action_create_albums), false);
                setMenuItemVisible(mMenu.findItem(R.id.action_album_setting), false);
                break;
            case POSITION_ALBUMSET:
                setMenuItemVisible(mMenu.findItem(R.id.action_select), false);
                setMenuItemVisible(mMenu.findItem(R.id.action_slideshow), false);
                setMenuItemVisible(mMenu.findItem(R.id.action_create_albums), true);
                setMenuItemVisible(mMenu.findItem(R.id.action_album_setting), true);
                break;
            default:
                Logger.d(TAG, "updateMenuItemVisible - undefined type");
                break;
        }
    }

    private void updateMenuItemEnabled(int position, boolean isEnable) {
        if (mMenu == null) return;
        switch (position) {
            case POSITION_TIMELINE:
                setMenuItemEnabled(mMenu.findItem(R.id.action_select), isEnable);
                setMenuItemChecked(mMenu.findItem(R.id.action_select), isEnable);
                setMenuItemEnabled(mMenu.findItem(R.id.action_slideshow), isEnable);
                setMenuItemChecked(mMenu.findItem(R.id.action_slideshow), isEnable);
                break;
            case POSITION_ALBUMSET:
                //setMenuItemEnabled(mMenu.findItem(R.id.action_create_albums), isEnable);
                //setMenuItemChecked(mMenu.findItem(R.id.action_create_albums), isEnable);
                //setMenuItemEnabled(mMenu.findItem(R.id.action_album_setting), isEnable);
                //setMenuItemChecked(mMenu.findItem(R.id.action_album_setting), isEnable);
                break;
            default:
                Logger.d(TAG, "updateMenuItemEnabled - undefined type");
                break;
        }
    }

    private void setMenuItemVisible(MenuItem menuItem, boolean visible) {
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    private void setMenuItemEnabled(MenuItem menuItem, boolean visible) {
        if (menuItem != null) {
            menuItem.setEnabled(visible);
        }
    }

    private void setMenuItemChecked(MenuItem menuItem, boolean visible) {
        if (menuItem != null) {
            menuItem.setChecked(visible);
        }
    }

    class StateWallper {
        ArrayList<ActivityState> states = new ArrayList();

        public void addState(ActivityState state) {
            for (ActivityState s : states) {
                if (s == state) return;
            }
            states.add(state);
        }

        public void onStateResult(int requestCode, int resultCode, Intent data) {
            for (ActivityState s : states) {
                s.onStateResult(requestCode, resultCode, data);
            }
        }

        public void clear() {
            states.clear();
        }

        public int size() {
            return states.size();
        }

        public ActivityState getState(int index) {
            if (index >= states.size()) {
                return null;
            }
            return states.get(index);
        }

        protected void onCreate(Bundle data, Bundle storedState) {

        }

        protected void onPause() {
            for (ActivityState state : states) {
                state.onPause();
            }
            Logger.d(TAG, "StateWallper - onPause...");
        }

        protected void onResume() {
            for (ActivityState state : states) {
                state.onResume();
            }
            Logger.d(TAG, "StateWallper - onResume...");
        }

        protected void onDestroy() {
            for (ActivityState state : states) {
                state.onDestroy();
            }
            Logger.d(TAG, "StateWallper -onDestroy...");
        }
    }
}
