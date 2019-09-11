package com.android.gallery3d.filtershow.controller;


public interface ParameterCurves extends Parameter {
    public static String sParameterType = "ParameterCurves";

    int getOutputValues();

    int getInputValues();
}
