package com.eftabsprodns.aio.service;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;


import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.core.CIDRIP;
import com.eftabsprodns.aio.core.NetworkSpace;
import com.eftabsprodns.aio.core.vpnutils.Pdnsd;
import com.eftabsprodns.aio.core.vpnutils.Tun2Socks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.tunnel.vpncommons.utils.VpnUtils;
import app.tunnel.vpncommons.vpnstatus.TkLogStatus;


public class SSHTunnelService extends VpnService implements SettingsConstants {
    public static final String START_SSH_SERVICE = "ovpn.ssh.ssl.service.START_SSH_SERVICE";
    public static final String STOP_SSH_SERVICE = "ovpn.ssh.ssl.service.STOP_SSH_SERVICE";
    private ConfigUtil mConfig;
    private int mMtu = 1500;
    private Thread mBuilderThread;
    private Pdnsd mPdnsd;
    private static final String VPN_INTERFACE_NETMASK = "255.255.255.0";
    private Tun2Socks mTun2Socks;
    private static final int DNS_RESOLVER_PORT = 53;
    public static boolean isSSHRunning = false;
    private final IBinder mBinder = new LocalBinder();
    private VpnUtils.PrivateAddress mPrivateAddress;
    private AtomicReference<ParcelFileDescriptor> mTunFd;
    private AtomicBoolean mRoutingThroughTunnel;
    private NetworkSpace mRoutes;
    public class LocalBinder extends Binder {
        public SSHTunnelService getService() {
            return SSHTunnelService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(START_SSH_SERVICE)) return mBinder;
        else return super.onBind(intent);
    }

    public void onCreate() {
        super.onCreate();
        StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder();
        StrictMode.setThreadPolicy(builder.permitAll().build());
        mConfig = ConfigUtil.getInstance(this);
        mTunFd = new AtomicReference<>();
        mRoutingThroughTunnel = new AtomicBoolean(false);
        mRoutes = new NetworkSpace();
    }

    public int onStartCommand(Intent intent, int flags, int startId)  {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case START_SSH_SERVICE:
                    TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTED, getString(R.string.state_connected));
                    submit_establish_builder();
                    break;
                case STOP_SSH_SERVICE:
                    submit_destroy_builder();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    
  
    private void destroyAll(){
        if (isSSHRunning)addLogInfo("<b>SSH Tunnel: </b>application is not prepared or revoked");
        submit_destroy_builder();
        if(TkLogStatus.isTunnelActive())startService(new Intent(SSHTunnelService.this, VPNService.class).setAction(VPNService.STOP_SERVICE));
    }

    private void submit_destroy_builder(){
        new Thread(() -> {
            try {
                if (isSSHRunning)addLogInfo("<b>VPNService stopped</b>");
                isSSHRunning = false;
                if (mTun2Socks != null) {
                    mTun2Socks.interrupt();
                    mTun2Socks = null;
                }
                if (mPdnsd != null) {
                    mPdnsd.interrupt();
                    mPdnsd = null;
                }
                ParcelFileDescriptor tunFd = mTunFd.getAndSet(null);
                if (tunFd != null) {
                    try {
                        tunFd.close();
                    }
                    catch (IOException ignored) {}
                }
                if (mBuilderThread != null) {
                    mBuilderThread.interrupt();
                    mBuilderThread = null;
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                isSSHRunning = false;
                addLogInfo("<font color = #d50000>VPN interface error: "+e.getMessage());
                addLogInfo("<font color = #d50000>Failed to close the VPN interface file descriptor.");
                if(TkLogStatus.isTunnelActive())startService(new Intent(SSHTunnelService.this, VPNService.class).setAction(VPNService.STOP_SERVICE));
            }
        }).start();
    }


    private String excludeIps() {
        try {
            String serverIP = mConfig.getSecureString(SERVER_KEY);
            if (mConfig.getPayloadType()==PAYLOAD_TYPE_HTTP_PROXY||mConfig.getPayloadType()==PAYLOAD_TYPE_SSL_PROXY) {
                serverIP = mConfig.getSecureString(PROXY_IP_KEY);
            }
            if (mConfig.getServerType().equals(SERVER_TYPE_DNS)) {
                serverIP = mConfig.getSecureString(DNS_NAME_SERVER_KEY);
            }
            InetAddress addr = InetAddress.getByName(serverIP);
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private boolean dnsByPassMod(){
        return mConfig.getServerType().equals(SERVER_TYPE_DNS);
    }




    @SuppressLint("DefaultLocale")
    private boolean routeThroughTunnel(final String socksServerAddress, final String[] dnsResolver, boolean forwardDns, final String udpResolver, final boolean transparentDns) {
        if (!mRoutingThroughTunnel.compareAndSet(false, true)) {
            destroyAll();
            return false;
        }

        final ParcelFileDescriptor tunFd = mTunFd.get();
        if (tunFd == null) {
            destroyAll();
            return false;
        }

        String dnsgwRelay = null;
        if (forwardDns) {
            int pdnsdPort = VpnUtils.findAvailablePort(8091, 10);
            dnsgwRelay = String.format("%s:%d", mPrivateAddress.mIpAddress, pdnsdPort);
            mPdnsd = new Pdnsd(SSHTunnelService.this, dnsResolver, DNS_RESOLVER_PORT, mPrivateAddress.mIpAddress, pdnsdPort);
            mPdnsd.setOnPdnsdListener(() -> addLogInfo("Pdnsd started"));
            mPdnsd.start();
        }
        mTun2Socks = new Tun2Socks(SSHTunnelService.this, tunFd, mMtu, mPrivateAddress.mRouter, VPN_INTERFACE_NETMASK, socksServerAddress, udpResolver, dnsgwRelay, transparentDns);
        mTun2Socks.setOnTun2SocksListener(() -> addLogInfo("Tun2socks started"));
        mTun2Socks.start();
        isSSHRunning = true;
        addLogInfo("<font color='#0B8C1C'><b>VPNService Connected</b>");
        return true;
    }
	
	
	
	

    @SuppressLint("ObsoleteSdkInt")
    private void startVpn(boolean forwardDns, String[] dnsResolver, String excludeIps, boolean enabledFilter, boolean filterBypassMode, String[] filterApps, boolean enableTethering, boolean isSlowDNS) throws Exception {

        String socksServerAddress = "127.0.0.1:"+mConfig.getLocalPort();
        String udpResolver = (this.mConfig.getVpnUdpForward())?this.mConfig.getVpnUdpResolver():null;
        boolean mUdpDnsRelay = (forwardDns && udpResolver == null || !forwardDns && udpResolver != null);
        StringBuilder routeMessage = new StringBuilder("Routes: ");
       StringBuilder routeExcludeMessage = new StringBuilder("Routes Excluded: ");

		
		
		String NmN = mConfig.getVpnUdpResolver();
		//boolean udpResolver = mConfig.getVpnUdpForward();
		String getUdpLastString = NmN.substring(NmN.lastIndexOf(":") + 1);
	//	String m_udpResolver = mConfig.getVpnUdpForward() ? mConfig.getVpnUdpResolver() : null;
		// Get servidorIP based on tunnel type
	//	if (udpResolver) {
			if (!getUdpLastString.isEmpty()) {
				addLogInfo("UDP : " + "<font color='#FBBD00'>" + getUdpLastString + "</font>");
			} else {
				addLogInfo(
					"<strong><font color='#C33E3D'>UDP Forwarding activated, but UDP cannot be empty!</font></strong>");
			//}
		}
		
		
        mPrivateAddress = VpnUtils.selectPrivateAddress();

        if (excludeIps!=null){
            mRoutes.addIP(new CIDRIP(excludeIps, 32), false);
        }

        Locale previousLocale = Locale.getDefault();

        final String errorMessage = "startVpn failed";
        try {
            Locale.setDefault(new Locale("en"));

            ParcelFileDescriptor tunFd = null;

            Builder builder = new Builder().addAddress(mPrivateAddress.mIpAddress, mPrivateAddress.mPrefixLength);
            mRoutes.addIP(new CIDRIP("0.0.0.0", 0), true);
            mRoutes.addIP(new CIDRIP("10.0.0.0", 8), false);
            mRoutes.addIP(new CIDRIP(mPrivateAddress.mSubnet, mPrivateAddress.mPrefixLength), false);

            if (enableTethering) {
                mRoutes.addIP(new CIDRIP("192.168.42.0", 23), false);
                mRoutes.addIP(new CIDRIP("192.168.44.0", 24), false);
                mRoutes.addIP(new CIDRIP("192.168.49.0", 24), false);
            }

            for (String dns : dnsResolver) {
                try {
                    builder.addDnsServer(dns);
                    mRoutes.addIP(new CIDRIP(dns, 32), forwardDns);
                } catch (IllegalArgumentException iae) {
                    addLogInfo(String.format("Error Adding dns %s, %s", dns, iae.getLocalizedMessage()));
                }
            }

            String release = Build.VERSION.RELEASE;
            if ((Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !release.startsWith("4.4.3")
                    && !release.startsWith("4.4.4") && !release.startsWith("4.4.5") && !release.startsWith("4.4.6"))
                    && mMtu < 1280) {
                addLogInfo(String.format(Locale.US, "Forcing MTU to 1280 instead of %d to workaround Android Bug #70916", mMtu));
                mMtu = 1280;
            }
            builder.setMtu(mMtu);

            Collection<NetworkSpace.ipAddress> include_routes = mRoutes.getNetworks(true);
            for (NetworkSpace.ipAddress ip : include_routes) {
                routeMessage.append(String.format("%s/%s", ip.getIPv4Address(), ip.networkMask));
                routeMessage.append(", ");
            }
           routeMessage.deleteCharAt(routeMessage.lastIndexOf(", "));

            Collection<NetworkSpace.ipAddress> exclude_routes = mRoutes.getNetworks(false);
            for (NetworkSpace.ipAddress ip : exclude_routes) {
                routeExcludeMessage.append(String.format("%s/%s", ip.getIPv4Address(), ip.networkMask));
               routeExcludeMessage.append(", ");
            }
            routeExcludeMessage.deleteCharAt(routeExcludeMessage.lastIndexOf(", "));

         //   addLogInfo(routeMessage.toString());
          //  if (excludeIps!=null)
              //  addLogInfo(routeExcludeMessage.toString().replace(excludeIps, "******"));
      //   else
                //addLogInfo(routeExcludeMessage.toString());

            NetworkSpace.ipAddress multicastRange = new NetworkSpace.ipAddress(new CIDRIP("224.0.0.0", 3), true);

            for (NetworkSpace.ipAddress route : mRoutes.getPositiveIPList()) {
                try {
                    if (multicastRange.containsNet(route))
                        addLogInfo("VPN: Ignoring multicast route: " + route);
                    else
                        builder.addRoute(route.getIPv4Address(), route.networkMask);
                } catch (IllegalArgumentException ia) {
                    addLogInfo("Route rejected: " + route + " " + ia.getLocalizedMessage());
                }
            }

            if (isSlowDNS) {
                builder.addDisallowedApplication(getPackageName());
            }
            if (enabledFilter) {
                for (String app_pacote : filterApps) {
                    try {
                        if (filterBypassMode) {
                            builder.addDisallowedApplication(app_pacote);
                            addLogInfo(String.format("VPN disabled for<font color = #64dd17> %s", app_pacote));
                        }
                        else {
                            builder.addAllowedApplication(app_pacote);
                            addLogInfo(String.format("VPN enabled for<font color = #64dd17> %s", app_pacote));
                        }
                    } catch(PackageManager.NameNotFoundException e) {
                        addLogInfo("App " + app_pacote + " not found. Apps filter will not work, check settings.");
                    }
                }
            }
			

            tunFd = builder
                    .setSession(String.format("%s - %s", getString(R.string.app_name), "APPTknetwork"))
                    .setConfigureIntent(ConfigUtil.getPendingIntent(SSHTunnelService.this))
                    .establish();

            if (tunFd == null) {
                destroyAll();
                return;
            }

            mTunFd.set(tunFd);
            mRoutingThroughTunnel.set(false);

            if (routeThroughTunnel(socksServerAddress,dnsResolver,forwardDns,udpResolver,mUdpDnsRelay)){
                mRoutes.clear();
            }
        }
        catch (IllegalArgumentException | SecurityException | IllegalStateException e)
        {
            throw new Exception(errorMessage, e);
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

	
	
	
	private void submit_establish_builder(){
        if (mBuilderThread != null) {
            mBuilderThread.interrupt();
            mBuilderThread = null;
        }
        boolean enableTethering = mConfig.getIsTetheringSubnet();
        boolean dnsByPass = dnsByPassMod();
        boolean enabledFilter = mConfig.getIsFilterApps();
        boolean filterBypassMode = mConfig.getIsFilterBypassMode();
        boolean forwardDns = mConfig.getVpnDnsForward();
        String[] dnsResolver;
        if (forwardDns) {
            String dnsPrimary = mConfig.getVpnDnsResolver1();
            String dnsSecondary = mConfig.getVpnDnsResolver2();
            dnsResolver = new String[]{dnsPrimary, dnsSecondary};

			if (!dnsPrimary.isEmpty() || !dnsSecondary.isEmpty()) {
				addLogInfo("DNS 1: " + "<font color='#FBBD00'>" + dnsPrimary + "</font>");
				addLogInfo("DNS 2: " + "<font color='#FBBD00'>" + dnsSecondary + "</font>");

			}

        } else {
			addLogInfo(
				"<strong><font color='#C33E3D'>DNS Forwarding activated, but DNS cannot be empty!</font></strong>");



            List<String> lista = VpnUtils.getNetworkDnsServer(this);
            dnsResolver = new String[]{lista.get(0)};
        }





		// TkLogStatus.logInfo(String.format("DnsForwarder: %s", forwardDns));
		// TkLogStatus.logInfo(String.format("dnsPrimary: %s", (Object) dnsResolver));
		// String[] dnsResolver = dnsResolvers();
        String excludeIps = excludeIps();
        String[] filterApps = mConfig.getFilterApps();
        mBuilderThread = new Thread(() -> {
            try{
                startVpn(forwardDns,dnsResolver,excludeIps,enabledFilter,filterBypassMode,filterApps,enableTethering,dnsByPass);
            }catch (Exception ex){
                destroyAll();
            }
        });
        mBuilderThread.start();

    }
	
	

    private void addLogInfo(final String _msg){
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        String msg = _msg.trim().replace(hst,"******").replace(prx,"******");
        TkLogStatus.logInfo(msg);
    }

}


