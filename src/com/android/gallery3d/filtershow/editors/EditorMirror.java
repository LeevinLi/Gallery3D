/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.editors;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.GeometryMathUtils;
import com.android.gallery3d.filtershow.imageshow.ImageMirror;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;

import java.util.ArrayList;

public class EditorMirror extends Editor implements EditorInfo {
    public static final String TAG = EditorMirror.class.getSimpleName();
    public static final int ID = R.id.editorFlip;
    ImageMirror mImageMirror;
    GeometryMathUtils.GeometryHolder holder = null;

    public EditorMirror() {
        super(ID);
        mChangesGeometry = true;
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        if (mImageMirror == null) {
            mImageMirror = new ImageMirror(context);
        }
        mView = mImageShow = mImageMirror;
        mImageMirror.setEditor(this);
    }

    @Override
    public void reflectCurrentFilter() {
        MasterImage master = MasterImage.getImage();
        master.setCurrentFilterRepresentation(master.getPreset()
                .getFilterWithSerializationName(FilterMirrorRepresentation.SERIALIZATION_NAME));
        super.reflectCurrentFilter();
        ImagePreset preset = master.getPreset();
        ArrayList<FilterRepresentation> geometry =
                (ArrayList<FilterRepresentation>) preset.getGeometryFilters();
        holder = GeometryMathUtils.unpackGeometry(geometry);
        FilterRepresentation rep = getLocalRepresentation();
        if (rep == null || rep instanceof FilterMirrorRepresentation) {
            mImageMirror.setFilterMirrorRepresentation((FilterMirrorRepresentation) rep);
        } else {
            Log.w(TAG, "Could not reflect current filter, not of type: "
                    + FilterMirrorRepresentation.class.getSimpleName());
        }
        mImageMirror.invalidate();
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout controls = (LinearLayout) inflater.inflate(
                R.layout.filtershow_mirror_ui, accessoryViewList, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        controls.setLayoutParams(lp);
        accessoryViewList.removeAllViews();
        accessoryViewList.addView(controls);
        final FilterMirrorRepresentation representation = mImageMirror.getFinalRepresentation();
        accessoryViewList.findViewById(R.id.ltrmirror).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.rotation == 0 || holder.rotation == 180) {
                    representation.setMirrorDir(FilterMirrorRepresentation.LTR);
                } else {
                    representation.setMirrorDir(FilterMirrorRepresentation.TTB);

                }
                mImageMirror.getActivity().showRepresentation(representation);

            }
        });
        accessoryViewList.findViewById(R.id.ttbmirror).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.rotation == 0 || holder.rotation == 180) {
                    representation.setMirrorDir(FilterMirrorRepresentation.TTB);
                } else {
                    representation.setMirrorDir(FilterMirrorRepresentation.LTR);
                }
                mImageMirror.getActivity().showRepresentation(representation);

            }
        });
    }

    @Override
    public void setUtilityPanelUI(View actionButton, View editControl) {
        if (ParametricEditor.useCompact(mContext)) {
            super.setUtilityPanelUI(actionButton, editControl);
            return;
        }
    }

    @Override
    public void finalApplyCalled() {
        commitLocalRepresentation(mImageMirror.getFinalRepresentation());
    }

    @Override
    public int getTextId() {
        return R.string.mirror;
    }

    @Override
    public int getOverlayId() {
        return R.drawable.filtershow_button_geometry_flip;
    }

    @Override
    public boolean getOverlayOnly() {
        return true;
    }

    @Override
    public boolean showsSeekBar() {
        return false;
    }

    @Override
    public boolean showsPopupIndicator() {
        return false;
    }
}
