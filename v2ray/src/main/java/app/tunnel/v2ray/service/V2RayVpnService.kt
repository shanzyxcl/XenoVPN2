package app.tunnel.v2ray.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresApi
import app.tunnel.v2ray.R
import app.tunnel.v2ray.data.dto.ERoutingMode
import app.tunnel.v2ray.utils.V2RayUtils
import app.tunnel.vpncommons.VpnConstants
import app.tunnel.vpncommons.VpnExt
import app.tunnel.vpncommons.vpnstatus.TkLogStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.SoftReference


class V2RayVpnService : VpnService(), ServiceControl {
    /**
     * Tknetwork01/07/2024...
     */
    companion object {
        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN4_CLIENT = "26.26.26.1"
        private const val PRIVATE_VLAN4_ROUTER = "26.26.26.2"
        private const val PRIVATE_VLAN6_CLIENT = "da26:2626::1"
        private const val PRIVATE_VLAN6_ROUTER = "da26:2626::2"
        private const val TUN2SOCKS = "libtun2socksv.so"
    }

    private val mBinder: IBinder = object : IOpenVPNServiceInternal.Stub() {
        @Throws(RemoteException::class)
        override fun protect(fd: Int): Boolean {
            return this@V2RayVpnService.protect(fd)
        }

        @Throws(RemoteException::class)
        override fun userPause(shouldbePaused: Boolean) {
            //TODO
        }

        @Throws(RemoteException::class)
        override fun stopVPN(replaceConnection: Boolean): Boolean {
            Log.d("V2RayServ", "stopVPN v2ray")
            this@V2RayVpnService.stopV2Ray()
            return true
        }

        @Throws(RemoteException::class)
        override fun challengeResponse(repsonse: String) {
            //TODO
        }
    }

    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var mBundle: Bundle? = null

    //val fd: Int get() = mInterface.fd
    private lateinit var process: Process

    /**destroy
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface: https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // it's a good idea to refresh capabilities
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        V2RayServiceManager.serviceControl = SoftReference(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }

    override fun onRevoke() {
        stopV2Ray()
    }

//    override fun onLowMemory() {
//        stopV2Ray()
//        super.onLowMemory()
//    }

    private fun setup() {
        val prepare = prepare(this)
        if (prepare != null) {
            return
        }

        // If the old interface has exactly the same parameters, use it!
        // Configure a builder while parsing the parameters.
        val builder = Builder()
        //val enableLocalDns = defaultDPreference.getPrefBoolean(AppConfig.PREF_LOCAL_DNS_ENABLED, false)

        //TODO (settings)
//        val routingMode = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_MODE)
//            ?: ERoutingMode.GLOBAL_PROXY.value

        val routingMode = ERoutingMode.GLOBAL_PROXY.value

        builder.setMtu(VPN_MTU)
        builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)
        //builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
        if (routingMode == ERoutingMode.BYPASS_LAN.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value) {
            resources.getStringArray(R.array.bypass_private_ip_address).forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        //TODO (settings)
        /*if (settingsStorage?.decodeBool(AppConfig.PREF_PREFER_IPV6) == true) {
            builder.addAddress(PRIVATE_VLAN6_CLIENT, 126)
            if (routingMode == ERoutingMode.BYPASS_LAN.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value) {
                builder.addRoute("2000::", 3) //currently only 1/8 of total ipV6 is in use
            } else {
                builder.addRoute("::", 0)
            }
        }

         */

        //TODO (settings)
        /*if (settingsStorage?.decodeBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
        } else {
            Utils.getVpnDnsServers()
                .forEach {
                    if (Utils.isPureIpAddress(it)) {
                        builder.addDnsServer(it)
                    }
                }
        }

         */
        V2RayUtils.getVpnDnsServers()
            .forEach {
                if (V2RayUtils.isPureIpAddress(it)) {
                    builder.addDnsServer(it)
                }
            }

        //TODO tunnel
        //builder.setSession(V2RayServiceManager.currentConfig?.remarks.orEmpty())

        //TODO (settings)
        /*
        if (settingsStorage?.decodeBool(AppConfig.PREF_PER_APP_PROXY) == true) {
            val apps = settingsStorage?.decodeStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            val bypassApps = settingsStorage?.decodeBool(AppConfig.PREF_BYPASS_APPS) ?: false
            apps?.forEach {
                try {
                    if (bypassApps)
                        builder.addDisallowedApplication(it)
                    else
                        builder.addAllowedApplication(it)
                } catch (e: PackageManager.NameNotFoundException) {
                    //Logger.d(e)
                }
            }
        }
         */

        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close()
        } catch (ignored: Exception) {
            // ignored
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Create a new interface using the builder and save the parameters.
        try {
            mInterface = builder.establish()!!
            isRunning = true
            runTun2socks()
        } catch (e: Exception) {
            // non-nullable lateinit var
            e.printStackTrace()
            stopV2Ray()
        }
    }

    private fun runTun2socks() {
        val socksPort = VpnExt.parseInt(
            mBundle?.getString(VpnConstants.PREF_SOCKS_PORT),
            VpnConstants.PORT_SOCKS.toInt()
        )

        val cmd = arrayListOf(
            File(applicationContext.applicationInfo.nativeLibraryDir, TUN2SOCKS).absolutePath,
            "--netif-ipaddr",
            PRIVATE_VLAN4_ROUTER,
            "--netif-netmask",
            "255.255.255.252",
            "--socks-server-addr",
            "127.0.0.1:${socksPort}",
            "--tunmtu",
            VPN_MTU.toString(),
            "--sock-path",
            "sock_path",//File(applicationContext.filesDir, "sock_path").absolutePath,
            "--enable-udprelay",
            "--loglevel",
            "notice"
        )

        //TODO (settings)
//        if (settingsStorage?.decodeBool(AppConfig.PREF_PREFER_IPV6) == true) {
//            cmd.add("--netif-ip6addr")
//            cmd.add(PRIVATE_VLAN6_ROUTER)
//        }
        //TODO (settings)
        /*if (settingsStorage?.decodeBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            val localDnsPort = Utils.parseInt(
                settingsStorage?.decodeString(AppConfig.PREF_LOCAL_DNS_PORT),
                AppConfig.PORT_LOCAL_DNS.toInt()
            )
            cmd.add("--dnsgw")
            cmd.add("127.0.0.1:${localDnsPort}")
        }

         */

        Log.d(packageName, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(applicationContext.filesDir)
                .start()
            Thread {
                Log.d(packageName, "$TUN2SOCKS check")
                process.waitFor()
                Log.d(packageName, "$TUN2SOCKS exited")
                if (isRunning) {
                    Log.d(packageName, "$TUN2SOCKS restart")
                    runTun2socks()
                }
            }.start()
            Log.d(packageName, process.toString())

            sendFd()
        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sendFd() {
        val fd = mInterface.fileDescriptor
        val path = File(applicationContext.filesDir, "sock_path").absolutePath
        Log.d(packageName, path)

        GlobalScope.launch(Dispatchers.IO) {
            var tries = 0
            while (true) try {
                Thread.sleep(50L shl tries)
                Log.d(packageName, "sendFd tries: $tries")
                LocalSocket().use { localSocket ->
                    localSocket.connect(
                        LocalSocketAddress(
                            path,
                            LocalSocketAddress.Namespace.FILESYSTEM
                        )
                    )
                    localSocket.setFileDescriptorsForSend(arrayOf(fd))
                    localSocket.outputStream.write(42)
                }
                break
            } catch (e: Exception) {
                Log.d(packageName, e.toString())
                if (tries > 5) break
                tries += 1
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTING, "Connecting")
        intent?.extras?.let {
            mBundle = it
            V2RayServiceManager.startV2rayPoint(it)
        }

        return START_STICKY
    }

    private fun stopV2Ray(isForced: Boolean = true) {
//        val configName = defaultDPreference.getPrefString(PREF_CURR_CONFIG_GUID, "")
//        val emptyInfo = VpnNetworkInfo()
//        val info = loadVpnNetworkInfo(configName, emptyInfo)!! + (lastNetworkInfo ?: emptyInfo)
//        saveVpnNetworkInfo(configName, info)
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {
                // ignored
            }
        }

        try {
            TkLogStatus.logInfo("Tun2Socks Destroy")
            process.destroy()
        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }

        TkLogStatus.updateStateString(TkLogStatus.VPN_STOPPING, "Stopping Service")
        TkLogStatus.logInfo("V2Ray Stopping...")
        V2RayServiceManager.stopV2rayPoint()

        if (isForced) {
            //stopSelf has to be called ahead of mInterface.close(). otherwise v2ray core cannot be stooped
            //It's strage but true.
            //This can be verified by putting stopself() behind and call stopLoop and startLoop
            //in a row for several times. You will find that later created v2ray core report port in use
            //which means the first v2ray core somehow failed to stop and release the port.
            stopSelf()

            try {
                mInterface.close()
            } catch (ignored: Exception) {
                // ignored
            }
        }
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        setup()
    }

    override fun stopService() {
        stopV2Ray(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }
}
