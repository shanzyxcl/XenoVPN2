package app.tunnel.v2ray.service

import android.os.Build
import android.os.Bundle
import android.util.Log
import app.tunnel.logger.BuildConfig
import app.tunnel.v2ray.R
import app.tunnel.v2ray.common.V2RayConstants.TAG_AGENT
import app.tunnel.v2ray.data.model.ServerConfig
import app.tunnel.v2ray.utils.V2RayUtils
import app.tunnel.v2ray.utils.V2rayConfigUtil
import app.tunnel.vpncommons.vpnstatus.TrafficData
import app.tunnel.vpncommons.vpnstatus.TkLogStatus
import go.Seq
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import rx.Observable
import rx.Subscription
import java.lang.ref.SoftReference
import java.util.concurrent.TimeUnit

object V2RayServiceManager {
    /**
     * Tknetwork01/07/2024...
     */
    private val v2rayPoint: V2RayPoint =
        Libv2ray.newV2RayPoint(V2RayCallback(), Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)

    @JvmStatic
    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            Seq.setContext(value?.get()?.getService()?.applicationContext)
            Libv2ray.initV2Env(V2RayUtils.userAssetPath(value?.get()?.getService()))
        }

    private var currentConfig: ServerConfig? = null
    private var lastQueryTime = 0L

    private class V2RayCallback : V2RayVPNServiceSupportsSet {
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            // called by go
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, e.toString())
                -1
            }
        }

        override fun prepare(): Long {
            return 0
        }

        override fun protect(l: Long): Boolean {
            val serviceControl = serviceControl?.get() ?: return true
            return serviceControl.vpnProtect(l.toInt())
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            //Logger.d(s)
            return 0
        }

        override fun setup(s: String): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            //Logger.d(s)
            return try {
                serviceControl.startService()
                lastQueryTime = System.currentTimeMillis()
                startSpeedNotification()
                0
            } catch (e: Exception) {
                Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, e.toString())
                -1
            }
        }
    }


    private var mSubscription: Subscription? = null

    fun startV2rayPoint(bundle: Bundle) {
        val service = serviceControl?.get()?.getService() ?: return

        if (!v2rayPoint.isRunning) {
            val result = V2rayConfigUtil.getV2rayConfig(service, bundle)

            if (result.error != null) {
                TkLogStatus.logInfo("V2RayConfig Error: ${result.error}")
                return
            }

            result.content?.let {
                v2rayPoint.configureFileContent = result.content
                v2rayPoint.domainName = result.domainAndPort
                currentConfig = result.config

                try {
                    //TODO
                    // v2rayPoint.runLoop(settingsStorage?.decodeBool(AppConfig.PREF_PREFER_IPV6) ?: false)
                    v2rayPoint.runLoop(false)
                } catch (e: Exception) {
                    Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, e.toString())
                }

                if (v2rayPoint.isRunning) {
                    measureV2rayDelay()
                    // MessageUtil.sendMsg2Service(service, V2RayConstants.MSG_MEASURE_DELAY, "")
                } else {
                    TkLogStatus.logInfo("Not")
                    TkLogStatus.updateStateString(TkLogStatus.VPN_DISCONNECTED, "Disconnected")
                    //cancelNotification()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun measureV2rayDelay() {
        GlobalScope.launch(Dispatchers.IO) {
            TkLogStatus.logInfo("Checking Connection ...")

            val service = serviceControl?.get()?.getService() ?: return@launch
            var time = -1L
            var errstr = ""

            delay(1500L)
            if (v2rayPoint.isRunning) {
                try {
                    time = v2rayPoint.measureDelay()
                } catch (e: Exception) {
                    Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, "measureV2rayDelay: $e")
                    errstr = e.message?.substringAfter("\":") ?: "empty message"
                }
            }

            val result = if (time == -1L) {
                service.getString(R.string.connection_test_error, errstr)
            } else {
                TkLogStatus.logInfo("V2Ray Connected")
                TkLogStatus.updateStateString(TkLogStatus.VPN_CONNECTED, "Connected")
                service.getString(R.string.connection_test_available, time)
            }

            TkLogStatus.logInfo(result)
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    fun stopV2rayPoint() {
        if (v2rayPoint.isRunning) {
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    v2rayPoint.stopLoop()
                    TkLogStatus.logInfo("V2Ray Stopped")
                } catch (e: Exception) {
                    Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, e.toString())
                }
            }
        }
        //   VpnStatus.updateStateString(VpnState.STATE_DISCONNECTED, "Disconnected")

        TkLogStatus.stopNetStat()
    }

    private fun startSpeedNotification() {
        TkLogStatus.startNetStat()

        if (mSubscription == null &&
            v2rayPoint.isRunning
        ) {
            mSubscription = Observable.interval(2, TimeUnit.SECONDS)
                .subscribe {
                    val queryTime = System.currentTimeMillis()

                    val up = v2rayPoint.queryStats(TAG_AGENT, "uplink")
                    val down = v2rayPoint.queryStats(TAG_AGENT, "downlink")

                    TrafficData.addBytesDownload(up)
                    TrafficData.addBytesSend(down)

                    lastQueryTime = queryTime
                }
        }
    }

    private fun stopSpeedNotification() {
        mSubscription?.unsubscribe() //stop queryStats
        mSubscription = null
    }
}
