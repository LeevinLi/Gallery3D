package com.android.gallery3d.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.gif.GifView;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Logger;

public class GifActivity extends Activity {

    private static final String TAG = "GifActivity";
    public static DisplayMetrics mMetrics;

    private LinearLayout mRootLayout;
    private GifView mGifView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gif_activity);
        Intent intent = getIntent();
        if (intent == null || intent.getAction() == null || !intent.getAction().equals(GalleryUtils.ACTION_VIEW_GIF)) {
            finish();
            return;
        }

        mMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        Uri mPath = intent.getData();

        showGif(mPath);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGifView != null) {
            mGifView.freeMemory();
            mGifView = null;
        }
    }

    private void showGif(Uri uri) {
        Logger.d(TAG, "showGif");
        mRootLayout = (LinearLayout) findViewById(R.id.gif_root_view);
        mGifView = new GifView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        mRootLayout.addView(mGifView, params);
        mGifView.setLayoutParams(mMetrics.widthPixels, mMetrics.heightPixels);
        if (mGifView.setDrawable(uri)) return;
        //open gif fail
        Toast.makeText(this, R.string.fail_to_load, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        if (mGifView != null) {
            mGifView.setLayoutParams(mMetrics.widthPixels, mMetrics.heightPixels);
        }
    }
}
