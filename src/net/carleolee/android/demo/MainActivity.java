package net.carleolee.android.demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.AsyncTask;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    ProgressDialog mProgressDlg = null;
    String mCacheDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCacheDir = MiscUtils.getCacheDir(this);

        setContentView(R.layout.main);
        Button b = (Button) findViewById(R.id.clear_btn);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearCache();
            }
        });
        Intent intent = getIntent();
        boolean test = intent.getBooleanExtra("test", false);
        b = (Button) findViewById(R.id.list_btn);
        if (test) {
            setTitle(R.string.title_test);
            b.setVisibility(View.GONE);
            return;
        }

        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showIconList();
            }
        });

    }

    void showIconList() {
        Intent intent = new Intent(this, AsyncListActivity.class);
        startActivity(intent);
    }

    void clearCache() {
        if (mProgressDlg == null) {
            mProgressDlg = new ProgressDialog(this);
            mProgressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDlg.setMax(100);
            mProgressDlg.setCancelable(false);
            mProgressDlg.setMessage(getString(R.string.wait));
        }
        mProgressDlg.show();
        MyTask task = new MyTask();
        task.execute(mCacheDir);
    }

    class MyTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            if (params.length != 1)
                return null;
            String dir = params[0];
            MiscUtils.clearCache(dir);
            return null;
        }

        protected void onPostExecute(Void result) {
            if (mProgressDlg != null)
                mProgressDlg.dismiss();
        }
    }

}
