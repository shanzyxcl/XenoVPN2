package app.tunnel.vpncommons.enums;

import androidx.annotation.NonNull;

public enum VpnType {
    Hysteria,
    OpenVPN,
    Dnstt,
    SSH,
    PSIPHON,
    UDPCUSTOM,
    V2Ray;

    public static VpnType fromString(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return name();
    }
}
