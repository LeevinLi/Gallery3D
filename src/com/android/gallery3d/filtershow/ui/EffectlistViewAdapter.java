package com.android.gallery3d.filtershow.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorChanSat;
import com.android.gallery3d.filtershow.editors.EditorDraw;


public class EffectlistViewAdapter extends BaseAdapter {
    Context mContext;
    String[] mTitles;
    LayoutInflater mInflater;
    int mEditorID;
    int mSelected;

    public EffectlistViewAdapter(Context context, String[] titles) {
        mContext = context;
        mTitles = titles;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public EffectlistViewAdapter(Context context, String[] titles, int editorID) {
        mContext = context;
        mTitles = titles;
        mEditorID = editorID;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (EditorDraw.ID == mEditorID) {
            mSelected = 2;
        }
    }

    @Override
    public int getCount() {
        return mTitles == null ? null : mTitles.length;
    }

    @Override
    public Object getItem(int position) {
        return mTitles[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.effect_list_item, null);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.mButton.setText(mTitles[position]);
        if (position == mSelected) {
            holder.mButton.setBackgroundResource(R.drawable.filtershow_effect_button_background);
        } else {
            holder.mButton.setBackgroundColor(mContext.getResources().getColor(R.color.transparent));
        }
        return convertView;
    }

    public void selected(int position) {
        mSelected = position;
    }

    class ViewHolder {
        final Button mButton;

        public ViewHolder(View view) {
            mButton = (Button) view.findViewById(R.id.effect_button);
            if (EditorChanSat.ID == mEditorID) {
                ViewGroup.LayoutParams lp = mButton.getLayoutParams();
                lp.width = mContext.getResources().getDimensionPixelOffset(R.dimen.effect_chansat_button_width);
                mButton.setLayoutParams(lp);
            }
        }
    }
}
