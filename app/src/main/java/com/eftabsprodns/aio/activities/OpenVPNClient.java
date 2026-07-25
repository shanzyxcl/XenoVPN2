package com.eftabsprodns.aio.activities;

import static app.tunnel.vpncommons.TkLogStatus.CoreAppUtils.humanReadableByteCount;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigDataBase;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.core.ConfigParser;
import com.eftabsprodns.aio.core.*;
import com.eftabsprodns.aio.core.vpnutils.TunnelUtils;
import com.eftabsprodns.aio.logger.VPNLogs;
import com.eftabsprodns.aio.myhotspot.MainActivityWifi;
import com.eftabsprodns.aio.service.OpenVPNService;
import com.eftabsprodns.aio.service.VPNService;
import com.eftabsprodns.aio.thread.checkUpdate;
import com.eftabsprodns.aio.utils.ExpiryUpdate;
import com.eftabsprodns.aio.utils.FileUtils;
import com.eftabsprodns.aio.utils.GoogleMobileAdsConsentManager;
import com.eftabsprodns.aio.utils.RetrieveData;
import com.eftabsprodns.aio.utils.util;

import android.text.Html;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import com.eftabsprodns.aio.view.StatisticGraphData;
import com.github.mikephil.charting.charts.LineChart;

import com.google.android.material.navigation.NavigationView;

import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.tasks.OnSuccessListener;
import com.google.android.play.core.appupdate.AppUpdateInfo;

import com.google.android.material.snackbar.Snackbar;

import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentDebugSettings;
import android.widget.FrameLayout;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import net.openvpn.openvpn.PasswordUtil;
import net.openvpn.openvpn.PrefUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.content.SharedPreferences;
import app.tunnel.v2ray.service.ServiceControl;
import app.tunnel.v2ray.service.V2RayServiceManager;
import app.tunnel.vpncommons.vpnstatus.ConnectionStatus;
import app.tunnel.vpncommons.vpnstatus.TkLogStatus;

import android.content.Context;
import android.app.Activity;
import android.app.ActivityManager;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentDebugSettings;
import android.widget.LinearLayout;


import android.view.ViewGroup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import android.app.Dialog;
import java.util.Random;
import com.eftabsprodns.aio.thread.SSHTunnelThread;


public class OpenVPNClient extends OpenVPNClientBase implements TkLogStatus.StateListener, /*ExpiryUpdate.ExpiryTknetwork.ExpireDateListener,*/ NavigationView.OnNavigationItemSelectedListener, SettingsConstants, TkLogStatus.ByteCountListener, OnClickListener {

    private static final int START_BIND_CALLED = 1;
    private static final int REQUEST_IMPORT_FILE = 2;
    private static boolean isConnected = false;
    private static long m_SentBytes = 0;
    private static long m_ReceivedBytes = 0;
    private final Handler stats_timer_handler = new Handler();
    private Button btn_connector,btn_connector2;
    // Config fields (ganti spinner server/tweak)
    private EditText etServerHost, etServerPort;
    private EditText etUsername, etPassword;
    private EditText etPayload, etSslPayload;
    private EditText etSni;
    private EditText etProxyIp, etProxyPort;
    private Spinner spinnerPayloadType;
    private TextView byteIn_view, status_view, Config_vers;
    
	
    private ConfigUtil configUtil;;
    private util mUtils;
    private Handler mHandler;
    private AlertDialog cBuiler;

    private SwitchCompat dnsforward, udpforward;

	
	private TextView servidorAtual, conexaoAtual;
    private String TAG = "OpenVPNClient";
    private PrefUtil prefs;
    
    private DrawerLayout drawer;
    private View myView;
    private boolean isdown;
    private PasswordUtil pwds;

    
    private RadioGroup mTunnelType;
    private final Runnable stats_timer_task = new Runnable() {

        public void run() {
            if (TkLogStatus.isTunnelActive()) {
             //   duration_view.setText(upDateBytes.isConnected() ? upDateBytes.elapsedTimeToDisplay(upDateBytes.getElapsedTime()) : "00h:00m:00s");
            }
            OpenVPNClient.this.show_stats();
            OpenVPNClient.this.schedule_stats();
        }
    };
    private boolean isCheckUpdateIsRunning = false;

    
	
	private LinearLayout Layout_Spinner;
	private LinearLayout Layout_Conectado;
    
    private ProgressDialog progressDialog;
	public static boolean mConnected;
	public static boolean mShown;
    private AppUpdateManager mAppUpdateManager;
	private static final int RC_APP_UPDATE = 100;

    private AlertDialog mDialog;
    private void cancel_stats() {
        this.stats_timer_handler.removeCallbacks(this.stats_timer_task);
    }

    private void schedule_stats() {
        cancel_stats();
        this.stats_timer_handler.postDelayed(this.stats_timer_task, 1000);
    }
    
    private void showLoading(Context context, String msg) {
		View v = LayoutInflater.from(context).inflate(R.layout.loading, null);
		final TextView text = v.findViewById(R.id.loadingText);
		text.setText(msg);
		mDialog = new AlertDialog.Builder(context)
			.setView(v)
			.setCancelable(false)
			.create();

		mDialog.show();
	}

    public void show_stats() {
        if (TkLogStatus.isTunnelActive()) {
            if (mConfig.getServerType().equals(SERVER_TYPE_OVPN)) {
                try {
                    OpenVPNService.ConnectionStats stats = get_connection_stats();
                    TkLogStatus.updateByteCount(stats.bytes_in, stats.bytes_out);
                } catch (Exception ignored) {
                }
            } else if (mConfig.getServerType().equals(SERVER_TYPE_SSH) || mConfig.getServerType().equals(SERVER_TYPE_DNS)) {
                TkLogStatus.updateByteCount( upDateBytes.getTotalBytesReceived(), upDateBytes.getTotalBytesSent());
            } else if (mConfig.getServerType().equals(SERVER_TYPE_UDP_HYSTERIA_V1)) {
                List<Long> allData;
                allData = RetrieveData.findData();
                long upload = allData.get(1);
                long download = allData.get(0);
                m_SentBytes += upload;
                m_ReceivedBytes += download;
                TkLogStatus.updateByteCount(m_ReceivedBytes, m_SentBytes);
            }
            if (isConnected) {
              //  mDataGraph.start();
            }
        }
    }
    
    
    
    
    private void setConfigFieldsEnabled(boolean enabled) {
        etServerHost.setEnabled(enabled);
        etServerPort.setEnabled(enabled);
        etUsername.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        etPayload.setEnabled(enabled);
        etSslPayload.setEnabled(enabled);
        etSni.setEnabled(enabled);
        etProxyIp.setEnabled(enabled);
        etProxyPort.setEnabled(enabled);
        spinnerPayloadType.setEnabled(enabled);
        mTunnelType.setEnabled(enabled);
    }

    private void doUpdateLayout() {
        boolean isRunning = TkLogStatus.isTunnelActive();
        setConfigFieldsEnabled(!isRunning);
        udpforward.setEnabled(!isRunning);
        dnsforward.setEnabled(!isRunning);
        if (!isConnected) {
            btn_connector.setText("INICIAR");
            btn_connector.setBackgroundResource(R.drawable.btn_connect);
            status_view.setTextColor(Color.RED);
            enabledWidgets(true);
            setConfigFieldsEnabled(true);
            Layout_Conectado.setVisibility(View.VISIBLE);
            Layout_Spinner.setVisibility(View.GONE);
        }
        if (!isConnected) {
            btn_connector2.setText("PARAR");
            btn_connector2.setBackgroundResource(R.drawable.btn_connect);
            status_view.setTextColor(Color.GREEN);
            enabledWidgets(false);
            setConfigFieldsEnabled(false);
            Layout_Conectado.setVisibility(View.GONE);
            Layout_Spinner.setVisibility(View.VISIBLE);
        }
    }

    
    
    
    
    
    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level, int progress) {
        mHandler.post(
                () -> {
                    isConnected = state.equalsIgnoreCase(resString(R.string.state_connected));
                    //  showInterstitial();
                    status_view.setText(state);
                    if (state.contains("Connected")) {

                    
                    
                    
                        Toast.makeText(getApplicationContext(), state, Toast.LENGTH_SHORT).show();
                        // showInterstitial();
                    }
                    if (TkLogStatus.isTunnelActive() && !isConnected) {
                        btn_connector.setBackgroundResource(R.drawable.btn_connect);

                                    btn_connector2.setBackgroundResource(R.drawable.btn_connect);

                        status_view.setTextColor(Color.RED);
                    }

                    doUpdateLayout();
                });
        switch (state) {
            case TkLogStatus.VPN_CONNECTED:
            //showInterstitial();
           
				Layout_Conectado.setVisibility(View.VISIBLE);
				Layout_Spinner.setVisibility(View.GONE);
            
                
                isConnected = true;
                
           case TkLogStatus.VPN_RECONNECTING:
                //if(mPref.getBoolean(IS_RANDOM_SERVER,false))reLoad_Configs();
                break;
            
            
          
            
            case TkLogStatus.VPN_DISCONNECTED:
				
				Layout_Conectado.setVisibility(View.GONE);
				Layout_Spinner.setVisibility(View.VISIBLE);
            
           isConnected = true;
                //if(mPref.getBoolean(IS_RANDOM_SERVER,false))reLoad_Configs();
                break;
            
        }
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        Resources res = getResources();
        String bytesIn ="↓ " + humanReadableByteCount(in, false, res) + "   ↑ " + humanReadableByteCount(out, false, res);

        runOnUiThread(() -> {
            // showExpireDate();
            byteIn_view.setText(bytesIn);
            
        });
    }

    public void pindutan1(View v) {
        mUpdate();
        
    }
    
    public void pindutan2(View v) {
        mLogs();
        
    }
    
    
    
    public void pindutan3(View v) {
        tethering();
        
    }
    
    public void pindutan4(View v) {
        restore();
        
    }
    
    private void enabledWidgets(boolean enabled) {
        if (enabled) {
            
        } else {
            
        }
    }
    
    private int getCheckedPosition1(int p1) {
        if (p1 == R.id.type_ovpn) {
            findViewById(R.id.layoutnetworks).setVisibility(View.VISIBLE);
            return 0;
        } else if (p1 == R.id.type_udp) {
            findViewById(R.id.layoutnetworks).setVisibility(View.VISIBLE);
            return 1;
        } else if (p1 == R.id.type_ssh) {
            findViewById(R.id.layoutnetworks).setVisibility(View.VISIBLE);
            return 2;
        } else if (p1 == R.id.type_dns) {
            findViewById(R.id.layoutnetworks).setVisibility(View.VISIBLE);
            return 3;
        } else if (p1 == R.id.type_v2ray) {
            findViewById(R.id.layoutnetworks).setVisibility(View.GONE);
            return 4;
        }
        return 0;
    }

    private void loadIds() {  
        btn_connector = findViewById(R.id.btn_connect);
       
       btn_connector2 = findViewById(R.id.btn_connect2);
        
        byteIn_view = findViewById(R.id.velocidadeDown);
        
        status_view = findViewById(R.id.status);
        Config_vers = findViewById(R.id.config_version);
        // Config fields
        etServerHost    = findViewById(R.id.et_server_host);
        etServerPort    = findViewById(R.id.et_server_port);
        etUsername      = findViewById(R.id.et_username);
        etPassword      = findViewById(R.id.et_password);
        etPayload       = findViewById(R.id.et_payload);
        etSslPayload    = findViewById(R.id.et_ssl_payload);
        etSni           = findViewById(R.id.et_sni);
        etProxyIp       = findViewById(R.id.et_proxy_ip);
        etProxyPort     = findViewById(R.id.et_proxy_port);
        spinnerPayloadType = findViewById(R.id.spinner_payload_type);

        Layout_Conectado = (LinearLayout) findViewById(R.id.Layout_Conectado);
        Layout_Spinner   = (LinearLayout) findViewById(R.id.Layout_Spinner);

        servidorAtual = findViewById(R.id.servidorAtual);
        servidorAtual.setText(mConfig.getServerName());
        conexaoAtual = findViewById(R.id.conexaoAtual);
        conexaoAtual.setText(mConfig.getPayloadName());

        // Isi field dari config tersimpan
        loadConfigToFields();
		
	
        
        cBuiler = new AlertDialog.Builder(this).create();
        
        NavigationView navigation_view1 = findViewById(R.id.navigation_view);
        navigation_view1.setNavigationItemSelectedListener(this);

        drawer = findViewById(R.id.drawer);
        @SuppressLint("CutPasteId") NavigationView navigation_view = findViewById(R.id.navigation_view);
        navigation_view.setNavigationItemSelectedListener(this);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, R.string.app_name, R.string.app_name);
        drawer.addDrawerListener(toggle);
        toggle.setDrawerIndicatorEnabled(true);
        toggle.syncState();
        
        

        dnsforward = navigation_view.getMenu().findItem(R.id.dnsforward).getActionView().findViewById(R.id.drawer_switch);
        boolean isFORWARDER = configUtil.getVpnDnsForward();
        dnsforward.setChecked(isFORWARDER);
        // Attach listener
        dnsforward.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    configUtil.setVpnDnsForward(true);

                } else {

                    configUtil.setVpnDnsForward(false);

                }
                drawer.closeDrawer(GravityCompat.START);
            }
        });

        udpforward = navigation_view.getMenu().findItem(R.id.udpforward).getActionView().findViewById(R.id.drawer_switch);
        boolean isUDPFORWARDER = configUtil.getVpnUdpForward();
        udpforward.setChecked(isUDPFORWARDER);

        // Attach listener
        udpforward.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    configUtil.setVpnUdpForward(true);

                } else {

                    configUtil.setVpnUdpForward(false);

                }
                drawer.closeDrawer(GravityCompat.START);
            }

        });

    }

    @SuppressLint("CutPasteId")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mUtils = new util(this);
        FileUtils mFileUtils = new FileUtils();
        configUtil = ConfigUtil.getInstance(this);
        mHandler = new Handler();
        this.prefs = new PrefUtil(PreferenceManager.getDefaultSharedPreferences(this));
        this.pwds = new PasswordUtil(PreferenceManager.getDefaultSharedPreferences(this));
        mPref = MyApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
        serverData = new ConfigDataBase(OpenVPNClient.this, "mServerData");
        networkData = new ConfigDataBase(OpenVPNClient.this, "mNetwrokData");
        init_default_preferences(this.prefs);
        LoadDefaultConfig();
        loadDefaultProfiles();
        loadIds();
        doBindService();
        
        //findViewById(R.id.layoutnetworks).setVisibility(View.GONE);
        
        mTunnelType = findViewById(R.id.tunnel_radio);
        int[] rbtn = {R.id.type_ovpn, R.id.type_udp, R.id.type_ssh, R.id.type_dns, R.id.type_v2ray};
        // Setup payload type spinner
        String[] payloadTypes = {
            "Direct",          // 1 = PAYLOAD_TYPE_DIRECT
            "Direct + Payload",// 2 = PAYLOAD_TYPE_DIRECT_PAYLOAD
            "HTTP Proxy",      // 3 = PAYLOAD_TYPE_HTTP_PROXY
            "SSL",             // 4 = PAYLOAD_TYPE_SSL
            "SSL + Payload",   // 5 = PAYLOAD_TYPE_SSL_PAYLOAD
            "SSL + Proxy",     // 6 = PAYLOAD_TYPE_SSL_PROXY
            "SSL + Proxy + HTTP",// 7 = PAYLOAD_TYPE_SSL_PROXY_HTTP_PROXY
            "OVPN UDP"         // 8 = PAYLOAD_TYPE_OVPN_UDP
        };
        ArrayAdapter<String> payloadAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, payloadTypes);
        payloadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPayloadType.setAdapter(payloadAdapter);
        // Restore saved payload type (index = type - 1)
        int savedPayloadType = mConfig.getPayloadType();
        spinnerPayloadType.setSelection(Math.max(0, savedPayloadType - 1));
        spinnerPayloadType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                mConfig.setPaylodType(position + 1);
                saveConfigFromFields();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        mTunnelType.check(rbtn[this.mPref.getInt("manual_tunnel_radio", 0)]);
        mTunnelType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                mEditor.putInt("manual_tunnel_radio", getCheckedPosition1(id)).apply();
            }
        });
        
        
       btn_connector.setOnClickListener(this);
        
        btn_connector2.setOnClickListener(this);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Log.i("hello", "world");
            runOnUiThread(() -> {
                try {
                    if (util.isNetworkAvailable(OpenVPNClient.this)) {
                        if (!isCheckUpdateIsRunning) {
                            autoUpdate();
                        }
                    }
                } catch (Exception e) {
                    TkLogStatus.logDebug(e.getMessage());
                }
            });
        }, 0, 120, TimeUnit.SECONDS);

        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(this).build();
        
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
        //.setConsentDebugSettings(debugSettings)
        .setTagForUnderAgeOfConsent(false)
        .build();

    
        
        

    }
   
    
    
	private void showCompleterUpdate()
	{
		Snackbar snacks = Snackbar.make(findViewById(android.R.id.content), "New app is ready!",
										Snackbar.LENGTH_INDEFINITE);
		snacks.setAction("Install", new View.OnClickListener()
			{
				@Override
				public void onClick(View view) {
					mAppUpdateManager.completeUpdate();
				}
			});
		snacks.setActionTextColor(Color.parseColor("#ffffff"));
		snacks.show();
	}
    
    private void onPermissionsResult(boolean isGranted) {
        String snackBar;
        if (isGranted) {
            snackBar = "Notification Permission Granted!";
        } else {
            snackBar = "Please grant Notification permission from App Settings";
        }
        Snackbar.make(findViewById(android.R.id.content).getRootView(), snackBar, Snackbar.LENGTH_SHORT).show();
    }

    private void autoUpdate() {
        isCheckUpdateIsRunning = true;
        new checkUpdate(OpenVPNClient.this, MyApplication.CONFIGURL, new checkUpdate.Listener() {
            @Override
            public void onError(String config) {
                isCheckUpdateIsRunning = false;

            }

            @Override
            public void onCompleted(final String config) {
                isCheckUpdateIsRunning = false;
                String mData = FileUtils.showJson(config);
                try {
                    JSONArray jarr = new JSONArray();
                    JSONObject obj = new JSONObject(mData);
                    if (obj.getDouble("Version") <= Double.valueOf(mPref.getString(CONFIG_VERSION, "0"))) {
                        Config_vers.setText(obj.getString("Version"));
                        // showExpireDate();
                    } else {
                        if (obj.getJSONArray("HTTPNetworks").length() != 0)
                            for (int i = 0; i < obj.getJSONArray("HTTPNetworks").length(); i++) {
                                jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                            }
                        if (obj.getJSONArray("SSLNetworks").length() != 0)
                            for (int i = 0; i < obj.getJSONArray("SSLNetworks").length(); i++) {
                                jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                            }
                        serverData.updateData("1", obj.getJSONArray("Servers").toString());
                        networkData.updateData("1", "" + jarr);
                        Config_vers.setText(obj.getString("Version"));
                        mEditor.putInt(SERVER_POSITION, 0).apply();
                        mEditor.putInt(NETWORK_POSITION, 0).apply();

                        mEditor.putString(CONFIG_VERSION, obj.getString("Version")).apply();
                        mEditor.putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                        mEditor.putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                        mEditor.putString(OpenVPN_CERT, obj.getString("Ovpn_Cert")).apply();
                        reLoad_Configs();
                        mConfig.clearSplit();
                        mEditor.putBoolean("isRandom", false).apply();
                        doUpdateLayout();
                        showDialog("Release Note", obj.getString("ReleaseNotes"));

                    }
                } catch (Exception e) {
                    isCheckUpdateIsRunning = false;
                }
            }
        }).start(false);
    }

    public void mUpdate() {
        isCheckUpdateIsRunning = true;
        new checkUpdate(OpenVPNClient.this, MyApplication.CONFIGURL, new checkUpdate.Listener() {
            @Override
            public void onError(String config) {
                isCheckUpdateIsRunning = false;
                Toast.makeText(OpenVPNClient.this, config, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCompleted(final String config) {
                isCheckUpdateIsRunning = false;
                String mData = FileUtils.showJson(config);
                try {
                    JSONArray jarr = new JSONArray();
                    JSONObject obj = new JSONObject(mData);
                    if (obj.getDouble("Version") <= Double.valueOf(mPref.getString(CONFIG_VERSION, "0"))) {
                        showDialog("Your config is up to date", obj.getString("ReleaseNotes"));
                    } else {
                        if (obj.getJSONArray("HTTPNetworks").length() != 0)
                            for (int i = 0; i < obj.getJSONArray("HTTPNetworks").length(); i++) {
                                jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                            }
                        if (obj.getJSONArray("SSLNetworks").length() != 0)
                            for (int i = 0; i < obj.getJSONArray("SSLNetworks").length(); i++) {
                                jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                            }
                        serverData.updateData("1", obj.getJSONArray("Servers").toString());
                        networkData.updateData("1", "" + jarr);

                        Config_vers.setText(obj.getString("Version"));
                        mEditor.putInt(SERVER_POSITION, 0).apply();
                        mEditor.putInt(NETWORK_POSITION, 0).apply();

                        mEditor.putString(CONFIG_VERSION, obj.getString("Version")).apply();
                        mEditor.putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                        mEditor.putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                        mEditor.putString(OpenVPN_CERT, obj.getString("Ovpn_Cert")).apply();
                        reLoad_Configs();
                        mConfig.clearSplit();
                        mEditor.putBoolean("isRandom", false).apply();
                        showDialog("Release Note", obj.getString("ReleaseNotes"));
                        doUpdateLayout();
                    }
                } catch (Exception e) {
                    isCheckUpdateIsRunning = false;
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }).start(true);
    }

    public void mImport(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_FILE);
    }


    public void settings(View v) {
        
        drawer.openDrawer(GravityCompat.START);
    }

    public void mIphunt() {
        View inflate = LayoutInflater.from(this).inflate(R.layout.notif2, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(inflate);
        TextView title = inflate.findViewById(R.id.notiftext1);
        final TextView ms = inflate.findViewById(R.id.confimsg);
        final TextView ok = inflate.findViewById(R.id.appButton1);
        TextView cancel = inflate.findViewById(R.id.appButton2);
        title.setText("GTM IP Hunter");
        ms.setText("To connect to GTM No Load No Blocking, Make sure that you are now in the Magic IP. Click the button to check your IP!");
        ok.setText("Check Now");
        cancel.setText("Close");
        final AlertDialog alert = alertDialogBuilder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.getWindow().setGravity(Gravity.CENTER);
        alert.show();
        ok.setOnClickListener(p1 -> {
            ms.setText("Please wait while we are checking your IP...");
            ok.setEnabled(false);
            ok.setText("Checking...");
            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    try {
                        int l = 0;
                        URL whatismyip = new URL("http://noloadbalance.globe.com.ph");
                        String magic = "✅ Congrats!! You are now connected to MAGIC IP.";
                        String fail = "🚫 Disconnected. Please Airplane Mode On/Off and Try Again.";
                        try {
                            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("104.16.213.74", 80));
                            HttpURLConnection connection = (HttpURLConnection) whatismyip.openConnection(proxy);
                            connection.setRequestMethod("GET");
                            connection.connect();
                            connection.getContentLength();
                            connection.setConnectTimeout(3000);
                            InputStream in = connection.getInputStream();
                            byte[] buffer = new byte[4096];
                            int countBytesRead;
                            while ((countBytesRead = in.read(buffer)) != -1) {
                                l += countBytesRead;
                            }
                            in.markSupported();
                            if (l == 333) {
                                ms.setText(magic);
                                ok.setText("Check Again");
                                ok.setEnabled(true);
                                return;
                            }
                            if (connection.getResponseCode() == 200) {
                                ms.setText(magic);
                                ok.setText("Check Again");
                                ok.setEnabled(true);
                                return;
                            }
                            in.close();
                            ms.setText(fail);
                            ;
                            ok.setText("Check Again");
                            ok.setEnabled(true);
                        } catch (IOException e) {
                            ok.setText("Check Again");
                            ok.setEnabled(true);
                            ms.setText(fail);
                        }

                    } catch (MalformedURLException e) {
                    }
                }
            }, 1000);
        });
        cancel.setOnClickListener(p1 -> alert.dismiss());
        alert.show();
    }

    public void mLogs() {
        startActivityForResult(new Intent(OpenVPNClient.this, VPNLogs.class), 0);
    }

    public void tethering() {
        startActivity(new Intent(this, MainActivityWifi.class));
    }

    private void showDialog(String t, String m) {
        if (cBuiler.isShowing()) cBuiler.dismiss();
        cBuiler = new AlertDialog.Builder(this).create();
        cBuiler.setTitle(t);
        cBuiler.setMessage(m);
        cBuiler.setIcon(R.drawable.icon_main);
        cBuiler.setButton(DialogInterface.BUTTON_POSITIVE, "Close", (dialog, which) -> dialog.dismiss());
        cBuiler.show();
    }

    public void LoadDefaultConfig() {
        boolean showFirstTime = mPref.getBoolean("connect_first_time", true);
        if (Boolean.valueOf(showFirstTime)) {
            String data = FileUtils.readFromAsset(OpenVPNClient.this);
            try {
                JSONObject obj = new JSONObject(data);
                JSONArray jarr = new JSONArray();
                if (obj.getJSONArray("HTTPNetworks").length() != 0)
                    for (int i = 0; i < obj.getJSONArray("HTTPNetworks").length(); i++) {
                        jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                    }
                if (obj.getJSONArray("SSLNetworks").length() != 0)
                    for (int i = 0; i < obj.getJSONArray("SSLNetworks").length(); i++) {
                        jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                    }
                if (jarr.length() == 0) {
                    networkData.insertData("[]");
                } else if (jarr.length() != 0) {
                    networkData.insertData("" + jarr);
                }
                serverData.insertData(obj.getJSONArray("Servers").toString());
                networkData.insertData("" + jarr);
                mEditor.putString(CONFIG_VERSION, obj.getString("Version")).apply();
                mEditor.putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                mEditor.putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                mEditor.putString(OpenVPN_CERT, obj.getString("Ovpn_Cert")).apply();
                reLoad_Configs();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1234);
                }
                mEditor.putBoolean("connect_first_time", false).apply();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadDefaultProfiles() {
        boolean isFinished = false;
        try {
            String server_name = "HarliesDevX";
            String encoded_name = String.format("%s.ovpn", URLEncoder.encode(server_name, "UTF-8"));
            String config = FileUtils.readFromRaw(this, R.raw.ovpn_cert);
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(config));
            VpnProfile vp = cp.convertProfile();// 解析.ovpn
            vp.mName = server_name;
            if (vp.checkProfile(this) != R.string.no_error_found) {
                throw new RemoteException(getString(vp.checkProfile(this)));
            }
            vp.mProfileCreator = getPackageName();
            vp.mUsername = "HarliesDevX";
            vp.mPassword = "HarliesDevX";
            vp.mUseCustomConfig = true;
            //vp.mCustomConfigOptions = new StringBuffer().append(vp.mCustomConfigOptions).append("http-proxy 127.0.0.1 8989").toString();
            String _config = vp.getConfigFile(this, true);
            File dir = new File(getFilesDir(), encoded_name);
            OutputStream out = new FileOutputStream(dir);
            out.write(_config.getBytes());
            out.flush();
            out.close();
            isFinished = true;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (isFinished) {
            startService(new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_ADD_PROFILE));
        }
    }


    private void loadConfigToFields() {
        String host = mConfig.getSecureString(SERVER_KEY);
        String port = mConfig.getSecureString(SERVER_PORT_KEY);
        String user = mConfig.getSecureString(USERNAME_KEY);
        String pass = mConfig.getSecureString(PASSWORD_KEY);
        String payload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
        String sslPayload = mConfig.getSecureString(CUSTOM_SSL_PAYLOAD_KEY);
        String sni = mConfig.getSecureString(SNI_HOST_KEY);
        String proxyIp = mConfig.getSecureString(PROXY_IP_KEY);
        String proxyPort = mConfig.getSecureString(PROXY_PORT_KEY);
        if (!host.isEmpty()) etServerHost.setText(host);
        if (!port.isEmpty()) etServerPort.setText(port);
        if (!user.isEmpty()) etUsername.setText(user);
        if (!pass.isEmpty()) etPassword.setText(pass);
        if (!payload.isEmpty()) etPayload.setText(payload);
        if (!sslPayload.isEmpty()) etSslPayload.setText(sslPayload);
        if (!sni.isEmpty()) etSni.setText(sni);
        if (!proxyIp.isEmpty()) etProxyIp.setText(proxyIp);
        if (!proxyPort.isEmpty()) etProxyPort.setText(proxyPort);
    }

    private void saveConfigFromFields() {
        String host = etServerHost.getText().toString().trim();
        String port = etServerPort.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String payload = etPayload.getText().toString().trim();
        String sslPayload = etSslPayload.getText().toString().trim();
        String sni = etSni.getText().toString().trim();
        String proxyIp = etProxyIp.getText().toString().trim();
        String proxyPort = etProxyPort.getText().toString().trim();
        if (!host.isEmpty()) mConfig.setServerHost(host);
        if (!port.isEmpty()) mConfig.setServerPort(port);
        if (!user.isEmpty()) mConfig.setUser(user);
        if (!pass.isEmpty()) mConfig.setUserPass(pass);
        if (!payload.isEmpty()) mConfig.setPayload(payload);
        if (!sslPayload.isEmpty()) mConfig.setPayload(sslPayload); // gunakan setSslPayload jika ada
        if (!sni.isEmpty()) mConfig.setSni(sni);
        if (!proxyIp.isEmpty()) mConfig.setProxyHost(proxyIp);
        if (!proxyPort.isEmpty()) mConfig.setProxyPort(proxyPort);
        servidorAtual.setText(!host.isEmpty() ? host : mConfig.getServerName());
        conexaoAtual.setText(mConfig.getPayloadName());
    }

    private boolean saveAndCheckConfig() {
        saveConfigFromFields();
        return checkConfiguration();
    }

    private boolean checkConfiguration() {
        if (!reLoad_Configs()) {
            Toast.makeText(getApplicationContext(), "error: Config load error!", Toast.LENGTH_LONG).show();
            return false;
        } else if (!util.isNetworkAvailable(this)) {
            Toast.makeText(getApplicationContext(), "Please connect to the internet", Toast.LENGTH_LONG).show();
            return false;
        } else if (mUtils.isSniffer(this)) {
            addlogInfo("<b>Another running VPN application has been detected, stop it before</b>");
            Toast.makeText(getApplicationContext(), "Another running VPN application has been detected, stop it before", Toast.LENGTH_LONG).show();
            
            submitDisconnectIntent();
            return false;
        } else if (mPref.getBoolean(isAutoLogIn, false)) {
            if (mConfig.getSecureString(USERNAME_KEY).isEmpty() || mConfig.getSecureString(PASSWORD_KEY).isEmpty()) {
                Toast.makeText(getApplicationContext(), "Server Account is empty!", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        } else if (mConfig.getSecureString(USERNAME_KEY).isEmpty() || mConfig.getSecureString(PASSWORD_KEY).isEmpty()) {
            Toast.makeText(getApplicationContext(), "Server Account is empty!", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void startOrStopTunnel() {
		        if (TkLogStatus.isTunnelActive()) {
                        stopTunnelService();
            cancel_stats();
        } else {
            mEditor.putInt("loadOnce", 0).apply();
            if (checkConfiguration()) {
               // showInterstitial();
                start_connect();
            findViewById(R.id.type_ovpn).setEnabled(false);
            findViewById(R.id.type_udp).setEnabled(false);
            findViewById(R.id.type_ssh).setEnabled(false);
            findViewById(R.id.type_dns).setEnabled(false);
            findViewById(R.id.type_v2ray).setEnabled(false);
            findViewById(R.id.tunnel_radio2).setVisibility(View.GONE);
            }
        }
    }

    public void stopTunnelService() {
            findViewById(R.id.tunnel_radio2).setVisibility(View.VISIBLE);
            findViewById(R.id.type_ovpn).setEnabled(true);
            findViewById(R.id.type_udp).setEnabled(true);
            findViewById(R.id.type_ssh).setEnabled(true);
            findViewById(R.id.type_dns).setEnabled(true);
            findViewById(R.id.type_v2ray).setEnabled(true);
        m_SentBytes = 0;
        m_ReceivedBytes = 0;
        btn_connector.setBackgroundResource(R.drawable.btn_connect);
       
       btn_connector2.setBackgroundResource(R.drawable.btn_connect);
              status_view.setTextColor(Color.RED);
        submitDisconnectIntent();
    }

    private void stop_service() {
        TkLogStatus.removeStateListener(this);
        TkLogStatus.removeByteCountListener(this);
    }

    private void stop() {
        stop_service();
        doUnbindService();
    }

    

    @Override
    protected void onResume() {
        super.onResume();
        TkLogStatus.addStateListener(this);
        TkLogStatus.addByteCountListener(this);
        if (TkLogStatus.isTunnelActive()) schedule_stats();
        Config_vers.setText(mPref.getString(CONFIG_VERSION, "1.1"));
        
        doUpdateLayout();
        autoUpdate();
        

        
       
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel_stats();
        stop();
       
    }
    
    

    public void onClick(View v) {
        int viewid = v.getId();
        if (viewid == R.id.btn_connect) {
			
            startOrStopTunnel();
			
			
			
        }
      
       if (viewid == R.id.btn_connect2) {
		   
            startOrStopTunnel();
		;
		   
        }
        
        
        
    }
   
    
    void showError(String error) {
        if (isFinishing()) {
            // Check if the activity is finishing or destroyed
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Error!");
        builder.setMessage(error);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
	
	void showWarning(String warning) {
        if (isFinishing()) {
            // Check if the activity is finishing or destroyed
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Warning!");
        builder.setMessage(warning);
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    @Override
    public void startOpenVPN() {
        resolve_epki_alias_then_connect();
        super.startOpenVPN();
    }

    private void start_connect() {
        String user = mConfig.getSecureString(USERNAME_KEY);
        if (user.equals("0")) {
            MyApplication.restart_app(OpenVPNClient.this);
            SoftReference<ServiceControl> s = V2RayServiceManager.getServiceControl();
            if (s != null) {
                s.get().stopService();
            }
            stopTunnelService();
        }
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            try {
                startActivityForResult(intent, START_BIND_CALLED);
                return;
            } catch (ActivityNotFoundException e) {
                // showSnack(getResources().getDrawable(R.drawable.ic_error),"CLI: requesting VPN actor rights failed", e.getMessage());
                return;
            }
        }
        TunnelUtils.restartRotateAndRandom();
        schedule_stats();
        StatisticGraphData.getStatisticData().getDataTransferStats().startConnected();
        startService(new Intent(OpenVPNClient.this, VPNService.class).setAction(VPNService.START_SERVICE));
    }

    private void resolve_epki_alias_then_connect() {
        resolveExternalPkiAlias(OpenVPNClient.this::do_connect);
    }

    private void do_connect(String epki_alias) {
        init_default_preferences(prefs);
        prefs.set_string("n_username", mConfig.getSecureString(USERNAME_KEY));
        String app_name = "net.openvpn.connect.android";
        String username = mConfig.getSecureString(USERNAME_KEY);
        String password = mConfig.getSecureString(PASSWORD_KEY);
        String proxy_name = null;
        String server = null;
        String pk_password = null;
        String response = null;
        boolean is_auth_pwd_save = false;
        String ipv6 = prefs.get_string("ipv6");
        String profile_name = "HarliesDevX";
        String vpn_proto = prefs.get_string("vpn_proto");
        String conn_timeout = prefs.get_string("conn_timeout");
        String compression_mode = prefs.get_string("compression_mode");
        submitConnectIntent(profile_name, server, vpn_proto, ipv6, conn_timeout, username, password, is_auth_pwd_save, pk_password, response, epki_alias, compression_mode, proxy_name, null, null, true, get_gui_version(app_name));
    }

    protected void onActivityResult(int request, int result, Intent data) {
        switch (request) {
            case START_BIND_CALLED:
                if (result == RESULT_OK) {
                    start_connect();
                    return;
                }
                return;
            case REQUEST_IMPORT_FILE:
                if (result == RESULT_OK) {
                    Uri uri = data.getData();
                    String mData = FileUtils.showJson(FileUtils.readTextUri(OpenVPNClient.this, uri));
                    try {
                        JSONArray jarr = new JSONArray();
                        JSONObject obj = new JSONObject(mData);
                        if (obj.getDouble("Version") <= Double.valueOf(mPref.getString(CONFIG_VERSION, "0"))) {
                            Toast.makeText(getApplicationContext(), "Your config is up to date", Toast.LENGTH_LONG).show();
                        } else {
                            if (obj.getJSONArray("HTTPNetworks").length() != 0)
                                for (int i = 0; i < obj.getJSONArray("HTTPNetworks").length(); i++) {
                                    jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                                }
                            if (obj.getJSONArray("SSLNetworks").length() != 0)
                                for (int i = 0; i < obj.getJSONArray("SSLNetworks").length(); i++) {
                                    jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                                }
                            serverData.updateData("1", obj.getJSONArray("Servers").toString());
                            networkData.updateData("1", "" + jarr);
                            mEditor.putString(CONFIG_VERSION, obj.getString("Version")).apply();
                            mEditor.putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                            mEditor.putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                            mEditor.putString(OpenVPN_CERT, obj.getString("Ovpn_Cert")).apply();
                            mConfig.clearSplit();
                            mEditor.putBoolean("isRandom", false).apply();
                            reLoad_Configs();
                            stopTunnelService();
                            Config_vers.setText(mPref.getString(CONFIG_VERSION, "1.1"));
                        }
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    stopTunnelService();
                    return;
                }
                recreate();
                return;
            default:
                super.onActivityResult(request, result, data);
        }
    }

    /**
     * Tknetwork01/16/2024...
     */
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(MenuItem p1) {
        switch (p1.getItemId()) {
            case R.id.item_udp -> {
                if (!TkLogStatus.isTunnelActive()) {
                    addUdpgwDialog();
                } else {
                    Toast.makeText(OpenVPNClient.this, "Please Disconnect first!!", Toast.LENGTH_SHORT).show();
                }
            }
            case R.id.item_dns -> {
                if (!TkLogStatus.isTunnelActive()) {
                    addDnsDialog();
                } else {
                    Toast.makeText(OpenVPNClient.this, "Please Disconnect first!!", Toast.LENGTH_SHORT).show();
                }
            
            }
        }


        drawer.closeDrawer(GravityCompat.START);
        // TODO: Implement this method
        return true;
    }

    
    
    
   @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
        
        
       if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawers();
        } else {
       
            
        }
    }
    
    
   
    
    
   private void restore() {
        View inflate = ((LayoutInflater) getSystemService("layout_inflater")).inflate(R.layout.hdb_dialog, (ViewGroup) null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.SpkDialog);
        builder.setView(inflate);
        TextView textView = (TextView) inflate.findViewById(R.id.notiftext1);
        TextView textView2 = (TextView) inflate.findViewById(R.id.confimsg);
        TextView textView3 = (TextView) inflate.findViewById(R.id.appButton1);
        TextView textView4 = (TextView) inflate.findViewById(R.id.appButton2);
        textView.setText("Restore Default");
        textView2.setText("Are you sure to clear HTTP Request Tunnel application data including config updates? Click OK to Proceed");
        textView3.setText("OK");
        textView4.setText("Cancel");

        final AlertDialog create = builder.create();
        create.setCanceledOnTouchOutside(false);
        create.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        create.getWindow().setGravity(17);
      //  create.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Translucent;
        inflate.findViewById(R.id.positiveBtn).setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    try {
                        // clearing app data
                        if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
                            ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData(); // note: it has a return value!
                        } else {
                            String packageName = getApplicationContext().getPackageName();
                            Runtime runtime = Runtime.getRuntime();
                            runtime.exec("pm clear "+packageName);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        inflate.findViewById(R.id.negativeBtn).setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    create.dismiss();
                }
            });
        create.show();
    }

    
    
  public void addUdpgwDialog() {
    final SharedPreferences defaultSharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(this);
    View inflate = LayoutInflater.from(this).inflate(R.layout.udpgw, null);
    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.SpkDialog);
    builder.setView(inflate);
    Button saveButton = inflate.findViewById(R.id.udpgw_save);
    final EditText editText = inflate.findViewById(R.id.fragment_udpgw);

    editText.setText(
        configUtil.getVpnUdpResolver().replace("127.0.0.1:", ""));

    builder.setCancelable(false);
    final AlertDialog dialog = builder.create();
    dialog.show();

    Objects.requireNonNull(dialog.getWindow())
        .setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.9), // Adjust width as needed
            ViewGroup.LayoutParams.WRAP_CONTENT);

    saveButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            String newText = editText.getText().toString();
            if (!newText.isEmpty()) {
              String ipAddress = "127.0.0.1:" + newText;
              defaultSharedPreferences
                  .edit()
                  .putString(configUtil.UDPRESOLVER_KEY, ipAddress)
                  .apply();
            } else {
              defaultSharedPreferences.edit().remove(configUtil.UDPRESOLVER_KEY).apply();
            }
            dialog.dismiss();
          }
        });

    Button cancelButton = inflate.findViewById(R.id.udpgw_cancel);
    cancelButton.setOnClickListener(view -> dialog.dismiss());
  }

    
    
   public void addDnsDialog() {
    final SharedPreferences defaultSharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(this);
    View inflate = LayoutInflater.from(this).inflate(R.layout.dns, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.SpkDialog);
    builder.setView(inflate);

    Button button = inflate.findViewById(R.id.dnsku_cancel);
    Button button2 = inflate.findViewById(R.id.dnsku_save);
    final EditText editText = inflate.findViewById(R.id.fragment_primary_dns);
    editText.setText(configUtil.getVpnDnsResolver1());
    final EditText editText2 = inflate.findViewById(R.id.fragment_secondary_dns);
    editText2.setText(configUtil.getVpnDnsResolver2());

    builder.setCancelable(false);
    final AlertDialog dialog = builder.create();
    dialog.show();

    Objects.requireNonNull(dialog.getWindow())
        .setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.9), // Adjust width as needed
            ViewGroup.LayoutParams.WRAP_CONTENT);

    button2.setOnClickListener(
        view -> {
          if (editText.getText().toString().isEmpty() || editText2.getText().toString().isEmpty()) {
            defaultSharedPreferences
                .edit()
                .remove(configUtil.getVpnDnsResolver1())
                .remove(configUtil.getVpnDnsResolver2())
                .apply();
          } else {
            defaultSharedPreferences
                .edit()
                .putString(configUtil.getVpnDnsResolver1(), editText.getText().toString())
                .putString(configUtil.getVpnDnsResolver2(), editText2.getText().toString())
                .apply();
          }

          dialog.dismiss();
        });

    button.setOnClickListener(view -> dialog.dismiss());
  }
   

    private enum FinishOnConnect {
        DISABLED, ENABLED, ENABLED_ACROSS_ONSTART, PENDING
    }

    
}
