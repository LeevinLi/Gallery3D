package com.android.gallery3d.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.util.Logger;

public class EmptyTextView extends GLView {

    private static final String TAG = "EmptyTextView";
    private static final int BITMAP_WIDTH = 600;
    private static final int BITMAP_HIGHT = 60;
    private static final int BORDER_SIZE = 0;
    private final int FONT_SIZE;
    private final BitmapTexture mEmptyTexture;
    private final Paint mTextPaint;
    private final int mBackGroundColor;
    private final String mContent;
    private int mWidth;
    private int mHight;

    public EmptyTextView(AbstractGalleryActivity activity) {
        mContent = activity.getResources().getString(R.string.empty_album);
        FONT_SIZE = activity.getResources().getDimensionPixelSize(R.dimen.empty_text_font_size);
        mBackGroundColor = activity.getColor(R.color.default_background);
        mTextPaint = getTextPaint(FONT_SIZE, R.color.empty_text_color, false);
        mEmptyTexture = new BitmapTexture(drawText());
    }

    private Paint getTextPaint(int textSize, int color, boolean isBold) {
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }
        return paint;
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top, int right, int bottom) {
        super.onLayout(changeSize, left, top, right, bottom);
        mWidth = right - left;
        mHight = bottom - top;
        Logger.d(TAG, "onLayout - w: " + mWidth + " h: " + mHight);
    }

    @Override
    protected void render(GLCanvas canvas) {
        super.render(canvas);
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        mEmptyTexture.draw(canvas, (mWidth - mEmptyTexture.getWidth()) / 2,
                (mHight - mEmptyTexture.getHeight()) / 2, BITMAP_WIDTH, BITMAP_HIGHT);
        canvas.restore();
        invalidate();
    }

    private Bitmap drawText() {
        Bitmap bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HIGHT, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                bitmap.getWidth() - BORDER_SIZE,
                bitmap.getHeight() - BORDER_SIZE);
        canvas.drawColor(mBackGroundColor, PorterDuff.Mode.SRC);

        canvas.translate(BORDER_SIZE, BORDER_SIZE);

        int w = (int) mTextPaint.measureText(mContent);
        drawText(canvas, (BITMAP_WIDTH - w) / 2, 15, mContent, mTextPaint);

        return bitmap;
    }

    private void drawText(Canvas canvas, int x, int y, String text, Paint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            //text = TextUtils.ellipsize(text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

}
