package com.eftabsprodns.aio.config;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.RemoteException;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.core.ConfigParser;
import com.eftabsprodns.aio.core.VpnProfile;
import com.eftabsprodns.aio.core.vpnutils.TunnelUtils;
import com.eftabsprodns.aio.utils.FileUtils;
import com.eftabsprodns.aio.utils.SecurePreferences;

import android.preference.PreferenceManager;

public class ConfigUtil implements SettingsConstants {
    private static ConfigUtil instance;
    private static SharedPreferences prefs;
    public static SharedPreferences.Editor editor;
    private static SharedPreferences splitPref;
    private static SharedPreferences.Editor splitEditor;
    private static Context mContext;
    private static String lastStateMsg;
    private static Class<? extends Activity> mNotificationActivityClass;
    private final SecurePreferences mPrefsPrivate;
    private final SharedPreferences.Editor secureEditor;
    private final ConnectivityManager connMgr;

   public boolean getVpnUdpForward(){
        return prefs.getBoolean(UDPFORWARD_KEY, true);
    }
   public void setVpnUdpForward(boolean use){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(UDPFORWARD_KEY, use);
        editor.commit();
    }
    
    /**
     * Tknetwork01/16/2024...
     */
    
    
   public String getVpnUdpResolver(){
        return prefs.getString(UDPRESOLVER_KEY, "127.0.0.1:7300");
    }

    public void setVpnUdpResolver(String str) {
        if (str == null || str.isEmpty()) {
            str = "127.0.0.1:7300";
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(UDPRESOLVER_KEY, str);
        editor.commit();
	}
    
    
    
    

    public boolean setAutoPing() {
        return prefs.getBoolean(AUTO_PINGER, true);
    }
	public String setPinger() {
        return prefs.getString(PINGER, "clients3.google.com");
    }

    public String getPingServer() {
        return prefs.getString("_mPingServer", "clients3.google.com:443");
    }

    public void setPingServer(String png) {
        editor.putString("_mPingServer", png).apply();
    }
	
    

public ConfigUtil(Context context) {
        mContext = context;
        mPrefsPrivate = MyApplication.getSecurePreferences();
        secureEditor = mPrefsPrivate.edit();
        prefs = MyApplication.getPrivateSharedPreferences();
        editor = prefs.edit();
        splitPref = MyApplication.getSlitSharedPreferences();
        splitEditor = splitPref.edit();
        connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        
        
        editor.putBoolean(DNSFORWARD_KEY, true);
        editor.putString(DNSRESOLVER_KEY_1, "8.8.8.8");
        editor.putString(DNSRESOLVER_KEY_2, "8.8.4.4");
        editor.putBoolean(UDPFORWARD_KEY, true);
        editor.putString(UDPRESOLVER_KEY, "127.0.0.1:7300");
        editor.putString(PINGER, "clients3.google.com");
	editor.putBoolean(AUTO_PINGER, true);
       // editor.putBoolean(AUTO_REPLACER, true);
       // editor.putString(MAXIMO_THREADS_KEY, "8th");
     //   editor.putBoolean(AUTO_CLEAR_LOGS_KEY, true);
       // editor.putBoolean(SSH_COMPRESSION, false);
       // editor.putBoolean(WAKELOCK_KEY, false);
		//editor.putBoolean(NETWORK_SPEED, false);
	//	editor.putBoolean(VIBRATE, false);
        //editor.remove(FILTER_APPS);
     //   editor.remove(FILTER_BYPASS_MODE);
        //editor.remove(FILTER_APPS_LIST);
     //   editor.remove(TETHERING_SUBNET);
     //   editor.remove(DISABLE_DELAY_KEY);
        editor.commit();
    
    
    
        
    }

    public static String hide(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            sb.append("*");
        }
        return sb.toString();
    }

    public static ConfigUtil getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigUtil(context);
        }
        mContext = context;
        return instance;
    }

    public static String render_bandwidth(long bytes, boolean mbit) {
        if (mbit)
            bytes = bytes * 8;
        int unit = mbit ? 1000 : 1024;
        if (bytes < unit)
            return bytes + (mbit ? " bit" : " B");

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + ("");
        if (mbit)
            return String.format(Locale.getDefault(), "%.1f %sbit", bytes / Math.pow(unit, exp), pre);
        else
            return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static PendingIntent getPendingIntent(Context c) {
        PendingIntent contentPendingIntent = getContentIntent(c);
        return contentPendingIntent != null ? contentPendingIntent : getGraphPendingIntent(c);
    }

    public static void setNotificationActivityClass(Class<? extends Activity> activityClass) {
        mNotificationActivityClass = activityClass;
    }

    public static PendingIntent getContentIntent(Context c) {
        try {
            if (mNotificationActivityClass != null) {
                Intent intent = new Intent(c, mNotificationActivityClass);
                try {
                    String typeStart = Objects.requireNonNull(mNotificationActivityClass.getField("TYPE_START").get(null)).toString();
                    Integer typeFromNotify = Integer.parseInt(Objects.requireNonNull(mNotificationActivityClass.getField("TYPE_FROM_NOTIFY").get(null)).toString());
                    intent.putExtra(typeStart, typeFromNotify);
                } catch (Exception ignored) {
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
                }
                return PendingIntent.getActivity(c, 0, intent, flags);
            }
        } catch (Exception e) {
            TkLogStatus.logDebug(c.getClass().getCanonicalName() + " Build detail intent error: " + e.getMessage());
        }
        return null;
    }

    public static PendingIntent getGraphPendingIntent(Context c) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(c, "com.eftabsprodns.aio.activities.OpenVPNClient"));
        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent startLW = PendingIntent.getActivity(c, 0, intent, flags);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;
    }

    public String getSecureString(String key) {
        if (key.equals(SERVER_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(SERVER_KEY, ""));
        }
        if (key.equals(DIRECT_UDP_CONFIG_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(DIRECT_UDP_CONFIG_KEY, ""));
        }
        if (key.equals(USERNAME_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(USERNAME_KEY, ""));
        }
        if (key.equals(PASSWORD_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(PASSWORD_KEY, ""));
        }
        if (key.equals(CUSTOM_PAYLOAD_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(CUSTOM_PAYLOAD_KEY, ""));
        }
        if (key.equals(SNI_HOST_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(SNI_HOST_KEY, ""));
        }
        if (key.equals(PROXY_IP_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(PROXY_IP_KEY, ""));
        }
        if (key.equals(DNS_NAME_SERVER_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(DNS_NAME_SERVER_KEY, ""));
        }
        if (key.equals(DNS_ADDRESS_KEY)) {
            return FileUtils.showJson(mPrefsPrivate.getString(DNS_ADDRESS_KEY, ""));
        }
        if (key.equals(FrontQuery)) {
            return FileUtils.showJson(mPrefsPrivate.getString(FrontQuery, ""));
        }
        if (key.equals(BackQuery)) {
            return FileUtils.showJson(mPrefsPrivate.getString(BackQuery, ""));
        }
        if (key.equals(DNS_PUBLIC_KEY)) {
            String publickey = FileUtils.showJson(mPrefsPrivate.getString(DNS_PUBLIC_KEY, ""));
            if (publickey.contains("hrKlev65")) {
                return publickey.replace("hrKlev65", "");
            }
            return publickey;
        }
        if (key.equals(CONFIG_V2RAY)) {
            String mV2ray = FileUtils.showJson(mPrefsPrivate.getString(CONFIG_V2RAY, ""));
            if (mV2ray.contains("JHsx382oL")) {
                return mV2ray.replace("JHsx382oL", "");
            }
            return mV2ray;
        }
        if (key.equals(PROXY_PORT_KEY)) {
            String prx = mPrefsPrivate.getString(PROXY_PORT_KEY, "");
            if (prx.isEmpty()) {
                return "80";
            }
            return prx;
        }
        return mPrefsPrivate.getString(key, "");
    }

    public String getQueryHost() {
        if (isQueryMode()) {
            if (FileUtils.showJson(mPrefsPrivate.getString(FrontQuery, "")).isEmpty()) {
                return getSecureString(SERVER_KEY) + "@" + FileUtils.showJson(mPrefsPrivate.getString(BackQuery, "")) + ":" + "80";
            } else {
                return FileUtils.showJson(mPrefsPrivate.getString(FrontQuery, "")) + "@" + getSecureString(SERVER_KEY) + ":" + "80";
            }
        } else if (getPayloadType() == PAYLOAD_TYPE_SSL_PROXY) {
            return getSecureString(PROXY_IP_KEY) + ":" + mPrefsPrivate.getString(SERVER_PORT_KEY, "");
        }
        return getSecureString(SERVER_KEY) + ":" + mPrefsPrivate.getString(SERVER_PORT_KEY, "");
    }

    public String getOvpnConfig() {
        StringBuilder configure = new StringBuilder();
        String[] _dns = getVpnDnsResolver();
        String[] HostPort = getQueryHost().split(":");
        boolean isDirect = mUseProxy();
        try {
            configure.append(getOvpnCert().replace("remote ", "##remote "));
            configure.append("\n").append(getProtocol()).append("\n");
            configure.append("\nremote ").append(HostPort[0]).append(" ").append(HostPort[1]).append("\n");
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(configure.toString()));
            VpnProfile vp = cp.convertProfile();
            vp.mName = "HarliesDevX";
            if (vp.checkProfile(mContext) != R.string.no_error_found) {
                throw new RemoteException(mContext.getString(vp.checkProfile(mContext)));
            }
            vp.mProfileCreator = mContext.getPackageName();
            vp.mUsername = getSecureString(USERNAME_KEY);
            vp.mPassword = getSecureString(PASSWORD_KEY);
            if (getVpnDnsForward()) {
                vp.mOverrideDNS = true;
                vp.mSearchDomain = "";
                vp.mDNS1 = _dns[0];
                vp.mDNS2 = _dns[1];
            }
            vp.mUseCustomConfig = isDirect;
            vp.mCustomConfigOptions = vp.mCustomConfigOptions + getProxySetups();
            return vp.getConfigFile(mContext, true);
        } catch (Exception e) {
            return null;
        }
    }

    private String getProxySetups() {
        String tweak = FileUtils.showJson(mPrefsPrivate.getString(CUSTOM_PAYLOAD_KEY, ""));
        String[] split = getProxyAddress().split(":");
        boolean def = tweak.contains("http-proxy-option");
        StringBuffer config = new StringBuffer();
        config.append("\n");
        if (def) {
            config.append("http-proxy ").append(getSecureString(PROXY_IP_KEY)).append(" ").append(getSecureString(PROXY_PORT_KEY));
            config.append("\n");
            config.append(tweak);
            config.append("\n");
        } else {
            config.append("http-proxy ").append(split[0]).append(" ").append(split[1]);
            config.append("\n");
        }
        return config.toString();
    }

    public boolean isDefaultOvpnTweak() {
        String pl = FileUtils.showJson(mPrefsPrivate.getString(CUSTOM_PAYLOAD_KEY, ""));
        return pl.contains("http-proxy-option");
    }

    private String getProtocol() {
        if (getPayloadType() == PAYLOAD_TYPE_OVPN_UDP) {
            return "proto udp";
        }
        return "proto tcp";
    }

    public boolean mUseProxy() {
        if (getPayloadType() == PAYLOAD_TYPE_DIRECT) {
            return false;
        } else return getPayloadType() != PAYLOAD_TYPE_OVPN_UDP;
    }

    public boolean getAutoReplace() {
        if (getServerType().equals(SERVER_TYPE_OVPN) || getServerType().equals(SERVER_TYPE_SSH)) {
            return prefs.getBoolean(isAutoReplace, false);
        }
        return false;
    }

    public boolean isQueryMode() {
        return prefs.getBoolean(QueryMode, false);
    }

    public void setIsQueryMode(boolean enable) {
        editor.putBoolean(QueryMode, enable).apply();
    }

    public void setFrontQuery(String query) {
        secureEditor.putString(FrontQuery, query).apply();
    }

    public void setBackQuery(String query) {
        secureEditor.putString(BackQuery, query).apply();
    }

    public String getPayloadName() {
        return prefs.getString("OVPN_PAYLOAD_NAME", "");
    }

    public void setPayloadName(String nm) {
        editor.putString("OVPN_PAYLOAD_NAME", nm).apply();
    }

    public String getServerName() {
        return prefs.getString("OVPN_SERVER_NAME", "");
    }

    public void setServerName(String proto) {
        editor.putString("OVPN_SERVER_NAME", proto).apply();
    }

    public void setUDPConfig(String obfs) {
        secureEditor.putString(DIRECT_UDP_CONFIG_KEY, obfs).apply();
    }

    public String getOvpnCert() {
        String ca = FileUtils.showJson(prefs.getString("OVPN_CERT_KEY", ""));
        if (ca.contains("U5b2AiMp")) {
            return ca.replace("U5b2AiMp", "");
        }
        return ca;
    }

    public void setOvpnCert(String ca) {
        editor.putString("OVPN_CERT_KEY", ca).apply();
    }

    public boolean getConfigIsAutoLogIn() {
        return prefs.getBoolean(isAutoLogIn, false);
    }

    public void setConfigIsAutoLogIn(boolean a) {
        editor.putBoolean(isAutoLogIn, a).apply();
    }

    public void setConfigV2ray(String v2) {
        secureEditor.putString(CONFIG_V2RAY, v2).apply();
    }

    public void setSni(String sni) {
        secureEditor.putString(SNI_HOST_KEY, sni).apply();
    }

    public void setPayload(String payload) {
        secureEditor.putString(CUSTOM_PAYLOAD_KEY, payload).apply();
    }

    public void setServerHost(String ip) {
        secureEditor.putString(SERVER_KEY, ip).apply();
    }

    public void setServerPort(String port) {
        secureEditor.putString(SERVER_PORT_KEY, port).apply();
    }

    public void setProxyHost(String proxy) {
        secureEditor.putString(PROXY_IP_KEY, proxy).apply();
    }

    public void setProxyPort(String proxyPort) {
        secureEditor.putString(PROXY_PORT_KEY, proxyPort).apply();
    }

    public void setUser(String str) {
        secureEditor.putString(USERNAME_KEY, str).apply();
    }

    public void setUserPass(String str) {
        secureEditor.putString(PASSWORD_KEY, str).apply();
    }

    public void setDNSaddress(String dns) {
        secureEditor.putString(DNS_ADDRESS_KEY, dns).apply();
    }

    public void setDNSpublicKey(String key) {
        secureEditor.putString(DNS_PUBLIC_KEY, key).apply();
    }

    public void setDNSnameServer(String dnsName) {
        secureEditor.putString(DNS_NAME_SERVER_KEY, dnsName).apply();
    }

    public void setCustomDNSaddress(String dnsCustom) {
        secureEditor.putString(CUSTOM_DNS_ADDRESS_KEY, dnsCustom).apply();
    }

    public String getServerType() {
        return prefs.getString(SERVER_TYPE, SERVER_TYPE_OVPN);
    }

    public void setServerType(String str) {
        editor.putString(SERVER_TYPE, str).apply();
    }

    public String getLocalPort() {
        return prefs.getString(PORTA_LOCAL_KEY, "1080");
    }

    public void setLocalPort(String str) {
        editor.putString(PORTA_LOCAL_KEY, str).apply();
    }

    public int getMaximoThreadsSocks() {
        String n = prefs.getString(MAXIMO_THREADS_KEY, "8th");
        if (n == null || n.isEmpty()) {
            n = "8th";
        }
        return Integer.parseInt(n.replace("th", ""));
    }

    public void setMaximoThreadsSocks(String str) {
        editor.putString(MAXIMO_THREADS_KEY, str).apply();
    }

    public boolean getAutoClearLog() {
        return prefs.getBoolean(AUTO_CLEAR_LOGS_KEY, true);
    }

    public void setAutoClearLog(boolean use) {
        editor.putBoolean(AUTO_CLEAR_LOGS_KEY, use).apply();
    }

    public boolean getIsFilterApps() {
        return prefs.getBoolean(FILTER_APPS, false);
    }

    public boolean getIsFilterBypassMode() {
        return prefs.getBoolean(FILTER_BYPASS_MODE, false);
    }

    public void setFilterBypassMode(boolean use) {
        editor.putBoolean(FILTER_BYPASS_MODE, use).apply();
    }

    public String[] getFilterApps() {
        String txt = prefs.getString(FILTER_APPS_LIST, "");
        if (txt.isEmpty()) {
            return new String[]{};
        } else {
            return txt.split("\n");
        }
    }

    public void setFilterApps(boolean use) {
        editor.putBoolean(FILTER_APPS, use).apply();
    }

    public void setFilterApps(String list) {
        editor.putString(FILTER_APPS_LIST, list).apply();
    }

    public boolean getIsTetheringSubnet() {
        return prefs.getBoolean(TETHERING_SUBNET, false);
    }

    public void setTetheringSubnet(boolean use) {
        editor.putBoolean(TETHERING_SUBNET, use).apply();
    }

    public boolean getIsDisabledDelaySSH() {
        return prefs.getBoolean(DISABLE_DELAY_KEY, true);
    }

    public void setDisabledDelaySSH(boolean use) {
        editor.putBoolean(DISABLE_DELAY_KEY, use).apply();
    }

    public boolean getCompression() {
        return prefs.getBoolean(Data_compression_key, true);
    }

    public void setCompression(boolean use) {
        editor.putBoolean(Data_compression_key, use).apply();
    }


    /**
     * Vpn Settings
     */

    public boolean getVpnDnsForward(){
        return prefs.getBoolean(DNSFORWARD_KEY, true);
    }

    public void setVpnDnsForward(boolean use){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(DNSFORWARD_KEY, use);
        editor.commit();
    }

    public String getVpnDnsResolver1(){
        return prefs.getString(DNSRESOLVER_KEY_1, "8.8.8.8");
    }
    public void setVpnDnsResolver1(String str) {
        if (str == null || str.isEmpty()) {
            str = "8.8.8.8";
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DNSRESOLVER_KEY_1, str);
        editor.commit();
    }

    public String getVpnDnsResolver2(){
        return prefs.getString(DNSRESOLVER_KEY_2, "8.8.4.4");
    }
    public void setVpnDnsResolver2(String str) {
        if (str == null || str.isEmpty()) {
            str = "8.8.4.4";
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DNSRESOLVER_KEY_2, str);
        editor.commit();
    }
	
	
	
    
    public String[] getVpnDnsResolver() {
        String[] dns = prefs.getString(DNSRESOLVER_KEY, "8.8.8.8:8.8.4.4").split(":");
        if (Arrays.toString(dns).length() <= 9) {
            return new String[]{"8.8.8.8", "8.8.4.4"};
        }
        return new String[]{dns[0], dns[1]};
    }

    public void setVpnDnsResolver(String dns) {
        if (dns.length() <= 9) {
            dns = "8.8.8.8:8.8.4.4";
        }
        editor.putString(DNSRESOLVER_KEY, dns).apply();
    }



    public int getSSHPinger() {
        String ping = prefs.getString(PINGER_KEY, "3");
        if (ping == null || ping.isEmpty()) {
            ping = "3";
        }
        return Integer.parseInt(ping);
    }

    public void setSSHPinger(String str) {
        editor.putString(PINGER_KEY, str).apply();
    }

    public boolean getPowerSaver() {
        return prefs.getBoolean(PAUSE_VPN_ON_BLANKED_SCREEN_KEY, false);
    }

    public void setPowerSaver(boolean use) {
        editor.putBoolean(PAUSE_VPN_ON_BLANKED_SCREEN_KEY, use).apply();
    }

    public void setPaylodType(int type) {
        editor.putInt(PAYLOAD_TYPE_KEY, type).apply();
    }

    public int getPayloadType() {
        return prefs.getInt(PAYLOAD_TYPE_KEY, PAYLOAD_TYPE_DIRECT);
    }

   
    public String getProxyAddress() {
        return prefs.getString("_mProxyAddress", "127.0.0.1:8989");
    }

    public void setProxyAddress(String adr) {
        editor.putString("_mProxyAddress", adr).apply();
    }

    public String getContactUrl() {
        String cUrl = prefs.getString(CONTACT_SUPPORT, "");
        if (cUrl.startsWith("http")) {
            return cUrl;
        }
        return "https://" + cUrl;
    }

    public int getReconnTime() {
        return prefs.getInt(AUTO_RECONN_TIME_KEY, 5);
    }

    public void setReconnTime(int r) {
        editor.putInt(AUTO_RECONN_TIME_KEY, r).apply();
    }

    public String getSSHKeypath() {
        return prefs.getString("KEYPATH_KEY", "");
    }

    private void networkStateChange(boolean showStatusRepetido) {
        String netstatestring;
        try {
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo == null) {
                netstatestring = "not connected";
            } else {
                String subtype = networkInfo.getSubtypeName();
                if (subtype == null)
                    subtype = "";
                String extrainfo = networkInfo.getExtraInfo();
                if (extrainfo == null)
                    extrainfo = "";
                netstatestring = String.format("%2$s %4$s to %1$s %3$s", networkInfo.getTypeName(), networkInfo.getDetailedState(), extrainfo, subtype);
            }
        } catch (Exception e) {
            netstatestring = e.getMessage();
        }
        if (showStatusRepetido || !netstatestring.equals(lastStateMsg))
            addLogInfo(netstatestring);
        lastStateMsg = netstatestring;
    }

    private String getIpPublic() {
        final android.net.NetworkInfo network = connMgr.getActiveNetworkInfo();
        if (network != null && network.isConnectedOrConnecting()) {
            return TunnelUtils.getLocalIpAddress();
        } else {
            return "Indisponivel";
        }
    }

    public void initializeMsg() {
        
        networkStateChange(true);
        addLogInfo(String.format("Local IP: %s", getIpPublic()));
        
        
    }

    private void addLogInfo(String msg) {
        TkLogStatus.logInfo(msg);
    }

    public void setConnectedSpitPayload(String p) {
        String name = getServerName() + getPayloadName();
        splitEditor.putString(name, p).apply();
    }

    public String getConnectedSpit() {
        String name = getServerName() + getPayloadName();
        return splitPref.getString(name, "");
    }

    public void clearSplit() {
        splitEditor.clear().apply();
    }

    public String getV2() {
        return prefs.getString("V2", "");
    }

    public void setV2(String proxy) {
        editor.putString("V2", proxy).apply();
    }

}
