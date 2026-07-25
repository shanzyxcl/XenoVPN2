package app.tunnel.vpncommons.interfaces

import app.tunnel.vpncommons.enums.VpnType

interface VpnListener {
    fun onStartVpn(vpnType: VpnType)
    fun onStopVpn(vpnType: VpnType)
}
