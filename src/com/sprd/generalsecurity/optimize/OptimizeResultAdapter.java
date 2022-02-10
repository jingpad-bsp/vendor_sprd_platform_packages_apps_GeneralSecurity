package com.sprd.generalsecurity.optimize;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.TeleUtils;

import java.util.ArrayList;
import java.util.List;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;


public class OptimizeResultAdapter extends BaseAdapter {

    private LayoutInflater mInflater;
    private final int mNameIds[] = {R.string.rubbish_file_scan, R.string.running_process,
            R.string.data_control};
    private final int mIconIds[] = {R.drawable.garbage_optimize, R.drawable.cache_optimize,
            R.drawable.data_optimize};
    private List<EntryItem> mItemData = new ArrayList<EntryItem>();
    private Context mContext;

    public OptimizeResultAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mItemData = getItemData(mNameIds, mIconIds);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ResultViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.optimize_result_list_item, null);
            holder = new ResultViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.description = (TextView) convertView.findViewById(R.id.description);
            holder.progress = (ProgressBar) convertView.findViewById(R.id.progress);
            holder.done = (ImageView) convertView.findViewById(R.id.done);
            holder.set = (ImageView) convertView.findViewById(R.id.set);
            convertView.setTag(holder);
        } else {
            holder = (ResultViewHolder) convertView.getTag();
        }
        holder.icon.setImageDrawable(mContext.getResources().getDrawable(mItemData.get(position).icon));
        holder.name.setText(mItemData.get(position).name);
        holder.description.setText(R.string.prepare_optimize);

        if (TeleUtils.getSimCount(mContext) == 0 && position == 2) {
            convertView.setVisibility(View.GONE);
        } else {
            convertView.setVisibility(View.VISIBLE);
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return mItemData.size();
    }

    @Override
    public Object getItem(int position) {
        return mItemData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private List<EntryItem> getItemData(int[] nameIds, int[] iconIds) {
        List<EntryItem> listItem = new ArrayList<EntryItem>();
        for (int i = 0; i < nameIds.length; i++) {
            EntryItem item = new EntryItem();
            item.icon = iconIds[i];
            item.name = nameIds[i];
            listItem.add(item);
        }
        return listItem;
    }

    class EntryItem {
        int icon;
        int name;
    }

    class ResultViewHolder {
        ImageView icon;
        TextView name;
        TextView description;
        ProgressBar progress;
        ImageView done;
        ImageView set;
    }
}