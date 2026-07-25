package app.tunnel.v2ray.utils

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import app.tunnel.v2ray.BuildConfig
import app.tunnel.v2ray.common.V2RayConstants
import app.tunnel.v2ray.data.dto.EConfigType
import app.tunnel.v2ray.data.dto.ERoutingMode
import app.tunnel.v2ray.data.model.ServerConfig
import app.tunnel.v2ray.data.model.V2rayConfig
import app.tunnel.v2ray.data.model.V2rayConfig.Companion.DEFAULT_NETWORK
import app.tunnel.v2ray.data.model.V2rayConfig.Companion.HTTP
import app.tunnel.vpncommons.VpnConstants
import app.tunnel.vpncommons.VpnExt
import com.google.gson.Gson

object V2rayConfigUtil {

    private val mGson by lazy { Gson() }

    data class V2RayResult(
        val error: String? = null,
        var content: String? = null,
        val config: ServerConfig? = null,
        val domainAndPort: String? = null
    )

    /**
     * 生成v2ray的客户端配置文件
     */
    fun getV2rayConfig(
        context: Context,
        bundle: Bundle
    ): V2RayResult {
        try {
            // val config = bundle.getString(ConfigUtil.get)
            val config = bundle.getString(V2RayConstants.V2RAY_CONFIG)
            if (config != null) {
                val serverConfig = mGson.fromJson(config, ServerConfig::class.java)

                if (serverConfig.configType == EConfigType.CUSTOM) {

                    val fullConfig = serverConfig.fullConfig?.toPrettyPrinting()
                        ?: return V2RayResult(error = "Config is null")

                    Log.d("${BuildConfig.LIBRARY_PACKAGE_NAME} full", fullConfig)
                    return V2RayResult(
                        content = fullConfig,
                        config = serverConfig,
                        domainAndPort = serverConfig.getV2rayPointDomainAndPort()
                    )
                }

                val _result = getV2rayNonCustomConfig(bundle, context, serverConfig)

                val result = _result.copy(config = serverConfig)

                result.content?.let { it1 -> Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, it1) }
                return result
            } else {
                return V2RayResult(error = "Config is null")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return V2RayResult(error = "${e.message}")
        }
    }

    /**
     * 生成v2ray的客户端配置文件
     */
    private fun getV2rayNonCustomConfig(
        bundle: Bundle,
        context: Context,
        serverConfig: ServerConfig,
    ): V2RayResult {
        val outbound = serverConfig.getProxyOutbound()
            ?: return V2RayResult(error = "V2RayConfig Outbound is null")

        //取得默认配置
        val assets = V2RayUtils.readTextFromAssets(context, "v2ray_config.json")
        if (TextUtils.isEmpty(assets)) {
            return V2RayResult(error = "v2ray_config.json is empty")
        }

        //转成Json
        val v2rayConfig = mGson.fromJson(assets, V2rayConfig::class.java) ?: return V2RayResult(
            error = "An error occured in v2ray json"
        )

        //TODO (settings)
//        v2rayConfig.log.loglevel = settingsStorage?.decodeString(V2RayGlobal.PREF_LOGLEVEL)
//            ?: "warning"

        v2rayConfig.log.loglevel = "warning"

        inbounds(bundle, v2rayConfig)

        httpRequestObject(outbound)

        v2rayConfig.outbounds[0] = outbound

        routing(v2rayConfig)

        fakedns()

        dns(v2rayConfig)

        //TODO (settings)
        /*if (settingsStorage?.decodeBool(V2RayGlobal.PREF_LOCAL_DNS_ENABLED) == true) {
            customLocalDns(v2rayConfig)
        }
        if (settingsStorage?.decodeBool(V2RayGlobal.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }

         */
        return V2RayResult(
            content = v2rayConfig.toPrettyPrinting(),
            domainAndPort = serverConfig.getV2rayPointDomainAndPort()
        )
    }

    /**
     *
     */
    private fun inbounds(
        bundle: Bundle,
        v2rayConfig: V2rayConfig
    ): Boolean {
        try {
            val socksPort = VpnExt.parseInt(
                bundle.getString(VpnConstants.PORT_SOCKS),
                VpnConstants.PORT_SOCKS.toInt()
            )
            val httpPort = VpnExt.parseInt(
                bundle.getString(VpnConstants.PREF_HTTP_PORT),
                VpnConstants.PORT_HTTP.toInt()
            )


            v2rayConfig.inbounds.forEach { curInbound ->
                //TODO (settings)
                /*if (settingsStorage?.decodeBool(V2RayGlobal.PREF_PROXY_SHARING) != true) {
                    //bind all inbounds to localhost if the user requests
                    curInbound.listen = "127.0.0.1"
                }

                 */
                curInbound.listen = "127.0.0.1"
            }
            v2rayConfig.inbounds[0].port = socksPort

            //TODO (settings)
            /*val fakedns = settingsStorage?.decodeBool(V2RayGlobal.PREF_FAKE_DNS_ENABLED)
                ?: false
            val sniffAllTlsAndHttp =
                settingsStorage?.decodeBool(V2RayGlobal.PREF_SNIFFING_ENABLED, true)
                    ?: true

             */
            val fakedns = false
            val sniffAllTlsAndHttp = true

            v2rayConfig.inbounds[0].sniffing?.enabled = fakedns || sniffAllTlsAndHttp
            if (!sniffAllTlsAndHttp) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.clear()
            }
            if (fakedns) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.add("fakedns")
            }

            v2rayConfig.inbounds[1].port = httpPort

//            if (httpPort > 0) {
//                val httpCopy = v2rayConfig.inbounds[0].copy()
//                httpCopy.port = httpPort
//                httpCopy.protocol = "http"
//                v2rayConfig.inbounds.add(httpCopy)
//            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun fakedns() {
        //TODO (settings)
        /*if (settingsStorage?.decodeBool(V2RayGlobal.PREF_FAKE_DNS_ENABLED) == true) {
            v2rayConfig.fakedns = listOf(V2rayConfig.FakednsBean())
            v2rayConfig.outbounds.filter { it.protocol == "freedom" }.forEach {
                it.settings?.domainStrategy = "UseIP"
            }
        }

         */
    }

    /**
     * routing
     */
    private fun routing(v2rayConfig: V2rayConfig): Boolean {
        try {
            //TODO (settings)
            /*routingUserRule(
                settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_AGENT)
                    ?: "", V2RayGlobal.TAG_AGENT, v2rayConfig
            )

             */
            routingUserRule("", V2RayConstants.TAG_AGENT, v2rayConfig)
            //TODO (settings)
            /* routingUserRule(
                 settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_DIRECT)
                     ?: "", V2RayGlobal.TAG_DIRECT, v2rayConfig
             )

             */
            routingUserRule("", V2RayConstants.TAG_DIRECT, v2rayConfig)
            //TODO (settings)
            /*routingUserRule(
                settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_BLOCKED)
                    ?: "", V2RayGlobal.TAG_BLOCKED, v2rayConfig
            )

             */
            routingUserRule("", V2RayConstants.TAG_BLOCKED, v2rayConfig)

            //TODO (settings)
            /*v2rayConfig.routing.domainStrategy =
                settingsStorage?.decodeString(V2RayGlobal.PREF_ROUTING_DOMAIN_STRATEGY)
                    ?: "IPIfNonMatch"

             */
            v2rayConfig.routing.domainStrategy = "IPIfNonMatch"


//            v2rayConfig.routing.domainMatcher = "mph"
            //TODO (settings)
            /*val routingMode = settingsStorage?.decodeString(V2RayGlobal.PREF_ROUTING_MODE)
                ?: ERoutingMode.GLOBAL_PROXY.value

             */
            val routingMode = ERoutingMode.GLOBAL_PROXY.value

            // Hardcode googleapis.cn
            val googleapisRoute = V2rayConfig.RoutingBean.RulesBean(
                type = "field",
                outboundTag = V2RayConstants.TAG_AGENT,
                domain = arrayListOf("domain:googleapis.cn")
            )

            when (routingMode) {
                ERoutingMode.BYPASS_LAN.value -> {
                    routingGeo("ip", "private", V2RayConstants.TAG_DIRECT, v2rayConfig)
                }

                ERoutingMode.BYPASS_MAINLAND.value -> {
                    routingGeo("", "cn", V2RayConstants.TAG_DIRECT, v2rayConfig)
                    v2rayConfig.routing.rules.add(0, googleapisRoute)
                }

                ERoutingMode.BYPASS_LAN_MAINLAND.value -> {
                    routingGeo("ip", "private", V2RayConstants.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "cn", V2RayConstants.TAG_DIRECT, v2rayConfig)
                    v2rayConfig.routing.rules.add(0, googleapisRoute)
                }

                ERoutingMode.GLOBAL_DIRECT.value -> {
                    val globalDirect = V2rayConfig.RoutingBean.RulesBean(
                        type = "field",
                        outboundTag = V2RayConstants.TAG_DIRECT,
                        port = "0-65535"
                    )
                    v2rayConfig.routing.rules.add(globalDirect)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun routingGeo(
        ipOrDomain: String,
        code: String,
        tag: String,
        v2rayConfig: V2rayConfig
    ) {
        try {
            if (!TextUtils.isEmpty(code)) {
                //IP
                if (ipOrDomain == "ip" || ipOrDomain == "") {
                    val rulesIP = V2rayConfig.RoutingBean.RulesBean()
                    rulesIP.type = "field"
                    rulesIP.outboundTag = tag
                    rulesIP.ip = ArrayList()
                    rulesIP.ip?.add("geoip:$code")
                    v2rayConfig.routing.rules.add(rulesIP)
                }

                if (ipOrDomain == "domain" || ipOrDomain == "") {
                    //Domain
                    val rulesDomain = V2rayConfig.RoutingBean.RulesBean()
                    rulesDomain.type = "field"
                    rulesDomain.outboundTag = tag
                    rulesDomain.domain = ArrayList()
                    rulesDomain.domain?.add("geosite:$code")
                    v2rayConfig.routing.rules.add(rulesDomain)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun routingUserRule(userRule: String, tag: String, v2rayConfig: V2rayConfig) {
        try {
            if (!TextUtils.isEmpty(userRule)) {
                //Domain
                val rulesDomain = V2rayConfig.RoutingBean.RulesBean()
                rulesDomain.type = "field"
                rulesDomain.outboundTag = tag
                rulesDomain.domain = ArrayList()

                //IP
                val rulesIP = V2rayConfig.RoutingBean.RulesBean()
                rulesIP.type = "field"
                rulesIP.outboundTag = tag
                rulesIP.ip = ArrayList()

                userRule.split(",").map { it.trim() }.forEach {
                    if (V2RayUtils.isIpAddress(it) || it.startsWith("geoip:")) {
                        rulesIP.ip?.add(it)
                    } else if (it.isNotEmpty())
//                                if (Utils.isValidUrl(it)
//                                    || it.startsWith("geosite:")
//                                    || it.startsWith("regexp:")
//                                    || it.startsWith("domain:")
//                                    || it.startsWith("full:"))
                    {
                        rulesDomain.domain?.add(it)
                    }
                }
                if (rulesDomain.domain?.size!! > 0) {
                    v2rayConfig.routing.rules.add(rulesDomain)
                }
                if (rulesIP.ip?.size!! > 0) {
                    v2rayConfig.routing.rules.add(rulesIP)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun userRule2Domian(userRule: String): ArrayList<String> {
        val domain = ArrayList<String>()
        userRule.split(",").map { it.trim() }.forEach {
            if (it.startsWith("geosite:") || it.startsWith("domain:")) {
                domain.add(it)
            }
        }
        return domain
    }

    /**
     * Custom Dns
     */
    private fun customLocalDns(v2rayConfig: V2rayConfig): Boolean {
        try {
            //TODO (settings)
            /*
             if (settingsStorage?.decodeBool(V2RayGlobal.PREF_FAKE_DNS_ENABLED) == true) {
                 val geositeCn = arrayListOf("geosite:cn")
                 val proxyDomain = userRule2Domian(
                     settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_AGENT)
                         ?: ""
                 )
                 val directDomain = userRule2Domian(
                     settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_DIRECT)
                         ?: ""
                 )
                 // fakedns with all domains to make it always top priority
                 v2rayConfig.dns.servers?.add(
                     0,
                     V2rayConfig.DnsBean.ServersBean(
                         address = "fakedns",
                         domains = geositeCn.plus(proxyDomain).plus(directDomain)
                     )
                 )
             }

             */


            // DNS inbound对象
            val remoteDns = V2RayUtils.getRemoteDnsServers()
            if (v2rayConfig.inbounds.none { e -> e.protocol == "dokodemo-door" && e.tag == "dns-in" }) {
                val dnsInboundSettings = V2rayConfig.InboundBean.InSettingsBean(
                    address = if (V2RayUtils.isPureIpAddress(remoteDns.first())) remoteDns.first() else "1.1.1.1",
                    port = 53,
                    network = "tcp,udp"
                )

                //TODO (settings)
//                val localDnsPort = Utils.parseInt(
//                    settingsStorage?.decodeString(V2RayGlobal.PREF_LOCAL_DNS_PORT),
//                    V2RayGlobal.PORT_LOCAL_DNS.toInt()
//                )
                val localDnsPort = 10853

                v2rayConfig.inbounds.add(
                    V2rayConfig.InboundBean(
                        tag = "dns-in",
                        port = localDnsPort,
                        listen = "127.0.0.1",
                        protocol = "dokodemo-door",
                        settings = dnsInboundSettings,
                        sniffing = null
                    )
                )
            }

            // DNS outbound对象
            if (v2rayConfig.outbounds.none { e -> e.protocol == "dns" && e.tag == "dns-out" }) {
                v2rayConfig.outbounds.add(
                    V2rayConfig.OutboundBean(
                        protocol = "dns",
                        tag = "dns-out",
                        settings = null,
                        streamSettings = null,
                        mux = null
                    )
                )
            }

            // DNS routing tag
            v2rayConfig.routing.rules.add(
                0, V2rayConfig.RoutingBean.RulesBean(
                    type = "field",
                    inboundTag = arrayListOf("dns-in"),
                    outboundTag = "dns-out",
                    domain = null
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun dns(v2rayConfig: V2rayConfig): Boolean {
        try {
            val hosts = mutableMapOf<String, String>()
            val servers = ArrayList<Any>()
            val remoteDns = V2RayUtils.getRemoteDnsServers()

            //TODO (settings)
//            val proxyDomain = userRule2Domian(
//                settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_AGENT)
//                    ?: ""
//            )

            val proxyDomain = userRule2Domian("")

            remoteDns.forEach {
                servers.add(it)
            }

            if (proxyDomain.size > 0) {
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        remoteDns.first(),
                        53,
                        proxyDomain,
                        null
                    )
                )
            }

            //TODO (settings)
            // domestic DNS
            /*val directDomain = userRule2Domian(
                settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_DIRECT)
                    ?: ""
            )

             */
            val directDomain = userRule2Domian("")

            //TODO (settings)
            /*val routingMode = settingsStorage?.decodeString(V2RayGlobal.PREF_ROUTING_MODE)
                ?: ERoutingMode.GLOBAL_PROXY.value

             */

            val routingMode = ERoutingMode.GLOBAL_PROXY.value

            if (directDomain.size > 0 || routingMode == ERoutingMode.BYPASS_MAINLAND.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value) {
                val domesticDns = V2RayUtils.getDomesticDnsServers()
                val geositeCn = arrayListOf("geosite:cn")
                val geoipCn = arrayListOf("geoip:cn")
                if (directDomain.size > 0) {
                    servers.add(
                        V2rayConfig.DnsBean.ServersBean(
                            domesticDns.first(),
                            53,
                            directDomain,
                            geoipCn
                        )
                    )
                }
                if (routingMode == ERoutingMode.BYPASS_MAINLAND.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value) {
                    servers.add(
                        V2rayConfig.DnsBean.ServersBean(
                            domesticDns.first(),
                            53,
                            geositeCn,
                            geoipCn
                        )
                    )
                }
                if (V2RayUtils.isPureIpAddress(domesticDns.first())) {
                    v2rayConfig.routing.rules.add(
                        0, V2rayConfig.RoutingBean.RulesBean(
                            type = "field",
                            outboundTag = V2RayConstants.TAG_DIRECT,
                            port = "53",
                            ip = arrayListOf(domesticDns.first()),
                            domain = null
                        )
                    )
                }
            }

            //TODO (settings)
            /* val blkDomain = userRule2Domian(
                 settingsStorage?.decodeString(V2RayGlobal.PREF_V2RAY_ROUTING_BLOCKED)
                     ?: ""
             )

             */
            val blkDomain = userRule2Domian("")

            if (blkDomain.size > 0) {
                hosts.putAll(blkDomain.map { it to "127.0.0.1" })
            }

            // hardcode googleapi rule to fix play store problems
            hosts["domain:googleapis.cn"] = "googleapis.com"

            // DNS dns对象
            v2rayConfig.dns = V2rayConfig.DnsBean(
                servers = servers,
                hosts = hosts
            )

            // DNS routing
            if (V2RayUtils.isPureIpAddress(remoteDns.first())) {
                v2rayConfig.routing.rules.add(
                    0, V2rayConfig.RoutingBean.RulesBean(
                        type = "field",
                        outboundTag = V2RayConstants.TAG_AGENT,
                        port = "53",
                        ip = arrayListOf(remoteDns.first()),
                        domain = null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun httpRequestObject(outbound: V2rayConfig.OutboundBean): Boolean {
        try {
            if (outbound.streamSettings?.network == DEFAULT_NETWORK
                && outbound.streamSettings?.tcpSettings?.header?.type == HTTP
            ) {
                val path = outbound.streamSettings?.tcpSettings?.header?.request?.path
                val host = outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host

                val requestString: String by lazy {
                    """{"version":"1.1","method":"GET","headers":{"User-Agent":["Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36","Mozilla/5.0 (iPhone; CPU iPhone OS 10_0_2 like Mac OS X) AppleWebKit/601.1 (KHTML, like Gecko) CriOS/53.0.2785.109 Mobile/14A456 Safari/601.1.46"],"Accept-Encoding":["gzip, deflate"],"Connection":["keep-alive"],"Pragma":"no-cache"}}"""
                }
                outbound.streamSettings?.tcpSettings?.header?.request = mGson.fromJson(
                    requestString,
                    V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean::class.java
                )
                outbound.streamSettings?.tcpSettings?.header?.request?.path =
                    if (path.isNullOrEmpty()) {
                        listOf("/")
                    } else {
                        path
                    }
                outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host = host!!
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

}
