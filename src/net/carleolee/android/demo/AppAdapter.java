package net.carleolee.android.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import net.carleolee.android.util.IconAdapter;

public class AppAdapter extends IconAdapter<String, Void> {

    LayoutInflater mInflater;
    ArrayList<AppItem> mList;
    final String mCacheDir;

    public AppAdapter(Context context, ArrayList list) {
        // load local image async; show default icon when loading
        super(true, R.drawable.default_icon, 0);
        mInflater = LayoutInflater.from(context);
        mList = list;
        mCacheDir = MiscUtils.getCacheDir(context);
    }

    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int position) {
        if (position >= 0 && position < mList.size())
            return mList.get(position);
        else
            return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.list_row, parent, false);
            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.dev = (TextView) convertView.findViewById(R.id.dev);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        AppItem item = mList.get(position);
        holder.title.setText(item.title);
        holder.dev.setText(item.dev);
        String url = item.icon;
        if (url != null && url.length() == 0)
            url = null;
        if (item.urlhash == null)
            item.urlhash = MiscUtils.md5Hex(url);

        bindImage(item.urlhash, url, null, holder.icon);

        return convertView;
    }

    @Override
    protected Bitmap loadImageLocal(String urlhash, String url, Void extra) {
        return MiscUtils.loadIcon(mCacheDir, urlhash);
    }

    @Override
    protected Bitmap loadImageRemote(String urlhash, String url, Void extra) {
        try {
            int maxSize = 50 * 1024;
            byte[] buff = new byte[maxSize];
            int n = MiscUtils.downloadIcon(url, buff, maxSize);
            if (n <= 0)
                return null;
            Bitmap bm = BitmapFactory.decodeByteArray(buff, 0, n);
            if (bm != null)
                MiscUtils.saveIcon(buff, n, mCacheDir, urlhash);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    static class ViewHolder {
        public ImageView icon;
        public TextView title;
        public TextView dev;
    }
}
