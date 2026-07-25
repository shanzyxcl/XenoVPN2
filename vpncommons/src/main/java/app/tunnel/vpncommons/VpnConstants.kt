package app.tunnel.vpncommons

object VpnConstants {

    const val LOCAL_PORT = "1080"
    const val MAX_THREADS = "8"

    const val KEY_ENABLE_APP_FILTER = "enableAppFilter"
    const val PREF_TETHERING = "pref_tethering"
    const val KEY_DISALLOWED_APPS = "disallowedApps"
    const val KEY_ALLOW_DISALLOWED_APPS = "allowDisallowedApps"

    /** DEFAULT **/
    const val PORT_MTU = "1500"
    const val PORT_SOCKS = "10808"
    const val PORT_HTTP = "10809"

    const val DEFAULT_PAYLOAD =
        "CONNECT [host_port] [protocol][crlf]Host: www.bughost.com[crlf][crlf]"
    const val DEFAULT_SNI = "www.bughost.com"
    const val DEFAULT_DNS = "127.0.0.1"
    const val DEFAULT_PROXY_PORT = 8080
    const val KEY_REPLACE_PAYLOAD = "replace_host"

    const val DEFAULT_PRIMARY_DNS = "8.8.8.8"
    const val DEFAULT_SECONDARY_DNS = "8.8.4.4"


    const val REMAINING_TIMER = "keyRemainingTimer"
    const val SELECTED_COUNTRY = "keySelectedCountry"

    const val SELECTED_COUNTRY_NAME = "keySelectedCountryName"
    const val SELECTED_COUNTRY_FLAG = "keySelectedCountryFlag"
    const val SELECTED_NETWORK = "keySelectedNetwork"
    const val IS_PRODUCTION = "keyProduction"

    const val KEY_LOCAL_PORT = "key_local_port"
    const val SERVER_ADDRESS = "server_address"
    const val SERVER_PORT = "server_port"

    const val EXCLUDE_ADDRESS = "exclude_address"

    const val AUTH_USERNAME = "auth_username"
    const val AUTH_PASSWORD = "auth_password"
    const val AUTH_KEY_PATH = "auth_key_path"
    const val PROXY_HOST = "proxy_host"
    const val PROXY_PORT = "proxy_port"

    const val HAS_PROXY = "useProxyKey"
    const val HAS_SNI = "useSniKey"
    const val HAS_PAYLOAD = "usePayloadKey"
    const val IS_DIRECT = "isDirectKey"
    const val IS_UDP = "isUdpKey"
    const val IS_OPENVPN = "isOpenVpnKey"
    const val AUTO_REPLACE = "autoReplaceKey"
    const val HAS_DNS = "hasDnsKey"
    const val WAKELOCK = "cpuWakelockKey"

    const val VPN_PROTOCOL = "key_vpn_protocol"

    const val PAYLOAD = "payloadKey"
    const val SNI = "sniKey"
    const val REQUEST_DOMAIN = "requestDomainKey"

    const val HYSTERIA_CONFIG = "hysteriaConfigKey"
    const val RECONNECT = "reconnectTimeKey"

    const val DNSTT_ADDRESS = "127.0.0.1"
    const val DNSTT_PORT = 2222
    const val DNSTT_RESOLVER_MODE = "dnsttResolverModeKey"
    const val DNSTT_RESOLVER_ADDR = "dnsttResolverAddressKey"
    const val DNSTT_PUB_KEY = "dnsttPubKey"
    const val DNSTT_NAME_SERVER = "dnsttNameServerKey"

    const val SSL_PROTOCOL = "sslProtocolKey"
    const val PREF_DNS_FORWARD = "key_dns_forward"

    const val PREF_SSH_MAX_THREADS = "ssh_max_threads"
    const val PREF_USE_DNS = "useDnsKey"
    const val DNS_SERVER = "dnsServer"
    const val DNS_PRIMARY = "primaryDnsKey"
    const val DNS_SECONDARY = "secondaryDnsKey"
    const val PREF_UDP_FORWARD = "pref_udp_forward"
    const val PREF_UDP_RESOLVER = "pref_udp_resolver"
    const val SSH_DATA_COMPRESS = "key_use_compression"
    const val DISABLE_DELAY = "disableDelaySSHKey"
    const val TCP_DELAY = "key_tcp_delay"
    const val SHOW_NET_STAT = "key_show_net_stat"
    const val KEX_TIMEOUT = "kexTimeoutKey"
    const val CONNECTION_TIMEOUT = "connectionTimeOutKey"
    const val RECONNECT_TIMEOUT = "reconnectTimeoutKey"

    const val V2RAY_URI = "v2RayUriKey"
    const val V2RAY_CONFIG = "v2RayConfigKey"
    const val V2RAY_FULL = "v2RayFullKey"
    const val V2RAY_TYPE = "v2RayTypeKey"

    const val KEY_UUID = "key_uuid"
    const val OPENVPN_UUID = "64215cf6-35d4-4d0f-a5be-9359a21d83a9"
    const val KEY_REMAINING_TIMER: String = "key_remaining_timer"

    /** preferences **/
    const val PREF_SOCKS_PORT = "pref_socks_port"
    const val PREF_HTTP_PORT = "pref_http_port"

}