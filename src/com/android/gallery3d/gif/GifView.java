package com.android.gallery3d.gif;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.android.gallery3d.app.GifActivity;
import com.android.gallery3d.util.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class GifView extends ImageView implements DecoderListener {

    private static final String TAG = "GifView";
    private static final float SCALE_LIMIT = 4;
    private static final long FRAME_DELAY = 200; //milliseconds
    private static final int MSG_INVALIDATE = 1;

    private DecoderThread mDecoderThread = null;
    private Bitmap mCurrentImage = null;
    private DrawThread mDrawThread = null;
    private Uri mUri;
    private Context mContext;

    private int mWidth;
    private int mHeight;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INVALIDATE:
                    invalidate();
                    break;
            }
        }
    };

    public GifView(Context context) {
        this(context, null);
    }

    public GifView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GifView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    public void setLayoutParams(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    public boolean setDrawable(Uri uri) {
        if (null == uri) {
            return false;
        }
        mUri = uri;

        InputStream is = getInputStream(uri);
        if (is == null || (getFileSize(is) == 0)) {
            return false;
        }
        startDecode(is);
        return true;
    }

    private int getFileSize(InputStream is) {
        if (is == null) return 0;

        int size = 0;
        try {
            if (is instanceof FileInputStream) {
                FileInputStream f = (FileInputStream) is;
                size = (int) f.getChannel().size();
            } else {
                while (-1 != is.read()) {
                    size++;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "catch exception:" + e);
        }

        return size;

    }

    private InputStream getInputStream(Uri uri) {
        ContentResolver cr = mContext.getContentResolver();
        InputStream input = null;
        try {
            input = cr.openInputStream(uri);
        } catch (IOException e) {
            Log.e(TAG, "catch exception:" + e);
        }
        return input;
    }

    private void startDecode(InputStream is) {
        freeGifDecoder();
        mDecoderThread = new DecoderThread(is, this);
        mDecoderThread.start();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDecoderThread == null) {
            return;
        }

        if (mCurrentImage == null) {
            mCurrentImage = mDecoderThread.getImage();
        }
        if (mCurrentImage == null) {
            // if this gif can not be displayed, just try to show it as jpg by parsing mUri
            setImageURI(mUri);
            return;
        }
        setImageURI(null);
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        Rect sRect = null;
        Rect dRect = null;

        int imageHeight = mCurrentImage.getHeight();
        int imageWidth = mCurrentImage.getWidth();

        //int displayHeight = GifActivity.mMetrics.heightPixels;
        //int displayWidth = GifActivity.mMetrics.widthPixels;

        int width, height;
        if (imageWidth >= mWidth || imageHeight >= mHeight) {
            // scale-down the image
            if (imageWidth * mHeight > mWidth * imageHeight) {
                width = mWidth;
                height = (imageHeight * width) / imageWidth;
            } else {
                height = mHeight;
                width = (imageWidth * height) / imageHeight;
            }
        } else {
            // scale-up the image
            float scale = Math.min(SCALE_LIMIT, Math.min(mWidth / (float) imageWidth,
                    mHeight / (float) imageHeight));
            width = (int) (imageWidth * scale);
            height = (int) (imageHeight * scale);
        }
        dRect = new Rect((mWidth - width) / 2, (mHeight - height) / 2,
                (mWidth + width) / 2, (mHeight + height) / 2);
        canvas.drawBitmap(mCurrentImage, sRect, dRect, null);
        canvas.restoreToCount(saveCount);
    }

    public void onDecode(boolean parseStatus, int frameIndex) {
        if (parseStatus) {
            //indicates the start of a new GIF
            if (mDecoderThread != null && frameIndex == -1
                    && mDecoderThread.getFrameCount() > 1) {
                if (mDrawThread != null) {
                    mDrawThread = null;
                }
                mDrawThread = new DrawThread();
                mDrawThread.start();
            }
        } else {
            Logger.e(TAG, "onDecode error");
        }
    }

    private long getDelay(GifFrame frame) {
        //in milliseconds
        return frame.mDelayInMs == 0 ? FRAME_DELAY : frame.mDelayInMs;
    }

    private void freeGifDecoder() {
        if (mDecoderThread != null) {
            mDecoderThread.free();
            mDecoderThread = null;
        }

    }

    public void freeMemory() {
        if (mDrawThread != null) {
            mDrawThread = null;
        }
        freeGifDecoder();
    }

    private class DrawThread extends Thread {
        public void run() {
            while (mDrawThread != null) {
                if (!isShown() || mHandler == null) {
                    break;
                }
                GifFrame frame;
                try {
                    frame = mDecoderThread.next();

                    mCurrentImage = frame.mImage;
                    mHandler.sendEmptyMessage(MSG_INVALIDATE);

                    Thread.sleep(getDelay(frame));
                } catch (Exception e) {
                    Logger.e(TAG, "DrawThread - run - err: " + e.getMessage());
                }
            }
        }
    }
}
