package com.android.gallery3d.ui;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.util.Logger;

import java.util.ArrayList;

public class GalleryViewPager extends GLView {
    public static final String TAG = "GalleryViewPager";

    public static final boolean DEBUG = false;

    private static final int MAX_SETTLE_DURATION = 600; // ms
    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips

    private static final int MIN_FLING_VELOCITY = 400; // dips

    /**
     * Indicates that the pager is in an idle, settled state. The current page
     * is fully in view and no animation is in progress.
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * Indicates that the pager is currently being dragged by the user.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * Indicates that the pager is in the process of settling to a final
     * position.
     */
    public static final int SCROLL_STATE_SETTLING = 2;

    private int mScrollState = SCROLL_STATE_IDLE;

    /**
     * Callback interface for responding to changing state of the selected page.
     */
    public interface OnPageChangeListener {

        /**
         * This method will be invoked when the current page is scrolled, either
         * as part of a programmatically initiated smooth scroll or a user
         * initiated touch scroll.
         * 
         * @param position
         *            Position index of the first page currently being
         *            displayed. Page position+1 will be visible if
         *            positionOffset is nonzero.
         * @param positionOffset
         *            Value from [0, 1) indicating the offset from the page at
         *            position.
         * @param positionOffsetPixels
         *            Value in pixels indicating the offset from position.
         */
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels);

        /**
         * This method will be invoked when a new page becomes selected.
         * Animation is not necessarily complete.
         * 
         * @param position
         *            Position index of the new selected page.
         */
        public void onPageSelected(int position);

        /**
         * Called when the scroll state changes. Useful for discovering when the
         * user begins dragging, when the pager is automatically settling to the
         * current page, or when it is fully stopped/idle.
         * 
         * @param state
         *            The new scroll state.
         * @see GalleryViewPager#SCROLL_STATE_IDLE
         * @see GalleryViewPager#SCROLL_STATE_DRAGGING
         * @see GalleryViewPager#SCROLL_STATE_SETTLING
         */
        public void onPageScrollStateChanged(int state);
    }

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mFlingDistance;
    private int mPageMargin;

    RawTexture[] mSurfaces;

    private boolean mIsBeingDragged;
    private boolean mIsUnableToDrag;

    public void setIsunableToDrag(boolean isUnableToDrag) {
        mIsUnableToDrag = isUnableToDrag;
    }

    private boolean mCannotToDrag = false;

    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    AbstractGalleryActivity mContext;

    private Scroller mScroller;

    public GalleryViewPager(AbstractGalleryActivity context) {
        this.mContext = context;
        initViewPager();
    }

    void initViewPager() {
        mScroller = new Scroller(mContext, sInterpolator);
        final ViewConfiguration configuration = ViewConfiguration.get(mContext);
        final float density = mContext.getResources().getDisplayMetrics().density;

        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
    }

    @Override
    protected void render(GLCanvas canvas) {
        super.render(canvas);
        boolean more = computeScroll();
        more |= mIsBeingDragged;
        if (more) {

            canvas.save();
            canvas.translate(0, 0);
            renderSurface(canvas);
            canvas.restore();

        } else {
            int componentCount = getComponentCount();
            if (componentCount > 0) {
                canvas.save();
                canvas.translate(0, 0);
                getComponent(Utils.clamp(mCurrentIndex, 0, componentCount - 1)).render(canvas);
                canvas.restore();
            }
        }
        if (more)
            invalidate();
    }

    protected void renderSurface(GLCanvas canvas) {
        int childCount = getComponentCount();
        if (mCurrentIndex < 0 || mCurrentIndex >= childCount)
            return;
        final int scrollX = Utils.clamp(getScrollX(), 0, getWidth());
        canvas.save();
        canvas.translate(-scrollX, 0);
        int stepX = getWidth();
        for (int i = 0; i < childCount; i++) {
            canvas.save();
            canvas.translate(stepX * i, 0);
            GLView childView = getComponent(i);
            canvas.beginRenderTarget(mSurfaces[i]);
            childView.render(canvas);
            canvas.endRenderTarget();
            mSurfaces[i].draw(canvas, 0, 0);
            canvas.restore();
        }

        canvas.restore();
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top, int right, int bottom) {
        initWindowTexture(right - left, bottom - top);
        for (int i = 0; i < this.getComponentCount(); i++) {
            GLView g = getComponent(i);
            g.layout(left, top, right, bottom);
        }
    }

    private void initWindowTexture(int width, int height) {
        if (width <= 0 || width <= 0) {
            throw new RuntimeException("prepareTexture error because width or height <= 0");
        }
        if (mSurfaces != null && mSurfaces.length > 0) {
            for (RawTexture t : mSurfaces) {
                if (t != null)
                    t.recycle();
                t = null;
            }
        }
        int childCount = getComponentCount();
        mSurfaces = new RawTexture[childCount];
        for (int i = 0; i < childCount; i++) {
            mSurfaces[i] = new RawTexture(width, height, true);
        }

    }

    @Override
    protected boolean dispatchTouchEvent(MotionEvent event) {
        // don't dispatch event to child
        return onTouch(event);
    }

    @Override
    protected void renderChild(GLCanvas canvas, GLView component) {
        // don't draw child
    }

    float mLastMotionX, mLastMotionY;
    float mInitialMotionX, mInitialMotionY;
    private VelocityTracker mVelocityTracker;
    int mCurrentIndex;
    private boolean mDispatchedCancel = false;

    @Override
    protected boolean onTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && event.getEdgeFlags() != 0) {
            // Don't handle edge touches immediately -- they may actually belong
            // to one of our
            // descendants.
            return false;
        }

        if (!mIsBeingDragged) {
            dispatchTouchEvent(0, 0, event);
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        boolean needsInvalidate = false;
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mCannotToDrag = false;
            mScroller.abortAnimation();
            mLastMotionX = mInitialMotionX = event.getX();
            mLastMotionY = mInitialMotionY = event.getY();
            break;
        case MotionEvent.ACTION_MOVE:

            if (!mIsBeingDragged) {
                final float x = event.getX();
                final float xDiff = Math.abs(x - mLastMotionX);
                final float y = event.getY();
                final float yDiff = Math.abs(y - mLastMotionY);
                if (DEBUG)
                    Logger.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
                if (xDiff > mTouchSlop && xDiff > yDiff && !mIsUnableToDrag && !mCannotToDrag) {
                    if (DEBUG)
                        Logger.v(TAG, "Starting drag!");
                    mIsBeingDragged = true;
                    mDispatchedCancel = false;
                    mLastMotionX = x - mInitialMotionX > 0 ? mInitialMotionX + mTouchSlop : mInitialMotionX - mTouchSlop;
                    mLastMotionY = y;
                    setScrollState(SCROLL_STATE_DRAGGING);
                } else if (yDiff > mTouchSlop) {
                    mCannotToDrag = true;
                }
            }
            // Not else! Note that mIsBeingDragged can be set above.
            if (mIsBeingDragged) {
                if (!mDispatchedCancel) {
                    dispatchTouchCancelEvent(0, 0, event);
                    mDispatchedCancel = true;
                }
                // Scroll to follow the motion event
                final float x = event.getX();
                needsInvalidate |= performDrag(x);
            }
            break;
        case MotionEvent.ACTION_UP:

            if (mIsBeingDragged) {
                final VelocityTracker velocityTracker = mVelocityTracker;
                final int pointerId = event.getPointerId(0);
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                final float initialVelocity = velocityTracker.getXVelocity(pointerId);

                final int scrollX = getScrollX();
                int nextPage = determineTargetPage(scrollX, initialVelocity, scrollX);
                scrollToItem(nextPage, true, (int) initialVelocity, true);
                endDrag();

            }
            break;
        case MotionEvent.ACTION_CANCEL:
            if (mIsBeingDragged) {
                endDrag();
            }
            break;

        default:
            break;
        }

        if (needsInvalidate)
            invalidate();

        return true;
    }

    /**
     * dispathTouchEvent to children , isCancel because cancel children event
     * when begin to drag
     * 
     * @param offsetX
     * @param offsetY
     * @param event
     * @param isCancel
     */
    private void dispatchTouchEvent(int offsetX, int offsetY, MotionEvent event) {
        if (mCurrentIndex >= 0 && mCurrentIndex < getComponentCount()) {
            GLView v = getComponent(mCurrentIndex);
            MotionEvent e = null;
            event.offsetLocation(offsetX, offsetY);
            if (v.dispatchTouchEvent(event)) {
                event.offsetLocation(0, 0);
            }

        }

    }

    public void dispatchTouchCancelEvent(int offsetX, int offsetY, MotionEvent event) {
        if (mCurrentIndex >= 0 && mCurrentIndex < getComponentCount()) {
            GLView v = getComponent(mCurrentIndex);
            MotionEvent e = null;
            try {
                e = MotionEvent.obtain(event);
                e.setAction(MotionEvent.ACTION_CANCEL);
                e.offsetLocation(offsetX, offsetY);
                v.dispatchTouchEvent(e);
            } finally {
                if (e != null) {
                    e.recycle();
                }
            }
        }
    }

    private int determineTargetPage(int scrollX, float velocity, float deltaX) {
        int targetPage;
        if (Math.abs(deltaX) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity) {
            targetPage = velocity > 0 ? mCurrentIndex - 1 : mCurrentIndex + 1;
        } else {
            if (scrollX > getWidth() / 2) {
                targetPage = 1;
            } else {
                targetPage = 0;
            }
        }
        if (getComponentCount() > 0) {
            targetPage = Utils.clamp(targetPage, 0, getComponentCount() - 1);
        }
        mCurrentIndex = targetPage;
        return targetPage;

    }

    private void endDrag() {
        mIsBeingDragged = false;
        mCannotToDrag = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private boolean performDrag(float x) {

        final float deltaX = mLastMotionX - x;
        mLastMotionX = x;

        float oldScrollX = getScrollX();
        float scrollX = oldScrollX + deltaX;
        if (scrollX <= 0) {
            scrollX = 0;
            mScrollX = 0;
        }
        int maxWidth = getWidth();
        if (scrollX >= maxWidth) {
            scrollX = maxWidth;
            mScrollX = maxWidth;
        }
        // Don't lose the rounded component
        mLastMotionX += scrollX - (int) scrollX;
        scrollTo((int) scrollX, getScrollY());
        pageScrolled((int) scrollX);

        return true;
    }

    void smoothScrollTo(int x, int y, int velocity) {
        if (getComponentCount() == 0) {

            return;
        }
        int sx = getScrollX();
        int sy = getScrollY();
        int dx = x - sx;
        int dy = y - sy;
        if (dx == 0 && dy == 0) {
            completeScroll();
            populate();
            setScrollState(SCROLL_STATE_IDLE);
            return;
        }

        setScrollState(SCROLL_STATE_SETTLING);

        final int width = getWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
        final float distance = halfWidth + halfWidth * distanceInfluenceForSnapDuration(distanceRatio);

        int duration = 0;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float pageWidth = getWidth();
            final float pageDelta = (float) Math.abs(dx) / (pageWidth + mPageMargin);
            duration = (int) ((pageDelta + 1) * 100);
        }
        duration = Math.min(duration, MAX_SETTLE_DURATION);

        mScroller.startScroll(sx, sy, dx, dy, duration);
        invalidate();
    }

    void populate() {
        populate(mCurrentIndex);
    }

    void populate(int newCurrentItem) {

    }

    private void scrollToItem(int index, boolean smoothScroll, int velocity, boolean dispatchSelected) {
        int destX = 0;
        if (index == 0) {
            destX = 0;
        }
        if (index == 1) {
            destX = getWidth();
        }
        if (smoothScroll) {
            smoothScrollTo(destX, 0, velocity);
            if (dispatchSelected) {
                dispatchOnPageSelected(index);
            }
        } else {
            if (dispatchSelected) {
                dispatchOnPageSelected(index);
            }

            scrollTo(destX, 0);
            completeScroll();

        }
    }

    ArrayList<OnPageChangeListener> pageChangeListeners = new ArrayList<OnPageChangeListener>();

    private void dispatchOnPageSelected(int position) {
        for (OnPageChangeListener listener : pageChangeListeners) {
            listener.onPageSelected(position);
        }
    }

    private void dispatchOnPageScrolled(int position, float offset, int offsetPixels) {
        for (OnPageChangeListener listener : pageChangeListeners) {
            listener.onPageScrolled(position, offset, offsetPixels);
        }
    }

    private void dispatchOnScrollStateChanged(int state) {
        for (OnPageChangeListener listener : pageChangeListeners) {
            listener.onPageScrollStateChanged(state);
        }
    }

    public void removePageChangeListener(OnPageChangeListener listener) {
        if (!pageChangeListeners.contains(listener)) {
            return;
        }
        pageChangeListeners.remove(listener);
    }

    public void addPageChangeListener(OnPageChangeListener listener) {
        if (pageChangeListeners.contains(listener)) {
            return;
        }
        pageChangeListeners.add(listener);
    }

    public boolean computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (x < 0 || x > getWidth())
                return false;
            if (oldX != x || oldY != y) {
                scrollTo(x, y);
                if (!pageScrolled(x)) {
                    mScroller.abortAnimation();
                    scrollTo(0, y);
                }
            }
            return true;
        }

        // Done with scroll, clean up state.
        completeScroll();
        return false;
    }

    public void scrollTo(int x, int y) {
        if (mScrollX != x || mScrollY != y) {
            mScrollX = x;
            mScrollY = y;
            invalidate();
        }
    }

    private void setScrollState(int newState) {
        if (mScrollState == newState) {
            return;
        }

        mScrollState = newState;
        dispatchOnScrollStateChanged(newState);
    }

    private void completeScroll() {
        boolean needPopulate = mScrollState == SCROLL_STATE_SETTLING;
        if (needPopulate) {
            mScroller.abortAnimation();
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (oldX != x || oldY != y) {
                scrollTo(x, y);
                if (x != oldX) {
                    pageScrolled(x);
                }
            }
        }
        if (needPopulate) {
            setScrollState(SCROLL_STATE_IDLE);
        }

    }

    private boolean pageScrolled(int xpos) {
        if (DEBUG)
            Logger.d(TAG, "xpos=" + xpos);
        if (getComponentCount() <= 0) {
            return false;
        }
        dispatchOnPageScrolled(0, (float) xpos / getWidth(), xpos);
        return true;
    }

    public int getScrollX() {
        return mScrollX;
    }

    public int getScrollY() {
        return mScrollY;
    }

    // We want the duration of the page snap animation to be influenced by the
    // distance that
    // the screen has to travel, however, we don't want this duration to be
    // effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect
    // that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    public void setSelectedTab(int tabIndex) {
        if (getComponentCount() > 0) {
            mCurrentIndex = Utils.clamp(tabIndex, 0, getComponentCount() - 1);
            if (!(mScrollState == SCROLL_STATE_SETTLING)) {
                int destX = 0;
                if (mCurrentIndex == 0) {
                    destX = 0;
                }
                if (mCurrentIndex == 1) {
                    destX = getWidth();
                }
                scrollTo(destX, 0);
            }
        }
        invalidate();
    }

}
