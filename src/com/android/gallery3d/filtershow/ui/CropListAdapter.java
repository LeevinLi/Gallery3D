package com.android.gallery3d.filtershow.ui;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;

public class CropListAdapter extends BaseAdapter {
    Context mContext;
    String[] mTitles;
    int[] mResIds;
    int[] mResSelectedIds;
    LayoutInflater mInflater;
    int mSelected;

    public CropListAdapter(Context context, String[] titles) {
        mContext = context;
        mTitles = titles;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResIds = new int[]{R.drawable.ic_edit_crop_normal,
                R.drawable.ic_edit_crop_1_1,
                R.drawable.ic_edit_crop_4_3,
                R.drawable.ic_edit_crop_3_4,
                R.drawable.ic_edit_crop_7_5,
                R.drawable.ic_edit_crop_5_7};
        mResSelectedIds = new int[]{R.drawable.ic_edit_crop_normal_selected,
                R.drawable.ic_edit_crop_1_1_selected,
                R.drawable.ic_edit_crop_4_3_selected,
                R.drawable.ic_edit_crop_3_4_selected,
                R.drawable.ic_edit_crop_7_5_selected,
                R.drawable.ic_edit_crop_5_7_selected};
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
            convertView = mInflater.inflate(R.layout.crop_list_item, null);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.cropText.setText(mTitles[position]);
        if (position == mSelected) {
            holder.cropText.setTextColor(mContext.getResources().getColor(R.color.effect_list_btn_bg));
            holder.cropSize.setImageResource(mResSelectedIds[position]);
        } else {
            holder.cropText.setTextColor(mContext.getResources().getColor(R.color.white));
            holder.cropSize.setImageResource(mResIds[position]);

        }
        return convertView;
    }

    public void setSelected(int position) {
        mSelected = position;
    }

    class ViewHolder {
        final ImageView cropSize;
        final TextView cropText;

        public ViewHolder(View view) {
            cropSize = (ImageView) view.findViewById(R.id.img_crop_size);
            cropText = (TextView) view.findViewById(R.id.txt_crop_size);
        }
    }
}
