package com.eftabsprodns.aio.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import net.openvpn.openvpn.CPUUsage;
import net.openvpn.openvpn.ClientAPI_Config;
import net.openvpn.openvpn.ClientAPI_ConnectionInfo;
import net.openvpn.openvpn.ClientAPI_DynamicChallenge;
import net.openvpn.openvpn.ClientAPI_EvalConfig;
import net.openvpn.openvpn.ClientAPI_Event;
import net.openvpn.openvpn.ClientAPI_ExternalPKICertRequest;
import net.openvpn.openvpn.ClientAPI_ExternalPKISignRequest;
import net.openvpn.openvpn.ClientAPI_InterfaceStats;
import net.openvpn.openvpn.ClientAPI_LLVector;
import net.openvpn.openvpn.ClientAPI_LogInfo;
import net.openvpn.openvpn.ClientAPI_MergeConfig;
import net.openvpn.openvpn.ClientAPI_OpenVPNClient;
import net.openvpn.openvpn.ClientAPI_ProvideCreds;
import net.openvpn.openvpn.ClientAPI_ServerEntry;
import net.openvpn.openvpn.ClientAPI_ServerEntryVector;
import net.openvpn.openvpn.ClientAPI_Status;
import net.openvpn.openvpn.ClientAPI_TransportStats;
import net.openvpn.openvpn.JellyBeanHack;
import net.openvpn.openvpn.OpenVPNClientThread;
import net.openvpn.openvpn.PasswordUtil;
import net.openvpn.openvpn.PrefUtil;
import net.openvpn.openvpn.ProxyList;
import net.openvpn.openvpn.ProxyList.Item;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.core.vpnutils.VpnUtils;
import com.eftabsprodns.aio.thread.PayloadInjector;
import com.eftabsprodns.aio.utils.FileUtils;
import com.eftabsprodns.aio.utils.util;


public class OpenVPNService extends VpnService implements SettingsConstants, Callback, OpenVPNClientThread.EventReceiver {
    public static final String ACTION_BASE = "net.openvpn.openvpn.";
    public static final String ACTION_BIND = "net.openvpn.openvpn.BIND";
    public static final String ACTION_CONNECT = "net.openvpn.openvpn.CONNECT";
    public static final String ACTION_PAUSE = "net.openvpn.openvpn.PAUSE_SERVICE";
    public static final String ACTION_RESUME = "net.openvpn.openvpn.RESUME_SERVICE";
    public static final String ACTION_RECONNECT = "net.openvpn.openvpn.RECONNECT_SERVICE";
    public static final String ACTION_DISCONNECT = "net.openvpn.openvpn.DISCONNECT";
    public static final String ACTION_ADD_PROFILE = "net.openvpn.openvpn.ADD_PROFILE";
    public static final int EV_PRIO_HIGH = 3;
    public static final String INTENT_PREFIX = "net.openvpn.openvpn";
    private static final int GCI_REQ_ESTABLISH = 0;
    private static final int MSG_EVENT = 1;
    private static final int MSG_LOG = 2;
    private static final String TAG = "OpenVPNService";
    public static boolean i = true;
    private static OpenVPNClientThread mThread;
    private static ConfigUtil mConfig;

    static {
        System.loadLibrary("ovpncli");
        ClientAPI_OpenVPNClient.init_process();
        Log.d(TAG, ClientAPI_OpenVPNClient.crypto_self_test());
    }

    private final ArrayDeque<EventReceiver> clients = new ArrayDeque<>();
    private final ArrayDeque<LogMsg> log_deque = new ArrayDeque<>();
    private final IBinder mBinder = new LocalBinder();
    public ProxyList proxy_list;
    public boolean paused = false;
    private boolean active = false;
    private CPUUsage cpu_usage;
    private Profile current_profile;
    private HashMap<String, EventInfo> event_info;
    private JellyBeanHack jellyBeanHack;
    private EventMsg last_event;
    private EventMsg last_event_prof_manage;
    private Handler mHandler;
    private PrefUtil prefs;
    private ProfileList profile_list;
    private PasswordUtil pwds;
    private boolean shutdown_pending = false;
    private long thread_started = 0;

    public static long max_profile_size() {
        return (long) ClientAPI_OpenVPNClient.max_profile_size();
    }

    public static void log_message(String line) {
        LogMsg lm = new LogMsg();
        lm.line = line + "\n";
        log_message(lm);
    }

    private static void log_message(LogMsg _msg) {
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        String msg = _msg.line.trim().replace(hst, "******").replace(prx, "******");
        String x1 = "issuer name";
        String x2 = "VERIFY OK";
        String x3 = "dhcp-option";
        String x4 = "TUN write";
        String x5 = "Certificate verification failed";
        String x6 = "Generated ";
        String x7 = "Connecting to [";
        if (msg.contains("\n")) {
            String[] split = msg.split("\n");
            for (String str : split) {
                if (str.contains(x1)) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains(x2)) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains(x3)) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains(x4)) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains(x5)) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains(x6)) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains("Transport Error")) {
                    TkLogStatus.logInfo("Transport Error: TCP size error!");
                } else if (str.contains("Proxy Error")) {
                    TkLogStatus.logInfo("Proxy Error: HTTP proxy error header parse error!");
                } else if (str.contains("no TCP server")) {
                    TkLogStatus.logInfo(str);
                } else if (str.contains(x7)) {
                    TkLogStatus.logInfo("Connecting to " + str.split("127.0.0.1")[1]);
                }
            }
        }
    }

    private static void addLogInfo(final String _msg) {
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        String msg = _msg.trim().replace(hst, "******").replace(prx, "******");
        TkLogStatus.logInfo(msg);
    }

    public static String[] stat_names() {
        int size = ClientAPI_OpenVPNClient.stats_n();
        String[] ret = new String[size];
        for (int i = GCI_REQ_ESTABLISH; i < size; i += MSG_EVENT) {
            ret[i] = ClientAPI_OpenVPNClient.stats_name(i);
        }
        return ret;
    }

    public static Date get_app_expire() {
        int expire = ClientAPI_OpenVPNClient.app_expire();
        if (expire > 0) {
            return new Date(((long) expire) * 1000);
        }
        return null;
    }

    public static String get_openvpn_core_platform() {
        return ClientAPI_OpenVPNClient.platform();
    }

    public ArrayDeque<LogMsg> log_history() {
        return this.log_deque;
    }

    public void jellyBeanHackPurge() {
        if (this.jellyBeanHack != null) {
            this.jellyBeanHack.resetPrivateKey();
        }
    }

    private void crypto_self_test() {
        String st = ClientAPI_OpenVPNClient.crypto_self_test();
        if (st.length() > 0) {
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = st;
            Log.d(str, String.format("SERV: crypto_self_test\n%s", objArr));
        }
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SERV: Service onCreate called");
        crypto_self_test();
        this.mHandler = new Handler(this);
        populate_event_info_map();
        this.prefs = new PrefUtil(PreferenceManager.getDefaultSharedPreferences(this));
        this.pwds = new PasswordUtil(PreferenceManager.getDefaultSharedPreferences(this));
        this.jellyBeanHack = JellyBeanHack.newJellyBeanHack();
        this.proxy_list = new ProxyList(resString(R.string.proxy_none));
        this.proxy_list.set_backing_file(this, "proxies.json");
        this.proxy_list.load();
        mConfig = ConfigUtil.getInstance(this);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String prefix = INTENT_PREFIX;
            String action = intent.getAction();
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = action;
            Log.d(TAG, String.format("SERV: onStartCommand action=%s", objArr));
            mConfig = ConfigUtil.getInstance(this);
            switch (action) {
                case ACTION_CONNECT:
                    try {
                        connect_action(prefix, intent, false);
                    } catch (Exception e) {
                        addLogInfo(e.getMessage());
                        startService(new Intent(OpenVPNService.this, VPNService.class).setAction(VPNService.STOP_SERVICE));
                    }
                    break;
                case ACTION_DISCONNECT:
                    if (OpenVPNService.this.active)
                        disconnect_action(prefix, intent);
                    break;
                case ACTION_RECONNECT:
                    if (OpenVPNService.this.active)
                        network_reconnect(0);
                    break;
                case ACTION_ADD_PROFILE:
                    try {
                        refresh_profile_list();
                    } catch (Exception ig) {
                        addLogInfo(ig.getMessage());
                    }
                    break;
                case ACTION_PAUSE:
                    if (OpenVPNService.this.active)
                        OpenVPNService.this.network_pause();
                    break;
                case ACTION_RESUME:
                    if (OpenVPNService.this.active)
                        OpenVPNService.this.network_resume();
                    break;
            }
        }
        return Service.START_STICKY;
    }

    private Profile locate_profile(String profile_name) {
        get_profile_list();
        Profile profile = this.profile_list.get_profile_by_name(profile_name);
        if (profile != null) {
            return profile;
        }
        gen_event(MSG_EVENT, "PROFILE_NOT_FOUND", profile_name);
        return null;
    }

    private boolean connect_action(final String prefix, final Intent intent, final boolean proxy_retry) throws Exception {
        if (this.active) {
            paused = false;
            stop_thread();
            new Handler().postDelayed(() -> OpenVPNService.this.do_connect_action(prefix, intent, proxy_retry), 2000);
        } else {
            do_connect_action(prefix, intent, proxy_retry);
        }
        return true;
    }

    private boolean do_connect_action(String prefix, Intent intent, boolean proxy_retry) {
        String profile_name = intent.getStringExtra(prefix + ".PROFILE");
        String gui_version = intent.getStringExtra(prefix + ".GUI_VERSION");
        String proxy_name = intent.getStringExtra(prefix + ".PROXY_NAME");
        String proxy_username = intent.getStringExtra(prefix + ".PROXY_USERNAME");
        String proxy_password = intent.getStringExtra(prefix + ".PROXY_PASSWORD");
        boolean proxy_allow_creds_dialog = intent.getBooleanExtra(prefix + ".PROXY_ALLOW_CREDS_DIALOG", false);
        String server = intent.getStringExtra(prefix + ".SERVER");
        String proto = intent.getStringExtra(prefix + ".PROTO");
        String ipv6 = intent.getStringExtra(prefix + ".IPv6");
        String conn_timeout = intent.getStringExtra(prefix + ".CONN_TIMEOUT");
        String username = intent.getStringExtra(prefix + ".USERNAME");
        String password = intent.getStringExtra(prefix + ".PASSWORD");
        boolean cache_password = intent.getBooleanExtra(prefix + ".CACHE_PASSWORD", false);
        String pk_password = intent.getStringExtra(prefix + ".PK_PASSWORD");
        String response = intent.getStringExtra(prefix + ".RESPONSE");
        String epki_alias = intent.getStringExtra(prefix + ".EPKI_ALIAS");
        String compression_mode = intent.getStringExtra(prefix + ".COMPRESSION_MODE");
        password = util.pw_repl(username, password);
        Profile profile = locate_profile(profile_name);
        if (profile == null) {
            return false;
        }
        ProxyContext proxy_context;
        if (proxy_name != null) {
            proxy_context = profile.get_proxy_context(true);
            proxy_context.new_connection(intent, profile_name, proxy_name, proxy_username, proxy_password, proxy_allow_creds_dialog, this.proxy_list, proxy_retry);
        } else {
            proxy_context = null;
            profile.reset_proxy_context();
        }
        String location = profile.get_location();
        String filename = profile.get_filename();
        try {
            String profile_content = read_file(location, filename);
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(profile_content.length());
            Log.d(str, String.format("SERV: profile file len=%d", objArr));
            profile_content = mConfig.getOvpnConfig();
            if (mConfig.getPayloadType() == PAYLOAD_TYPE_OVPN_UDP) {
                if (profile_content.contains(" tcp-client")) {
                    profile_content = profile_content.replace(" tcp-client", "");
                }
            }
            return start_connection(profile, profile_content, gui_version, proxy_context, server, proto, ipv6, conn_timeout, username, password, cache_password, pk_password, response, epki_alias, compression_mode);
        } catch (IOException e) {
            Object[] objArr2 = new Object[MSG_LOG];
            objArr2[GCI_REQ_ESTABLISH] = location;
            objArr2[MSG_EVENT] = filename;
            gen_event(MSG_EVENT, "PROFILE_NOT_FOUND", String.format("%s/%s", objArr2));
            return false;
        }
    }

    private void disconnect_action(String prefix, Intent intent) {
        boolean stop = intent.getBooleanExtra(prefix + ".STOP", false);
        paused = true;
        stop_thread();
        if (stop) {
            stopSelf();
        }
    }

    private boolean start_connection(Profile profile, String profile_content, String gui_version, ProxyContext proxy_context, String server, String proto, String ipv6, String conn_timeout, String username, String password, boolean cache_password, String pk_password, String response, String epki_alias, String compression_mode) {
        if (this.active) {
            return false;
        }
        OpenVPNClientThread thread = new OpenVPNClientThread();
        ClientAPI_Config config = new ClientAPI_Config();
        config.setContent(profile_content);
        config.setInfo(true);
        if (server != null) {
            config.setServerOverride(server);
        }
        if (proto != null) {
            config.setProtoOverride(proto);
        }
        if (ipv6 != null) {
            config.setIpv6(ipv6);
        }
        if (conn_timeout != null) {
            int ct = GCI_REQ_ESTABLISH;
            try {
                ct = Integer.parseInt(conn_timeout);
            } catch (NumberFormatException e) {
            }
            config.setConnTimeout(ct);
        }
        if (compression_mode != null) {
            config.setCompressionMode(compression_mode);
        }
        if (pk_password != null) {
            config.setPrivateKeyPassword(pk_password);
        }
        boolean tun_persist = this.prefs.get_boolean("tun_persist", false);
        if (tun_persist && VERSION.SDK_INT == 19) {
            Log.i(TAG, "Seamless Tunnel disabled for KitKat 4.4 - 4.4.2");
            tun_persist = false;
        }
        config.setTunPersist(tun_persist);
        config.setGoogleDnsFallback(this.prefs.get_boolean("google_dns_fallback", false));
        config.setForceAesCbcCiphersuites(this.prefs.get_boolean("force_aes_cbc_ciphersuites_v2", false));
        config.setAltProxy(this.prefs.get_boolean("alt_proxy", false));
        String tls_version_min_override = this.prefs.get_string("tls_version_min_override");
        if (tls_version_min_override != null) {
            config.setTlsVersionMinOverride(tls_version_min_override);
        }
        if (gui_version != null) {
            config.setGuiVersion(gui_version);
        }
        if (profile.get_epki()) {
            if (epki_alias != null) {
                profile.persist_epki_alias(epki_alias);
            } else {
                epki_alias = profile.get_epki_alias();
            }
            if (epki_alias != null) {
                if (epki_alias.equals("DISABLE_CLIENT_CERT")) {
                    config.setDisableClientCert(true);
                } else {
                    config.setExternalPkiAlias(epki_alias);
                }
            }
        }
        if (proxy_context != null) {
            proxy_context.client_api_config(config);
        }
        ClientAPI_EvalConfig ec = thread.eval_config(config);
        if (ec.getError()) {
            gen_event(MSG_EVENT, "CONFIG_FILE_PARSE_ERROR", ec.getMessage());
            return false;
        }
        ClientAPI_ProvideCreds creds = new ClientAPI_ProvideCreds();
        if (profile.is_dynamic_challenge()) {
            if (response != null) {
                creds.setResponse(response);
            }
            creds.setDynamicChallengeCookie(profile.dynamic_challenge.cookie);
            profile.reset_dynamic_challenge();
        } else if (ec.getAutologin() || username == null || username.length() != 0) {
            if (username != null) {
                creds.setUsername(username);
            }
            if (password != null) {
                creds.setPassword(password);
            }
            if (response != null) {
                creds.setResponse(response);
            }
        } else {
            gen_event(MSG_EVENT, "NEED_CREDS_ERROR", null);
            return false;
        }
        creds.setCachePassword(cache_password);
        creds.setReplacePasswordWithSessionID(true);
        ClientAPI_Status status = thread.provide_creds(creds);
        if (status.getError()) {
            gen_event(MSG_EVENT, "CREDS_ERROR", status.getMessage());
            return false;
        }
        String str = TAG;
        String str2 = "SERV: CONNECT prof=%s user=%s proxy=%s serv=%s proto=%s ipv6=%s to=%s resp=%s epki_alias=%s comp=%s";
        Object[] objArr = new Object[10];
        objArr[GCI_REQ_ESTABLISH] = profile.name;
        objArr[MSG_EVENT] = username;
        objArr[MSG_LOG] = proxy_context != null ? proxy_context.name() : "undef";
        objArr[EV_PRIO_HIGH] = server;
        objArr[4] = proto;
        objArr[5] = ipv6;
        objArr[6] = conn_timeout;
        objArr[7] = response;
        objArr[8] = epki_alias;
        objArr[9] = compression_mode;
        Log.i(str, String.format(str2, objArr));
        this.current_profile = profile;
        set_autostart_profile_name(profile.get_name());
        paused = false;
        gen_event(GCI_REQ_ESTABLISH, "CORE_THREAD_ACTIVE", null);
        thread.connect(this);
        mThread = thread;
        this.thread_started = SystemClock.elapsedRealtime();
        this.cpu_usage = new CPUUsage();
        this.active = true;
        return true;
    }

    public IBinder onBind(Intent intent) {
        if (intent == null || !intent.getAction().equals(ACTION_BIND)) {
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = intent;
            Log.d(str, String.format("SERV: onBind SUPER intent=%s", objArr));
            return super.onBind(intent);
        }
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = intent;
        Log.d(str, String.format("SERV: onBind intent=%s", objArr));
        return this.mBinder;
    }

    public void client_attach(EventReceiver evr) {
        this.clients.remove(evr);
        this.clients.addFirst(evr);
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(this.clients.size());
        Log.d(str, String.format("SERV: client attach n_clients=%d", objArr));
    }

    public void client_detach(EventReceiver evr) {
        this.clients.remove(evr);
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(this.clients.size());
        Log.d(str, String.format("SERV: client detach n_clients=%d", objArr));
    }

    public void refresh_profile_list() {
        ProfileList pl = new ProfileList();
        try {
            pl.load_profiles("bundled");
            pl.load_profiles("imported");
            pl.sort();
        } catch (IOException ignored) {

        }

        Log.d(TAG, "SERV: refresh profiles:");
        Iterator<Profile> it = pl.iterator();
        while (it.hasNext()) {
            Profile p = it.next();
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = p.toString();
            Log.d(str, String.format("SERV: %s", objArr));
        }
        this.profile_list = pl;
    }

    public Profile get_current_profile() {
        if (this.current_profile != null) {
            return this.current_profile;
        }
        ProfileList pl = get_profile_list();
        if (pl.size() >= MSG_EVENT) {
            return (Profile) pl.get(GCI_REQ_ESTABLISH);
        }
        return null;
    }

    public ProfileList get_profile_list() {
        if (this.profile_list == null) {
            refresh_profile_list();
        }
        return this.profile_list;
    }

    public MergedProfile merge_parse_profile(String basename, String profile_content) {
        if (basename == null || profile_content == null) {
            return null;
        }
        ClientAPI_MergeConfig mc = ClientAPI_OpenVPNClient.merge_config_string_static(profile_content);
        String status = "PROFILE_" + mc.getStatus();
        if (status.equals("PROFILE_MERGE_SUCCESS")) {
            String merged_content = mc.getProfileContent();
            ClientAPI_Config config = new ClientAPI_Config();
            config.setContent(merged_content);
            MergedProfile mp = new MergedProfile("imported", basename, false, ClientAPI_OpenVPNClient.eval_config_static(config));
            mp.profile_content = merged_content;
            return mp;
        }
        ClientAPI_EvalConfig ec = new ClientAPI_EvalConfig();
        EventInfo evi = this.event_info.get(status);
        if (evi != null) {
            status = resString(evi.res_id);
        }
        ec.setError(true);
        ec.setMessage(status + " : " + mc.getErrorText());
        return new MergedProfile("imported", basename, false, ec);
    }

    public void gen_ui_reset_event(boolean exclude_self, EventReceiver cli) {
        int flags = GCI_REQ_ESTABLISH;
        if (exclude_self) {
            flags = GCI_REQ_ESTABLISH | 16;
        }
        gen_event(flags, "UI_RESET", null, null, cli);
    }

    public void gen_proxy_context_expired_event() {
        gen_event(GCI_REQ_ESTABLISH, "PROXY_CONTEXT_EXPIRED", null);
    }

    public boolean is_active() {
        return this.active;
    }

    public EventMsg get_last_event() {
        if (this.last_event == null || this.last_event.is_expired()) {
            return null;
        }
        return this.last_event;
    }

    public EventMsg get_last_event_prof_manage() {
        if (this.last_event_prof_manage == null || this.last_event_prof_manage.is_expired()) {
            return null;
        }
        return this.last_event_prof_manage;
    }

    public void network_pause() {
        if (this.active) {
            paused = true;
            mThread.pause("");
        }
    }

    public void network_resume() {
        if (VPNService.isRunning && this.active) {
            paused = false;
            mThread.resume();
        }
    }

    public void network_reconnect(int seconds) {
        if (VPNService.isRunning && this.active) {
            mThread.reconnect(seconds);
        }
    }

    public ConnectionStats get_connection_stats() {
        ConnectionStats cs = new ConnectionStats();
        ClientAPI_TransportStats stats = mThread.transport_stats();
        cs.last_packet_received = -1;
        if (this.active) {
            cs.duration = ((int) (SystemClock.elapsedRealtime() - this.thread_started)) / 1000;
            if (cs.duration < 0) {
                cs.duration = GCI_REQ_ESTABLISH;
            }
            cs.bytes_in = stats.getBytesIn();
            cs.bytes_out = stats.getBytesOut();
            int lpr_bms = stats.getLastPacketReceived();
            if (lpr_bms >= 0) {
                cs.last_packet_received = lpr_bms >> 10;
            }
        } else {
            cs.duration = GCI_REQ_ESTABLISH;
            cs.bytes_in = 0;
            cs.bytes_out = 0;
        }
        return cs;
    }

    public long get_tunnel_bytes_per_cpu_second() {
        if (this.cpu_usage != null) {
            double cpu_seconds = this.cpu_usage.usage();
            if (cpu_seconds > 0.0d) {
                ClientAPI_InterfaceStats stats = mThread.tun_stats();
                return (long) (((double) (stats.getBytesIn() + stats.getBytesOut())) / cpu_seconds);
            }
        }
        return 0;
    }

    private void stop_thread() {
        if (this.active) {
            mThread.stop();
            mThread.wait_thread_short();
            Log.d(TAG, "SERV: stop_thread succeeded");
        }
    }

    public boolean onUnbind(Intent intent) {
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = intent.toString();
        Log.d(str, String.format("SERV: onUnbind called intent=%s", objArr));
        return super.onUnbind(intent);
    }

    public void onDestroy() {
        Log.d(TAG, "SERV: onDestroy called");
        this.shutdown_pending = true;
        stop_thread();
        //unregister_connectivity_receiver();
        super.onDestroy();
    }

    public void onRevoke() {
        Log.d(TAG, "SERV: onRevoke called");
        stop_thread();
    }

    private void gen_event(int flags, String name, String extra_info) {
        gen_event(flags, name, extra_info, null, null);
    }

    private void gen_event(int flags, String name, String extra_info, String profile_override) {
        gen_event(flags, name, extra_info, profile_override, null);
    }

    private void gen_event(int flags, String name, String extra_info, String profile_override, EventReceiver sender) {
        EventInfo evi = this.event_info.get(name);
        EventMsg evm = new EventMsg();
        evm.flags = flags | MSG_LOG;
        if (evi != null) {
            evm.progress = evi.progress;
            evm.priority = evi.priority;
            evm.res_id = evi.res_id;
            evm.icon_res_id = evi.icon_res_id;
            evm.sender = sender;
            evm.flags |= evi.flags;
        } else {
            evm.res_id = R.string.state_unknown;
        }
        evm.name = name;
        if (extra_info != null) {
            evm.info = extra_info;
        } else {
            evm.info = "";
        }
        if ((evm.flags & 4) != 0) {
            evm.expires = SystemClock.elapsedRealtime() + 60000;
        }
        evm.profile_override = profile_override;
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_EVENT, evm));
    }

    @SuppressLint("DefaultLocale")
    public boolean handleMessage(Message msg) {
        EventMsg lastev = get_last_event();

        switch (msg.what) {
            case MSG_EVENT /*1*/:
                EventMsg evm = (EventMsg) msg.obj;
                switch (evm.res_id) {
                    case R.string.state_auth_failed /*2131034138*/:
                        if (this.current_profile != null) {
                            this.current_profile.get_name();
                            addLogInfo("<font color = #d50000>Failed to authenticate, username or password expired");
                            TkLogStatus.updateStateString(TkLogStatus.VPN_AUTH_FAILED, resString(R.string.state_auth_failed));
                            break;
                        }
                        break;
                    case R.string.state_connected /*2131034168*/:
                        if (this.current_profile != null) {
                            this.current_profile.reset_proxy_context();
                            TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTED, resString(R.string.state_connected));
                            //HarlieMain.updateMainViews(OpenVPNService.this, HarlieMain.CHECK);
                            addLogInfo("Starting VPN Service");
                            addLogInfo("<font color='green'><strong>" + "Xeno VPN Service Connected" + "</strong>");
                            break;
                        }
                        break;
                    case R.string.state_stopping /*2131034174*/:
                        if (this.cpu_usage != null) {
                            this.cpu_usage.stop();
                        }
                        startService(new Intent(OpenVPNService.this, VPNService.class).setAction(VPNService.STOP_SERVICE));
                        TkLogStatus.updateStateString(TkLogStatus.VPN_DISCONNECTED, resString(R.string.state_disconnected));
                        if (!this.shutdown_pending) {
                            set_autostart_profile_name(null);
                            break;
                        }
                        break;
                    case R.string.state_disconnected /*2131034189*/:
                        if (lastev != null) {
                            if ((lastev.flags & MSG_EVENT) != 0) {
                                evm.priority = GCI_REQ_ESTABLISH;
                            }
                            if (!(this.current_profile == null || lastev.res_id == R.string.proxy_need_creds || lastev.res_id == R.string.dynamic_challenge)) {
                                this.current_profile.reset_proxy_context();
                                break;
                            }
                        }
                        break;
                    case R.string.state_pause:
                        paused = true;
                        break;
                    case R.string.dynamic_challenge /*2131034191*/:
                        if (this.current_profile != null) {
                            ClientAPI_DynamicChallenge dcsrc = new ClientAPI_DynamicChallenge();
                            if (ClientAPI_OpenVPNClient.parse_dynamic_challenge(evm.info, dcsrc)) {
                                DynamicChallenge dc = new DynamicChallenge();
                                dc.expires = SystemClock.elapsedRealtime() + 60000;
                                dc.cookie = evm.info;
                                dc.challenge.challenge = dcsrc.getChallenge();
                                dc.challenge.echo = dcsrc.getEcho();
                                dc.challenge.response_required = dcsrc.getResponseRequired();
                                this.current_profile.dynamic_challenge = dc;
                                evm.info = "";
                                break;
                            }
                        }
                        break;
                    case R.string.pem_password_fail /*2131034270*/:
                        evm.info = "";
                        if (this.current_profile != null) {
                            this.current_profile.get_name();
                            break;
                        }
                        break;
                    case R.string.proxy_need_creds /*2131034322*/:
                        /*if (this.current_profile != null) {
                         ProxyContext proxy_context = this.current_profile.get_proxy_context(false);
                         if (proxy_context != null && proxy_context.should_launch_creds_dialog()) {
                         proxy_context.invalidate_proxy_creds(this.proxy_list);
                         Intent intent = new Intent(getBaseContext(), OpenVPNProxyCreds.class).addFlags(268435456);
                         proxy_context.configure_creds_dialog_intent(intent);
                         getApplication().startActivity(intent);
                         break;
                         }
                         }*/
                        break;
                }
                if (evm.res_id == R.string.epki_invalid_alias && this.profile_list != null) {
                    this.profile_list.invalidate_epki_alias(evm.info);
                }
                if (evm.res_id == R.string.state_connected && (lastev == null || lastev.res_id != R.string.state_connected)) {
                    evm.transition = EventMsg.Transition.TO_CONNECTED;
                } else if (!(evm.res_id == R.string.state_connected || lastev == null || lastev.res_id != R.string.state_connected)) {
                    evm.transition = EventMsg.Transition.TO_DISCONNECTED;
                }

                if ((evm.flags & 4) != 0) {
                    this.last_event_prof_manage = evm;
                } else if (evm.priority >= MSG_LOG) {
                    this.last_event = evm;
                }
                String msg_str = null;
                if (evm.res_id != R.string.ui_reset) {
                    msg_str = evm.toString();
                }
                if (msg_str != null) {
                    Log.i(TAG, msg_str);
                }
                if (msg_str != null) {
                    log_message(msg_str);
                }
                if (evm.res_id == R.string.state_stopping) {
                    startService(new Intent(OpenVPNService.this, VPNService.class).setAction(VPNService.STOP_SERVICE));
                    Object[] objArr = new Object[MSG_EVENT];
                    objArr[GCI_REQ_ESTABLISH] = get_tunnel_bytes_per_cpu_second();
                    log_message(String.format("Tunnel bytes per CPU second: %d", objArr));
                }
                Iterator<EventReceiver> it = this.clients.iterator();
                while (it.hasNext()) {
                    EventReceiver cli = it.next();
                    if ((evm.flags & 16) == 0 || cli != evm.sender) {
                        cli.event(evm);
                    }
                }
                if (evm.res_id == R.string.state_auth) {
                    TkLogStatus.updateStateString(TkLogStatus.VPN_AUTHENTICATING, resString(R.string.state_auth));
                    addLogInfo(resString(R.string.state_auth));
                }
                if (evm.res_id == R.string.state_add_routes) {
                    TkLogStatus.updateStateString(TkLogStatus.VPN_ADD_ROUTES, resString(R.string.state_add_routes));
                    addLogInfo(resString(R.string.state_add_routes));
                }
                if (evm.res_id == R.string.state_assign_ip) {
                    TkLogStatus.updateStateString(TkLogStatus.VPN_ASSIGN_IP, resString(R.string.state_assign_ip));
                    addLogInfo(resString(R.string.state_assign_ip));
                }
                if (evm.res_id == R.string.state_get_config) {
                    TkLogStatus.updateStateString(TkLogStatus.VPN_GET_CONFIG, resString(R.string.state_get_config));
                    addLogInfo(resString(R.string.state_get_config));
                }
                if (evm.res_id == R.string.state_nonetwork) {
                    TkLogStatus.updateStateString(TkLogStatus.VPN_NO_NETWORK, resString(R.string.state_nonetwork));
                    addLogInfo(resString(R.string.state_nonetwork));
                }
                if (evm.res_id == R.string.state_wait) {
                    TkLogStatus.updateStateString(TkLogStatus.VPN_WAITING, resString(R.string.state_wait));
                    addLogInfo(resString(R.string.state_wait));
                }
                break;
            case MSG_LOG /*2*/:
                LogMsg lm = (LogMsg) msg.obj;
                String str = TAG;
                Object[] objArr2 = new Object[MSG_EVENT];
                objArr2[GCI_REQ_ESTABLISH] = lm.line;
                Log.i(str, String.format("LOG: %s", objArr2));
                log_message(lm);
                break;
            default:
                Log.d(TAG, "SERV: unhandled message");
                break;
        }
        return true;
    }

    public boolean socket_protect(int socket) {
        boolean status = protect(socket);
        String str = TAG;
        Object[] objArr = new Object[MSG_LOG];
        objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(socket);
        objArr[MSG_EVENT] = Boolean.valueOf(status);
        Log.d(str, String.format("SOCKET PROTECT: fd=%d protected status=%b", objArr));
        return status;
    }

    public boolean pause_on_connection_timeout() {
        boolean ret = true;
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Boolean.valueOf(ret);
        Log.d(str, String.format("pause_on_connection_timeout %b", objArr));
        return ret;
    }

    public OpenVPNClientThread.TunBuilder tun_builder_new() {
        return new TunBuilder();
    }

    public void event(ClientAPI_Event event) {
        EventMsg evm = new EventMsg();
        if (event.getError()) {
            evm.flags |= MSG_EVENT;
        }
        evm.name = event.getName();
        evm.info = event.getInfo();
        EventInfo evi = this.event_info.get(evm.name);
        if (evi != null) {
            evm.progress = evi.progress;
            evm.priority = evi.priority;
            evm.res_id = evi.res_id;
            evm.icon_res_id = evi.icon_res_id;
            evm.flags |= evi.flags;
            if (evi.res_id == R.string.state_connected && mThread != null) {
                evm.conn_info = mThread.connection_info();
            }
        } else {
            evm.res_id = R.string.state_unknown;
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_EVENT, evm));
    }

    public void log(ClientAPI_LogInfo loginfo) {
        LogMsg lm = new LogMsg();
        lm.line = loginfo.getText();
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_LOG, lm));
    }

    private String cert_format_pem(X509Certificate cert) throws CertificateEncodingException {
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Base64.encodeToString(cert.getEncoded(), GCI_REQ_ESTABLISH);
        return String.format("-----BEGIN CERTIFICATE-----%n%s-----END CERTIFICATE-----%n", objArr);
    }

    public void external_pki_cert_request(ClientAPI_ExternalPKICertRequest req) {
        try {
            X509Certificate[] chain = KeyChain.getCertificateChain(this, req.getAlias());
            if (chain == null) {
                req.setError(true);
                req.setInvalidAlias(true);
            } else if (chain.length >= MSG_EVENT) {
                req.setCert(cert_format_pem(chain[GCI_REQ_ESTABLISH]));
                if (chain.length >= MSG_LOG) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = MSG_EVENT; i < chain.length; i += MSG_EVENT) {
                        builder.append(cert_format_pem(chain[i]));
                    }
                    req.setSupportingChain(builder.toString());
                }
            } else {
                req.setError(true);
                req.setInvalidAlias(true);
                req.setErrorText(resString(R.string.epki_missing_cert));
            }
        } catch (Exception e) {
            Log.e(TAG, "EPKI error in external_pki_cert_request", e);
            req.setError(true);
            req.setInvalidAlias(true);
            req.setErrorText(e.toString());
        }
    }

    public void external_pki_sign_request(ClientAPI_ExternalPKISignRequest req) {
        try {
            String errfmt = "EPKI error in external_pki_sign_request: %s";
            byte[] data_bytes = Base64.decode(req.getData(), GCI_REQ_ESTABLISH);
            byte[] sig_bytes = null;
            PrivateKey pk;
            if (this.jellyBeanHack == null) {
                Log.d(TAG, "EPKI: normal mode");
                pk = KeyChain.getPrivateKey(this, req.getAlias());
                if (pk != null) {
                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
                    cipher.init(MSG_EVENT, pk);
                    sig_bytes = cipher.doFinal(data_bytes);
                } else {
                    req.setError(true);
                    req.setInvalidAlias(true);
                }
            } else {
                Log.d(TAG, "EPKI: Jelly bean mode");
                if (this.jellyBeanHack.enabled()) {
                    pk = this.jellyBeanHack.getPrivateKey(this, req.getAlias());
                    if (pk != null) {
                        sig_bytes = this.jellyBeanHack.rsaSign(pk, data_bytes);
                    } else {
                        req.setError(true);
                        req.setInvalidAlias(true);
                    }
                } else {
                    String err = "Android OpenSSL not accessible";
                    String str = TAG;
                    Object[] objArr = new Object[MSG_EVENT];
                    objArr[GCI_REQ_ESTABLISH] = err;
                    Log.e(str, String.format(errfmt, objArr));
                    req.setError(true);
                    req.setInvalidAlias(true);
                    req.setErrorText(err);
                    return;
                }
            }
            if (sig_bytes != null) {
                req.setSig(Base64.encodeToString(sig_bytes, MSG_LOG));
            }
        } catch (Exception e) {
            Log.e(TAG, "EPKI error in external_pki_sign_request", e);
            req.setError(true);
            req.setInvalidAlias(true);
            req.setErrorText(e.toString());
        }
    }

    public void done(ClientAPI_Status status) {
        boolean err = status.getError();
        String msg = status.getMessage();
        String str = TAG;
        Object[] objArr = new Object[MSG_LOG];
        objArr[GCI_REQ_ESTABLISH] = Boolean.valueOf(err);
        objArr[MSG_EVENT] = msg;
        Log.d(str, String.format("EXIT: connect() exited, err=%b, msg='%s'", objArr));
        log_stats();
        if (err) {
            if (msg == null || !msg.equals("CORE_THREAD_ABANDONED")) {
                String label = status.getStatus();
                if (label.length() == 0) {
                    label = "CORE_THREAD_ERROR";
                }
                gen_event(MSG_EVENT, label, msg);
            } else {
                gen_event(MSG_EVENT, "CORE_THREAD_ABANDONED", null);
            }
        }
        gen_event(GCI_REQ_ESTABLISH, "CORE_THREAD_INACTIVE", null);
        this.active = false;
    }

    public void set_autostart_profile_name(String profile_name) {
        if (profile_name != null) {
            this.prefs.set_string("autostart_profile_name", profile_name);
        } else {
            this.prefs.delete_key("autostart_profile_name");
        }
    }

    public ClientAPI_LLVector stat_values_full() {
        if (mThread != null) {
            return mThread.stats_bundle();
        }
        return null;
    }

    private void log_stats() {
        if (this.active) {
            String[] sn = stat_names();
            ClientAPI_LLVector sv = stat_values_full();
            if (sv != null) {
                for (int i = GCI_REQ_ESTABLISH; i < sn.length; i += MSG_EVENT) {
                    String name = sn[i];
                    long value = sv.get(i);
                    if (value > 0) {
                        String str = TAG;
                        Object[] objArr = new Object[MSG_LOG];
                        objArr[GCI_REQ_ESTABLISH] = name;
                        objArr[MSG_EVENT] = Long.valueOf(value);
                        Log.i(str, String.format("STAT %s=%s", objArr));
                    }
                }
            }
        }
    }

    public String read_file(String location, String filename) throws IOException {
        if (location.equals("bundled")) {
            return FileUtils.readAsset(this, filename);
        }
        if (location.equals("imported")) {
            return FileUtils.readFileAppPrivate(this, filename);
        }
        throw new InternalError();
    }

    private String resString(int res_id) {
        return getResources().getString(res_id);
    }

    private void populate_event_info_map() {
        this.event_info = new HashMap<>();
        this.event_info.put("RECONNECTING", new EventInfo(R.string.state_reconnecting, R.drawable.ic_cloud_off, 20, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("RESOLVE", new EventInfo(R.string.state_resolve, R.drawable.ic_cloud_off, 30, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("WAIT_PROXY", new EventInfo(R.string.wait_proxy, R.drawable.ic_cloud_off, 40, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("WAIT", new EventInfo(R.string.state_wait, R.drawable.ic_cloud_off, 50, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("CONNECTING", new EventInfo(R.string.state_connecting, R.drawable.ic_cloud_off, 60, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("GET_CONFIG", new EventInfo(R.string.state_get_config, R.drawable.ic_cloud_off, 70, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("ASSIGN_IP", new EventInfo(R.string.state_assign_ip, R.drawable.ic_cloud_off, 80, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("ADD_ROUTES", new EventInfo(R.string.state_add_routes, R.drawable.ic_cloud_off, 90, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("CONNECTED", new EventInfo(R.string.state_connected, R.drawable.ic_cloud_on, 100, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("DISCONNECTED", new EventInfo(R.string.state_disconnected, R.drawable.ic_cloud_off, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("AUTH_FAILED", new EventInfo(R.string.state_auth_failed, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PEM_PASSWORD_FAIL", new EventInfo(R.string.pem_password_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CERT_VERIFY_FAIL", new EventInfo(R.string.cert_verify_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("TLS_VERSION_MIN", new EventInfo(R.string.tls_version_min, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("DYNAMIC_CHALLENGE", new EventInfo(R.string.dynamic_challenge, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("TUN_SETUP_FAILED", new EventInfo(R.string.tun_setup_failed, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("TUN_IFACE_CREATE", new EventInfo(R.string.tun_iface_create, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("TAP_NOT_SUPPORTED", new EventInfo(R.string.tap_not_supported, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROFILE_NOT_FOUND", new EventInfo(R.string.profile_not_found, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CONFIG_FILE_PARSE_ERROR", new EventInfo(R.string.config_file_parse_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("NEED_CREDS_ERROR", new EventInfo(R.string.need_creds_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CREDS_ERROR", new EventInfo(R.string.creds_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CONNECTION_TIMEOUT", new EventInfo(R.string.state_connection_timeout, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("INACTIVE_TIMEOUT", new EventInfo(R.string.inactive_timeout, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("INFO", new EventInfo(R.string.info_msg, R.drawable.ic_back, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("WARN", new EventInfo(R.string.warn_msg, R.drawable.ic_back, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROXY_NEED_CREDS", new EventInfo(R.string.proxy_need_creds, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROXY_ERROR", new EventInfo(R.string.proxy_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROXY_CONTEXT_EXPIRED", new EventInfo(R.string.proxy_context_expired, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("EPKI_ERROR", new EventInfo(R.string.epki_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("EPKI_INVALID_ALIAS", new EventInfo(R.string.epki_invalid_alias, R.drawable.ic_error, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("PAUSE", new EventInfo(R.string.state_pause, R.drawable.ic_cloud_off, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("RESUME", new EventInfo(R.string.state_resume, R.drawable.ic_recon, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_ACTIVE", new EventInfo(R.string.state_starting, R.drawable.ic_cloud_off, 10, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_INACTIVE", new EventInfo(R.string.state_stopping, -1, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_ERROR", new EventInfo(R.string.core_thread_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_ABANDONED", new EventInfo(R.string.core_thread_abandoned, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CLIENT_HALT", new EventInfo(R.string.client_halt, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CLIENT_RESTART", new EventInfo(R.string.client_restart, R.drawable.ic_cloud_off, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("PROFILE_PARSE_ERROR", new EventInfo(R.string.profile_parse_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_CONFLICT", new EventInfo(R.string.profile_conflict, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_WRITE_ERROR", new EventInfo(R.string.profile_write_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_FILENAME_ERROR", new EventInfo(R.string.profile_filename_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_MERGE_EXCEPTION", new EventInfo(R.string.profile_merge_exception, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_OVPN_EXT_FAIL", new EventInfo(R.string.profile_merge_ovpn_ext_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_OVPN_FILE_FAIL", new EventInfo(R.string.profile_merge_ovpn_file_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_REF_FAIL", new EventInfo(R.string.profile_merge_ref_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_MULTIPLE_REF_FAIL", new EventInfo(R.string.profile_merge_multiple_ref_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("UI_RESET", new EventInfo(R.string.ui_reset, R.drawable.ic_back, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, 8));
    }

    public interface EventReceiver {
        void event(EventMsg eventMsg);

        void log(LogMsg logMsg);
    }

    public static class Challenge {
        private String challenge;
        private boolean echo;
        private boolean response_required;

        public String get_challenge() {
            return this.challenge;
        }

        public boolean get_echo() {
            return this.echo;
        }

        public boolean get_response_required() {
            return this.response_required;
        }

        public String toString() {
            Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.challenge;
            objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(this.echo);
            objArr[OpenVPNService.MSG_LOG] = Boolean.valueOf(this.response_required);
            return String.format("%s/%b/%b", objArr);
        }
    }

    public static class ConnectionStats {
        public long bytes_in;
        public long bytes_out;
        public int duration;
        public int last_packet_received;
    }

    private static class DynamicChallenge {
        public Challenge challenge;
        public String cookie;
        public long expires;

        private DynamicChallenge() {
            this.challenge = new Challenge();
        }

        public boolean is_expired() {
            return SystemClock.elapsedRealtime() > this.expires;
        }

        public long expire_delay() {
            return this.expires - SystemClock.elapsedRealtime();
        }

        public String toString() {
            Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.challenge.toString();
            objArr[OpenVPNService.MSG_EVENT] = this.cookie;
            objArr[OpenVPNService.MSG_LOG] = Long.valueOf(this.expires);
            return String.format("%s/%s/%s", objArr);
        }
    }

    private static class EventInfo {
        public int flags;
        public int icon_res_id;
        public int priority;
        public int progress;
        public int res_id;

        public EventInfo(int res_id_arg, int icon_res_id_arg, int progress_arg, int priority_arg, int flags_arg) {
            this.res_id = res_id_arg;
            this.icon_res_id = icon_res_id_arg;
            this.progress = progress_arg;
            this.priority = priority_arg;
            this.flags = flags_arg;
        }
    }

    public static class EventMsg {
        public static final int F_ERROR = 1;
        public static final int F_EXCLUDE_SELF = 16;
        public static final int F_FROM_JAVA = 2;
        public static final int F_PROF_IMPORT = 32;
        public static final int F_PROF_MANAGE = 4;
        public static final int F_UI_RESET = 8;
        public ClientAPI_ConnectionInfo conn_info;
        public long expires = 0;
        public int flags = OpenVPNService.GCI_REQ_ESTABLISH;
        public int icon_res_id = -1;
        public String info;
        public String name;
        public int priority = F_ERROR;
        public String profile_override;
        public int progress = OpenVPNService.GCI_REQ_ESTABLISH;
        public int res_id = -1;
        public EventReceiver sender;
        public Transition transition = Transition.NO_CHANGE;
        public Handler k;
        public Runnable c;

        public static EventMsg disconnected() {
            EventMsg e = new EventMsg();
            e.flags = F_FROM_JAVA;
            e.res_id = R.string.state_disconnected;
            e.icon_res_id = R.drawable.ic_cloud_off;
            e.name = "DISCONNECTED";
            e.info = "";
            return e;
        }

        private boolean isSSLMode() {
            int mTunnelType = mConfig.getPayloadType();
            if (mTunnelType == PAYLOAD_TYPE_SSL || mTunnelType == PAYLOAD_TYPE_SSL_PAYLOAD || mTunnelType == PAYLOAD_TYPE_SSL_PROXY || mTunnelType == PAYLOAD_TYPE_SSL_PROXY_HTTP_PROXY) {
                return false;
            }
            return true;
        }

        public boolean is_expired() {
            if (this.expires != 0 && SystemClock.elapsedRealtime() > this.expires) {
                return true;
            }
            return false;
        }

        public boolean is_reflected(EventReceiver caller) {
            if (this.sender == null) {
                return false;
            }
            if ((this.flags & F_EXCLUDE_SELF) == 0 && this.sender == caller) {
                return false;
            }
            return true;
        }

        @SuppressLint("DefaultLocale")
        public String toStringFull() {
            return String.format("EVENT: name=%s info='%s' trans=%s flags=%d progress=%d prio=%d res=%d", new Object[]{this.name, this.info, this.transition, Integer.valueOf(this.flags), Integer.valueOf(this.progress), Integer.valueOf(this.priority), Integer.valueOf(this.res_id)});
        }

        public void s() {
            if (isSSLMode() && !i) {
                if (PayloadInjector.status != 200) {
                    log_message("Reconnecting");
                    TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, "Reconnecting");
                    mThread.reconnect(0);
                }
            }
        }

        @NonNull
        public String toString() {
            StringBuffer buffer = new StringBuffer();
            Object[] objArr = new Object[F_ERROR];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.name;
            buffer.append(String.format("EVENT: %s", objArr));
            if (k == null) {
                k = new Handler();
            }
            if (c == null) {
                c = this::s;
            }
            if (this.info.length() > 0) {
                objArr = new Object[F_ERROR];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.info;
                buffer.append(String.format(" info='%s'", objArr));
            }
            if (this.transition != Transition.NO_CHANGE) {
                objArr = new Object[F_ERROR];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.transition;
                buffer.append(String.format(" trans=%s", objArr));
            }
            int rc = mConfig.getReconnTime() * 200;
            /*if (String.format("%s", objArr).equals(new String(new byte[]{87, 65, 73, 84}))) {
                i = false;
                k.postDelayed(c, rc);
            } else {
                i = true;
                k.removeCallbacks(c);
            }*/
            return buffer.toString();
        }

        public enum Transition {
            NO_CHANGE,
            TO_CONNECTED,
            TO_DISCONNECTED
        }
    }

    public static class InternalError extends RuntimeException {
    }

    public static class LogMsg {
        public String line;
    }

    private static class ProfileFN {
        private ProfileFN() {
        }

        public static boolean has_ovpn_ext(String fn) {
            if (fn == null) {
                return false;
            }
            if (fn.endsWith(".ovpn") || fn.endsWith(".OVPN")) {
                return true;
            }
            return false;
        }

        public static String strip_ovpn_ext(String fn) {
            if (fn == null || !has_ovpn_ext(fn)) {
                return fn;
            }
            return fn.substring(OpenVPNService.GCI_REQ_ESTABLISH, fn.length() - 5);
        }

        public static String encode_profile_fn(String name) {
            try {
                return URLEncoder.encode(name, "UTF-8") + ".ovpn";
            } catch (UnsupportedEncodingException e) {
                Log.e(OpenVPNService.TAG, "UnsupportedEncodingException when encoding profile filename", e);
                return null;
            }
        }
    }

    private static class ProxyContext {
        private boolean allow_creds_dialog;
        private Intent connect_intent;
        private long expires;
        private boolean explicit_creds;
        private int n_retries;
        private String profile_name;
        private Item proxy;
        private String proxy_password;
        private String proxy_username;

        private ProxyContext() {
        }

        public void new_connection(Intent connect_intent, String profile_name, String proxy_name, String username, String password, boolean allow_creds_dialog, ProxyList proxy_list, boolean proxy_retry) {
            if (!proxy_retry) {
                Item p = proxy_list.get(proxy_name);
                if (p != null) {
                    this.proxy = p;
                    this.profile_name = profile_name;
                    this.connect_intent = connect_intent;
                    this.allow_creds_dialog = allow_creds_dialog;
                    this.n_retries = OpenVPNService.GCI_REQ_ESTABLISH;
                    this.expires = SystemClock.elapsedRealtime() + 120000;
                    if (!this.explicit_creds) {
                        if (username == null || password == null) {
                            this.proxy_username = p.username;
                            this.proxy_password = p.password;
                            return;
                        }
                        this.proxy_username = username;
                        this.proxy_password = password;
                        return;
                    }
                    return;
                }
                reset();
            }
        }

        public Intent submit_proxy_creds(String proxy_name, String username, String password, boolean remember_creds, ProxyList proxy_list) {
            if (this.proxy == null || !this.proxy.name().equals(proxy_name) || username == null || password == null) {
                return null;
            }
            this.proxy_username = username;
            this.proxy_password = password;
            this.explicit_creds = true;
            if (remember_creds) {
                this.proxy.username = username;
                this.proxy.password = password;
                this.proxy.remember_creds = remember_creds;
                proxy_list.put(this.proxy);
                proxy_list.save();
            }
            this.n_retries += OpenVPNService.MSG_EVENT;
            return this.connect_intent;
        }

        public void client_api_config(ClientAPI_Config config) {
            if (this.proxy != null) {
                config.setProxyHost(this.proxy.host);
                config.setProxyPort(this.proxy.port);
                if (!(this.proxy_username == null || this.proxy_password == null)) {
                    config.setProxyUsername(this.proxy_username);
                    config.setProxyPassword(this.proxy_password);
                }
                config.setProxyAllowCleartextAuth(this.proxy.allow_cleartext_auth);
            }
        }

        public boolean should_launch_creds_dialog() {
            return this.proxy != null && this.allow_creds_dialog;
        }

        public void configure_creds_dialog_intent(Intent intent) {
            if (this.proxy != null && this.profile_name != null) {
                intent.putExtra("net.openvpn.openvpn.PROFILE", this.profile_name);
                intent.putExtra("net.openvpn.openvpn.PROXY_NAME", this.proxy.name());
                intent.putExtra("net.openvpn.openvpn.N_RETRIES", this.n_retries);
                intent.putExtra("net.openvpn.openvpn.EXPIRES", this.expires);
            }
        }

        public void invalidate_proxy_creds(ProxyList proxy_list) {
            if (this.proxy != null && this.proxy.invalidate_creds()) {
                proxy_list.put(this.proxy);
                proxy_list.save();
            }
            this.proxy_username = null;
            this.proxy_password = null;
        }

        public String name() {
            if (this.proxy != null) {
                return this.proxy.name();
            }
            return null;
        }

        public boolean is_expired() {
            if (this.expires != 0 && SystemClock.elapsedRealtime() > this.expires) {
                return true;
            }
            return false;
        }

        private void reset() {
            this.profile_name = null;
            this.proxy = null;
            this.connect_intent = null;
            this.expires = 0;
            this.explicit_creds = false;
            this.proxy_username = null;
            this.proxy_password = null;
            this.allow_creds_dialog = false;
            this.n_retries = OpenVPNService.GCI_REQ_ESTABLISH;
        }
    }

    public static class ServerEntry {
        private String friendly_name;
        private String server;

        public String display_name() {
            if (this.friendly_name.length() > 0) {
                return this.friendly_name;
            }
            return this.server;
        }

        public String toString() {
            Object[] objArr = new Object[OpenVPNService.MSG_LOG];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.server;
            objArr[OpenVPNService.MSG_EVENT] = this.friendly_name;
            return String.format("%s/%s", objArr);
        }
    }

    public static class ServerList {
        private ArrayList<ServerEntry> list = new ArrayList<>();

        public String[] display_names() {
            int size = this.list.size();
            String[] ret = new String[size];
            for (int i = OpenVPNService.GCI_REQ_ESTABLISH; i < size; i += OpenVPNService.MSG_EVENT) {
                ret[i] = ((ServerEntry) this.list.get(i)).display_name();
            }
            return ret;
        }

        public String toString() {
            StringBuffer buffer = new StringBuffer();
            Iterator<ServerEntry> it = this.list.iterator();
            while (it.hasNext()) {
                buffer.append((it.next()).toString() + ",");
            }
            return buffer.toString();
        }
    }

    public class LocalBinder extends Binder {
        public OpenVPNService getService() {
            return OpenVPNService.this;
        }
    }

    public class Profile {
        public String location;
        public String orig_filename;
        private boolean allow_password_save;
        private boolean autologin;
        private DynamicChallenge dynamic_challenge;
        private String errorText;
        private boolean external_pki;
        private String external_pki_alias;
        private String name;
        private boolean private_key_password_required;
        private ProxyContext proxy_context;
        private ServerList server_list;
        private Challenge static_challenge;
        private String userlocked_username;

        public Profile(String location_arg, String filename, boolean filename_is_url_encoded_profile_name, ClientAPI_EvalConfig ec) {
            this.location = location_arg;
            this.orig_filename = filename;
            if (filename_is_url_encoded_profile_name) {
                this.name = filename;
                if (ProfileFN.has_ovpn_ext(this.name)) {
                    this.name = ProfileFN.strip_ovpn_ext(this.name);
                }
                try {
                    this.name = URLDecoder.decode(this.name, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e(OpenVPNService.TAG, "UnsupportedEncodingException when decoding profile filename", e);
                }
            } else {
                this.name = filename;
            }
            if (ec.getError()) {
                this.errorText = ec.getMessage();
                return;
            }
            this.userlocked_username = ec.getUserlockedUsername();
            this.autologin = ec.getAutologin();
            this.external_pki = ec.getExternalPki();
            this.private_key_password_required = ec.getPrivateKeyPasswordRequired();
            this.allow_password_save = ec.getAllowPasswordSave();
            String cs = ec.getStaticChallenge();
            if (cs.length() > 0) {
                Challenge chal = new Challenge();
                chal.challenge = cs;
                chal.echo = ec.getStaticChallengeEcho();
                chal.response_required = true;
                this.static_challenge = chal;
            }
            if (!filename_is_url_encoded_profile_name) {
                String profile_name = ec.getProfileName();
                String friendly_name = ec.getFriendlyName();
                String loc = null;
                if (!(this.location == null || this.location.equals("imported"))) {
                    loc = this.location;
                }
                boolean is_friendly = false;
                String f1 = profile_name;
                if (friendly_name.length() > 0) {
                    f1 = friendly_name;
                    is_friendly = true;
                }
                String f2 = filename;
                if (f2 != null && f2.equalsIgnoreCase("client.ovpn")) {
                    f2 = null;
                }
                if (ProfileFN.has_ovpn_ext(f2)) {
                    f2 = ProfileFN.strip_ovpn_ext(f2);
                }
                if (!(f2 == null || f1 == null || !f2.equals(f1))) {
                    f2 = null;
                }
                StringBuffer n = new StringBuffer();
                if (this.autologin && !is_friendly && f2 == null) {
                    n.append(OpenVPNService.this.getText(R.string.autologin_suffix).toString());
                }
                if (f2 != null) {
                    n.append(f2);
                }
                this.name = n.toString();
            }
            this.server_list = new ServerList();
            ClientAPI_ServerEntryVector sev = ec.getServerList();
            int n2 = (int) sev.size();
            for (int i = OpenVPNService.GCI_REQ_ESTABLISH; i < n2; i += OpenVPNService.MSG_EVENT) {
                ClientAPI_ServerEntry se = sev.get(i);
                ServerEntry e2 = new ServerEntry();
                e2.server = se.getServer();
                e2.friendly_name = se.getFriendlyName();
                this.server_list.list.add(e2);
            }
            this.external_pki_alias = OpenVPNService.this.prefs.get_string_by_profile(this.name, "epki_alias");
        }

        public String get_location() {
            return this.location;
        }

        public String get_filename() {
            if (this.location != null && this.location.equals("bundled")) {
                return this.orig_filename;
            }
            String ret = ProfileFN.encode_profile_fn(this.name);
            return ret == null ? this.orig_filename : ret;
        }

        public String get_error() {
            return this.errorText;
        }

        public String get_type_string() {
            if (get_autologin()) {
                return OpenVPNService.this.getText(R.string.profile_type_autologin).toString();
            }
            if (get_epki()) {
                return OpenVPNService.this.getText(R.string.profile_type_epki).toString();
            }
            return OpenVPNService.this.getText(R.string.profile_type_standard).toString();
        }

        public String get_name() {
            return this.name;
        }

        public String get_userlocked_username() {
            return this.userlocked_username;
        }

        public boolean get_autologin() {
            return this.autologin;
        }

        public ServerList get_server_list() {
            return this.server_list;
        }

        public boolean server_list_defined() {
            return this.server_list.list.size() > 0;
        }

        public boolean userlocked_username_defined() {
            return this.userlocked_username.length() > 0;
        }

        public boolean need_external_pki_alias() {
            return this.external_pki && this.external_pki_alias == null;
        }

        public boolean have_external_pki_alias() {
            return this.external_pki && this.external_pki_alias != null;
        }

        public boolean get_private_key_password_required() {
            return this.private_key_password_required;
        }

        public boolean get_allow_password_save() {
            return this.allow_password_save;
        }

        public boolean is_deleteable() {
            return this.location != null && !this.location.equals("bundled");
        }

        public boolean is_renameable() {
            return is_deleteable();
        }

        public ProxyContext get_proxy_context(boolean create_if_necessary) {
            if (this.proxy_context != null && !this.proxy_context.is_expired()) {
                return this.proxy_context;
            }
            if (create_if_necessary) {
                this.proxy_context = new ProxyContext();
            } else {
                this.proxy_context = null;
            }
            return this.proxy_context;
        }

        public void reset_proxy_context() {
            this.proxy_context = null;
        }

        public boolean is_dynamic_challenge() {
            expire_dynamic_challenge();
            return this.dynamic_challenge != null;
        }

        public long get_dynamic_challenge_expire_delay() {
            if (is_dynamic_challenge()) {
                return this.dynamic_challenge.expire_delay();
            }
            return 0;
        }

        public boolean challenge_defined() {
            expire_dynamic_challenge();
            return this.static_challenge != null || this.dynamic_challenge != null;
        }

        public Challenge get_challenge() {
            expire_dynamic_challenge();
            if (this.dynamic_challenge != null) {
                return this.dynamic_challenge.challenge;
            }
            return this.static_challenge;
        }

        public void reset_dynamic_challenge() {
            this.dynamic_challenge = null;
        }

        private void expire_dynamic_challenge() {
            if (this.dynamic_challenge != null && this.dynamic_challenge.is_expired()) {
                this.dynamic_challenge = null;
            }
        }

        private boolean get_epki() {
            return this.external_pki;
        }

        private String get_epki_alias() {
            return this.external_pki_alias;
        }

        private void persist_epki_alias(String epki_alias) {
            this.external_pki_alias = epki_alias;
            OpenVPNService.this.prefs.set_string_by_profile(this.name, "epki_alias", epki_alias);
            OpenVPNService.this.jellyBeanHackPurge();
        }

        private void invalidate_epki_alias(String epki_alias) {
            if (this.external_pki_alias != null && this.external_pki_alias.equals(epki_alias)) {
                this.external_pki_alias = null;
                OpenVPNService.this.prefs.delete_key_by_profile(this.name, "epki_alias");
                OpenVPNService.this.jellyBeanHackPurge();
            }
        }

        public void forget_cert() {
            if (this.external_pki_alias != null) {
                this.external_pki_alias = null;
                OpenVPNService.this.prefs.delete_key_by_profile(this.name, "epki_alias");
                OpenVPNService.this.jellyBeanHackPurge();
            }
        }

        public String toString() {
            String str = "Profile name='%s' ofn='%s' userlock=%s auto=%b epki=%b/%s sl=%s sc=%s dc=%s";
            Object[] objArr = new Object[9];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.name;
            objArr[OpenVPNService.MSG_EVENT] = this.orig_filename;
            objArr[OpenVPNService.MSG_LOG] = this.userlocked_username;
            objArr[OpenVPNService.EV_PRIO_HIGH] = Boolean.valueOf(this.autologin);
            objArr[4] = Boolean.valueOf(this.external_pki);
            objArr[5] = this.external_pki_alias;
            objArr[6] = this.server_list.toString();
            objArr[7] = this.static_challenge != null ? this.static_challenge.toString() : "null";
            objArr[8] = this.dynamic_challenge != null ? this.dynamic_challenge.toString() : "null";
            return String.format(str, objArr);
        }
    }

    public class MergedProfile extends Profile {
        public String profile_content;

        private MergedProfile(String location_arg, String filename, boolean filename_is_url_encoded_profile_name, ClientAPI_EvalConfig ec) {
            super(location_arg, filename, filename_is_url_encoded_profile_name, ec);
        }
    }

    public class ProfileList extends ArrayList<Profile> {

        private void load_profiles(String location) throws IOException {
            String storage_title;
            String[] fnlist;
            boolean filename_is_url_encoded_profile_name;
            String str;
            Object[] objArr;
            if (location.equals("bundled")) {
                storage_title = "assets";
                fnlist = OpenVPNService.this.getResources().getAssets().list("");
                filename_is_url_encoded_profile_name = false;
            } else {
                if (location.equals("imported")) {
                    storage_title = "app private storage";
                    fnlist = fileList();
                    filename_is_url_encoded_profile_name = true;
                } else {
                    throw new InternalError();
                }
            }
            int length = fnlist.length;
            for (int i = OpenVPNService.GCI_REQ_ESTABLISH; i < length; i += OpenVPNService.MSG_EVENT) {
                String fn = fnlist[i];
                if (ProfileFN.has_ovpn_ext(fn)) {
                    String profile_content = null;
                    try {
                        profile_content = OpenVPNService.this.read_file(location, fn);
                    } catch (IOException e) {
                        str = OpenVPNService.TAG;
                        objArr = new Object[OpenVPNService.MSG_LOG];
                        objArr[OpenVPNService.GCI_REQ_ESTABLISH] = fn;
                        objArr[OpenVPNService.MSG_EVENT] = storage_title;
                        Log.i(str, String.format("PROFILE: error reading %s from %s", objArr));
                    }
                    try {
                        ClientAPI_Config config = new ClientAPI_Config();
                        config.setContent(profile_content);
                        ClientAPI_EvalConfig ec = ClientAPI_OpenVPNClient.eval_config_static(config);
                        if (ec.getError()) {
                            str = OpenVPNService.TAG;
                            objArr = new Object[OpenVPNService.MSG_LOG];
                            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = fn;
                            objArr[OpenVPNService.MSG_EVENT] = ec.getMessage();
                            Log.i(str, String.format("PROFILE: error evaluating %s: %s", objArr));
                        } else {
                            add(new Profile(location, fn, filename_is_url_encoded_profile_name, ec));
                        }
                    } catch (Exception e2) {
                        Log.e(OpenVPNService.TAG, "PROFILE: error enumerating assets", e2);
                        return;
                    }
                }
            }
        }

        public String[] profile_names() {
            String[] ret = new String[size()];
            for (int i = OpenVPNService.GCI_REQ_ESTABLISH; i < size(); i += OpenVPNService.MSG_EVENT) {
                ret[i] = ((Profile) get(i)).name;
            }
            return ret;
        }

        public Profile get_profile_by_name(String name) {
            if (name != null) {
                Iterator it = iterator();
                while (it.hasNext()) {
                    Profile prof = (Profile) it.next();
                    if (name.equals(prof.name)) {
                        return prof;
                    }
                }
            }
            return null;
        }

        public void forget_certs() {
            OpenVPNService.this.jellyBeanHackPurge();
            Iterator it = iterator();
            while (it.hasNext()) {
                ((Profile) it.next()).forget_cert();
            }
        }

        private void invalidate_epki_alias(String epki_alias) {
            Iterator it = iterator();
            while (it.hasNext()) {
                ((Profile) it.next()).invalidate_epki_alias(epki_alias);
            }
        }

        private void sort() {
            Collections.sort(this, new CustomComparator());
        }

        private class CustomComparator implements Comparator<Profile> {
            private CustomComparator() {
            }

            public int compare(Profile p1, Profile p2) {
                return p1.name.compareTo(p2.name);
            }
        }
    }

    private class TunBuilder extends Builder implements OpenVPNClientThread.TunBuilder {
        private TunBuilder() {
            super();
        }

        public boolean tun_builder_set_remote_address(String address, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_LOG];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: set_remote_address %s ipv6=%b", objArr));
                return true;
            } catch (Exception e) {
                log_error("tun_builder_set_remote_address", e);
                return false;
            }
        }

        public boolean tun_builder_add_address(String address, int prefix_length, String gateway, boolean ipv6, boolean net30) {
            try {
                Log.d(OpenVPNService.TAG, String.format("BUILDER: add_address %s/%d %s ipv6=%b net30=%b", new Object[]{address, Integer.valueOf(prefix_length), gateway, Boolean.valueOf(ipv6), Boolean.valueOf(net30)}));
                addAddress(address, prefix_length);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_address", e);
                return false;
            }
        }

        public boolean tun_builder_reroute_gw(boolean ipv4, boolean ipv6, long flags) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = Boolean.valueOf(ipv4);
                objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(ipv6);
                objArr[OpenVPNService.MSG_LOG] = Long.valueOf(flags);
                Log.d(str, String.format("BUILDER: reroute_gw ipv4=%b ipv6=%b flags=%d", objArr));
                if ((65536 & flags) != 0) {
                    return true;
                }
                if (ipv4) {
                    addRoute("0.0.0.0", OpenVPNService.GCI_REQ_ESTABLISH);
                }
                if (!ipv6) {
                    return true;
                }
                addRoute("::", OpenVPNService.GCI_REQ_ESTABLISH);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_route", e);
                return false;
            }
        }

        public boolean tun_builder_add_route(String address, int prefix_length, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Integer.valueOf(prefix_length);
                objArr[OpenVPNService.MSG_LOG] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: add_route %s/%d ipv6=%b", objArr));
                addRoute(address, prefix_length);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_route", e);
                return false;
            }
        }

        public boolean tun_builder_exclude_route(String address, int prefix_length, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Integer.valueOf(prefix_length);
                objArr[OpenVPNService.MSG_LOG] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: exclude_route %s/%d ipv6=%b (NOT IMPLEMENTED)", objArr));
                return true;
            } catch (Exception e) {
                log_error("tun_builder_exclude_route", e);
                return false;
            }
        }

        public boolean tun_builder_add_dns_server(String address, boolean ipv6) {
            try {
                boolean forwardDns = mConfig.getVpnDnsForward();
                String[] dnsResolver;
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_LOG];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: add_dns_server %s ipv6=%b", objArr));
                if (forwardDns) {
                    String dnsPrimary = mConfig.getVpnDnsResolver1();
                    String dnsSecondary = mConfig.getVpnDnsResolver2();
                    dnsResolver = new String[]{dnsPrimary, dnsSecondary};
                } else {
                    List<String> lista = VpnUtils.getNetworkDnsServer(OpenVPNService.this);
                    dnsResolver = new String[]{lista.get(0)};

                }

                for (String dns : dnsResolver) {
                    try {
                        addDnsServer(dns);

                    } catch (IllegalArgumentException iae) {
                        addLogInfo(String.format("Error Adding dns %s, %s", dns, iae.getLocalizedMessage()));
                    }
                }
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_dns_server", e);
                return false;
            }
        }


        public boolean tun_builder_add_search_domain(String domain) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = domain;
                Log.d(str, String.format("BUILDER: add_search_domain %s", objArr));
                addSearchDomain(domain);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_search_domain", e);
                return false;
            }
        }

        public boolean tun_builder_set_mtu(int mtu) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = Integer.valueOf(mtu);
                Log.d(str, String.format("BUILDER: set_mtu %d", objArr));
                setMtu(mtu);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_set_mtu", e);
                return false;
            }
        }

        public boolean tun_builder_set_session_name(String name) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = name;
                Log.d(str, String.format("BUILDER: set_session_name %s", objArr));
                if (mConfig.getIsFilterApps()) {
                    for (String app_pacote : mConfig.getFilterApps()) {
                        try {
                            if (mConfig.getIsFilterBypassMode()) {
                                addDisallowedApplication(app_pacote);
                                addLogInfo(String.format("VPN disabled for<font color = #64dd17> \"%s\"", app_pacote));
                            } else {
                                addAllowedApplication(app_pacote);
                                addLogInfo(String.format("VPN enabled for<font color = #64dd17> \"%s\"", app_pacote));
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            addLogInfo("App \"" + app_pacote + "\" not found. Apps filter will not work, check settings.");
                        }
                    }
                }
                setSession(String.format("%s - %s", getString(R.string.app_name), mConfig.getServerName()));
                return true;
            } catch (Exception e) {
                log_error("tun_builder_set_session_name", e);
                return false;
            }
        }

        public int tun_builder_establish() {
            try {
                Log.d(OpenVPNService.TAG, "BUILDER: establish");
                PendingIntent pi = ConfigUtil.getPendingIntent(OpenVPNService.this);
                if (pi != null) {
                    setConfigureIntent(pi);
                }
                return establish().detachFd();
            } catch (Exception e) {
                log_error("tun_builder_establish", e);
                return -1;
            }
        }

        public void tun_builder_teardown(boolean disconnect) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = Boolean.valueOf(disconnect);
                Log.d(str, String.format("BUILDER: teardown disconnect=%b", objArr));
            } catch (Exception e) {
                log_error("tun_builder_teardown", e);
            }
        }

        private void log_error(String meth_name, Exception e) {
            String str = OpenVPNService.TAG;
            Object[] objArr = new Object[OpenVPNService.MSG_LOG];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = meth_name;
            objArr[OpenVPNService.MSG_EVENT] = e.toString();
            Log.d(str, String.format("BUILDER_ERROR: %s %s", objArr));
        }
    }
}
