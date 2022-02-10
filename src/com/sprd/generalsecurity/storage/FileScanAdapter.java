package com.sprd.generalsecurity.storage;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sprd.generalsecurity.R;

public class FileScanAdapter extends BaseAdapter {
    private static final String TAG = "FileScanAdapter";

    private final int mNameIds[] = { R.string.cache_file,
            R.string.rubbish_file, R.string.invalid_file, R.string.temp_file, R.string.large_file };

    private LayoutInflater mInflater;
    private Context mContext;

    public FileScanAdapter(Context context) {
        mContext = context;
        this.mInflater = LayoutInflater.from(context);
        Log.i(TAG, "mInflater:" + mInflater);
    }

    @Override
    public int getCount() {
        return mNameIds != null ? mNameIds.length : 0;
    }

    @Override
    public Object getItem(int position) {
        return mNameIds[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();

            convertView = mInflater.inflate(R.layout.storage_type_item, null);
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progress);
            holder.img = (ImageView) convertView.findViewById(R.id.img);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.name.setText(mNameIds[position]);
        /**
         * UNISOC: Bug776770,855718,861577 when scan from FileExplorer, not show progress image
         * @{
         */
        if (((StorageManagement) mContext).getScanStatus() && holder.img.getVisibility() == View.GONE) {
            holder.progressBar.setVisibility(View.VISIBLE);
        }
        /**
         * @}
         */
        Log.i(TAG, position+"\t convertView:" + convertView);
        return convertView;
    }

    class ViewHolder {
        TextView name;
        ProgressBar progressBar;
        ImageView img;
    }
}
