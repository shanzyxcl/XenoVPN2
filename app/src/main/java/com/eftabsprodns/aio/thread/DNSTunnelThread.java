package com.eftabsprodns.aio.thread;

import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.IOException;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.core.vpnutils.CustomNativeLoader;
import com.eftabsprodns.aio.core.vpnutils.StreamGobbler;
import com.eftabsprodns.aio.core.vpnutils.VpnUtils;
import com.eftabsprodns.aio.service.VPNService;
import com.eftabsprodns.aio.utils.util;


public class DNSTunnelThread extends Thread implements SettingsConstants {

    private static final String DNS_BIN = "libdns";
    private final Context context;
    private final ConfigUtil mConfig;
    private Process dnsProcess;
    private File filedns;

    public DNSTunnelThread(Context context) {
        this.context = context;
        mConfig = ConfigUtil.getInstance(context);
    }

    @Override
    public final void run() {
        super.run();
        if (!VPNService.isRunning) {
            interrupt();
            return;
        }
        try {
            TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTING, context.getString(R.string.state_connecting));
            addLogInfo("<b>DNS Tunnel: </b>" + context.getString(R.string.state_connecting));

            StringBuilder cmd1 = new StringBuilder();

            filedns = CustomNativeLoader.loadNativeBinary(context, DNS_BIN, new File(context.getFilesDir(), DNS_BIN));
            if (filedns == null) {
                interrupt();
                throw new IOException("<b>DNS Tunnel: </b> bin not found");
            }

            cmd1.append(filedns.getCanonicalPath());
            cmd1.append(" -udp ").append(this.mConfig.getSecureString(DNS_ADDRESS_KEY)).append(":53   -pubkey ").append(mConfig.getSecureString(DNS_PUBLIC_KEY)).append(" ").append(this.mConfig.getSecureString(DNS_NAME_SERVER_KEY)).append(" ").append("127.0.0.1:2222");

            dnsProcess = Runtime.getRuntime().exec(cmd1.toString());

            StreamGobbler.OnLineListener onLineListener = log -> {
                addLogInfo("<b>DNS Tunnel: </b>" + log);
                if (log.contains("address of UDP DNS resolver") && mConfig.getSecureString(DNS_PUBLIC_KEY).isEmpty() && TkLogStatus.isTunnelActive()) {
                    addLogInfo("<b>DNS Tunnel: </b> Connection error!");
                    context.startService(new Intent(context, VPNService.class).setAction(VPNService.RECONNECT_SERVICE));
                }
            };

            StreamGobbler stdoutGobbler = new StreamGobbler(dnsProcess.getInputStream(), onLineListener);
            StreamGobbler stderrGobbler = new StreamGobbler(dnsProcess.getErrorStream(), onLineListener);

            stdoutGobbler.start();
            stderrGobbler.start();

            dnsProcess.waitFor();
        } catch (IOException | InterruptedException e) {
            interrupt();
            addLogInfo("DNS Tunnel error: " + e.getMessage());
        } catch (Exception ex) {
            TkLogStatus.logDebug("DNS Tunnel Error" + ex.getMessage());
        }

        dnsProcess = null;
    }


    private void addLogInfo(String mLog) {
        if (util.isNetworkAvailable(context) && TkLogStatus.isTunnelActive()) {
            if (mLog.contains(this.mConfig.getSecureString(DNS_ADDRESS_KEY)) || mLog.contains(this.mConfig.getSecureString(DNS_NAME_SERVER_KEY))) {
                mLog = mLog.trim().replace(this.mConfig.getSecureString(DNS_ADDRESS_KEY), "[dns address]").replace(this.mConfig.getSecureString(DNS_NAME_SERVER_KEY), "[dns ServerName]");
            }
            if (!mLog.contains("network is unreachable") && util.isNetworkAvailable(context) && !mLog.contains("DNS Tunnel error: null")) {
                TkLogStatus.logInfo(mLog);
            }
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        if (dnsProcess != null)
            dnsProcess.destroy();
        try {
            if (filedns != null)
                VpnUtils.killProcess(filedns);
        } catch (Exception ignored) {
        }
        dnsProcess = null;
        filedns = null;
    }

}


