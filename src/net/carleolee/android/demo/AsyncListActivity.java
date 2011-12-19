package net.carleolee.android.demo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.json.JSONObject;
import org.json.JSONArray;

public class AsyncListActivity extends Activity {

    enum TaskState {
        OK,
        TIMEOUT,
        NETWORK_ERROR,
        ERROR
    }

    static class TaskResult {
        TaskState mState;
        int mTotal;
        int mOffset;
        ArrayList<AppItem> mList;
    }

    final static String URL_PREFIX = "http://gae.carleolee.net/hotapp?t=json&c=15&o=";

    AppTask mTask = null;
    int mTotal = -1;
    ArrayList<AppItem> mList;
    AppAdapter mAdapter = null;
    ListFooter mFooter;

    volatile boolean mNetworkUp = true;
    BroadcastReceiver mNetworkStateReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_activity);

        mList = new ArrayList<AppItem>();
        mTask = null;
        mTotal = -1;

        LayoutInflater inflater = LayoutInflater.from(this);
        View footer = inflater.inflate(R.layout.footer, null, false);
        mFooter = new ListFooter(footer.findViewById(R.id.footer_content));
        ListView listview = (ListView) findViewById(R.id.list);
        listview.addFooterView(footer);
        mFooter.hide();
        mAdapter = new AppAdapter(this, mList);
        listview.setAdapter(mAdapter);
        listview.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                int last = firstVisibleItem + visibleItemCount;
                if (totalItemCount > 0 && last == totalItemCount)
                    loadMore();
            }

            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // do nothing
            }
        });

        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        mNetworkUp = (info != null && info.isAvailable());
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mNetworkStateReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    NetworkInfo info = intent.getParcelableExtra(
                            ConnectivityManager.EXTRA_NETWORK_INFO);
                    onNetworkToggle(info != null && info.isAvailable());
                }
            }
        };
        registerReceiver(mNetworkStateReceiver, filter);

        mAdapter.setNetworkStatus(mNetworkUp);
        loadMore();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAdapter != null)
            mAdapter.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null)
            mAdapter.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mAdapter != null)
            mAdapter.onDestroy();
        if (mTask != null) {
            mTask.cancel(true);
        }
        if (mNetworkStateReceiver != null) {
            unregisterReceiver(mNetworkStateReceiver);
            mNetworkStateReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.demo, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!super.onPrepareOptionsMenu(menu))
            return false;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_refresh:
            refresh();
            return true;
        case R.id.menu_test:
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("test", true);
            startActivity(i);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    void onNetworkToggle(boolean available) {
        mNetworkUp = available;
        if (mAdapter != null)
            mAdapter.setNetworkStatus(mNetworkUp);
    }

    void refresh() {
        if (mTask != null) {
            // refreshing, just return
            if (mList.size() == 0)
                return;
            mTask.cancel(true);
            mTask = null;
        }
        if (mAdapter != null)
            mAdapter.notifyDataSetChanged();

        mList.clear();
        loadMore();
    }

    void loadMore() {
        if (mTotal != -1 && mList.size() >= mTotal) {
            mFooter.hide();
            return;
        }
        if (mTask != null)
            return;

        mFooter.showLoading();
        String url = URL_PREFIX + mList.size();
        mTask = new AppTask();
        mTask.execute(url);
    }

    void showResult(TaskResult result) {
        switch (result.mState) {
        case TIMEOUT:
            if (mNetworkUp)
                mFooter.showNetworkTimeout();
            else
                mFooter.showNoConnection();
            break;
        case NETWORK_ERROR:
            if (mNetworkUp)
                mFooter.showNetworkError();
            else
                mFooter.showNoConnection();
            break;
        case OK:
            mFooter.hide();
            mTotal = result.mTotal;
            if (mTotal < 0)
                mTotal = 0;
            if (result.mList != null && result.mList.size() > 0) {
                mList.addAll(result.mList);
                mAdapter.notifyDataSetChanged();
            }
            break;
        default:
            mFooter.showError();
            break;
        }
    }

    TaskResult loadAppList(String urlstr) {
        TaskResult result = new TaskResult();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlstr);
            URLConnection connection = url.openConnection();
            if (!(connection instanceof HttpURLConnection)) {
                result.mState = TaskState.ERROR;
                return result;
            }
            conn = (HttpURLConnection) connection;
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept-Encoding", "gzip,deflate");
            conn.connect();
            InputStream in = null;
            in = new BufferedInputStream(conn.getInputStream(), 8 * 1024);
            String encoding = conn.getContentEncoding();
            if ("gzip".equalsIgnoreCase(encoding)) {
                in = new GZIPInputStream(in);
            } else if ("deflate".equalsIgnoreCase(encoding)) {
                in = new InflaterInputStream(in);
            }
            int size = 64 * 1024;
            byte[] buff = new byte[size];
            int off = 0;
            int len = size;
            while (len > 0) {
                int count = in.read(buff, off, len);
                if (count == -1) {
                    break;
                } else if (count == 0) {
                    throw new IOException("read time out");
                } else {
                    off += count;
                    len -= count;
                    if (len <= 0) {
                        throw new Exception("result too long");
                    }
                }
            }
            in.close();
            if (off == 0) {
                throw new Exception("empty result");
            }
            String data = new String(buff, 0, off, "UTF-8");
            JSONObject message = new JSONObject(data);
            String status = message.getString("result");
            if (!"ok".equals(status)) {
                result.mState = TaskState.ERROR;
                return result;
            }
            result.mTotal = message.getInt("total");
            if (result.mTotal <= 0) {
                result.mState = TaskState.OK;
                return result;
            }
            result.mOffset = message.getInt("offset");
            if (result.mOffset < 0) {
                result.mState = TaskState.ERROR;
                return result;
            }
            if (!message.isNull("data")) {
                JSONArray array = message.getJSONArray("data");
                int length = array.length();
                ArrayList<AppItem> list = new ArrayList<AppItem>(length);
                for (int i = 0; i < length; i++) {
                    JSONObject o = array.getJSONObject(i);
                    AppItem item = new AppItem();
                    item.icon = o.getString("icon");
                    item.title = o.getString("title");
                    item.dev = o.getString("dev");
                    list.add(item);
                }
                result.mList = list;
            }
            result.mState = TaskState.OK;
            return result;
        } catch (SocketTimeoutException e1) {
            result.mState = TaskState.TIMEOUT;
            return result;
        } catch (IOException e2) {
            result.mState = TaskState.NETWORK_ERROR;
            return result;
        } catch (Exception e) {
            //
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e4) {
                    // ignore
                }
            }
        }
        result.mState = TaskState.ERROR;
        return result;
    }


    class AppTask extends AsyncTask<String, Void, TaskResult> {
        @Override
        protected TaskResult doInBackground(String... params) {
            if (params.length != 1)
                throw new IllegalArgumentException("AppTask accept only one param");
            return loadAppList(params[0]);
        }

        @Override
        protected void onPostExecute(TaskResult result) {
            if (this.isCancelled() || result == null)
                return;
            if (mTask == this) {
                mTask = null;
                showResult(result);
            }
        }
    }

    class ListFooter {
        private View mView;
        private TextView mText;
        private View mLoadingView;
        private Button mRetry;

        public ListFooter(View view) {
            mView = view;
            mLoadingView = mView.findViewById(R.id.loading);
            mText = (TextView) mView.findViewById(R.id.text);
            mRetry = (Button) mView.findViewById(R.id.retry);
            mRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadMore();
                }
            });
        }

        public void hide() {
            mView.setVisibility(View.GONE);
        }

        public void showLoading() {
            mView.setVisibility(View.VISIBLE);
            mLoadingView.setVisibility(View.VISIBLE);
            mRetry.setVisibility(View.GONE);
            mText.setText(R.string.loading);
        }

        public void showNoConnection() {
            mText.setText(R.string.no_connection);
            showRetry();
        }

        public void showNetworkError() {
            mText.setText(R.string.network_error);
            showRetry();
        }

        public void showNetworkTimeout() {
            mText.setText(R.string.network_timeout);
            showRetry();
        }

        public void showError() {
            mText.setText(R.string.unknown_error);
            showRetry();
        }

        private void showRetry() {
            mView.setVisibility(View.VISIBLE);
            mLoadingView.setVisibility(View.GONE);
            mRetry.setVisibility(View.VISIBLE);
        }
    }

}
