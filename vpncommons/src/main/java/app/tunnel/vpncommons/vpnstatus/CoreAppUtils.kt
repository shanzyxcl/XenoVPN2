package app.tunnel.vpncommons.TkLogStatus

import android.content.res.Resources
import app.tunnel.vpncommons.R
import kotlin.math.ln
import kotlin.math.pow

object CoreAppUtils {

    // From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    @JvmStatic
    fun humanReadableByteCount(bytes: Long, speed: Boolean, res: Resources): String {
        var bytes0 = bytes
        if (speed) {
            bytes0 *= 8
        }
        val unit = if (speed) 1000 else 1024
        val exp = 0.coerceAtLeast(
            (ln(bytes0.toDouble()) / ln(unit.toDouble()))
                .toInt()
                .coerceAtMost(3)
        )
        val bytesUnit = (bytes0 / unit.toDouble().pow(exp.toDouble())).toFloat()
        return if (speed) {
            when (exp) {
                0 -> res.getString(R.string.bits_per_second, bytesUnit)
                1 -> res.getString(R.string.kbits_per_second, bytesUnit)
                2 -> res.getString(R.string.mbits_per_second, bytesUnit)
                else -> res.getString(R.string.gbits_per_second, bytesUnit)
            }
        } else {
            when (exp) {
                0 -> res.getString(R.string.volume_byte, bytesUnit)
                1 -> res.getString(R.string.volume_kbyte, bytesUnit)
                2 -> res.getString(R.string.volume_mbyte, bytesUnit)
                else -> res.getString(R.string.volume_gbyte, bytesUnit)
            }
        }
    }
}
