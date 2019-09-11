package com.android.gallery3d.data;

import com.android.gallery3d.app.ActivityState;

public class ActionTab {
    private String text;
    private Class state;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTextColor() {

    }

    public Class getFragment() {
        return state;
    }

    public void setFragment(Class state) {
        this.state = state;
    }

    /**
     * @param string
     * @param state
     */
    public ActionTab(String string, Class state) {
        this.text = string;
        this.state = state;
    }
}
