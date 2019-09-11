/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.util.Logger;

import java.util.ArrayList;

/**
 * Wallpaper picker for the gallery application. This just redirects to the
 * standard pick action.
 */
public class Wallpaper extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "Wallpaper";

    private static final String IMAGE_TYPE = "image/*";
    private static final String KEY_STATE = "activity-state";
    private static final String KEY_PICKED_ITEM = "picked-item";

    private static final int STATE_INIT = 0;
    private static final int STATE_PHOTO_PICKED = 1;

    private int mState = STATE_INIT;
    private Uri mPickedItem;

    private static final int PERMISSION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle != null) {
            mState = bundle.getInt(KEY_STATE);
            mPickedItem = (Uri) bundle.getParcelable(KEY_PICKED_ITEM);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle saveState) {
        saveState.putInt(KEY_STATE, mState);
        if (mPickedItem != null) {
            saveState.putParcelable(KEY_PICKED_ITEM, mPickedItem);
        }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private Point getDefaultDisplaySize(Point size) {
        Display d = getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.HONEYCOMB_MR2) {
            d.getSize(size);
        } else {
            size.set(d.getWidth(), d.getHeight());
        }
        return size;
    }

    @SuppressWarnings("fallthrough")
    @Override
    protected void onResume() {
        super.onResume();
        if (!needRequestPermission()) {
            resume();
        }
    }

    private void resume() {
        Intent intent = getIntent();
        switch (mState) {
            case STATE_INIT: {
                mPickedItem = intent.getData();
                if (mPickedItem == null) {
                    Intent request = new Intent(Intent.ACTION_GET_CONTENT)
                            .setClass(this, DialogPicker.class)
                            .setType(IMAGE_TYPE);
                    startActivityForResult(request, STATE_PHOTO_PICKED);
                    return;
                }
                mState = STATE_PHOTO_PICKED;
                // fall-through
            }
            case STATE_PHOTO_PICKED: {
                Intent cropAndSetWallpaperIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());
                    try {
                        cropAndSetWallpaperIntent = wpm.getCropAndSetWallpaperIntent(mPickedItem);
                        startActivity(cropAndSetWallpaperIntent);
                        finish();
                        return;
                    } catch (ActivityNotFoundException anfe) {
                        // ignored; fallthru to existing crop activity
                    } catch (IllegalArgumentException iae) {
                        // ignored; fallthru to existing crop activity
                    }
                }

                int width = getWallpaperDesiredMinimumWidth();
                int height = getWallpaperDesiredMinimumHeight();
                Point size = getDefaultDisplaySize(new Point());
                float spotlightX = (float) size.x / width;
                float spotlightY = (float) size.y / height;
                cropAndSetWallpaperIntent = new Intent(CropActivity.CROP_ACTION)
                        .setClass(this, CropActivity.class)
                        .setDataAndType(mPickedItem, IMAGE_TYPE)
                        .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        .putExtra(CropExtras.KEY_OUTPUT_X, width)
                        .putExtra(CropExtras.KEY_OUTPUT_Y, height)
                        .putExtra(CropExtras.KEY_ASPECT_X, width)
                        .putExtra(CropExtras.KEY_ASPECT_Y, height)
                        .putExtra(CropExtras.KEY_SPOTLIGHT_X, spotlightX)
                        .putExtra(CropExtras.KEY_SPOTLIGHT_Y, spotlightY)
                        .putExtra(CropExtras.KEY_SCALE, true)
                        .putExtra(CropExtras.KEY_SCALE_UP_IF_NEEDED, true)
                        .putExtra(CropExtras.KEY_SET_AS_WALLPAPER, true);
                startActivity(cropAndSetWallpaperIntent);
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST: {
                if (checkPermissionGrantResults(grantResults)) {
                    resume();
                } else {
                    Toast.makeText(this, R.string.denied_required_permission, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    private boolean needRequestPermission() {
        boolean needRequest = false;
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
//                , Manifest.permission.READ_PHONE_STATE
        };

        ArrayList<String> permissionList = new ArrayList<String>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
                needRequest = true;
            }
        }

        if (needRequest) {
            int count = permissionList.size();
            if (count > 0) {
                String[] permissionArray = new String[count];
                for (int i = 0; i < count; i++) {
                    permissionArray[i] = permissionList.get(i);
                }
                Logger.d(TAG,"need request permission: " + permissionArray.toString());
                requestPermissions(permissionArray, PERMISSION_REQUEST);
            }
        }
        return needRequest;
    }

    private boolean checkPermissionGrantResults(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            setResult(resultCode);
            finish();
            return;
        }
        mState = requestCode;
        if (mState == STATE_PHOTO_PICKED) {
            mPickedItem = data.getData();
        }

        // onResume() would be called next
    }
}
