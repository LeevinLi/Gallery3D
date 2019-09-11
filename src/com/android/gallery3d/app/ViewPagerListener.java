package com.android.gallery3d.app;


import com.android.gallery3d.data.Path;

public interface ViewPagerListener {

    public void onSelectionModeChange(int mode);

    public void onSelectionChange(Path path, boolean selected);

    public void onDataChange(int params, boolean isEmpty);
}
