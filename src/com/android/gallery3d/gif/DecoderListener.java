package com.android.gallery3d.gif;

/**
 * Gif decoder listener
 */
public interface DecoderListener {
    public void onDecode(boolean parseStatus, int frameIndex);
}
