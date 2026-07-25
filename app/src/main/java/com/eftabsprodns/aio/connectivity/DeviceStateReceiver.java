package com.eftabsprodns.aio.connectivity;

import android.content.Context;
import android.content.Intent;

import app.tunnel.vpncommons.vpnstatus.TkLogStatus;
import com.tpv.plus.R;
import com.eftabsprodns.aio.config.ConfigUtil;
import com.eftabsprodns.aio.config.SettingsConstants;
import com.eftabsprodns.aio.service.OpenVPNService;
import com.eftabsprodns.aio.service.VPNService;

public class DeviceStateReceiver extends ConnectivityReceiverBase implements SettingsConstants {
    private ConnectionState currentState = getConnectionState();

    public DeviceStateReceiver(Context context) {
        super(context);
    }

    public void onAvailable(Object obj) {
        checkNewState();
    }

    public void onLost(Object obj) {
        checkNewState();
    }

    private void checkNewState() {
        ConnectionState connectionState = getConnectionState();
        if (this.currentState.hasChanged(connectionState)) {
            onStateChange(connectionState);
        }
        this.currentState = connectionState;
    }

    private void onStateChange(ConnectionState connectionState) {
        boolean isOVPN = ConfigUtil.getInstance(context).getServerType().equals(SERVER_TYPE_OVPN);
        if (this.currentState.isConnected() && connectionState.isDisconnected()) {
            if (TkLogStatus.isTunnelActive()) {
                TkLogStatus.updateStateString(TkLogStatus.VPN_PAUSE, context.getString(R.string.state_pause));
                TkLogStatus.logInfo(context.getString(R.string.state_pause));
                if (isOVPN)
                    context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_PAUSE));
                else
                    context.startService(new Intent(context, VPNService.class).setAction(VPNService.RECONNECT_SERVICE));
            }
        } else if (this.currentState.isDisconnected() && connectionState.isConnected()) {
            if (TkLogStatus.isTunnelActive()) {
                TkLogStatus.updateStateString(TkLogStatus.VPN_RESUME, context.getString(R.string.state_resume));
                TkLogStatus.logInfo(context.getString(R.string.state_resume));
                if (isOVPN)
                    context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RESUME));
                else
                    context.startService(new Intent(context, VPNService.class).setAction(VPNService.RECONNECT_SERVICE));
            }
        } else {
            if (TkLogStatus.isTunnelActive()) {
                TkLogStatus.updateStateString(TkLogStatus.VPN_RECONNECTING, context.getString(R.string.state_reconnecting));
                context.startService(new Intent(context, VPNService.class).setAction(VPNService.RECONNECT_SERVICE));
            }
        }
    }

    private ConnectionState getConnectionState() {
        return ConnectionState.getInstance(getManager());
    }

}
