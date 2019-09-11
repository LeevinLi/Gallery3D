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

package com.android.gallery3d.ui;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumSelectionDataLoader;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeInTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.glrenderer.UploadedTexture;
import com.android.gallery3d.ui.AlbumSelectionSlidingWindow.AlbumSelectionEntry;
import com.android.gallery3d.util.GalleryUtils;

public class AlbumSelectionSlotRenderer extends AbstractSlotRenderer {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSelectionSlotRenderer";
    private static final int CACHE_SIZE = 96;
    protected final LabelSpec mLabelSpec;
    private final int mPlaceholderColor;
    private final ColorTexture mWaitLoadingTexture;
    private final ResourceTexture mCameraOverlay;
    private final AbstractGalleryActivity mActivity;
    private final SelectionManager mSelectionManager;
    protected AlbumSelectionSlidingWindow mDataWindow;
    private SlotView mSlotView;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private Path mHighlightItemPath = null;
    private boolean mInSelectionMode;

    public AlbumSelectionSlotRenderer(AbstractGalleryActivity activity, SelectionManager selectionManager,
                                      SlotView slotView, LabelSpec labelSpec, int placeholderColor) {
        super(activity);
        mActivity = activity;
        mSelectionManager = selectionManager;
        mSlotView = slotView;
        mLabelSpec = labelSpec;
        mPlaceholderColor = placeholderColor;

        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);
        mCameraOverlay = new ResourceTexture(activity, R.drawable.ic_cameraalbum_overlay);
    }

    private static Texture checkLabelTexture(Texture texture) {
        return ((texture instanceof UploadedTexture)
                && ((UploadedTexture) texture).isUploading()) ? null : texture;
    }

    private static Texture checkContentTexture(Texture texture) {
        return ((texture instanceof TiledTexture)
                && !((TiledTexture) texture).isReady()) ? null : texture;
    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index) return;
        mPressedIndex = index;
        mSlotView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1) return;
        mAnimatePressedUp = true;
        mSlotView.invalidate();
    }

    public void setHighlightItemPath(Path path) {
        if (mHighlightItemPath == path) return;
        mHighlightItemPath = path;
        mSlotView.invalidate();
    }

    public void setModel(AlbumSelectionDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mDataWindow = null;
            mSlotView.setSlotCount(0);
        }
        if (model != null) {
            mDataWindow = new AlbumSelectionSlidingWindow(mActivity, model, mLabelSpec, CACHE_SIZE);
            mDataWindow.setListener(new MyCacheListener());
            mSlotView.setSlotCount(mDataWindow.size());
        }
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        AlbumSelectionEntry entry = mDataWindow.get(index);
        int renderRequestFlags = 0;
        if (GalleryUtils.SUPPORT_3D) {
            renderRequestFlags |= renderContent3D(canvas, entry, width, height);
        } else {
            renderRequestFlags |= renderContent(canvas, entry, width, height);
        }
        renderRequestFlags |= renderLabel(canvas, entry, width, height);
        //renderRequestFlags |= renderOverlay(canvas, index, entry, width, height);
        return renderRequestFlags;
    }

    protected int renderOverlay(GLCanvas canvas, int index, AlbumSelectionEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        if (entry.album != null && entry.album.isCameraRoll()) {
            int uncoveredHeight = height - mLabelSpec.labelBackgroundHeight;
            int dim = uncoveredHeight / 2;
            mCameraOverlay.draw(canvas, (width - dim) / 2, (uncoveredHeight - dim) / 2, dim, dim);
        }
        if (mPressedIndex == index) {
            if (mAnimatePressedUp) {
                drawPressedUpFrame(canvas, width, height);
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
                if (isPressedUpFrameFinished()) {
                    mAnimatePressedUp = false;
                    mPressedIndex = -1;
                }
            } else {
                drawPressedFrame(canvas, width, height);
            }
        } else if ((mHighlightItemPath != null) && (mHighlightItemPath == entry.setPath)) {
//            drawSelectedFrame(canvas, width, height);
            drawSelectedOverlay(canvas, width, height);
        } else if (mInSelectionMode && mSelectionManager.isItemSelected(entry.setPath)) {
            //drawSelectedFrame(canvas, width, height);
            drawSelectedOverlay(canvas, width, height);
        }
        return renderRequestFlags;
    }

    protected int renderContent(GLCanvas canvas, AlbumSelectionEntry entry, int width, int height) {
        int renderRequestFlags = 0;

        Texture content = checkContentTexture(entry.content[0]);
        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitLoadingDisplayed = true;
        } else if (entry.isWaitLoadingDisplayed) {
            entry.isWaitLoadingDisplayed = false;
            content = new FadeInTexture(mPlaceholderColor, entry.bitmapTexture[0]);
            entry.content[0] = content;
        }
        drawContent(canvas, content, width, height, entry.rotation);
        if ((content instanceof FadeInTexture) && ((FadeInTexture) content).isAnimating()) {
            renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
        }
        return renderRequestFlags;
    }

    protected int renderContent3D(GLCanvas canvas, AlbumSelectionEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        int oneSize = width;
        int oneCoordinate = (width - oneSize) / 2;

        int twoSize = oneSize - 10;
        int twoX = (oneSize - twoSize) / 2 + oneCoordinate;
        int twoY = oneCoordinate - 5;

        int threeSize = twoSize - 10;
        int threeX = (twoSize - threeSize) / 2 + twoX;
        int threeY = twoY - 5;
        if (entry.coverItem == null || entry.coverItem.length <= 0) {
            //mBackOverlay.draw(canvas, oneCoordinate, oneCoordinate, oneSize, oneSize);
        } else {
            if (entry.coverItem.length == 1) {
                renderRequestFlags |= renderContent(2, true, canvas, null, threeX, threeY, threeSize, oneSize);
                renderRequestFlags |= renderContent(1, true, canvas, null, twoX, twoY, twoSize, oneSize);
                renderRequestFlags |= renderContent(0, false, canvas, entry, oneCoordinate, oneCoordinate, oneSize, oneSize);
            }
            if (entry.coverItem.length == 2) {
                renderRequestFlags |= renderContent(2, true, canvas, null, threeX, threeY, threeSize, oneSize);
                renderRequestFlags |= renderContent(1, true, canvas, entry, twoX, twoY, twoSize, oneSize);
                renderRequestFlags |= renderContent(0, false, canvas, entry, oneCoordinate, oneCoordinate, oneSize, oneSize);
            }
            if (entry.coverItem.length == 3) {
                renderRequestFlags |= renderContent(2, true, canvas, entry, threeX, threeY, threeSize, oneSize);
                renderRequestFlags |= renderContent(1, true, canvas, entry, twoX, twoY, twoSize, oneSize);
                renderRequestFlags |= renderContent(0, false, canvas, entry, oneCoordinate, oneCoordinate, oneSize, oneSize);
            }
        }
        return renderRequestFlags;
    }

    protected int renderContent(int textureIndex, boolean isAlpha, GLCanvas canvas, AlbumSelectionEntry entry, int drawX, int drawY, int
            width,
                                int height) {
        int renderRequestFlags = 0;
        if (entry == null) {
            //mBackOverlay.draw(canvas, drawX, drawY, width, height);
            renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
        } else {
            Texture content = checkContentTexture(entry.content[textureIndex]);
            if (content == null) {
                content = mWaitLoadingTexture;
                //content = mBackOverlay;
                entry.isWaitLoadingDisplayed = true;
            } else if (entry.isWaitLoadingDisplayed) {
                entry.isWaitLoadingDisplayed = false;
                content = entry.bitmapTexture[textureIndex];
                entry.content[textureIndex] = content;
            }
            if (isAlpha) {
                canvas.save();
                canvas.setAlpha(0.6f);
                drawContent(canvas, content, drawX, drawY, width, height, entry.rotation);
                canvas.restore();
            } else {
                drawContent(canvas, content, drawX, drawY, width, height, entry.rotation);
            }

            if ((content instanceof FadeInTexture) && ((FadeInTexture) content).isAnimating()) {
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
            }
        }
        return renderRequestFlags;
    }

    protected int renderLabel(GLCanvas canvas, AlbumSelectionEntry entry, int width, int height) {
        Texture content = checkLabelTexture(entry.labelTexture);
        if (content == null) {
            content = mWaitLoadingTexture;
        }
        int b = AlbumSelectionLabelMaker.getBorderSize();
        int h = mLabelSpec.labelBackgroundHeight;
        content.draw(canvas, -b, height - h + b, width + b + b, h);

        return 0;
    }

    @Override
    public void prepareDrawing() {
        mInSelectionMode = mSelectionManager.inSelectionMode();
    }

    public void pause() {
        mDataWindow.pause();
    }

    public void resume() {
        mDataWindow.resume();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
        }
    }

    @Override
    public void onSlotSizeChanged(int width, int height) {
        if (mDataWindow != null) {
            mDataWindow.onSlotSizeChanged(width, height);
        }
    }

    public static class LabelSpec {
        public int labelBackgroundHeight;
        public int titleOffset;
        public int countOffset;
        public int titleFontSize;
        public int countFontSize;
        public int leftMargin;
        public int iconSize;
        public int titleRightMargin;
        public int backgroundColor;
        public int titleColor;
        public int countColor;
        public int borderSize;
    }

    private class MyCacheListener implements AlbumSelectionSlidingWindow.Listener {

        @Override
        public void onSizeChanged(int size) {
            mSlotView.setSlotCount(size);
        }

        @Override
        public void onContentChanged() {
            mSlotView.invalidate();
        }
    }
}
