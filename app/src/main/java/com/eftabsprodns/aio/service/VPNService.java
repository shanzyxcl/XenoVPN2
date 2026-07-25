package com.eftabsprodns.aio.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import app.tunnel.v2ray.V2RayConfigManager;
import app.tunnel.v2ray.common.V2RayConstants;
import app.tunnel.v2ray.service.ServiceControl;
import app.tunnel.v2ray.service.V2RayServiceManager;
import app.tunnel.v2ray.service.V2RayVpnService;
import app.tunnel.vpncommons.vpnstatus.ConnectionStatus;
import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.connectivity.DeviceStateReceiver;
import com.eftabsprodns.aio.thread.BackServer;
import com.eftabsprodns.aio.thread.PayloadInjector;
import com.eftabsprodns.aio.thread.SSHTunnelThread;
import com.eftabsprodns.aio.thread.UDPTunnelThread;
import com.eftabsprodns.aio.utils.FileUtils;
import com.eftabsprodns.aio.utils.SSLUtil;
import com.eftabsprodns.aio.utils.util;
import com.eftabsprodns.aio.view.StatisticGraphData;
import com.eftabsprodns.aio.core.vpnutils.TunnelUtils;
import android.net.ConnectivityManager;


public class VPNService extends Service implements Handler.Callback, SettingsConstants, TkLogStatus.StateListener, TkLogStatus.ByteCountListener {
    public static final String START_SERVICE = "mtk.all.net.com:startTunnel";
    public static final String STOP_SERVICE = "mtk.all.net.com:stopTunnel";
    public static final String RECONNECT_SERVICE = "mtk.all.net.com:reconnecTunnel";
    public static final String CHANNEL_ID = "NOTIFICATION_ID";
    public static final String NOTIFICATION_CHANNEL_BG_ID = "NOTIFICATION_CHANNEL_ID";
    private final static int RECONNECT_TRIES = 5;
    public static boolean isRunning = false;
    public static HttpsURLConnection huc;
    public static BackServer mBackServerThread;
    public static SSLSocket mSSLSocket;
    public static Socket server;
    private static ConfigUtil mConfig;
    private static boolean mDisplayBytecount = false;
    private static String SplitPayload = "";
    private final int NOTIFICATION_ID = 123;
    public SharedPreferences mPref;
    public SharedPreferences.Editor mEditor;
    public boolean mPause = false;
    int spt = 0;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager nm;
    private NotificationCompat.Builder mNotifyBuilder = null;
    private Thread mInjectThread;
    private UDPTunnelThread mUDPTunnelThread;
    //private UDPTunnelThread2 mUDPTunnelThread2;
    private boolean mStopping = false;
    private VPNService.InjectorListener InjectorListener;
    private ServerSocket ss;
    private Socket client;
    private int repeatCount = 0;
    private Handler mHandler;
    private SSHTunnelThread mSSHTunnelThread;
    private CountDownLatch mTunnelThreadStopSignal;
    private boolean isSplitPayload = false;
    private boolean mReconnecting = false;
    private int rcn = -1;
    private DeviceStateReceiver mDeviceStateReceiver;
    Runnable starTunnel = new Runnable() {
        @Override
        public void run() {
            int tries = 0;
            mTunnelThreadStopSignal = new CountDownLatch(1);
            final int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);
            boolean def = mConfig.getServerType().equals(SERVER_TYPE_OVPN) && mConfig.isDefaultOvpnTweak();
            while (!mStopping) {
                try {
                    if (!util.isNetworkAvailable(VPNService.this)) {
                        network_reconnect();
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e2) {
                            onDestroy();
                            break;
                        }
                    } else {
                        if (tries > 0) {
                            TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, getString(R.string.state_reconnecting));
                            addLogInfo("<b>" + getString(R.string.state_reconnecting) + "</b>");
                            if (tries == 100) {
                                addLogInfo("<b>Connection timeout</b>");
                                onDestroy();
                                break;
                            }
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e2) {
                            onDestroy();
                            break;
                        }
                        if (mConfig.getServerType().equals(SERVER_TYPE_DNS) || mConfig.getServerType().equals(SERVER_TYPE_SSH) && mConfig.getPayloadType() == PAYLOAD_TYPE_DIRECT_PAYLOAD || mConfig.getServerType().equals(SERVER_TYPE_SSH) && mConfig.getPayloadType() == PAYLOAD_TYPE_DIRECT) {
                            addLogInfo("<b>SSH Tunnel: </b>starting");
                            mHandler.sendEmptyMessage(1);
                            break;
                        } else if (mConfig.getServerType().equals(SERVER_TYPE_UDP_HYSTERIA_V1)) {
                            TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTING, getString(R.string.state_connecting));
                            addLogInfo("<b>UDP Tunnel: </b>starting");
                            mHandler.sendEmptyMessage(2);
                            break;
                        } else if (tunnelRadio == 4) {
                            addLogInfo("<b>v2ray Tunnel: </b>starting");
                            TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, getString(R.string.state_connecting));
                            String s = mConfig.getV2();
                            if (s == null) {
                                return;
                            }
                            String payload = FileUtils.showJson(s);
                            Log.i("V2RayConfig", payload);
                            String result = V2RayConfigManager.convertToConfig(payload, false);
                            if (result == null) {
                                // error to
                                return;
                            }
                            startService(new Intent(VPNService.this, V2RayVpnService.class).putExtra(V2RayConstants.V2RAY_CONFIG, result));

                            break;
                        } else if (def) {
                            mHandler.sendEmptyMessage(3);
                            break;
                        } else {
                            startTunnelProxy();
                            break;
                        }
                    }
                } catch (Exception e) {
                    addLogInfo(e.getMessage());
                    network_reconnect();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e2) {
                        onDestroy();
                        break;
                    }
                }
                tries++;
            }
            if (!mStopping) {
                try {
                    mTunnelThreadStopSignal.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    };

    public void setInjectorListener(InjectorListener InjectorListener) {
        this.InjectorListener = InjectorListener;
    }

    @Override
    public IBinder onBind(Intent p1) {
        return new MyBinder();
    }

    @SuppressLint("WakelockTimeout")
    private void setWakelock() {
        try {
            this.wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(1, "SocksIP::Tag");
            this.wakeLock.acquire();
        } catch (Exception e) {
            android.util.Log.d("WAKELOCK", e.getMessage());
        }
    }

    private void unsetWakelock() {
        if (this.wakeLock != null && this.wakeLock.isHeld()) {
            android.util.Log.e("WAKELOCK", "is disabled");
            this.wakeLock.release();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mPref = MyApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);
        mHandler = new Handler(this);
        mConfig = ConfigUtil.getInstance(VPNService.this);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //new util(VPNService.this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        /*if(!util.isMyApp()){
            onDestroy();
            addLogInfo("<font color = #d50000>"+new String(new byte[]{80,108,97,101,115,101,32,105,110,115,116,97,108,108,32,116,104,101,32,111,114,105,103,105,110,97,108,32,55,84,117,110,110,101,108,32,80,114,111,}));
            try {
                if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
                    ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
                } else {
                    String packageName = getApplicationContext().getPackageName();
                    Runtime runtime = Runtime.getRuntime();
                    runtime.exec("pm clear " + packageName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return START_NOT_STICKY;
        }
        else */
        if (action != null) {
            switch (action) {
                case START_SERVICE:
                    mConfig = ConfigUtil.getInstance(VPNService.this);
                    TkLogStatus.addStateListener(VPNService.this);
                    TkLogStatus.addByteCountListener(VPNService.this);
                    TkLogStatus.updateStateString(TkLogStatus.VPN_STARTING, getString(R.string.state_starting));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        start_notification();
                    }
                    isRunning = true;
                    mStopping = false;
                    registerDeviceStateReceiver();
                    mConfig.initializeMsg();
                    setWakelock();
                    TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTING, getString(R.string.state_connecting));
                    //HarlieMain.updateMainViews(this,HarlieMain.CHECK);
                    if (mInjectThread != null) {
                        mInjectThread.interrupt();
                    }
                    mInjectThread = new Thread(starTunnel);
                    mInjectThread.start();
                    break;
                case STOP_SERVICE:
                    onDestroy();
                    break;
                case RECONNECT_SERVICE:
                    mConfig = ConfigUtil.getInstance(VPNService.this);
                    if (TkLogStatus.isTunnelActive()) {
                        network_reconnect();
                    }
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);
        if (isRunning) addLogInfo("<b>" + getString(R.string.state_disconnected) + "</b>");
        isRunning = false;
        mPause = false;
        mStopping = true;
        mReconnecting = false;
        repeatCount = 0;
        spt = 0;
        UDPTunnel_handler(false);
        SSHTunnel_handler(false);
        new Thread(VPNService.this::stopAll).start();
        if (mConfig.getServerType().equals(SERVER_TYPE_OVPN)) {
            startService(new Intent(VPNService.this, OpenVPNService.class).setAction(OpenVPNService.ACTION_DISCONNECT).putExtra(OpenVPNService.INTENT_PREFIX + ".STOP", false));
        }
        if (tunnelRadio == 4) {
            SoftReference<ServiceControl> s = V2RayServiceManager.getServiceControl();
            if (s != null) {
                s.get().stopService();
            }
        }
        StatisticGraphData.getStatisticData().getDataTransferStats().stop();
        unsetWakelock();
        if (mDeviceStateReceiver != null) unregisterDeviceStateReceiver();
        new PayloadInjector().interrupt();
        if (mTunnelThreadStopSignal != null) mTunnelThreadStopSignal.countDown();
        TkLogStatus.updateStateString(TkLogStatus.VPN_DISCONNECTED, getString(R.string.state_disconnected));
        endTunnelService();
    }

    private void connectSocket(String host, int port, boolean ssl) throws Exception {
        server = new Socket();
        if (ssl) server.bind(new InetSocketAddress(0));
        server.connect(new InetSocketAddress(host, port));
        doVpnProtect(server);
    }

    private boolean connectSocket() throws Exception {
        try {
            String readLine;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            StringBuffer stringBuffer = new StringBuffer();
            while (true) {
                readLine = bufferedReader.readLine();
                if (readLine != null && readLine.length() > 0) {
                    stringBuffer.append(readLine);
                    stringBuffer.append("\r\n");
                } else {
                    break;
                }
                if (stringBuffer.toString().equals("")) {
                    addLogInfo("Get Request", "Get request data failed, empty requestline");
                    return false;
                }

                int tunnel_type = mConfig.getPayloadType();

                if (tunnel_type == PAYLOAD_TYPE_DIRECT) {
                    String[] split = stringBuffer.toString().split("\r\n");
                    String str = split[0].split(" ")[1];
                    String host = str.split(":")[0];
                    int port = Integer.parseInt(str.split(":")[1]);
                    connectSocket(host, port, false);
                    send200Status(client.getOutputStream());
                    return true;
                } else if (tunnel_type == PAYLOAD_TYPE_DIRECT_PAYLOAD) {
                    String payload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
                    String c1 = c(stringBuffer.toString(), payload);
                    if (c1 == null) {
                        return false;
                    }
                    String[] split = stringBuffer.toString().split("\r\n");
                    String str = split[0].split(" ")[1];
                    String host = str.split(":")[0];
                    int port = Integer.parseInt(str.split(":")[1]);
                    connectSocket(host, port, false);
                    if (!c1.equals("")) {
                        a(c1, server);
                    }
                    send200Status(client.getOutputStream());
                    return true;
                } else if (tunnel_type == PAYLOAD_TYPE_OVPN_UDP) {
                    String[] split = stringBuffer.toString().split("\r\n");
                    String str = split[0].split(" ")[1];
                    String host = str.split(":")[0];
                    int port = Integer.parseInt(str.split(":")[1]);
                    connectSocket(host, port, false);
                    send200Status(client.getOutputStream());
                    return true;
                } else if (tunnel_type == PAYLOAD_TYPE_HTTP_PROXY) {
                    String payload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
                    String c2 = c(stringBuffer.toString(), payload);
                    if (c2 == null) {
                        return false;
                    }
                    String proxy = mConfig.getSecureString(PROXY_IP_KEY);
                    int proxyPort = Integer.parseInt(mConfig.getSecureString(PROXY_PORT_KEY));
                    connectSocket(proxy, proxyPort, false);
                    if (!c2.equals("")) {
                        a(c2, server);
                    }
                    return true;

                } else if (tunnel_type == PAYLOAD_TYPE_SSL) {
                    String[] split = stringBuffer.toString().split("\r\n");
                    String str = split[0].split(" ")[1];
                    String host = str.split(":")[0];
                    int port = Integer.parseInt(str.split(":")[1]);
                    connectSocket(host, port, true);
                    connectSSL();
                    send200Status(client.getOutputStream());
                    return true;

                } else if (tunnel_type == PAYLOAD_TYPE_SSL_PAYLOAD) {
                    String payload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
                    String c3 = c(stringBuffer.toString(), payload);
                    if (c3 == null) {
                        return false;
                    }
                    String[] split = stringBuffer.toString().split("\r\n");
                    String str = split[0].split(" ")[1];
                    String host = str.split(":")[0];
                    int port = Integer.parseInt(str.split(":")[1]);
                    connectSocket(host, port, true);
                    connectSSL();
                    if (!c3.equals("")) {
                        a(c3, mSSLSocket);
                    }
                    return true;

                } else if (tunnel_type == PAYLOAD_TYPE_SSL_PROXY) {
                    String payload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
                    String c4 = c(stringBuffer.toString(), payload);
                    if (c4 == null) {
                        return false;
                    }
                    String proxy = mConfig.getSecureString(PROXY_IP_KEY);
                    int proxyPort = Integer.parseInt(mConfig.getSecureString(PROXY_PORT_KEY));
                    connectSocket(proxy, proxyPort, true);
                    connectSSL();
                    if (!c4.equals("")) {
                        a(c4, mSSLSocket);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            network_reconnect();
        }
        return false;
    }

    private void a(String str, Socket socket) throws Exception {
        int i = 0;
        Random g;

        OutputStream outputStream = socket.getOutputStream();
        if (str.contains("[random]")) {
            g = new Random();
            String[] split = str.split(Pattern.quote("[random]"));
            str = split[g.nextInt(split.length)];
        }
        if (str.contains("[repeat]")) {
            String[] split = str.split(Pattern.quote("[repeat]"));
            str = split[repeatCount];
            repeatCount++;
            if (repeatCount > split.length - 1) {
                repeatCount = 0;
            }
        }
        String payload = str.replace("\r\n", "\\r\\n");
        //addLogInfo(String.format("Payload: %s", ConfigUtil.hide(payload)));
        //addLogInfo(String.format("Payload: %s",payload));
        addLogInfo("Injecting");

        if (str.contains("[split_delay]")) {
            String[] split = str.split(Pattern.quote("[split_delay]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(1500);
                }
                i++;
            }
        } else if (str.contains("[split_instant]")) {
            String[] split = str.split(Pattern.quote("[split_instant]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(0);
                }
                i++;
            }
        } else if (str.contains("[instant_split]")) {
            String[] split = str.split(Pattern.quote("[instant_split]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(0);
                }
                i++;
            }
        } else if (str.contains("[delay_split]")) {
            String[] split = str.split(Pattern.quote("[delay_split]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(1500);
                }
                i++;
            }
        } else if (a(str, outputStream)) {
            outputStream.write(str.getBytes());
            outputStream.flush();
        }
    }


    private boolean a(String str, OutputStream outputStream) throws Exception {
        if (!str.contains("[split]")) {
            return true;
        }
        for (String str2 : str.split(Pattern.quote("[split]"))) {
            outputStream.write(str2.getBytes());
            outputStream.flush();
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void jbNotificationExtras(Notification.Builder nbuilder) {
        try {
            if (NotificationManager.IMPORTANCE_LOW != 0) {
                Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
                try {
                    setpriority.invoke(nbuilder, NotificationManager.IMPORTANCE_LOW);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
                Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(nbuilder, true);
            }
            //ignore exception
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException |
                 IllegalAccessException e) {
            System.out.println(e);
        }
    }

    private void connectSSL() throws Exception {
        addLogInfo("Setting up SNI...");
        SSLSocketFactory factory = new SSLUtil(this);
        //String mSni = (mConfig.getSecureString(SNI_HOST_KEY).startsWith("http")) ? mConfig.getSecureString(SNI_HOST_KEY) : "https://" + mConfig.getSecureString(SNI_HOST_KEY);
        
        String mSni = mConfig.getSecureString(SNI_HOST_KEY);
        if(mSni.contains("[host]"))
        {
        mSni = mConfig.getSecureString(SNI_HOST_KEY).replace("[host]", mConfig.getSecureString(SERVER_KEY));
        }
        
        URL url = new URL("https://" + mSni);
        mSni = url.getHost();
        if (url.getPort() > 0) {
            mSni = mSni + ":" + url.getPort();
        }
        if (!url.getPath().equals("/")) {
            mSni = mSni + url.getPath();
        }
        //addLogInfo("SNI Setup:", mSni);
        //addLogInfo("SNI Host", ConfigUtil.hide(mSni));
        huc = (HttpsURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, mBackServerThread.getLocalSocketAddr()));
        this.huc.setHostnameVerifier((str, sSLSession) -> true);
        huc.setSSLSocketFactory(factory);
        huc.connect();
    }

    private String c(String str, String payload) {
        String str2 = null;
        if (str != null) {
            try {
                if (!str.equals("")) {
                    String charSequence = str.split("\r\n")[0];
                    String[] split = charSequence.split(" ");
                    String[] split2 = split[1].split(":");
                    String host = split2[0];
                    String port = split2[1];
                    String mHost = mConfig.getSecureString(PROXY_IP_KEY);
                    String iHost = mConfig.getSecureString(SERVER_KEY);
                    str2 = d(payload.replace("[rlb]", mHost).replace("[xeno]", iHost).replace("[ua]", ua()).replace("[real_raw]", str).replace("[raw]", charSequence).replace("[method]", split[0]).replace("[host_port]", split[1]).replace("[host]", host).replace("[port]", port).replace("[protocol]", split[2]).replace("[cr]", "\r").replace("[lf]", "\n").replace("[crlf]", "\r\n").replace("[lfcr]", "\n\r").replace("\\r", "\r").replace("\\n", "\n"));
                    return str2;
                }
            } catch (Exception e) {
                addLogInfo("Payload Error", e.toString());
            }
        }
        addLogInfo("Payload Error", "Payload is null or empty");
        return str2;
    }

    private String d(String str) {
        if (str.contains("[cr*")) {
            str = a(str, "[cr*", "\r");
        }
        if (str.contains("[lf*")) {
            str = a(str, "[lf*", "\n");
        }
        if (str.contains("[crlf*")) {
            str = a(str, "[crlf*", "\r\n");
        }
        return str.contains("[lfcr*") ? a(str, "[lfcr*", "\n\r") : str;
    }

    private String a(String str, String str2, String str3) {
        while (str.contains(str2)) {
            Matcher matcher = Pattern.compile("\\[.*?\\*(.*?[0-9])\\]").matcher(str);
            if (matcher.find()) {
                int intValue = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
                CharSequence charSequence = "";
                for (int i = 0; i < intValue; i++) {
                    charSequence = charSequence + str3;
                }
                str = str.replace(str2 + intValue + "]", charSequence);
            }
        }
        return str;
    }

    private String ua() {
        String property = System.getProperty("http.agent");
        return property == null ? "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36" : property;
    }

    private void send200Status(OutputStream output) throws Exception {
        output.write("HTTP/1.0 200 Connection Established\r\n\r\n".getBytes());
        output.flush();
    }

    @Override
    public boolean handleMessage(Message p1) {
        switch (p1.what) {
            case 1:
                new Thread(VPNService.this::startClienteSSH).start();
                break;
            case 2:
                new Thread(VPNService.this::startUDPTunnel).start();
                break;
            case 3:
                startConnect();
                break;
            /*case 4:
                new Thread(VPNService.this::startUDPTunnel2).start();
                break;*/
        }
        return true;
    }

    public void startTunnelProxy() {
        new Thread(() -> {
            int prxAdrss = Integer.parseInt(mConfig.getProxyAddress().split(":")[1]);
            boolean autoReplace = mConfig.getAutoReplace();
            int mTunnelType = mConfig.getPayloadType();
            addLogInfo("Starting Injector Thread");
            try {
                ss = new ServerSocket(prxAdrss);
                if (mTunnelType == PAYLOAD_TYPE_SSL || mTunnelType == PAYLOAD_TYPE_SSL_PAYLOAD || mTunnelType == PAYLOAD_TYPE_SSL_PROXY) {
                    try {
                        mBackServerThread.Stop();
                    } catch (Exception ignored) {
                    }
                    mBackServerThread = new BackServer();
                    mBackServerThread.start();
                }
                mHandler.sendEmptyMessage(3);
                while (isRunning) {
                    client = ss.accept();
                    if (client != null && !client.isClosed() && connectSocket()) {
                        client.setKeepAlive(true);
                        if (mSSLSocket != null && mSSLSocket.isConnected()) {
                            mSSLSocket.setKeepAlive(true);
                            server.setKeepAlive(true);
                            doVpnProtect(mSSLSocket);
                            PayloadInjector.connect(client, mSSLSocket, autoReplace);
                        } else if (server != null && server.isConnected()) {
                            server.setKeepAlive(true);
                            doVpnProtect(server);
                            PayloadInjector.connect(client, server, autoReplace);
                        }
                    }
                }
            } catch (Exception e) {
                if (e.getMessage().contains("bind failed")) {
                    addLogInfo(e.getMessage());
                    onDestroy();
                    return;
                }
                addLogInfo("<b>Injector: </b>" + e.getMessage());
                network_reconnect();
            }
        }).start();
    }

    private void startConnect() {
        if (mConfig.getServerType().equals(SERVER_TYPE_OVPN)) {
            addLogInfo("<b>OVPN Tunnel: </b>starting");
            InjectorListener.startOpenVPN();
        } else {
            addLogInfo("<b>SSH Tunnel: </b>starting");
            new Thread(VPNService.this::startClienteSSH).start();
        }
    }

    private void doVpnProtect(Socket socket) {
        boolean isOVPN = mConfig.getServerType().equals(SERVER_TYPE_OVPN);
        if (isOVPN)
            new OpenVPNService().protect(socket);
        else
            new SSHTunnelService().protect(socket);
    }

    public void network_reconnect() {
        new Thread(() -> {
            if (isRunning) {
                mReconnect();
            }
        }).start();
    }

    private void mReconnect() {
        final int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);
        if (mStopping | !isRunning | mReconnecting) {
            return;
        }
        UDPTunnel_handler(false);
        SSHTunnel_handler(false);
        if (tunnelRadio == 4) {
            startService(new Intent(VPNService.this, V2RayVpnService.class).setAction("STOP_SERVICE"));
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            mReconnecting = false;
            return;
        }

        for (int i = 0; i < RECONNECT_TRIES; i++) {
            if (mStopping) {
                mReconnecting = false;
                return;
            }
            int sleepTime = 5;

            rcn++;
            if (!util.isNetworkAvailable(VPNService.this)) {
                mPause = true;
                if (rcn == 0) {
                    if (TkLogStatus.isTunnelActive())
                        TkLogStatus.updateStateString(TkLogStatus.VPN_PAUSE, getString(R.string.state_pause));
                    else
                        TkLogStatus.updateStateString(TkLogStatus.VPN_NO_NETWORK, getString(R.string.state_pause));
                }
            } else {
                sleepTime = 3;
                rcn = 0;
                if (!mConfig.getServerType().equals(SERVER_TYPE_OVPN)) {
                    if (!mPause)
                        TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, getString(R.string.state_reconnecting));
                    if (mPause) {
                        TkLogStatus.updateStateString(TkLogStatus.VPN_RESUME, getString(R.string.state_resume));
                        mPause = false;
                    }
                }
                try {
                    addLogInfo("<b>" + getString(R.string.state_reconnecting) + "</b>");
                    if (mConfig.getServerType().equals(SERVER_TYPE_OVPN)) {
                        startService(new Intent(VPNService.this, OpenVPNService.class).setAction(OpenVPNService.ACTION_RECONNECT));
                        mReconnecting = false;
                        mPause = false;
                        return;
                    }
                    if (mConfig.getServerType().equals(SERVER_TYPE_UDP_HYSTERIA_V1)) {
                        new Thread(VPNService.this::startUDPTunnel).start();
                        mReconnecting = false;
                        mPause = false;
                        return;
                    }
                    if (tunnelRadio == 4) {
                        startService(new Intent(VPNService.this, V2RayVpnService.class).setAction("RESTART_SERVICE"));
                        mReconnecting = false;
                        mPause = false;
                        return;
                    }
                    if (mConfig.getServerType().equals(SERVER_TYPE_SSH) || mConfig.getServerType().equals(SERVER_TYPE_DNS)) {
                        if (mSSHTunnelThread != null) mSSHTunnelThread.reconnectSSH();
                        mReconnecting = false;
                        mPause = false;
                        return;
                    }
                } catch (Exception e) {
                    addLogInfo("<b>" + getString(R.string.state_disconnected) + "</b>");
                }
            }
            try {
                Thread.sleep(sleepTime * 1000);
                i--;
            } catch (InterruptedException e2) {
                mReconnecting = false;
                return;
            }
        }
        mReconnecting = false;
        onDestroy();
    }

    private void stopAll() {
        try {
            if (ss != null) {
                ss.close();
                ss = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (server != null) {
                server.close();
                server = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (mSSLSocket != null) {
                mSSLSocket.close();
                mSSLSocket = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (huc != null) {
                huc.disconnect();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mBackServerThread != null) {
                mBackServerThread.Stop();
            }
        } catch (Exception ignored) {
        }
        if (mSSHTunnelThread != null) {
            mSSHTunnelThread.interrupt();
            mSSHTunnelThread = null;
        }
        if (mUDPTunnelThread != null) {
            mUDPTunnelThread.interrupt();
            mUDPTunnelThread = null;
        }
        /*if (mUDPTunnelThread2 != null) {
            mUDPTunnelThread2.interrupt();
            mUDPTunnelThread2 = null;
        }*/
        if (mInjectThread != null) {
            mInjectThread.interrupt();
        }
    }

    private void startClienteSSH() {
        if (mSSHTunnelThread != null) {
            mSSHTunnelThread.interrupt();
            mSSHTunnelThread = null;
        }
        mSSHTunnelThread = new SSHTunnelThread(VPNService.this);
        mSSHTunnelThread.setOnTun2SocksListener(() -> {
            if (mConfig.getServerType().equals(SERVER_TYPE_SSH) || mConfig.getServerType().equals(SERVER_TYPE_DNS)) {
                onDestroy();
            }
        });
        mSSHTunnelThread.start();
    }

    private void startUDPTunnel() {
        if (mUDPTunnelThread != null) {
            mUDPTunnelThread.interrupt();
            mUDPTunnelThread = null;
        }
        mUDPTunnelThread = new UDPTunnelThread(this);
        mUDPTunnelThread.setOnTun2SocksListener(new UDPTunnelThread.OnTun2SocksListener() {
            @Override
            public void onConnected() {
                UDPTunnel_handler(true);
            }

            @Override
            public void onReconnect() {
                network_reconnect();
            }

            @Override
            public void onStop() {
                UDPTunnel_handler(false);
                if (mConfig.getServerType().equals(SERVER_TYPE_UDP_HYSTERIA_V1)) {
                    onDestroy();
                }
            }
        });
        mUDPTunnelThread.start();
    }

    /*private void startUDPTunnel2() {
        if (mUDPTunnelThread2 != null) {
            mUDPTunnelThread2.interrupt();
            mUDPTunnelThread2 = null;
        }
        mUDPTunnelThread2 = new UDPTunnelThread2(this);
        mUDPTunnelThread2.setOnTun2SocksListener(new UDPTunnelThread2.OnTun2SocksListener() {
            @Override
            public void onConnected() {
                UDPTunnel_handler(true);
            }
            @Override
            public void onReconnect() {
                network_reconnect();
            }
            @Override
            public void onStop() {
                UDPTunnel_handler(false);
                if(mConfig.getServerType().equals(SERVER_TYPE_UDP_HYSTERIA_V2)){
                    onDestroy();
                }
            }
        });
        mUDPTunnelThread2.start();
    }*/
    public void UDPTunnel_handler(boolean on) {
        try {
            Intent intent = new Intent(VPNService.this, UDPTunnelService.class);
            if (on) {
                startService(intent.setAction(UDPTunnelService.START_UDP_SERVICE));
            } else {
                if (UDPTunnelService.isUDPRunning)
                    startService(intent.setAction(UDPTunnelService.STOP_UDP_SERVICE));
            }
        } catch (Exception e) {
            addLogInfo("<font color = #d50000>Something wen't wrong in UDP Tunnel VPNService.");
        }
    }

    public void SSHTunnel_handler(boolean on) {
        try {
            Intent intent = new Intent(VPNService.this, SSHTunnelService.class);
            if (on) {
                startService(intent.setAction(SSHTunnelService.START_SSH_SERVICE));
            } else {
                if (SSHTunnelService.isSSHRunning)
                    startService(intent.setAction(SSHTunnelService.STOP_SSH_SERVICE));
            }
        } catch (Exception e) {
            addLogInfo("<font color = #d50000>Something wen't wrong in SSH Tunnel VPNService.");
        }
    }

    @Override
    @SuppressLint("StringFormatMatches")
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (mDisplayBytecount) {
            String netstat = String.format(getString(R.string.statusline_bytecount,
                    ConfigUtil.render_bandwidth(in, false),
                    ConfigUtil.render_bandwidth(diffIn, true),
                    ConfigUtil.render_bandwidth(out, false),
                    ConfigUtil.render_bandwidth(diffOut, true)));
            update_notification_event(netstat, ConnectionStatus.LEVEL_CONNECTED);
        }
    }

	
    
	
	
    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level, int progress) {
        String stateMsg = getString(TkLogStatus.getLocalizedState(TkLogStatus.getLastState()));
        mDisplayBytecount = level.equals(ConnectionStatus.LEVEL_CONNECTED);
        if (mDisplayBytecount && isSplitPayload && spt == 0) {
            mConfig.setConnectedSpitPayload(SplitPayload);
            spt = 1;
        }
        update_notification_event(stateMsg, level);
    }

    @SuppressLint("ForegroundServiceType")
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void start_notification() {
        this.mNotifyBuilder = new NotificationCompat.Builder(VPNService.this, NOTIFICATION_CHANNEL_BG_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_BG_ID, getString(R.string.channel_name_background), importance);
            notificationChannel.setDescription(getString(R.string.channel_description_background));
            nm.createNotificationChannel(notificationChannel);
            mNotifyBuilder.setChannelId(NOTIFICATION_CHANNEL_BG_ID);
        }
        this.mNotifyBuilder.setContentIntent(ConfigUtil.getPendingIntent(this)).
                setSmallIcon(R.drawable.ic_cloud_off).
                setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon_main)).
                setContentTitle(mConfig.getServerName() + " • " + mConfig.getPayloadName()).
                setContentText("Status: " + getString(R.string.state_connecting)).
                setOnlyAlertOnce(true).
                setOngoing(true).
                setWhen(new Date().getTime()).
                setPriority(NotificationCompat.PRIORITY_DEFAULT);
        addVpnActionsToNotification(mNotifyBuilder);
        jbNotificationExtras(NotificationManager.IMPORTANCE_LOW, mNotifyBuilder);
        lpNotificationExtras(mNotifyBuilder, Notification.CATEGORY_SERVICE);
        nm.notify(NOTIFICATION_ID, mNotifyBuilder.build());
        startForeground(NOTIFICATION_ID, mNotifyBuilder.getNotification());
    }

    @SuppressLint("WrongConstant")
    private void addVpnActionsToNotification(NotificationCompat.Builder nbuilder) {
        final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 268435456;
        PendingIntent reconnectSSHService = PendingIntent.getService(VPNService.this, 0, new Intent(VPNService.this, VPNService.class).setAction(RECONNECT_SERVICE), flags);
        PendingIntent disconnectSSHService = PendingIntent.getService(VPNService.this, 0, new Intent(VPNService.this, VPNService.class).setAction(STOP_SERVICE), flags);
        nbuilder.addAction(R.drawable.ic_recon, "Reconnect", reconnectSSHService);
        nbuilder.addAction(R.drawable.ic_cloud_off, "Disconnect", disconnectSSHService);
    }

    @SuppressLint("ForegroundServiceType")
    private void update_notification_event(String str, ConnectionStatus status) {
        int icon = getIconByConnectionStatus(status);
        if (this.mNotifyBuilder != null) {
            if (str.contains(getString(R.string.state_connected))) {
                this.mNotifyBuilder.setTicker(getString(R.string.state_connected));
            }
            this.mNotifyBuilder.setSmallIcon(icon);
            this.mNotifyBuilder.setContentTitle(mConfig.getServerName() +" : "+ mConfig.getPayloadName());
            this.mNotifyBuilder.setContentText((mDisplayBytecount) ? str : "Status: " + str);
            nm.notify(NOTIFICATION_ID, mNotifyBuilder.build());
            startForeground(NOTIFICATION_ID, this.mNotifyBuilder.getNotification());
        }
    }

    private void lpNotificationExtras(NotificationCompat.Builder nbuilder, String category) {
        nbuilder.setCategory(category);
        nbuilder.setLocalOnly(true);
    }

    private void jbNotificationExtras(int priority, NotificationCompat.Builder nbuilder) {
        try {
            if (priority != 0) {
                Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
                setpriority.invoke(nbuilder, priority);
                Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(nbuilder, true);
            }
        } catch (NoSuchMethodException | IllegalArgumentException |
                 InvocationTargetException | IllegalAccessException e) {
            addLogInfo(e.getMessage());
        }
    }

    private void endTunnelService() {
        new Thread(() -> {
            stopForeground(true);
            nm.cancel(NOTIFICATION_ID);
            TkLogStatus.removeStateListener(VPNService.this);
            TkLogStatus.removeByteCountListener(VPNService.this);
        }).start();
    }

    private int getIconByConnectionStatus(ConnectionStatus level) {
        switch (level) {
            case LEVEL_CONNECTED:
                return R.drawable.ic_cloud_on;
            case LEVEL_AUTH_FAILED:
            case LEVEL_NONETWORK:
            case LEVEL_NOTCONNECTED:
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
            case LEVEL_CONNECTING_SERVER_REPLIED:
            case UNKNOWN_LEVEL:
            default:
                return R.drawable.ic_cloud_off;
        }
    }

    private void addLogInfo(String tag, String msg) {
        addLogInfo(String.format("%s: %s", tag, msg));
    }

    public void addLogInfo(String msg) {
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        if (!msg.contains("Socket closed") || !msg.contains(hst) || !msg.contains(prx)) {
            TkLogStatus.logInfo(msg.trim().replace("java.io.IOException:", ""));
        }
    }

    private void registerDeviceStateReceiver() {
        mDeviceStateReceiver = new DeviceStateReceiver(VPNService.this);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        mDeviceStateReceiver.register();
    }

    private void unregisterDeviceStateReceiver() {
        mDeviceStateReceiver.unregister();
    }

    public interface InjectorListener {
        void startOpenVPN();
    }

    public class MyBinder extends Binder {
        public VPNService getService() {
            return VPNService.this;
        }
    }


}
