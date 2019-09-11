package com.android.gallery3d.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesUtils {


    public static final int TYPE_INT = 0;
    public static final int TYPE_LONG = 1;
    public static final int TYPE_STRING = 2;
    public static final int TYPE_FLOAT = 3;
    public static final int TYPE_BOOLEAN = 4;
    private static final String PREF_NAME = "gallery.pref";

    public static void setParam(Context context, int type, String key, Object value) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        switch (type) {
            case TYPE_INT:
                editor.putInt(key, (Integer) value);
                break;
            case TYPE_STRING:
                editor.putString(key, (String) value);
                break;
            case TYPE_LONG:
                editor.putLong(key, (Long) value);
                break;
            case TYPE_BOOLEAN:
                editor.putBoolean(key, (Boolean) value);
                break;
            case TYPE_FLOAT:
                editor.putFloat(key, (Float) value);
                break;
        }
        editor.commit();
    }

    public static Object getParam(Context context, int type, String key, Object defaultValue) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        switch (type) {
            case TYPE_INT:
                pref.getInt(key, (Integer) defaultValue);
                break;
            case TYPE_STRING:
                pref.getString(key, (String) defaultValue);
                break;
            case TYPE_LONG:
                pref.getLong(key, (Long) defaultValue);
                break;
            case TYPE_BOOLEAN:
                pref.getBoolean(key, (Boolean) defaultValue);
                break;
            case TYPE_FLOAT:
                pref.getFloat(key, (Float) defaultValue);
                break;
        }
        return null;
    }
}
