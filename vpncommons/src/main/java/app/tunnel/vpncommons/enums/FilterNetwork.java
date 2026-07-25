package app.tunnel.vpncommons.enums;

import androidx.annotation.NonNull;

public enum FilterNetwork {
    SSH,
    OpenVPN_TCP,
    OpenVPN_UDP,
    UDP,
    Hysteria,
    Hysteria_OpenVPN,
    V2Ray,
    USE_PAYLOAD,
    USE_SNI,
    SlowDNS,
    USE_PROXY;

    public static FilterNetwork fromString(String value) {
        for (FilterNetwork network : values()) {
            if (network.toString().equals(value.toLowerCase())) {
                return network;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

