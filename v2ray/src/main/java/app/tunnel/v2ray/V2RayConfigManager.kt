package app.tunnel.v2ray

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.Keep
import app.tunnel.v2ray.data.dto.EConfigType
import app.tunnel.v2ray.data.model.ServerConfig
import app.tunnel.v2ray.data.model.V2rayConfig
import app.tunnel.v2ray.data.model.V2rayConfig.Companion.DEFAULT_SECURITY
import app.tunnel.v2ray.data.model.V2rayConfig.Companion.TLS
import app.tunnel.v2ray.data.model.VmessQRCode
import app.tunnel.v2ray.extension.idnHost
import app.tunnel.v2ray.utils.V2RayUtils
import app.tunnel.v2ray.utils.V2rayConfigUtil
import app.tunnel.vpncommons.VpnExt
import com.google.gson.Gson
import java.net.URI


object V2RayConfigManager {
    /**
     * Tknetwork01/07/2024...
     */
    sealed class ProcessResult {
        @Keep
        data class Success(val value: String) : ProcessResult()

        @Keep
        data class Error(val message: Int) : ProcessResult()
    }

    @Keep
    @JvmStatic
    fun convertToConfig(data: String, allowInsecure: Boolean): String? {
        val conf = when (val import = importConfig(data, allowInsecure)) {
            is ProcessResult.Success -> import.value
            is ProcessResult.Error -> null
        }

        return conf
    }

    /**
     * import config form qrcode or...
     */
    private fun importConfig(
        str: String?,
        allowInsecure: Boolean
    ): ProcessResult {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return ProcessResult.Error(R.string.toast_none_data)
            }

            var config: ServerConfig? = null

            if (str.startsWith(EConfigType.VMESS.protocolScheme)) {
                config = ServerConfig.create(EConfigType.VMESS)
                val streamSetting =
                    config.outboundBean?.streamSettings ?: return ProcessResult.Error(-1)

                if (!tryParseNewVmess(str, config, allowInsecure)) {
                    if (str.indexOf("?") > 0) {
                        if (!tryResolveVmess4Kitsunebi(str, config)) {
                            return ProcessResult.Error(R.string.toast_incorrect_protocol)
                        }
                    } else {
                        var result = str.replace(EConfigType.VMESS.protocolScheme, "")
                        result = V2RayUtils.decode(result)
                        if (TextUtils.isEmpty(result)) {
                            return ProcessResult.Error(R.string.toast_decoding_failed)
                        }
                        val vmessQRCode = Gson().fromJson(result, VmessQRCode::class.java)
                        // Although VmessQRCode fields are non null, looks like Gson may still create null fields

                        if (TextUtils.isEmpty(vmessQRCode.add)
                            || TextUtils.isEmpty(vmessQRCode.port)
                            || TextUtils.isEmpty(vmessQRCode.id)
                            || TextUtils.isEmpty(vmessQRCode.net)
                        ) {
                            return ProcessResult.Error(R.string.toast_incorrect_protocol)
                        }

                        config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
                            vnext.address = vmessQRCode.add
                            vnext.port = VpnExt.parseInt(vmessQRCode.port)
                            vnext.users[0].id = vmessQRCode.id
                            vnext.users[0].security =
                                if (TextUtils.isEmpty(vmessQRCode.scy)) DEFAULT_SECURITY else vmessQRCode.scy
                            vnext.users[0].alterId = VpnExt.parseInt(vmessQRCode.aid)
                        }
                        val sni = streamSetting.populateTransportSettings(
                            vmessQRCode.net,
                            vmessQRCode.type,
                            vmessQRCode.host,
                            vmessQRCode.path,
                            vmessQRCode.path,
                            vmessQRCode.host,
                            vmessQRCode.path,
                            // vmessQRCode.type,
                            vmessQRCode.path
                        )

                        val fingerprint = vmessQRCode.fp
                        streamSetting.populateTlsSettings(
                            vmessQRCode.tls, allowInsecure,
                            if (TextUtils.isEmpty(vmessQRCode.sni)) sni else vmessQRCode.sni,
                            fingerprint, vmessQRCode.alpn, null, null, null
                        )
                    }
                }
            } else if (str.startsWith(EConfigType.SHADOWSOCKS.protocolScheme)) {
                config = ServerConfig.create(EConfigType.SHADOWSOCKS)

                if (!tryResolveResolveSip002(str, config)) {
                    var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
                    val indexSplit = result.indexOf("#")
                    if (indexSplit > 0) {
                        result = result.substring(0, indexSplit)
                    }

                    //part decode
                    val indexS = result.indexOf("@")
                    result = if (indexS > 0) {
                        V2RayUtils.decode(result.substring(0, indexS)) + result.substring(
                            indexS,
                            result.length
                        )
                    } else {
                        V2RayUtils.decode(result)
                    }

                    val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)/?$".toRegex()
                    val match = legacyPattern.matchEntire(result)
                        ?: return ProcessResult.Error(R.string.toast_incorrect_protocol)

                    config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                        server.address = match.groupValues[3].removeSurrounding("[", "]")
                        server.port = match.groupValues[4].toInt()
                        server.password = match.groupValues[2]
                        server.method = match.groupValues[1].lowercase()
                    }
                }

            } else if (str.startsWith(EConfigType.SOCKS.protocolScheme)) {
                var result = str.replace(EConfigType.SOCKS.protocolScheme, "")
                val indexSplit = result.indexOf("#")
                config = ServerConfig.create(EConfigType.SOCKS)
                if (indexSplit > 0) {
                    result = result.substring(0, indexSplit)
                }

                //part decode
                val indexS = result.indexOf("@")
                result = if (indexS > 0) {
                    V2RayUtils.decode(result.substring(0, indexS)) + result.substring(
                        indexS,
                        result.length
                    )
                } else {
                    V2RayUtils.decode(result)
                }

                val legacyPattern = "^(.*):(.*)@(.+?):(\\d+?)$".toRegex()
                val match =
                    legacyPattern.matchEntire(result)
                        ?: return ProcessResult.Error(R.string.toast_incorrect_protocol)

                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                    server.address = match.groupValues[3].removeSurrounding("[", "]")
                    server.port = match.groupValues[4].toInt()
                    val socksUsersBean =
                        V2rayConfig.OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                    socksUsersBean.user = match.groupValues[1].lowercase()
                    socksUsersBean.pass = match.groupValues[2]
                    server.users = listOf(socksUsersBean)
                }
            } else if (str.startsWith(EConfigType.TROJAN.protocolScheme)) {
                val uri = URI(V2RayUtils.fixIllegalUrl(str))
                config = ServerConfig.create(EConfigType.TROJAN)

                var flow = ""
                var fingerprint = config.outboundBean?.streamSettings?.tlsSettings?.fingerprint
                if (uri.rawQuery != null) {
                    val queryParam = uri.rawQuery.split("&")
                        .associate { it.split("=").let { (k, v) -> k to V2RayUtils.urlDecode(v) } }

                    val sni = config.outboundBean?.streamSettings?.populateTransportSettings(
                        queryParam["type"] ?: "tcp",
                        queryParam["headerType"],
                        queryParam["host"],
                        queryParam["path"],
                        queryParam["seed"],
                        queryParam["quicSecurity"],
                        queryParam["key"],
                        //queryParam["mode"],
                        queryParam["serviceName"]
                    )
                    fingerprint = queryParam["fp"] ?: ""
                    config.outboundBean?.streamSettings?.populateTlsSettings(
                        queryParam["security"] ?: TLS,
                        allowInsecure, queryParam["sni"] ?: sni!!, fingerprint, queryParam["alpn"],
                        null, null, null
                    )
                    flow = queryParam["flow"] ?: ""
                } else {
                    config.outboundBean?.streamSettings?.populateTlsSettings(
                        TLS, allowInsecure, "",
                        fingerprint, null, null, null, null
                    )
                }

                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                    server.address = uri.idnHost
                    server.port = uri.port
                    server.password = uri.userInfo
                    server.flow = flow
                }

            } else if (str.startsWith(EConfigType.VLESS.protocolScheme)) {
                val uri = URI(V2RayUtils.fixIllegalUrl(str))
                val queryParam = uri.rawQuery.split("&")
                    .associate { it.split("=").let { (k, v) -> k to V2RayUtils.urlDecode(v) } }
                config = ServerConfig.create(EConfigType.VLESS)
                val streamSetting =
                    config.outboundBean?.streamSettings ?: return ProcessResult.Error(-1)
                var fingerprint: String?

                config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
                    vnext.address = uri.idnHost
                    vnext.port = uri.port
                    vnext.users[0].id = uri.userInfo
                    vnext.users[0].encryption = queryParam["encryption"] ?: "none"
                    vnext.users[0].flow = queryParam["flow"] ?: ""
                }

                val sni = streamSetting.populateTransportSettings(
                    queryParam["type"] ?: "tcp",
                    queryParam["headerType"],
                    queryParam["host"],
                    queryParam["path"],
                    queryParam["seed"],
                    queryParam["quicSecurity"],
                    queryParam["key"],
                    //   queryParam["mode"],
                    queryParam["serviceName"]
                )
                fingerprint = queryParam["fp"] ?: ""
                val pbk = queryParam["pbk"] ?: ""
                val sid = queryParam["sid"] ?: ""
                val spx = V2RayUtils.urlDecode(queryParam["spx"] ?: "")
                streamSetting.populateTlsSettings(
                    queryParam["security"] ?: "", allowInsecure,
                    queryParam["sni"] ?: sni, fingerprint, queryParam["alpn"], pbk, sid, spx
                )
            }
            if (config == null) {
                return ProcessResult.Error(R.string.toast_incorrect_protocol)
            }
            val gson = Gson().toJson(config)
            return ProcessResult.Success(gson)
        } catch (e: Exception) {
            e.printStackTrace()
            return ProcessResult.Error(-1)
        }
    }

    private fun tryParseNewVmess(
        uriString: String,
        config: ServerConfig,
        allowInsecure: Boolean
    ): Boolean {
        return runCatching {
            val uri = URI(uriString)
            check(uri.scheme == "vmess")
            val (_, protocol, tlsStr, uuid, alterId) =
                Regex("(tcp|http|ws|kcp|quic|grpc)(\\+tls)?:([0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12})")
                    .matchEntire(uri.userInfo)?.groupValues
                    ?: error("parse user info fail.")
            val tls = tlsStr.isNotBlank()
            val queryParam = uri.rawQuery.split("&")
                .associate { it.split("=").let { (k, v) -> k to V2RayUtils.urlDecode(v) } }

            val streamSetting = config.outboundBean?.streamSettings ?: return false
            config.outboundBean.settings?.vnext?.get(0)?.let { vnext ->
                vnext.address = uri.idnHost
                vnext.port = uri.port
                vnext.users[0].id = uuid
                vnext.users[0].security = DEFAULT_SECURITY
                vnext.users[0].alterId = alterId.toInt()
            }
            val fingerprint = streamSetting.tlsSettings?.fingerprint
            val sni = streamSetting.populateTransportSettings(protocol,
                queryParam["type"],
                queryParam["host"]?.split("|")?.get(0) ?: "",
                queryParam["path"]?.takeIf { it.trim() != "/" } ?: "",
                queryParam["seed"],
                queryParam["security"],
                queryParam["key"],
                //   queryParam["mode"],
                queryParam["serviceName"])
            streamSetting.populateTlsSettings(
                if (tls) TLS else "", allowInsecure, sni, fingerprint, null,
                null, null, null
            )
            true
        }.getOrElse { false }
    }

    private fun tryResolveVmess4Kitsunebi(server: String, config: ServerConfig): Boolean {

        var result = server.replace(EConfigType.VMESS.protocolScheme, "")
        val indexSplit = result.indexOf("?")
        if (indexSplit > 0) {
            result = result.substring(0, indexSplit)
        }
        result = V2RayUtils.decode(result)

        val arr1 = result.split('@')
        if (arr1.count() != 2) {
            return false
        }
        val arr21 = arr1[0].split(':')
        val arr22 = arr1[1].split(':')
        if (arr21.count() != 2) {
            return false
        }

        config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
            vnext.address = arr22[0]
            vnext.port = VpnExt.parseInt(arr22[1])
            vnext.users[0].id = arr21[1]
            vnext.users[0].security = arr21[0]
            vnext.users[0].alterId = 0
        }
        return true
    }

    private fun tryResolveResolveSip002(str: String, config: ServerConfig): Boolean {
        try {
            val uri = URI(V2RayUtils.fixIllegalUrl(str))

            val method: String
            val password: String
            if (uri.userInfo.contains(":")) {
                val arrUserInfo = uri.userInfo.split(":").map { it.trim() }
                if (arrUserInfo.count() != 2) {
                    return false
                }
                method = arrUserInfo[0]
                password = V2RayUtils.urlDecode(arrUserInfo[1])
            } else {
                val base64Decode = V2RayUtils.decode(uri.userInfo)
                val arrUserInfo = base64Decode.split(":").map { it.trim() }
                if (arrUserInfo.count() < 2) {
                    return false
                }
                method = arrUserInfo[0]
                password = base64Decode.substringAfter(":")
            }

            config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                server.address = uri.idnHost
                server.port = uri.port
                server.password = password
                server.method = method
            }
            return true
        } catch (e: Exception) {
            // Log.d(BuildConfig.LIBRARY_PACKAGE_NAME, e.toString())
            return false
        }
    }


    /**
     * shareFullContent2Clipboard
     */
    fun shareFullContent2Clipboard(context: Context, bundle: Bundle): Int {
        try {
            val result = V2rayConfigUtil.getV2rayConfig(context, bundle)
            // TODO
            result.let {
                if (it.error != null) {
                    Toast.makeText(context, "Error: ${it.error}", Toast.LENGTH_SHORT).show()
                    return -1
                }

                it.content?.let { content ->
                    V2RayUtils.setClipboard(context, content)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }
}
