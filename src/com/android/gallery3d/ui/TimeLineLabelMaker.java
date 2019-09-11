/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.gallery3d.R;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.photos.data.GalleryBitmapPool;

public class TimeLineLabelMaker {

    private static final String TAG = "TimeLineLabelMaker";
    private static final int BORDER_SIZE = 0;

    private final TimeLineSlotRenderer.LabelSpec mSpec;
    private final TextPaint mLabelPaint;
    private final Context mContext;
    private final LazyLoadedBitmap mVideoIcon;
    private int mLabelWidth;
    private int mBitmapWidth;
    private int mBitmapHeight;

    public TimeLineLabelMaker(Context context, TimeLineSlotRenderer.LabelSpec spec) {
        mContext = context;
        mSpec = spec;

        mLabelPaint = getTextPaint(spec.timeFontSize, spec.timeColor, false);
        mVideoIcon = new LazyLoadedBitmap(R.drawable.ic_video_label);
    }

    public static int getBorderSize() {
        return BORDER_SIZE;
    }

    private static TextPaint getTextPaint(int textSize, int color, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        //paint.setShadowLayer(2f, 0f, 0f, Color.LTGRAY);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    static void drawText(Canvas canvas, int x, int y, String text, int lengthLimit, TextPaint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            text = TextUtils.ellipsize(text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

    public synchronized void setLabelWidth(int width) {
        if (mLabelWidth == width) return;
        //Logger.d(TAG, "setLabelWidth width: " + width);
        mLabelWidth = width;
        int borders = 2 * BORDER_SIZE;
        mBitmapWidth = width + borders;
        mBitmapHeight = mSpec.labelBackgroundHeight + borders;
    }

    public ThreadPool.Job<Bitmap> requestLabel(String label) {
        return new TimeLabelJob(label);
    }

    public void recycleLabel(Bitmap label) {
        GalleryBitmapPool.getInstance().put(label);
    }

    private class LazyLoadedBitmap {
        private Bitmap mBitmap;
        private int mResId;

        public LazyLoadedBitmap(int resId) {
            mResId = resId;
        }

        public synchronized Bitmap get() {
            if (mBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Config.ARGB_8888;
                mBitmap = BitmapFactory.decodeResource(mContext.getResources(), mResId, options);
            }
            return mBitmap;
        }
    }

    private class TimeLabelJob implements ThreadPool.Job<Bitmap> {
        private final String mLabel;

        public TimeLabelJob(String label) {
            mLabel = label;
        }

        @Override
        public Bitmap run(JobContext jc) {
            TimeLineSlotRenderer.LabelSpec spec = mSpec;

            String sLabel = mLabel;
            Bitmap icon = mVideoIcon.get();

            Bitmap bitmap;
            int labelWidth;

            synchronized (this) {
                labelWidth = mLabelWidth;
                bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth, mBitmapHeight);
            }

            if (bitmap == null) {
                int borders = 2 * BORDER_SIZE;
                bitmap = Bitmap.createBitmap(labelWidth + borders, spec.labelBackgroundHeight + borders, Config.ARGB_8888);
            }

            if (bitmap == null) {
                return null;
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE, bitmap.getWidth() - BORDER_SIZE, bitmap.getHeight() - BORDER_SIZE);
            canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);

            canvas.translate(BORDER_SIZE, BORDER_SIZE);

            // draw label
            if (jc.isCancelled()) return null;
            int x = spec.leftMargin * 2 + spec.iconSize;
            // TODO: is the offset relevant in new reskin?
            //int y = s.timeOffset;
            int y = (spec.labelBackgroundHeight - spec.timeFontSize) / 2;
            //Logger.d(TAG, "TimeLabelJob - x: " + x + " y: " + y + " labelWidth: " + labelWidth);
            drawText(canvas, x, y, sLabel, labelWidth - spec.leftMargin - x - spec.rightMargin, mLabelPaint);

            // draw count
//            if (jc.isCancelled()) return null;
//            x = labelWidth - s.rightMargin;
//            y = (s.labelBackgroundHeight - s.timeFontSize) / 2;
//            drawText(canvas, x, y, count, labelWidth - x, mLabelPaint);

            // draw the icon
            if (icon != null) {
                if (jc.isCancelled()) return null;
                float scale = (float) spec.iconSize / icon.getWidth();
                canvas.translate(spec.leftMargin, (spec.labelBackgroundHeight - Math.round(scale * icon.getHeight())) / 2f);
                canvas.scale(scale, scale);
                canvas.drawBitmap(icon, 0, 0, null);
            }
            return bitmap;
        }
    }
}
