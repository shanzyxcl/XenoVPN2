package app.tunnel.vpncommons


import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

object VpnExt {

    private const val DEFAULT_PRIMARY_DNS_SERVER = "8.8.4.4"
    private const val DEFAULT_SECONDARY_DNS_SERVER = "8.8.8.8"

    /**
     * parseInt
     */
    fun parseInt(str: String): Int {
        return parseInt(str, 0)
    }

    fun parseInt(str: String?, default: Int): Int {
        str ?: return default
        return try {
            Integer.parseInt(str)
        } catch (e: Exception) {
            e.printStackTrace()
            default
        }
    }


    private fun md5(s: String): String {
        try {
            // Create MD5 Hash
            val digest = MessageDigest.getInstance("MD5")
            digest.update(s.toByteArray())
            val messageDigest = digest.digest()

            // Create Hex String
            val hexString = StringBuilder()
            for (b in messageDigest) hexString.append(Integer.toHexString(0xFF and b.toInt()))
            return hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return ""
    }

    fun uniqueDevice(context: Context): String {
        @SuppressLint("HardwareIds") val androidID = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return md5(androidID).uppercase(Locale.getDefault())
    }

}
