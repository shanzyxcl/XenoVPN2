package com.eftabsprodns.aio.myhotspot;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.tpv.plus.R;

public class MainActivityWifi extends AppCompatActivity {
    private EditText portEditText;
    private Button start, stop, restart, hdwifi;
    private ImageView wifiTetherButton;
    private TextView proxyStatusTextView, proxyURLTextView;
    private SharedPreferences sp;
    private Toolbar tb;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setContentView(R.layout.activity_wifi);
        tb = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(tb);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        sp = getSharedPreferences("Wifi_Tethering", Context.MODE_PRIVATE);
        initializeViews();
        initializeListeners();


    }

    private void initializeListeners() {

        wifiTetherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchHotspotSettings();
            }
        });
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartapp();
            }
        });
        hdwifi.setOnClickListener(new View.OnClickListener() {

            private Builder ab;

            @Override
            public void onClick(View v) {
                ab = new Builder(MainActivityWifi.this);
                ab.setTitle(Html.fromHtml("Cách Phát Wifi Qua Proxy"));
                ab.setMessage(Html.fromHtml("</strong> " + "1. Kết Nối VPN ,Chọn Mục Phát Wifi ( Proxy ) Trên Ứng Dụng<br>2. Kích Hoạt Phát Wifi Trên Máy Hoặc Điểm Phát Sóng <br>3. Bạn Nhập Cổng Như Sau :<br>Đối Với AZZPHUC PRO ( SSH) Là <font color=#f70217>1080 , 8080</font> \nĐối Với V2Ray , V2FlyNG Là <font color=#f70217>10809</font><br>4. Bấm Bắt Đầu , Yêu Cầu Đã Kết Nối 4G VPN , Đã Bật Phát Wifi Bạn Sẽ Thấy Dòng <font color=#f70217>192.168.xx.x</font>: Cổng Đã Nhập)<br>5. Trên Máy Bắt Các Bạn Kết Nối Wifi Đó , Chọn Mục Proxy ( Ở Trạng Thái Không Có ) , Chọn Thủ Công<br>6. Nhập IP <font color=#f70217>192.x.x.x</font> ( Tên Máy Chủ , Server) , Nhập Cổng Sau Đó Lưu Và Kết Nối Lại Wifi<br>7. Nếu <font color=#f70217>Lỗi</font> , Cổng Bận Bạn Hãy Buộc Dừng App . Chúc Các Bạn Thành Công" + "</strong>"));
                ab.setPositiveButton(Html.fromHtml("Đồng Ý"), null);
                ab.create().show();
            }


        });
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });
    }

    private void start() {
        if (!portEditText.getText().toString().matches("\\d+")) {
            proxyStatusTextView.setText(getString(R.string.enter_the_port));
            proxyURLTextView.setText("");
            return;
        }
        int port = Integer.parseInt(portEditText.getText().toString());
        String ip = getIPAddress(true);
        if (!ip.trim().startsWith("192.")) {
            launchHotspotSettings();
            return;
        }
        try {
            if (!(new CheckingPortTask().execute(port).get())) {
                //proxyStatusTextView.setText(getString(R.string.busy_port));
                //proxyURLTextView.setText(getString(R.string.enter_another_port));
                restart.setVisibility(View.GONE);

            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            proxyStatusTextView.setText(getString(R.string.errors));
            proxyURLTextView.setText("");
            return;
        }
        Intent intent = new Intent(MainActivityWifi.this, ProxyService.class);
        intent.putExtra("port", port);
        sp.edit().putString("port", portEditText.getText().toString()).apply();
        startService(intent);
        proxyStatusTextView.setText(getString(R.string.proxy_is_running));
        proxyURLTextView.setText(String.format("%s:%d", getIPAddress(true), port));

        start.setVisibility(View.GONE);
        stop.setVisibility(View.VISIBLE);
        portEditText.setEnabled(false);

    }

    private void stop() {
        stopService(new Intent(MainActivityWifi.this, ProxyService.class));
        proxyStatusTextView.setText(getString(R.string.proxy_stopped));
        proxyURLTextView.setText("");
        if (isProxyServiceRunning(ProxyService.class)) {
            start.setVisibility(View.VISIBLE);
            stop.setVisibility(View.GONE);
            portEditText.setEnabled(false);
            //startandstop.setText(R.string.stop_server);
        } else {
            start.setVisibility(View.VISIBLE);
            stop.setVisibility(View.GONE);
            //startandstop.setText(R.string.start_server);
            //Toast.makeText(this, "Loading", Toast.LENGTH_SHORT).show();
        }
        portEditText.setEnabled(true);

    }

    private void initializeViews() {

        portEditText = findViewById(R.id.portEditText);
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        wifiTetherButton = findViewById(R.id.WiFiTetherButton);
        proxyStatusTextView = findViewById(R.id.proxyStatus);
        proxyURLTextView = findViewById(R.id.proxyURL);
        hdwifi = findViewById(R.id.hdwifi);
        restart = findViewById(R.id.restart);
        if (isProxyServiceRunning(ProxyService.class)) {
            start.setVisibility(View.GONE);
            stop.setVisibility(View.VISIBLE);
            portEditText.setEnabled(false);
            //startandstop.setText(R.string.stop_server);
        } else {
            start.setVisibility(View.VISIBLE);
            stop.setVisibility(View.GONE);
            //startandstop.setText(R.string.start_server);
            //Toast.makeText(this, "Loading", Toast.LENGTH_SHORT).show();
        }
        portEditText.setText(sp.getString("port", "8080"));
    }

    public void restartapp() {
        Intent intent = new Intent(this, MainActivityWifi.class);
        int i = 123456;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, i, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + ((long) 2000), pendingIntent);
        System.runFinalizersOnExit(true);
        System.exit(0);
        Process.killProcess(Process.myPid());
    }

    public void stopp() {
        stop();
        System.runFinalizersOnExit(true);
        System.exit(0);
        Process.killProcess(Process.myPid());

    }

    public String getIPAddress(boolean useIPv4) {
        try {
            boolean isIPv4;
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } // for now eat exceptions
        return "";
    }

    private void launchHotspotSettings() {
        Intent tetherSettings = new Intent();
        tetherSettings.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        startActivity(tetherSettings);
    }

    private boolean isProxyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private class CheckingPortTask extends AsyncTask<Integer, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Integer... port) {
            try {
                ServerSocket serverSocket = new ServerSocket(port[0]);
                serverSocket.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }


}
