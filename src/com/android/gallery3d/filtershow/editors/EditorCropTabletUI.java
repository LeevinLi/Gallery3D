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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterCropRepresentation;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.filters.FilterStraightenRepresentation;
import com.android.gallery3d.filtershow.imageshow.ImageMirror;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.ui.CropListAdapter;
import com.android.gallery3d.filtershow.ui.EffectListView;
import com.android.gallery3d.filtershow.ui.WheelView;

import java.util.ArrayList;
import java.util.List;


public class EditorCropTabletUI {
    private final static String POSITION_WHEN_CROP_BACK = "position when crop back";
    public final static String IS_CROP_BACK = "is crop back";

    public EditorCropTabletUI(final FilterShowActivity activity, final EditorCrop editorCrop, Context context, LinearLayout lp) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        EffectListView listView = (EffectListView) lp.findViewById(R.id.land_crop_list);
        LinearLayout ltrMirror = (LinearLayout) lp.findViewById(R.id.ltrmirror);
        LinearLayout ttbMirror = (LinearLayout) lp.findViewById(R.id.ttbmirror);
        Button rotateReset = (Button) lp.findViewById(R.id.rotate_reset);
        ImageButton rotate90 = (ImageButton) lp.findViewById(R.id.rotate_90);
        final PopupMenu popupMenu = new PopupMenu(context, listView);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_crop, popupMenu.getMenu());
        String[] titles = new String[popupMenu.getMenu().size()];
        for (int i = 0; i < titles.length; i++) {
            titles[i] = (String) popupMenu.getMenu().getItem(i).getTitle();
        }
        final CropListAdapter adapter = new CropListAdapter(context, titles);
        listView.setAdapter(adapter);
        final FilterCropRepresentation cropRepresentation = (FilterCropRepresentation) activity
                .getCategoryGeometryAdapter().getItem(0).getRepresentation();
        final FilterStraightenRepresentation straightenRepresentation = (FilterStraightenRepresentation) activity
                .getCategoryGeometryAdapter().getItem(1).getRepresentation();
        final FilterRotateRepresentation rotateRepresentation = (FilterRotateRepresentation) activity
                .getCategoryGeometryAdapter().getItem(2).getRepresentation();
        final FilterMirrorRepresentation mirrorRepresentation = (FilterMirrorRepresentation) activity
                .getCategoryGeometryAdapter().getItem(3).getRepresentation();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                FilterRepresentation representation = MasterImage.getImage().getCurrentFilterRepresentation();
                if (representation != null && !(representation.equals(cropRepresentation))) {
                    activity.showRepresentation(cropRepresentation);
                    prefs.edit().putInt(POSITION_WHEN_CROP_BACK, position).commit();
                    prefs.edit().putBoolean(IS_CROP_BACK, true).commit();
                    return;
                }
                prefs.edit().putBoolean(IS_CROP_BACK, false).commit();
                editorCrop.changeCropAspect(popupMenu.getMenu().getItem(position).getItemId());
                editorCrop.finalApplyCalled();
                adapter.setSelected(position);
                adapter.notifyDataSetChanged();
            }
        });
        ltrMirror.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(IS_CROP_BACK, false).commit();
                mirrorRepresentation.setEditorId(ImageOnlyEditor.ID);
                mirrorRepresentation.setMirrorDir(FilterMirrorRepresentation.LTR);
                activity.showRepresentation(mirrorRepresentation);
            }
        });
        ttbMirror.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(IS_CROP_BACK, false).commit();
                mirrorRepresentation.setEditorId(ImageOnlyEditor.ID);
                mirrorRepresentation.setMirrorDir(FilterMirrorRepresentation.TTB);
                activity.showRepresentation(mirrorRepresentation);
            }
        });
        final TextView rotateAngle = (TextView) lp.findViewById(R.id.rotate_angle);
        rotateAngle.setText("0°");
        rotateReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotateRepresentation.resetImage();
                rotateRepresentation.setBaseRotation(0);
                rotateAngle.setText("0°");
                activity.showRepresentation(rotateRepresentation);
            }
        });
        rotate90.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotateRepresentation.rotate90();
                rotateRepresentation.setBaseRotation(rotateRepresentation.getRotation());
                rotateAngle.setText(rotateRepresentation.getRotation() + "°");
                activity.showRepresentation(rotateRepresentation);
            }
        });

        WheelView rotateView = (WheelView) lp.findViewById(R.id.rotate_wheel_view);
        final List<String> items = new ArrayList<>();
        for (int i = -45; i < 46; i++) {
            items.add(String.valueOf(i));
        }
        rotateView.setItems(items);
        rotateView.selectIndex(45);
        rotateView.setOnWheelItemSelectedListener(new WheelView.OnWheelItemSelectedListener() {
            @Override
            public void onWheelItemChanged(WheelView wheelView, int position) {
                rotateRepresentation.setRotation((Integer.valueOf(items.get(position))
                        + rotateRepresentation.getBaseRotation()) % 360);
                rotateAngle.setText(rotateRepresentation.getRotation() + "°");
            }

            @Override
            public void onWheelItemSelected(WheelView wheelView, int position) {
                activity.showRepresentation(rotateRepresentation);
            }
        });
        boolean isCropBack = prefs.getBoolean(IS_CROP_BACK, false);
        if (isCropBack) {
            int cropPosition = prefs.getInt(POSITION_WHEN_CROP_BACK, 0);
            editorCrop.changeCropAspect(popupMenu.getMenu().getItem(cropPosition).getItemId());
            adapter.setSelected(cropPosition);
            adapter.notifyDataSetChanged();
        }
    }

}
