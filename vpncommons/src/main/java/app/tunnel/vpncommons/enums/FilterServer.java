package app.tunnel.vpncommons.enums;

import androidx.annotation.NonNull;

public enum FilterServer {
    SSH,
    OpenVPN,
    Hysteria,
    V2Ray,
    SlowDNS;

    public static FilterServer fromString(String value) {
        for (FilterServer network : values()) {
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

