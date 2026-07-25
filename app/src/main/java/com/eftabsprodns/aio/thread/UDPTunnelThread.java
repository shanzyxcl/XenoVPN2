package com.eftabsprodns.aio.thread;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import app.tunnel.vpncommons.utils.CustomNativeLoader;
import app.tunnel.vpncommons.utils.StreamGobbler;
import app.tunnel.vpncommons.utils.VpnUtils;
import app.tunnel.vpncommons.vpnstatus.TkLogStatus;

public class UDPTunnelThread extends Thread implements SettingsConstants {

    static {
        System.loadLibrary("ovpnudp");
    }
    private final ConfigUtil mConfig;
    private File filehysteria;
    private Process hysteriaProcess;
    private final Context mContext;
    private static final String UDP_BIN = "libovpnudp";
    private static final String UDP_CLI = "{\n  \"server\": \"%s\",\n  \"obfs\": \"%s\",\n  \"auth_str\": \"%s\",\n  \"up_mbps\": %s,\n  \"down_mbps\": %s,\n  \"retry\": %s,\n  \"retry_interval\": %s,\n  \"socks5\": {\n    \"listen\": \"%s\"\n  },\n  \"http\": {\n    \"listen\": \"%s\"\n  },\n  \"insecure\": %s,\n  \"lazy_start\": true,\n  \"ca\": \"%s\",\n  \"recv_window_conn\": %s,\n \"recv_window\": %s\n}";

    public UDPTunnelThread(Context mContext) {
        this.mContext = mContext;
        mConfig = ConfigUtil.getInstance(mContext);
    }

    private OnTun2SocksListener mListener;
    public interface OnTun2SocksListener {
        void onConnected();
        void onReconnect();
        void onStop();
    }

    public void setOnTun2SocksListener(OnTun2SocksListener listener){
        this.mListener = listener;
    }
    /**
     * Tknetwork01/16/2024...
     */
    @Override
    public void run() {
        super.run();
        try {
            TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTING, mContext.getString(R.string.state_connecting));
            addLog("<b>UDP Tunnel: </b>" + mContext.getString(R.string.state_connecting));

            StringBuilder cmd = new StringBuilder();

            filehysteria = CustomNativeLoader.loadNativeBinary(mContext, UDP_BIN, new File(mContext.getFilesDir(), UDP_BIN));

            if (filehysteria == null) {
                interrupt();
                throw new IOException("Bin UDP was not found");
            }

            if (this.mConfig.getSecureString(SERVER_KEY).isEmpty()) {
                interrupt();
                addLog("<b>UDP Tunnel: </b> Server is empty!");
                TkLogStatus.updateStateString(TkLogStatus.VPN_AUTH_FAILED, mContext.getString(R.string.state_auth_failed));
                mListener.onStop();
                throw new IOException("Invalid UDP Server");
            }
            TkLogStatus.updateStateString(TkLogStatus.VPN_GET_CONFIG, mContext.getString(R.string.state_get_config));
            addLog("<b>UDP Tunnel: </b>" + mContext.getString(R.string.state_get_config));
            addLog("<b>UDP Tunnel: </b>" + mContext.getString(R.string.state_wait));
            File s = makeUdpConf(mContext.getFilesDir());

            cmd.append(filehysteria.getCanonicalPath());
            cmd.append(" client -c ");
            cmd.append(s.getAbsoluteFile());

            if(s==null){
                TkLogStatus.logInfo("<font color = #d50000>Failed to get file!");
                mListener.onStop();
                return;

            }
            hysteriaProcess = Runtime.getRuntime().exec(cmd.toString());

            StreamGobbler.OnLineListener onLineListener = log -> {
                Log.d(UDP_BIN, "<b>UDP Client: </b>" + log);
                if (log.toLowerCase().contains("connected")) {
                    addLog("<b>UDP Client: </b>" + log.replace(excludeIps(),"******"));
                    addLog("<font color='#0B8C1C'><strong>" + "UDP Client: Connected" + "</b>");

                    mListener.onConnected();
                } else if (log.toLowerCase().contains("auth error")) {
                    addLog("<font color = #d50000>Failed to authenticate, username or password expired");
                    TkLogStatus.updateStateString(TkLogStatus.VPN_AUTH_FAILED, mContext.getString(R.string.state_auth_failed));
                    mListener.onStop();
                } else if (log.toLowerCase().contains("out of retries")) {
                    addLog("<b>UDP Client: </b>out of retries");
                    // interrupt();
                    mListener.onReconnect();
                } else if (log.contains("[ERRO]")) {
                    addLog("<b>UDP Client: </b>" + log);
                    // interrupt();
                    mListener.onReconnect();
                }
            };

            StreamGobbler stdoutGobbler = new StreamGobbler(hysteriaProcess.getInputStream(), onLineListener);
            StreamGobbler stderrGobbler = new StreamGobbler(hysteriaProcess.getErrorStream(), onLineListener);

            stdoutGobbler.start();
            stderrGobbler.start();

            hysteriaProcess.waitFor();
        } catch (IOException | InterruptedException e) {
            interrupt();
            addLog("UDP Client: " + e.getMessage());
        }
    }

    @Override
    public void interrupt() {
        if (hysteriaProcess != null)
            hysteriaProcess.destroy();
        try {
            if (filehysteria != null)
                VpnUtils.killProcess(filehysteria);
        } catch (Exception ignored) {
        }
        hysteriaProcess = null;
        filehysteria = null;
        super.interrupt();
    }
    /**
     * Tknetwork01/16/2024...
     */
    public String getUDPConfig(){
        try {
            JSONObject js = new JSONObject(mConfig.getSecureString(DIRECT_UDP_CONFIG_KEY));
            String mObfs = js.getString("obfs");
            String mUser = mConfig.getSecureString(USERNAME_KEY);
            String mPass = mConfig.getSecureString(PASSWORD_KEY);
            int up = js.getInt("up_mbps");
            int dw = js.getInt("down_mbps");
            int rt = js.getInt("retry");
            int rt_in = js.getInt("retry_interval");
            String mSocks5 =  js.getJSONObject("socks5").getString("listen");
            String mHttp =  js.getJSONObject("http").getString("listen");
            boolean mInsecure =  js.getBoolean("insecure");
            String mCa =  js.getString("ca");
            int mRecv_window_conn =  js.getInt("recv_window_conn");
            int mRecv_window =  js.getInt("recv_window");

            return String.format(UDP_CLI, excludeIps() + ":20000-50000",  mObfs, String.format("%s:%s", mUser, mPass), up, dw, rt,rt_in, mSocks5, mHttp, mInsecure, mCa,mRecv_window_conn, mRecv_window);


        } catch (JSONException e) {
            return mConfig.getSecureString(DIRECT_UDP_CONFIG_KEY);
        }
    }

    private File makeUdpConf(File fileDir) throws IOException {
        System.out.println(excludeIps());
        String conf = getUDPConfig();
        File f = new File(fileDir, "config.json");
        if (f.exists()) {
            f.delete();
        }
        saveTextFile(f, conf);

        File cache = new File(fileDir, "config.json");
        if (!cache.exists()) {
            try {
                cache.createNewFile();
            } catch (Exception e) {
            }
        }
        return f;
    }

    public static boolean saveTextFile(File file, String contents) {
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter writer = new FileWriter(file, false);
            writer.write(contents);
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String excludeIps() {
        try {
            InetAddress addr = InetAddress.getByName(mConfig.getSecureString(SERVER_KEY));
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            interrupt();
            return null;
        }
    }

    public static void addLog(String msg) {
        TkLogStatus.logInfo(msg);
    }

}
