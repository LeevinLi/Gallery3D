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

import android.os.Bundle;
import android.view.Menu;

import com.android.gallery3d.R;
import com.android.gallery3d.ui.EmptyTextView;
import com.android.gallery3d.util.Logger;

public class EmptyPage extends ActivityState {

    public static final String KEY_SET_TITLE = "empty-set-title";
    private static final String TAG = "EmptyPage";
    private GalleryActionBar mActionBar;
    private String mTitle;
    private EmptyTextView mEmptyTextView;

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_selection_background;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        mActionBar = mActivity.getGalleryActionBar();
        mTitle = data.getString(KEY_SET_TITLE);
        initializeViews();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mEmptyTextView = null;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        setContentPane(mEmptyTextView);
    }

    private void initializeViews() {
        mEmptyTextView = new EmptyTextView(mActivity);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Logger.d(TAG, "onCreateActionBar");
        mActionBar.setTitle(mTitle);
        return super.onCreateActionBar(menu);
    }
}
