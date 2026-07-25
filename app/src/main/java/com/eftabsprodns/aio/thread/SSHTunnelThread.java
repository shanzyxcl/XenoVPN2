package com.eftabsprodns.aio.thread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.os.Build;
import android.util.Log;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ProxyData;
import com.trilead.ssh2.ServerHostKeyVerifier;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.eftabsprodns.aio.MyApplication;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.core.PasswordCache;
import com.eftabsprodns.aio.service.VPNService;
import com.eftabsprodns.aio.utils.util;


public class SSHTunnelThread extends Thread implements ConnectionMonitor, InteractiveCallback, ServerHostKeyVerifier, DebugLogger, SettingsConstants {
    private static final String TAG = SSHTunnelThread.class.getSimpleName();
    private final static int AUTH_TRIES = 1;
    private final static int RECONNECT_TRIES = 5;
    private static final String AUTH_PUBLICKEY = "publickey", 
    AUTH_PASSWORD = "password";
    private final SharedPreferences mPref;
    private final VPNService mContext;
    private final ConfigUtil mConfig;
    public boolean mReconnecting = false;
    private boolean mStopping = false, mStarting = false;
    private DNSTunnelThread mDNSTunnelThread;
    private CountDownLatch mTunnelThreadStopSignal;
    private OnTun2SocksListener mListener;
    private Connection mConnection;
    private boolean mConnected = false;
    private boolean useProxy = false;
    private DynamicPortForwarder dpf;
    private LocalPortForwarder dnsForwarder;

   
	private Pinger pinger;

    
    public SSHTunnelThread(VPNService context) {
        mContext = context;
        mConfig = ConfigUtil.getInstance(context);
        mPref = MyApplication.getPrivateSharedPreferences();
        new Thread(this::startDNSTunnel).start();
        
    }

    public void setOnTun2SocksListener(OnTun2SocksListener listener) {
        this.mListener = listener;
    }
    
    
   private void addLogInfo(String tag, String msg) {
        addLogInfo(String.format("%s: %s", tag, msg));
    }
    
    

    private void startDNSTunnel() {
        if (mDNSTunnelThread != null) {
            mDNSTunnelThread.interrupt();
            mDNSTunnelThread = null;
        }
        if (mConfig.getServerType().equals(SERVER_TYPE_DNS)) {
            mDNSTunnelThread = new DNSTunnelThread(mContext);
            mDNSTunnelThread.start();
        }
    }

    @Override
    public void run() {
        super.run();
        if (!VPNService.isRunning) {
            interrupt();
            return;
        }
        mStarting = true;
        mTunnelThreadStopSignal = new CountDownLatch(1);
        int tries = 0;
        while (!mStopping) {
            try {
                if (!util.isNetworkAvailable(mContext)) {
                    if (TkLogStatus.isTunnelActive()) {
                        TkLogStatus.updateStateString(TkLogStatus.VPN_PAUSE, mContext.getString(R.string.state_pause));
                    }
                    if (!TkLogStatus.isTunnelActive()) {
                        TkLogStatus.updateStateString(TkLogStatus.VPN_NO_NETWORK, mContext.getString(R.string.state_pause));
                    }
                    
                         
                  
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e2) {
                        mListener.onStop();
                        break;
                    }
                } else {
                    if (tries > 0) {
                        TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, mContext.getString(R.string.state_reconnecting) + " " + tries + "/" + "50");
                        if (tries == 50) {
                            addLogInfo("<b>Connection timeout</b>");
                            mListener.onStop();
                            break;
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e2) {
                        mListener.onStop();
                        break;
                    }
                    startClienteSSH();
                    break;
                }
            } catch (Exception e) {
                new Thread(this::closeSSH).start();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e2) {
                    mListener.onStop();
                    break;
                }
            }
            tries++;
        }
        mStarting = false;
        if (!mStopping) {
            try {
                mTunnelThreadStopSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void startForwarder(int portaLocal) throws Exception {
        if (!mConnected) {
            throw new Exception();
        }
        startForwarderSocks(portaLocal);
        mContext.SSHTunnel_handler(true);
        new Thread(() -> {
            while (true) {
                if (!mConnected) break;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
                    
                   if (lastPingLatency <100) {
							TkLogStatus.logInfo(String.format("Ping Latency: <font color=\"green\">%d ms</font>", lastPingLatency));  
							break;
						}else if (lastPingLatency > 100){
							TkLogStatus.logInfo(String.format("Ping Latency: <font color=\"red\">%d ms</font>", lastPingLatency));  
							break;
                        }
                    
                    
                    
            }
        }).start();
        
        
        
       String PING = mConfig.setPinger();
        if (mConfig.setAutoPing()){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            if (!PING.equals("")){
                pinger = new Pinger(mConnection, PING);
                pinger.start();
            }

        }
        
    }

    protected void startClienteSSH() throws Exception {
        if (!VPNService.isRunning) return;
        mStopping = false;
        String[] prxAdrss = mConfig.getProxyAddress().split(":");
        String[] mHost = mConfig.getQueryHost().split(":");
        int serverPort = Integer.parseInt(mHost[1]);
        String useraccount = mConfig.getSecureString(USERNAME_KEY);
        String passaccount = mConfig.getSecureString(PASSWORD_KEY);
        String senha = passaccount.isEmpty() ? PasswordCache.getAuthPassword(null, false) : passaccount;
        String keyPath = mConfig.getSSHKeypath();
        int portaLocal = Integer.parseInt(mConfig.getLocalPort());
        try {
            conectar(mHost[0], serverPort, prxAdrss);
            for (int i = 0; i < AUTH_TRIES; i++) {
                if (mStopping) {
                    return;
                }
                try {
                    autenticar(useraccount, senha, keyPath);
                    break;
                } catch (IOException e) {
                    if (i + 1 >= AUTH_TRIES) {
                        throw new IOException("Autenticação falhou");
                    } else {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e2) {
                            return;
                        }
                    }
                }
            }
            addLogInfo("<b>SSH Core: </b>" + mContext.getString(R.string.state_connected));
            startForwarder(portaLocal);
        } catch (Exception e) {
            mConnected = false;
            reconnectSSH();
            throw e;
        }
    }

    public void closeSSH() {
        mContext.SSHTunnel_handler(false);
        stopForwarderSocks();
        if (mConnection != null) {
            mConnection.close();
        }
    }

    protected void conectar(String servidor, int porta, String[] prxAdrss) throws Exception {
        if (!mStarting) {
            throw new Exception();
        }
        try {
            int recon = mConfig.getReconnTime();
            mConnection = new Connection(servidor, porta);
            if (mConfig.getIsDisabledDelaySSH()) {
                mConnection.setTCPNoDelay(true);
            }
            if (mConfig.getCompression()) {
                mConnection.setCompression(true);
            }
            addProxy(mConnection, prxAdrss[0], Integer.parseInt(prxAdrss[1]));
            mConnection.addConnectionMonitor(this);
            if (Build.VERSION.SDK_INT >= 23) {
                ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                ProxyInfo proxy = cm.getDefaultProxy();
                if (proxy != null) {
                    addLogInfo("<b>Proxy na Rede:</b> " + String.format("%s:%d", proxy.getHost(), proxy.getPort()));
                }
            }
            TkLogStatus.updateStateString(TkLogStatus.VPN_GET_CONFIG, mContext.getString(R.string.state_get_config));
            addLogInfo(mContext.getString(R.string.state_get_config));
            addLogInfo(mContext.getString(R.string.state_connecting));
            addLogInfo(mContext.getString(R.string.state_wait));
            mConnection.connect(this, recon * 1000, recon * 2 * 1000);
            mConnected = true;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String cause = e.getCause().toString();
            if (useProxy && cause.contains("Key exchange was not finished")) {
                addLogInfo("<b>SSH Core: </b>Proxy lost connection");
            } else {
                TkLogStatus.logDebug("<b>SSH Core: </b>" + cause);
            }
            throw new Exception(e);
        }
    }

    protected void autenticar(String usuario, String senha, String keyPath) throws IOException {
        if (!mConnected) {
            throw new IOException();
        }
        TkLogStatus.updateStateString(TkLogStatus.VPN_AUTHENTICATING, mContext.getString(R.string.state_auth));
        try {
            if (mConnection.isAuthMethodAvailable(usuario, AUTH_PASSWORD)) {
                addLogInfo("Authenticate with password");
                if (mConnection.authenticateWithPassword(usuario, senha)) {
                    addLogInfo("<b>" + mContext.getString(R.string.state_auth_success) + "</b>");
                }
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Connection went away while we were trying to authenticate", e);
        } catch (Exception e) {
            Log.e(TAG, "Problem during handleAuthentication()", e);
        }
        try {
            if (mConnection.isAuthMethodAvailable(usuario, AUTH_PUBLICKEY) && keyPath != null && !keyPath.isEmpty()) {
                File f = new File(keyPath);
                if (f.exists()) {
                    if (senha.equals("")) senha = null;
                    addLogInfo("Autenticando com public key");
                    if (mConnection.authenticateWithPublicKey(usuario, f, senha)) {
                        addLogInfo("<b>" + mContext.getString(R.string.state_auth_success) + "</b>");
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Host does not support 'Public key' authentication.");
        }
        if (!mConnection.isAuthenticationComplete()) {
            interrupt();
            addLogInfo("<font color = #d50000>Failed to authenticate, username or password expired");
            TkLogStatus.updateStateString(TkLogStatus.VPN_AUTH_FAILED, mContext.getString(R.string.state_auth_failed));
            throw new IOException("Não foi possivel autenticar com os dados fornecidos");
        }
    }

    // XXX: Is it right?
    @Override
    public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) throws Exception {
        String passaccount = mConfig.getSecureString(PASSWORD_KEY);
        String[] responses = new String[numPrompts];
        for (int i = 0; i < numPrompts; i++) {
            if (prompt[i].toLowerCase().contains("password"))
                responses[i] = passaccount;
        }
        return responses;
    }

    @Override
    public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
        String fingerPrint = KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey);
        //int fingerPrintStatus = SSHConstants.FINGER_PRINT_CHANGED;
        addLogInfo("Finger Print: " + fingerPrint);
        //Log.d(TAG, "Finger Print Type: " + "");
        return true;
    }

    private boolean addProxy(Connection conn, String mProxy, int mPort) {
        String s = mConfig.getServerType();
        int p = mConfig.getPayloadType();
        if (s.equals(SERVER_TYPE_SSH) && p == PAYLOAD_TYPE_DIRECT || s.equals(SERVER_TYPE_DNS)) {
            useProxy = false;
            return true;
        } else if (s.equals(SERVER_TYPE_SSH) && p == PAYLOAD_TYPE_DIRECT_PAYLOAD) {
            useProxy = false;
            String mCustomPayload = (!mConfig.getSecureString(CUSTOM_PAYLOAD_KEY).isEmpty() ? mConfig.getSecureString(CUSTOM_PAYLOAD_KEY) : null);
            ProxyData proxyData = new HttpProxyCustom(mConfig.getSecureString(SERVER_KEY), Integer.parseInt(mConfig.getSecureString(SERVER_PORT_KEY)), null, null, mCustomPayload, true, mContext);
            conn.setProxyData(proxyData);
            return true;
        } else {
            useProxy = true;
            conn.setProxyData(new HTTPProxyData(mProxy, mPort));
            return true;
        }
    }

    @SuppressLint("DefaultLocale")
    private void startForwarderSocks(int portaLocal) throws Exception {
        if (!mConnected) {
            throw new Exception();
        }
        String[] pingServ = mConfig.getPingServer().trim().split(":");
        boolean isWebsocks = mPref.getString("mServerType", "").equals("http");
        addLogInfo("starting socks local");
        TkLogStatus.logDebug(String.format("socks local listen: %d", portaLocal));
        try {
            //int nThreads = mConfig.getMaximoThreadsSocks();
            dnsForwarder = mConnection.createLocalPortForwarder(8053, pingServ[0], Integer.parseInt(pingServ[1]));
            if (isWebsocks)
                dpf = mConnection.createDynamicPortForwarder(portaLocal);
            else
                dpf = mConnection.createDynamicPortForwarder(new InetSocketAddress("127.0.0.1", portaLocal));

            addLogInfo("<b>SSH Socket</b> Forward Successful");
        } catch (Exception e) {
            TkLogStatus.logError("Socks Local: " + e.getCause());
            throw new Exception();
        }
    }

    private void stopForwarderSocks() {
        if (dnsForwarder != null) {
            try {
                dnsForwarder.close();
            } catch (IOException ignored) {
            }
            dnsForwarder = null;
        }
        if (dpf != null) {
            try {
                dpf.close();
            } catch (IOException ignored) {
            }
            dpf = null;
        }
    }

    
    
   private synchronized void interruptPinger() {
        if (pinger != null && pinger.isAlive()) {
            //SkStatus.logInfo("Stopping Pinger");
            pinger.interrupt();
        }
    }

    protected void stopForwarder() {
        mContext.SSHTunnel_handler(false);
        
        
        stopForwarderSocks();
    }
    
    
   

    /**
     * Pinger
     */

    private Thread thPing;
    private long lastPingLatency = -1;
	public static long latencia = 0;
    private void startPinger(final int timePing) throws Exception {
        if (!mConnected) {
            throw new Exception();
        }

        //SkStatus.logInfo("starting pinger");

        thPing = new Thread() {
            @Override
            public void run() {
                while (mConnected) {
                    try {
                        makePinger();
                    } catch(InterruptedException e) {
                        break;
                    }
                }
				//     SkStatus.logDebug("pinger stopped");
            }

            private synchronized void makePinger() throws InterruptedException {
                try {
                    if (mConnection != null) {
                        long ping = mConnection.ping();
                        if (lastPingLatency < 0) {
                            lastPingLatency = ping;
							latencia = ping;
                        }
                    }
                    else throw new InterruptedException();
                } catch(Exception e) {
                    Log.e(TAG, "ping error", e);
                }

                if (timePing == 0)
                    return;

                if (timePing > 0)
                    sleep(timePing*1000);
                else {
                    TkLogStatus.logError("ping invalid");
                    throw new InterruptedException();
                }
            }
        };

        // inicia
        thPing.start();
    }

    private synchronized void stopPinger() {
        if (thPing != null && thPing.isAlive()) {
			//  SkStatus.logInfo("stopping pinger");

            thPing.interrupt();
            thPing = null;
        }
    }

    
    
    
    
    @Override
    public void connectionLost(Throwable reason) {
        if (!VPNService.isRunning || mStarting || mStopping || mReconnecting) {
            return;
        }
        mContext.SSHTunnel_handler(false);
        if (reason != null) {
            if (reason.getMessage().contains("There was a problem during connect")) {
                return;
            } else if (reason.getMessage().contains("Closed due to user request")) {
                return;
            } else if (reason.getMessage().contains("The connect timeout expired")) {
                mListener.onStop();
                return;
            }
        } else {
            mListener.onStop();
            return;
        }
        reconnectSSH();
    }

    @Override
    public void onReceiveInfo(int infoId, String infoMsg) {

    }

    public void reconnectSSH() {
        new Thread(this::closeSSH).start();
        if (mConfig.getServerType().equals(SERVER_TYPE_DNS)) {
            new Thread(this::startDNSTunnel).start();
            new Thread(this::mReconnectSSH).start();
        } else
            new Thread(this::mReconnectSSH).start();
    }

    private void mReconnectSSH() {
        if (!VPNService.isRunning || mStarting || mStopping || mReconnecting) {
            return;
        }
        mReconnecting = true;
        TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, mContext.getString(R.string.state_reconnecting));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            mReconnecting = false;
            return;
        }
        for (int i = 0; i < RECONNECT_TRIES; i++) {
            if (!VPNService.isRunning || mStopping) {
                mReconnecting = false;
                return;
            }
            int sleepTime = 5;
            if (!util.isNetworkAvailable(mContext)) {
                TkLogStatus.updateStateString(TkLogStatus.VPN_PAUSE, mContext.getString(R.string.state_pause));
            } else {
                sleepTime = 3;
                mStarting = true;
                TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, mContext.getString(R.string.state_reconnecting));
                try {
                    startClienteSSH();
                    mStarting = false;
                    mReconnecting = false;
                    return;
                } catch (Exception e) {
                    mListener.onStop();
                }
                mStarting = false;
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
        mListener.onStop();
    }

    @Override
    public void log(int level, String className, String message) {
        TkLogStatus.logDebug(String.format("%s: %s", className, message));
    }

    @Override
    public void interrupt() {
        super.interrupt();
        mStopping = true;
        mStarting = false;
        mReconnecting = false;
        new Thread(this::closeSSH).start();
        if (mTunnelThreadStopSignal != null) mTunnelThreadStopSignal.countDown();
        if (mDNSTunnelThread != null) {
            mDNSTunnelThread.interrupt();
            mDNSTunnelThread = null;
        }
    }

    public void addLogInfo(String msg) {
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        if (msg.contains(hst) || msg.contains(prx)) {
            msg = msg.trim().replace(hst, "[Server]").replace(prx, "[Proxy]");
        }
        if (!msg.trim().contains("Socket closed")) {
            if (msg.trim().contains("Connection timed out")) {
                TkLogStatus.logInfo("<b>Connection timed out</b>");
                mListener.onStop();
            } else {
                TkLogStatus.logInfo(msg.trim().replace("java.io.IOException:", ""));
            }
        }
    }

    public interface OnTun2SocksListener {
        void onStop();
    }

}
