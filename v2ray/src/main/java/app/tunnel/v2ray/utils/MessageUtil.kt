package app.tunnel.v2ray.utils

import android.content.Context
import android.content.Intent
import app.tunnel.v2ray.BuildConfig
import app.tunnel.v2ray.common.V2RayConstants
import java.io.Serializable


object MessageUtil {

    fun sendMsg2Service(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, V2RayConstants.BROADCAST_ACTION_SERVICE, what, content)
    }

    private fun sendMsg(ctx: Context, action: String, what: Int, content: Serializable) {
        try {
            val intent = Intent()
            intent.action = action
            intent.`package` = BuildConfig.LIBRARY_PACKAGE_NAME
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
