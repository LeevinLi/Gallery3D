package com.android.gallery3d.filtershow.controller;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.Editor;

public class CurvesControl implements Control {
    private final String LOGTAG = "ParametricEditor";
    private ImageButton mCurvesClose;
    private ImageButton mCurvesCheck;
    private Button mOutput;
    private Button mInput;
    protected ParameterCurves mParameter;
    Editor mEditor;
    View mTopView;
    protected int mLayoutID = R.layout.filtershow_curves_ui;

    @Override
    public void setUp(ViewGroup container, Parameter parameter, Editor editor) {
        container.removeAllViews();
        mEditor = editor;
        Context context = container.getContext();
        mParameter = (ParameterCurves) parameter;
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTopView = inflater.inflate(mLayoutID, container, true);
        mTopView.setVisibility(View.VISIBLE);
        mCurvesClose = (ImageButton) mTopView.findViewById(R.id.curves_colse_button);
        mCurvesClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditor.getImageShow().resetParameter();
                mEditor.getImageShow().getActivity().loadMainPanel();
            }
        });
        mCurvesCheck = (ImageButton) mTopView.findViewById(R.id.curves_check_button);
        mCurvesCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditor.getImageShow().getActivity().loadMainPanel();
            }
        });
        mOutput = (Button) mTopView.findViewById(R.id.curve_output);
        mInput = (Button) mTopView.findViewById(R.id.curve_input);
        updateUI();
    }

    @Override
    public View getTopView() {
        return mTopView;
    }

    @Override
    public void setPrameter(Parameter parameter) {
        mParameter = (ParameterCurves) parameter;
        if (mTopView != null) {
            updateUI();
        }
    }

    @Override
    public void updateUI() {
        mOutput.setText(String.valueOf(mParameter.getOutputValues()));
        mInput.setText(String.valueOf(mParameter.getInputValues()));
    }
}
