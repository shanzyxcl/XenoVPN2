package app.tunnel.v2ray.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.text.Editable
import android.util.Base64
import android.util.Patterns
import android.webkit.URLUtil
import app.tunnel.v2ray.common.V2RayConstants
import libv2ray.Libv2ray
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

object V2RayUtils {

    /**
     * convert string to editalbe for kotlin
     *
     * @param text
     * @return
     */
    fun getEditable(text: String): Editable {
        return Editable.Factory.getInstance().newEditable(text)
    }

    /**
     * find value in array position
     */
    fun arrayFind(array: Array<out String>, value: String): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return -1
    }


    /**
     * get text from clipboard
     */
    fun getClipboard(context: Context): String {
        return try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cmb.primaryClip?.getItemAt(0)?.text.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * set text to clipboard
     */
    fun setClipboard(context: Context, content: String) {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(null, content)
            cmb.setPrimaryClip(clipData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * base64 decode
     */
    fun decode(text: String): String {
        tryDecodeBase64(text)?.let { return it }
        if (text.endsWith('=')) {
            // try again for some loosely formatted base64
            tryDecodeBase64(text.trimEnd('='))?.let { return it }
        }
        return ""
    }

    fun tryDecodeBase64(text: String): String? {
        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(charset("UTF-8"))
        } catch (e: Exception) {
            //  Log.i(BuildConfig.LIBRARY_PACKAGE_NAME, "Parse base64 standard failed $e")
        }
        try {
            return Base64.decode(text, Base64.NO_WRAP.or(Base64.URL_SAFE))
                .toString(charset("UTF-8"))
        } catch (e: Exception) {
            //Log.i(BuildConfig.LIBRARY_PACKAGE_NAME, "Parse base64 url safe failed $e")
        }
        return null
    }

    /**
     * base64 encode
     */
    fun encode(text: String): String {
        return try {
            Base64.encodeToString(text.toByteArray(charset("UTF-8")), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }


    /**
     * is ip address
     */
    fun isIpAddress(value: String): Boolean {
        try {
            var addr = value
            if (addr.isEmpty() || addr.isBlank()) {
                return false
            }
            //CIDR
            if (addr.indexOf("/") > 0) {
                val arr = addr.split("/")
                if (arr.count() == 2 && Integer.parseInt(arr[1]) > 0) {
                    addr = arr[0]
                }
            }

            // "::ffff:192.168.173.22"
            // "[::ffff:192.168.173.22]:80"
            if (addr.startsWith("::ffff:") && '.' in addr) {
                addr = addr.drop(7)
            } else if (addr.startsWith("[::ffff:") && '.' in addr) {
                addr = addr.drop(8).replace("]", "")
            }

            // addr = addr.toLowerCase()
            val octets = addr.split('.').toTypedArray()
            if (octets.size == 4) {
                if (octets[3].indexOf(":") > 0) {
                    addr = addr.substring(0, addr.indexOf(":"))
                }
                return isIpv4Address(addr)
            }

            // Ipv6addr [2001:abc::123]:8080
            return isIpv6Address(addr)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVpnDnsServers(): List<String> {
        val vpnDns = "8.8.8.8,8.8.4.4"
        return vpnDns.split(",").filter { isPureIpAddress(it) }
        // allow empty, in that case dns will use system default
    }


    fun isPureIpAddress(value: String): Boolean {
        return (isIpv4Address(value) || isIpv6Address(value))
    }

    fun isIpv4Address(value: String): Boolean {
        val regV4 =
            Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
        return regV4.matches(value)
    }

    fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.indexOf("[") == 0 && addr.lastIndexOf("]") > 0) {
            addr = addr.drop(1)
            addr = addr.dropLast(addr.count() - addr.lastIndexOf("]"))
        }
        val regV6 =
            Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")
        return regV6.matches(addr)
    }

    private fun isCoreDNSAddress(s: String): Boolean {
        return s.startsWith("https") || s.startsWith("tcp") || s.startsWith("quic")
    }

    /**
     * is valid url
     */
    fun isValidUrl(value: String?): Boolean {
        try {
            if (value != null && Patterns.WEB_URL.matcher(value).matches() || URLUtil.isValidUrl(
                    value
                )
            ) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return false
    }

    fun openUri(context: Context, uriString: String) {
        val uri = Uri.parse(uriString)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * uuid
     */
    fun getUuid(): String {
        return try {
            UUID.randomUUID().toString().replace("-", "")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun urlDecode(url: String): String {
        return try {
            URLDecoder.decode(URLDecoder.decode(url), "utf-8")
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
    }


    /**
     * readTextFromAssets
     */
    fun readTextFromAssets(context: Context, fileName: String): String {
        val content = context.assets.open(fileName).bufferedReader().use {
            it.readText()
        }
        return content
    }

    fun userAssetPath(context: Context?): String {
        if (context == null)
            return ""
        val extDir = context.getExternalFilesDir(V2RayConstants.DIR_ASSETS)
            ?: return context.getDir(V2RayConstants.DIR_ASSETS, 0).absolutePath
        return extDir.absolutePath
    }

    fun getUrlContext(url: String, timeout: Int): String {
        var result: String
        var conn: HttpURLConnection? = null

        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("Connection", "close")
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            //val code = conn.responseCode
            result = conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            result = ""
        } finally {
            conn?.disconnect()
        }
        return result
    }

    fun getDarkModeStatus(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK
        return mode == UI_MODE_NIGHT_YES
    }

    fun getIpv6Address(address: String): String {
        return if (isIpv6Address(address)) {
            String.format("[%s]", address)
        } else {
            address
        }
    }

    private fun getSysLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        LocaleList.getDefault()[0]
    } else {
        Locale.getDefault()
    }

    fun fixIllegalUrl(str: String): String {
        return str
            .replace(" ", "%20")
            .replace("|", "%7C")
    }

    fun removeWhiteSpace(str: String?): String? {
        return str?.replace(" ", "")
    }

    fun idnToASCII(str: String): String {
        val url = URL(str)
        return URL(url.protocol, IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED), url.port, url.file)
            .toExternalForm()
    }

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /**
     * get remote dns servers from preference
     */
    fun getRemoteDnsServers(): List<String> {

        //TODO (settings)
        // val remoteDns = settingsStorage?.decodeString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_AGENT
        val ret = listOf(V2RayConstants.DNS_AGENT)
        return ret
    }

    /**
     * get remote dns servers from preference
     */
    fun getDomesticDnsServers(): List<String> {

        return listOf("223.5.5.5")
    }


    fun getLibVersion(): String {
        return Libv2ray.checkVersionX()
    }

    /**
     * stopVService
     */
    fun stopVService(context: Context) {
        MessageUtil.sendMsg2Service(context, V2RayConstants.MSG_STATE_STOP, "")
    }
}
