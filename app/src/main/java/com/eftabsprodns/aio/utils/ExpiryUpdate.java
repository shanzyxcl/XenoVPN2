package com.eftabsprodns.aio.utils;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ExpiryUpdate {
    public static class ExpiryTknetwork extends AsyncTask<String, String, String> {
        private String url;
        private ExpireDateListener listener;

        public ExpiryTknetwork() {
        }

        public void setURL(String url) {
            this.url = url;
        }

        public void setExpireDateListener(ExpireDateListener OnUpdateListener) {
            listener = OnUpdateListener;
        }

        public void start() {
            try {
                execute(url);
            } catch (Exception ignored) {

            }
        }

        @Override
        protected String doInBackground(String[] s) {
            try {

                String link = s[0];

                OkHttpClient.Builder builder = new OkHttpClient.Builder();
                builder.connectTimeout(60, TimeUnit.SECONDS);
                builder.readTimeout(60, TimeUnit.SECONDS);
                builder.writeTimeout(60, TimeUnit.SECONDS);

                OkHttpClient client = builder.build();

                Request request = new Request.Builder()
                        .url(link)
                        .build();

                Response response = client.newCall(request).execute();

                return Objects.requireNonNull(response.body()).string();
            } catch (Exception e) {
                e.printStackTrace();
                return "Error: " + e.getMessage();
            }

        }

        @Override
        protected void onPostExecute(String result) {
            if (result.startsWith("Error")) {
                listener.onError(result);
            } else {

                if (result.startsWith("error")) {
                    listener.onError(result);
                } else {
                    try {
                        JSONObject jsonObject = new JSONObject(result);
                        String expiry = jsonObject.getString("expiry");
                        Object value = jsonObject.get("device_match");
                        if (value instanceof Boolean) {
                            boolean b = jsonObject.getBoolean("device_match");
                            if (b) {
                                listener.onExpireDate(jsonObject.getString("expiry"));
                            } else {
                                listener.onDeviceNotMatch("This pin is use in another device");
                            }
                        } else {
                            String device = jsonObject.getString("device_match");
                            if (expiry.equals("none") && device.equals("none")) {
                                listener.onAuthFailed("Authentication Failed");
                            }
                        }
                    } catch (Exception e) {
                        listener.onError("Expire Date: " + e.getMessage());
                    }
                }
            }
            super.onPostExecute(result);
        }

        public interface ExpireDateListener {
            void onExpireDate(String expire_date);


            void onDeviceNotMatch(String s);


            void onAuthFailed(String authenticationFailed);

            void onError(String error);
        }
    }
}
