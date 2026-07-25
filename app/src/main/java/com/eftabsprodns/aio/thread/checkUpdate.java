package com.eftabsprodns.aio.thread;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class checkUpdate {

    private Context context;
    private Listener listener;
    private String URL_JSON;
    private ProgressDialog pd;

    public checkUpdate(Context mContext, String mUrl, Listener listener) {
        this.context = mContext;
        this.listener = listener;
        this.URL_JSON = mUrl;
        pd = new ProgressDialog(context);
        pd.setMessage("Checking Please Wait...");
        pd.setCancelable(true);
        if (pd.isShowing()) pd.dismiss();
    }

    public void start(boolean isShow) {
        if (isShow) {
            pd.show();
        }
        new FetchJSON().execute();
    }

    public interface Listener {
        void onCompleted(String config);

        void onError(String ex);
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchJSON extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(URL_JSON).build();
                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (Exception e) {
                TkLogStatus.logDebug(e.getMessage());
                return "error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            pd.dismiss();
            if (result != null) {
                if (result.startsWith("error")) {
                    listener.onError(result);
                } else
                    listener.onCompleted(result);
            }
        }
    }

}

