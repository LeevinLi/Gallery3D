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

package com.android.gallery3d.filtershow.editors;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.controller.Control;
import com.android.gallery3d.filtershow.controller.CurvesControl;
import com.android.gallery3d.filtershow.controller.FilterView;
import com.android.gallery3d.filtershow.controller.Parameter;
import com.android.gallery3d.filtershow.controller.ParameterCurves;
import com.android.gallery3d.filtershow.filters.FilterCurvesRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.ImageCurves;

public class EditorCurves extends ParametricEditor implements ParameterCurves {
    public static final int ID = R.id.imageCurves;
    ImageCurves mImageCurves;
    Button mRgb;
    Button mRed;
    Button mGreen;
    Button mBlue;
    Button mOutput;
    Button mInput;
    CurvesControl mCuresControl;

    public EditorCurves() {
        super(ID);
        int k = R.menu.filtershow_menu_curves;
    }

    @Override
    protected void updateText() {

    }

    @Override
    public boolean showsPopupIndicator() {
        return true;
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        mView = mImageShow = mImageCurves = new ImageCurves(context);
        super.createEditor(context, frameLayout);
        mImageCurves.setEditor(this);
    }

    @Override
    public void reflectCurrentFilter() {
        super.reflectCurrentFilter();
        FilterRepresentation rep = getLocalRepresentation();
        if (rep != null && getLocalRepresentation() instanceof FilterCurvesRepresentation) {
            FilterCurvesRepresentation drawRep = (FilterCurvesRepresentation) rep;
            mImageCurves.setFilterDrawRepresentation(drawRep);
        }
    }

    @Override
    public void setUtilityPanelUI(View actionButton, View editControl) {
        if (ParametricEditor.useCompact(mContext)) {
            super.setUtilityPanelUI(actionButton, editControl);
            return;
        }
        LinearLayout group = (LinearLayout) editControl;
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout controls = (LinearLayout) inflater.inflate(
                R.layout.filtershow_curves_controls, group, false);
        ViewGroup.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controls.setLayoutParams(lp);
        group.removeAllViews();
        group.addView(controls);
        mRgb = (Button) group.findViewById(R.id.curve_menu_rgb);
        mRgb.setBackgroundResource(R.drawable.filtershow_effect_button_background);
        mRgb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageCurves.setChannel(R.id.curve_menu_rgb);
                setSlectedBackground(true, false, false, false);
            }
        });
        mRed = (Button) group.findViewById(R.id.curve_menu_red);
        mRed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageCurves.setChannel(R.id.curve_menu_red);
                setSlectedBackground(false, true, false, false);
            }
        });
        mGreen = (Button) group.findViewById(R.id.curve_menu_green);
        mGreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageCurves.setChannel(R.id.curve_menu_green);
                setSlectedBackground(false, false, true, false);
            }
        });
        mBlue = (Button) group.findViewById(R.id.curve_menu_blue);
        mBlue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageCurves.setChannel(R.id.curve_menu_blue);
                setSlectedBackground(false, false, false, true);
            }
        });
        mOutput = (Button) group.findViewById(R.id.curve_output);
        mOutput.setText("0");
        mInput = (Button) group.findViewById(R.id.curve_input);
        mInput.setText("0");
        setMenuIcon(true);
    }

    private void setSlectedBackground(boolean rgb, boolean red, boolean green, boolean blue) {
        if (rgb) {
            mRgb.setBackgroundResource(R.drawable.filtershow_effect_button_background);
        } else {
            mRgb.setBackgroundColor(mContext.getResources().getColor(R.color.transparent));

        }
        if (red) {
            mRed.setBackgroundResource(R.drawable.filtershow_effect_button_background);
        } else {
            mRed.setBackgroundColor(mContext.getResources().getColor(R.color.transparent));

        }
        if (green) {
            mGreen.setBackgroundResource(R.drawable.filtershow_effect_button_background);
        } else {
            mGreen.setBackgroundColor(mContext.getResources().getColor(R.color.transparent));

        }
        if (blue) {
            mBlue.setBackgroundResource(R.drawable.filtershow_effect_button_background);
        } else {
            mBlue.setBackgroundColor(mContext.getResources().getColor(R.color.transparent));

        }

    }

    @Override
    public String getParameterName() {
        return null;
    }

    @Override
    public String getParameterType() {
        return sParameterType;
    }

    @Override
    public String getValueString() {
        return null;
    }

    @Override
    public void setController(Control c) {
        mCuresControl = (CurvesControl) c;
    }

    @Override
    public void setFilterView(FilterView editor) {

    }

    @Override
    public void copyFrom(Parameter src) {

    }

    @Override
    public int getOutputValues() {
        return mOutputValues;
    }

    @Override
    public int getInputValues() {
        return mInputValues;
    }

    int mOutputValues;
    int mInputValues;

    public void setIOValues(int outputValue, int inputValues) {
        mOutputValues = outputValue;
        mInputValues = inputValues;
        if (useCompact(mContext)) {
            mCuresControl.updateUI();
        } else {
            mOutput.setText(String.valueOf(outputValue));
            mInput.setText(String.valueOf(inputValues));
        }
    }
}
