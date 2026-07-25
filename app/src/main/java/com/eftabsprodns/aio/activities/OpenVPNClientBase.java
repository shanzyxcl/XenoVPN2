package com.eftabsprodns.aio.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.security.KeyChainAliasCallback;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import androidx.appcompat.app.AppCompatActivity;

import net.openvpn.openvpn.PrefUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigDataBase;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.service.OpenVPNService;
import com.eftabsprodns.aio.service.VPNService;
import com.eftabsprodns.aio.utils.FileUtils;
import com.eftabsprodns.aio.utils.JsonUtils;
import com.eftabsprodns.aio.utils.util;
import com.eftabsprodns.aio.view.StatisticGraphData;

public abstract class OpenVPNClientBase extends AppCompatActivity implements SettingsConstants, VPNService.InjectorListener, OpenVPNService.EventReceiver {
    private static final String TAG = "OpenVPNClientBase";
    public SharedPreferences mPref;
    public SharedPreferences.Editor mEditor;
    public ConfigUtil mConfig;
    public ConfigDataBase serverData, networkData;
    public StatisticGraphData.DataTransferStats upDateBytes;
    protected VPNService mInjector;
    private final ServiceConnection mInjectorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName p1, IBinder p2) {
            mInjector = ((VPNService.MyBinder) p2).getService();
            mInjector.setInjectorListener(OpenVPNClientBase.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName p1) {
            mInjector = null;
        }
    };
    private OpenVPNService mBoundService = null;
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            OpenVPNClientBase.this.mBoundService = ((OpenVPNService.LocalBinder) service).getService();
            Log.d(OpenVPNClientBase.TAG, "CLIBASE: onServiceConnected: " + OpenVPNClientBase.this.mBoundService.toString());
            OpenVPNClientBase.this.mBoundService.client_attach(OpenVPNClientBase.this);
            OpenVPNClientBase.this.post_bind();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(OpenVPNClientBase.TAG, "CLIBASE: onServiceDisconnected");
            OpenVPNClientBase.this.mBoundService = null;
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder();
        StrictMode.setThreadPolicy(builder.permitAll().build());
        mPref = MyApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
        mConfig = ConfigUtil.getInstance(OpenVPNClientBase.this);
        serverData = new ConfigDataBase(OpenVPNClientBase.this, "mServerData");
        networkData = new ConfigDataBase(OpenVPNClientBase.this, "mNetwrokData");
        upDateBytes = StatisticGraphData.getStatisticData().getDataTransferStats();
        // new PiracyChecker(OpenVPNClientBase.this).enableInstallerId(InstallerID.GOOGLE_PLAY).start();
    }

    protected void doBindService() {
        bindService(new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_BIND), this.mConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, VPNService.class), mInjectorConnection, Context.BIND_AUTO_CREATE);
    }

    protected void doUnbindService() {
        Log.d(TAG, "CLIBASE: doUnbindService");
        if (this.mBoundService != null) {
            unbindService(this.mConnection);
            this.mBoundService = null;
        }
        if (mInjector != null) {
            unbindService(mInjectorConnection);
            mInjector = null;
        }
    }

    public void expand(final View v) {
        Animation a = expandAction(v);
        a.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                v.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        v.startAnimation(a);
    }

    private Animation expandAction(final View v) {
        v.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int targetedHeight = v.getMeasuredHeight();
        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1 ? ViewGroup.LayoutParams.WRAP_CONTENT : (int) (targetedHeight * interpolatedTime);
                v.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        a.setDuration((int) (targetedHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
        return a;
    }

    public void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        a.setDuration((int) (initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    @Override
    public void startOpenVPN() {
        // TODO: Implement this method
    }

    protected OpenVPNService.ConnectionStats get_connection_stats() {
        if (this.mBoundService != null) {
            return this.mBoundService.get_connection_stats();
        }
        return null;
    }

    public String get_gui_version(String name) {
        String versionName = "0.0";
        int versionCode = 0;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (Exception e) {
            Log.e(TAG, "cannot obtain version info", e);
        }
        return String.format("%s %s-%d", new Object[]{name, versionName, Integer.valueOf(versionCode)});
    }

    protected void submitConnectIntent(String profile_name, String server, String vpn_proto, String ipv6, String conn_timeout, String username, String password, boolean cache_password, String pk_password, String response, String epki_alias, String compression_mode, String proxy_name, String proxy_username, String proxy_password, boolean proxy_allow_creds_dialog, String gui_version) {
        String prefix = OpenVPNService.INTENT_PREFIX;
        Intent intent = new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_CONNECT).putExtra(prefix + ".PROFILE", profile_name).putExtra(prefix + ".GUI_VERSION", gui_version).putExtra(prefix + ".PROXY_NAME", proxy_name).putExtra(prefix + ".PROXY_USERNAME", proxy_username).putExtra(prefix + ".PROXY_PASSWORD", proxy_password).putExtra(prefix + ".PROXY_ALLOW_CREDS_DIALOG", proxy_allow_creds_dialog).putExtra(prefix + ".SERVER", server).putExtra(prefix + ".PROTO", vpn_proto).putExtra(prefix + ".IPv6", ipv6).putExtra(prefix + ".CONN_TIMEOUT", conn_timeout).putExtra(prefix + ".USERNAME", username).putExtra(prefix + ".PASSWORD", password).putExtra(prefix + ".CACHE_PASSWORD", cache_password).putExtra(prefix + ".PK_PASSWORD", pk_password).putExtra(prefix + ".RESPONSE", response).putExtra(prefix + ".EPKI_ALIAS", epki_alias).putExtra(prefix + ".COMPRESSION_MODE", compression_mode);
        if (this.mBoundService != null) {
            this.mBoundService.client_attach(this);
        }
        ComponentName serv = startService(intent);
        Log.d(TAG, "CLI: submitConnectIntent: " + profile_name);
    }

    protected void submitDisconnectIntent() {
        startService(new Intent(OpenVPNClientBase.this, VPNService.class).setAction(VPNService.STOP_SERVICE));
    }

    public void resolveExternalPkiAlias(final EpkiPost next_action) {
        final Handler handler = new Handler();
        new KeyChainAliasCallback() {
            public void alias(final String alias) {
                if (alias != null) {
                    handler.post(() -> next_action.post_dispatch(alias));
                }
            }
        };
        if (mConfig.getServerName().isEmpty()) {
            next_action.post_dispatch(null);
        } else {
            next_action.post_dispatch("DISABLE_CLIENT_CERT");
        }
    }

    protected void init_default_preferences(PrefUtil prefs) {
        prefs.set_boolean("google_dns_fallback", mConfig.getVpnDnsForward());
        prefs.set_string("compression_mode", mConfig.getCompression() ? "yes" : "no");
        prefs.set_string("vpn_proto", "adaptive");
        prefs.set_string("ipv6", "default");
        prefs.set_string("conn_timeout", "0");
        prefs.set_string("tls_version_min_override", "default");
        if (!prefs.contains_key("autostart_finish_on_connect")) {
            prefs.set_boolean("autostart_finish_on_connect", true);
        }
    }

    public void addlogInfo(String msg) {
        if (!msg.contains("EVENT")) TkLogStatus.logInfo(msg);
    }

    protected void post_bind() {
    }

    public void event(OpenVPNService.EventMsg ev) {
    }

    public void log(OpenVPNService.LogMsg lm) {
    }

    protected String resString(int res_id) {
        return getResources().getString(res_id);
    }

    /**
     * Tknetwork01/07/2024...
     */
    public JSONArray ServerArray() {
        try {
            //JSONObject js = new JSONObject();
            JSONArray jar = new JSONArray();
            JSONArray jarr = new JSONArray(serverData.getData());
            int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);


            for (int i = 0; i < jarr.length(); i++) {
                int serverType = jarr.getJSONObject(i).getInt("serverType");
                if ((tunnelRadio == 0 && serverType == 0) ||
                        (tunnelRadio == 2 && serverType == 1) ||
                        (tunnelRadio == 3 && serverType == 2) ||
                        (tunnelRadio == 1 && serverType == 4) ||
                        (tunnelRadio == 4 && serverType == 3)) {
                    jar.put(jarr.getJSONObject(i));
                }
            }


            /*if (jarr.length()>=2){
                js.put("Category",3);
                js.put("FLAG","random");
                js.put("Name","[Auto Select]");
                jar.put(js);
            }*/
            jar = JsonUtils.sort(jar, JsonUtils.getComparator(this, "Name", 1));
            return jar;
        } catch (JSONException e) {
            util.showSnackInfo(R.drawable.ic_error, "Error!", e.getMessage(), this);
        }
        return null;
    }

    /**
     * Tknetwork01/07/2024...
     */
    public JSONArray NetworkArray() {
        try {
            JSONArray jar = new JSONArray();
            JSONArray jarr = new JSONArray(networkData.getData());
            if (mPref.getBoolean("isRandom", false)) {
                jarr = JsonUtils.sort(jarr, JsonUtils.getComparator(this, "FLAG", 1));
                return jarr;
            }
            final int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);
            for (int i = 0; i < jarr.length(); i++) {
                JSONObject js = jarr.getJSONObject(i);
                int protoSpin = js.getInt("proto_spin");

                if (tunnelRadio == 0 &&
                        (protoSpin == 0 ||
                                protoSpin == 3 ||
                                protoSpin == 4 ||
                                protoSpin == 5)) {
                    jar.put(jarr.getJSONObject(i));
                } else if (tunnelRadio == 2 &&
                        (protoSpin == 0 ||
                                protoSpin == 3 ||
                                protoSpin == 4 ||
                                protoSpin == 5)) {
                    jar.put(jarr.getJSONObject(i));
                } else if (tunnelRadio == 3 && protoSpin == 2) {
                    jar.put(jarr.getJSONObject(i));
                } else if (tunnelRadio == 1 && protoSpin == 1) {
                    jar.put(jarr.getJSONObject(i));
                } else if (tunnelRadio == 4 && protoSpin == 6) {
                    jar.put(jarr.getJSONObject(i));
                }
            }
            jar = JsonUtils.sort(jar, JsonUtils.getComparator(this, "FLAG", 1));
            return jar;
        } catch (JSONException e) {
            util.showSnackInfo(R.drawable.ic_error, "Error!", e.getMessage(), this);
        }
        return null;
    }

    private String getNetworkType(JSONObject js) {
        try {
            int protoSpin = js.getInt("proto_spin");
            if (protoSpin == 0) {
                return "HTTP";
            } else if (protoSpin == 1) {
                return "UDP";
            } else if (protoSpin == 2) {
                return "SLOWDNS";
            } else if (protoSpin == 3) {
                return "SSL";
            } else if (protoSpin == 4) {
                return "SSL+PAYLOAD";
            } else if (protoSpin == 5) {
                return "SSL+PAYLOAD+WS";
            } else if (protoSpin == 6) {
                return "V2RAY";
            }
        } catch (Exception e) {
            util.showSnackInfo(R.drawable.ic_error, "getNetworkType", e.getMessage(), OpenVPNClientBase.this);
        }
        return "";
    }

    /**
     * Tknetwork01/07/2024...
     */
    private String getServerType(JSONObject sjs, JSONObject pjs) {
        try {
            mEditor.putString("mServerType", pjs.getString("server_type")).apply();
            if (pjs.getString("server_type").equals("cf")) {
                return sjs.getString("ServerIP");
            } else if (pjs.getString("server_type").equals("ws")) {
                return sjs.getString("ServerCloudFront");
            } else if (pjs.getString("server_type").equals("http")) {
                return sjs.getString("ServerHTTP");
            }
            util.showSnackInfo(R.drawable.ic_error, "Oppss!", "Server error", OpenVPNClientBase.this);
            return null;
        } catch (Exception e) {
            util.showSnackInfo(R.drawable.ic_error, "getServerType", e.getMessage(), OpenVPNClientBase.this);
        }
        return null;
    }

    private String t1() {
        int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);
        return switch (tunnelRadio) {
            case 1 -> SERVER_TYPE_SSH;
            case 2 -> SERVER_TYPE_DNS;
            case 3 -> SERVER_TYPE_v2ray;
            case 4 -> SERVER_TYPE_UDP_HYSTERIA_V1;
            default -> SERVER_TYPE_OVPN;
        };
    }

    private String t() {
        if (mPref.getInt("manual_tunnel_radio", 0) == 0) {
            return SERVER_TYPE_OVPN;
        } else if (mPref.getInt("manual_tunnel_radio", 0) == 2) {
            return SERVER_TYPE_SSH;
        } else if (mPref.getInt("manual_tunnel_radio", 0) == 3) {
            return SERVER_TYPE_DNS;
        } else if (mPref.getInt("manual_tunnel_radio", 0) == 1) {
            return SERVER_TYPE_UDP_HYSTERIA_V1;
        } else if (mPref.getInt("serverType", 0) == 3) {
            return SERVER_TYPE_v2ray;
        }
        return SERVER_TYPE_OVPN;
    }

    /**
     * Tknetwork01/07/2024...
     */
    public boolean reLoad_Configs() {
        try {
            final int tunnelRadio = mPref.getInt("manual_tunnel_radio", 0);
            int mRandomServerIndex;
            JSONArray jarr1 = ServerArray();
            JSONArray jarr2 = NetworkArray();
            if (jarr1.length() == 0 || jarr2.length() == 0) {
                return false;
            }
            JSONObject main_js = jarr1.getJSONObject(mPref.getInt(SERVER_POSITION, 0));
            if (main_js.getString("Name").equals("[Auto Select]")) {
                mRandomServerIndex = new Random().nextInt(jarr1.length());
                if (mRandomServerIndex == 0) mRandomServerIndex = 1;
                mEditor.putBoolean("isRandom", true).apply();
            } else {
                mRandomServerIndex = mPref.getInt(SERVER_POSITION, 0);
                mEditor.putBoolean("isRandom", false).apply();
            }
            JSONObject s_js = jarr1.getJSONObject(mRandomServerIndex);
            JSONObject p_js = jarr2.getJSONObject(mPref.getInt(NETWORK_POSITION, 0));
            String serType = t();
            String netType = getNetworkType(p_js);
            mConfig.setServerType(serType);
            String mHost = getServerType(s_js, p_js);
            mConfig.setIsQueryMode(false);
            mEditor.putString("Network_info", "").apply();
            mConfig.setProxyHost(FileUtils.hideJson("127.0.0.1"));
            mConfig.setProxyPort("8989");
            if (serType.equals(SERVER_TYPE_UDP_HYSTERIA_V1)) {
                if (mHost.isEmpty() || mHost == null) return false;
                mConfig.setServerName(s_js.getString("Name"));
                mConfig.setServerHost(mHost);
                mConfig.setServerPort("20000-50000");
                mConfig.setUDPConfig(p_js.getString("NetworkPayload"));
                mConfig.setConfigIsAutoLogIn(s_js.getBoolean("AutoLogIn"));
                if (s_js.getBoolean("AutoLogIn")) {
                    mConfig.setUser(s_js.getString("Username"));
                    mConfig.setUserPass(s_js.getString("Password"));
                } else {
                    mConfig.setUser(FileUtils.hideJson(mPref.getString("_screenUsername_key", "")));
                    mConfig.setUserPass(FileUtils.hideJson(mPref.getString("_screenPassword_key", "")));
                }
                mEditor.putString("IPHunter_pName", p_js.getString("Name")).apply();
                if (p_js.has("Info") && !p_js.getString("Info").isEmpty()) {
                    mEditor.putString("Network_info", p_js.getString("Info")).apply();
                }
                mConfig.setPayloadName(p_js.getString("Name"));
                return true;
            }
            if (tunnelRadio == 4) {
                if (s_js.has("ServerCloudFront")) {
                    mConfig.setV2((s_js.getString("ServerCloudFront")));
                }
                mConfig.setConfigIsAutoLogIn(s_js.getBoolean("AutoLogIn"));
                if (s_js.getBoolean("AutoLogIn")) {
                    mConfig.setUser(s_js.getString("Username"));
                    mConfig.setUserPass(s_js.getString("Password"));
                } else {
                    mConfig.setUser(FileUtils.hideJson(mPref.getString("_screenUsername_key", "")));
                    mConfig.setUserPass(FileUtils.hideJson(mPref.getString("_screenPassword_key", "")));
                }
                mEditor.putString("IPHunter_pName", s_js.getString("Name")).apply();
                if (p_js.has("Info") && !p_js.getString("Info").isEmpty()) {
                    mEditor.putString("Network_info", p_js.getString("Info")).apply();
                }
                mConfig.setPayloadName(p_js.getString("Name"));
                return true;
            }
            if (serType.equals(SERVER_TYPE_DNS)) {
                mConfig.setServerName(s_js.getString("Name"));
                mConfig.setServerHost(FileUtils.hideJson("127.0.0.1"));
                mConfig.setServerPort("2222");
                mConfig.setDNSpublicKey(s_js.getString("ServerCloudFront"));
                mConfig.setDNSnameServer(s_js.getString("ServerIP"));
                mConfig.setDNSaddress(p_js.getString("NetworkPayload"));
                mConfig.setConfigIsAutoLogIn(s_js.getBoolean("AutoLogIn"));
                if (s_js.getBoolean("AutoLogIn")) {
                    mConfig.setUser(s_js.getString("Username"));
                    mConfig.setUserPass(s_js.getString("Password"));
                } else {
                    mConfig.setUser(FileUtils.hideJson(mPref.getString("_screenUsername_key", "")));
                    mConfig.setUserPass(FileUtils.hideJson(mPref.getString("_screenPassword_key", "")));
                }
                mEditor.putString("IPHunter_pName", p_js.getString("Name")).apply();
                if (p_js.has("Info") && !p_js.getString("Info").isEmpty()) {
                    mEditor.putString("Network_info", p_js.getString("Info")).apply();
                }
                mConfig.setPayloadName(p_js.getString("Name"));
                return true;
            }
            if (serType.equals(SERVER_TYPE_OVPN)) {
                if (s_js.has("MultiCert")) {
                    mConfig.setOvpnCert(s_js.getBoolean("MultiCert") ? s_js.getString("ovpnCertificate") : mPref.getString(OpenVPN_CERT, ""));
                } else {
                    mConfig.setOvpnCert(mPref.getString(OpenVPN_CERT, ""));
                }
            }
            if (netType.equals("HTTP")) {
                if (mHost.isEmpty() || mHost == null) return false;
                String proxy = p_js.getString("SquidProxy").isEmpty() ? mHost : p_js.getString("SquidProxy");
                String SquidProxy = p_js.getBoolean("UseDefProxy") ? mHost : proxy;
                String front_query = p_js.getString("NetworkFrontQuery");
                String back_query = p_js.getString("NetworkBackQuery");
                mConfig.setPaylodType(PAYLOAD_TYPE_HTTP_PROXY);
                mEditor.putBoolean(isAutoReplace, p_js.has("AutoReplace") ? p_js.getBoolean("AutoReplace") : false).apply();
                mConfig.setServerHost(mHost);
                mConfig.setProxyHost(SquidProxy);
                mConfig.setPayload(p_js.getString("NetworkPayload"));
                if (front_query.isEmpty() && back_query.isEmpty()) {
                    mConfig.setIsQueryMode(false);
                } else if (!back_query.isEmpty()) {
                    mConfig.setIsQueryMode(true);
                    mConfig.setBackQuery(back_query);
                    mConfig.setFrontQuery("");
                } else if (!front_query.isEmpty()) {
                    mConfig.setIsQueryMode(true);
                    mConfig.setFrontQuery(front_query);
                    mConfig.setBackQuery("");
                }
                if (s_js.has("ProxyPort")) {
                    String port = p_js.getString("SquidPort").isEmpty() ? s_js.getString("ProxyPort") : p_js.getString("SquidPort");
                    String SquidPort = p_js.getBoolean("UseDefProxy") ? s_js.getString("ProxyPort") : port;
                    mConfig.setProxyPort(SquidPort);
                } else {
                    mConfig.setProxyPort(p_js.getString("SquidPort"));
                }
                if (s_js.getString("TcpPort").contains(":")) {
                    String[] split = s_js.getString("TcpPort").split(":");
                    mConfig.setServerPort(split[0]);
                } else {
                    mConfig.setServerPort(s_js.getString("TcpPort"));
                }
                if (p_js.getString("Name").contains("Direct") || p_js.getString("Name").contains("direct")) {
                    if (serType.equals(SERVER_TYPE_OVPN)) {
                        if (p_js.getString("NetworkPayload").isEmpty()) {
                            mConfig.setPaylodType(PAYLOAD_TYPE_DIRECT);
                        } else {
                            mConfig.setPaylodType(PAYLOAD_TYPE_DIRECT_PAYLOAD);
                        }
                    }
                    if (serType.equals(SERVER_TYPE_SSH)) {
                        if (p_js.getString("NetworkPayload").isEmpty()) {
                            mConfig.setPaylodType(PAYLOAD_TYPE_DIRECT);
                        } else {
                            mConfig.setPaylodType(PAYLOAD_TYPE_DIRECT_PAYLOAD);
                            if (s_js.getString("TcpPort").contains(":")) {
                                String[] split = s_js.getString("TcpPort").split(":");
                                mConfig.setServerPort(split[1]);
                            } else {
                                mConfig.setServerPort(s_js.getString("TcpPort"));
                            }
                        }
                    }
                }
            }
            if (netType.equals("SSL")) {
                if (mHost.isEmpty() || mHost == null) return false;
                String sslPort = p_js.getString("SSLPort").isEmpty() ? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                mConfig.setPaylodType(PAYLOAD_TYPE_SSL);
                mConfig.setSni(p_js.getString("SSLSNI"));
                mConfig.setServerHost(mHost);
                mConfig.setServerPort(sslPort);
                
            }
            if (netType.equals("SSL+PAYLOAD")) {
                if (mHost.isEmpty() || mHost == null) return false;
                mConfig.setPaylodType(PAYLOAD_TYPE_SSL_PAYLOAD);
                String sslPort = p_js.getString("SSLPort").isEmpty() ? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                mConfig.setSni(p_js.getString("SSLSNI"));
                mConfig.setPayload(p_js.getString("SSLPayload"));
                mConfig.setServerHost(mHost);
                mConfig.setServerPort(sslPort);
            }
            if (netType.equals("SSL+PAYLOAD+WS")) {
                if (mHost.isEmpty() || mHost == null) return false;
                mConfig.setPaylodType(PAYLOAD_TYPE_SSL_PROXY);
                String proxy = p_js.getString("SquidProxy").isEmpty() ? mHost : p_js.getString("SquidProxy");
                String SquidProxy = p_js.getBoolean("UseDefProxy") ? mHost : proxy;
                String sslPort = p_js.getString("SSLPort").isEmpty() ? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                mConfig.setSni(p_js.getString("SSLSNI"));
                mConfig.setPayload(p_js.getString("SSLPayload"));
                mConfig.setServerHost(mHost);
                mConfig.setServerPort(sslPort);
                mConfig.setProxyHost(SquidProxy);
                if (s_js.has("ProxyPort")) {
                    String port = p_js.getString("SquidPort").isEmpty() ? s_js.getString("ProxyPort") : p_js.getString("SquidPort");
                    String SquidPort = p_js.getBoolean("UseDefProxy") ? s_js.getString("ProxyPort") : port;
                    mConfig.setProxyPort(SquidPort);
                } else {
                    mConfig.setProxyPort(p_js.getString("SquidPort"));
                }
            }
            if (p_js.getString("Name").contains("OVPN_UDP") || p_js.getString("Name").contains("OVPN UDP") || p_js.getString("Name").contains("ovpn_udp") || p_js.getString("Name").contains("ovpn udp")) {
                if (mHost.isEmpty() || mHost == null) return false;
                mConfig.setPaylodType(PAYLOAD_TYPE_OVPN_UDP);
                mConfig.setServerHost(mHost);
                if (s_js.getString("TcpPort").contains(":")) {
                    String port = s_js.getString("TcpPort").split(":")[1];
                    mConfig.setServerPort(port);
                } else {
                    mConfig.setServerPort("53");
                }
            }
            mConfig.setServerName(s_js.getString("Name"));
            mConfig.setPayloadName(p_js.getString("Name"));
            if (s_js.getBoolean("AutoLogIn")) {
                mConfig.setUser(s_js.getString("Username"));
                mConfig.setUserPass(s_js.getString("Password"));
            } else {
                mConfig.setUser(FileUtils.hideJson(mPref.getString("_screenUsername_key", "")));
                mConfig.setUserPass(FileUtils.hideJson(mPref.getString("_screenPassword_key", "")));
            }
            mEditor.putString("IPHunter_pName", p_js.getString("Name")).apply();
            mConfig.setConfigIsAutoLogIn(s_js.getBoolean("AutoLogIn"));
            if (p_js.has("Info") && !p_js.getString("Info").isEmpty()) {
                mEditor.putString("Network_info", p_js.getString("Info")).apply();
            }
            return true;
        } catch (Exception e) {
            util.showSnackInfo(R.drawable.ic_error, "Error!", e.getMessage(), this);
            return false;
        }
    }

    protected interface EpkiPost {
        void post_dispatch(String str);
    }

}
