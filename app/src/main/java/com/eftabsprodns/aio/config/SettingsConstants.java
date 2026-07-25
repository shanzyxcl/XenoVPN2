package com.eftabsprodns.aio.config;

public interface SettingsConstants {
    // Geral settings
    public static final String
            isAutoLogIn = "_isAutoLogIn",
            isAdsShowing = "_isAdsShowing",
            isAutoReplace = "_autoReplace",
            PAUSE_VPN_ON_BLANKED_SCREEN_KEY = "7pause_vpn_on_blanked_screen",
            AUTO_CLEAR_LOGS_KEY = "7autoClearLogs",
            AUTO_RECONN_TIME_KEY = "7autoReconnTime",
            TETHERING_SUBNET = "7tetherSubnet",
            DISABLE_DELAY_KEY = "7disableDelaySSH",
            MAXIMO_THREADS_KEY = "7numberMaxThreadSocks",
            Data_compression_key = "7data_compression",
            FILTER_APPS = "7filterApps",
            FILTER_BYPASS_MODE = "7filterBypassMode",
   PINGER = "ping_server",
    AUTO_PINGER = "auto_ping",
    
    
    
            FILTER_APPS_LIST = "7filterAppsList",
            DIRECT_UDP_CONFIG_KEY = "_DIRECT_UDP_CONFIG_KEY";

    
       // Vpn
    public static final String
    DNSFORWARD_KEY = "dnsForward",
    DNSRESOLVER_KEY_1 = "dns_Resolver1",
    DNSRESOLVER_KEY_2 = "dns_Resolver2",
    UDPFORWARD_KEY = "udpForward",
    BYPASS_KEY = "bypassKey",
    UDPRESOLVER_KEY = "udpResolver";
    
    

    //DNS TYPE
    public static final String
            
            DNSRESOLVER_KEY = "7dnsResolver",
            
            
            DNS_PUBLIC_KEY = "7t_DNS_PUBLIC_KEY",
            DNS_ADDRESS_KEY = "7t_DNS_ADDRESS_KEY",
            DNS_NAME_SERVER_KEY = "7t_DNS_NAME_SERVER_KEY",
            CUSTOM_DNS_ADDRESS_KEY = "7t_CUSTOM_DNS_ADDRESS_KEY";
    // VPN
    public static final String
            SERVER_KEY = "7sshServer",
            SERVER_PORT_KEY = "7sshPort",
            PROXY_IP_KEY = "7proxyRemoto",
            PROXY_PORT_KEY = "7proxyRemotoPorta",
            USERNAME_KEY = "7sshUser",
            PASSWORD_KEY = "7sshPass",
            PORTA_LOCAL_KEY = "7sshPortaLocal",
            PINGER_KEY = "7pingerSSH",
            PAYLOAD_TYPE_KEY = "7mPAYLOAD_TYPE_KEY",
            CUSTOM_PAYLOAD_KEY = "7proxyPayload",
            CUSTOM_SSL_PAYLOAD_KEY = "_CUSTOM_SSL_PAYLOAD_KEY",
            SNI_V2RAY_KEY = "_SNI_V2RAY_KEY",
            SNI_HOST_KEY = "7ssl_sni_key";

    // TYPE SECURE SHELL PAYLOAD TYPE
    public static final int
            PAYLOAD_TYPE_DIRECT = 1,
            PAYLOAD_TYPE_DIRECT_PAYLOAD = 2,
            PAYLOAD_TYPE_HTTP_PROXY = 3,
            PAYLOAD_TYPE_SSL = 4,
            PAYLOAD_TYPE_SSL_PAYLOAD = 5,
            PAYLOAD_TYPE_SSL_PROXY = 6,
            PAYLOAD_TYPE_SSL_PROXY_HTTP_PROXY = 7,
            PAYLOAD_TYPE_OVPN_UDP = 8;

    // Stringer
    public static final String
            QueryMode = "isQueryMode",
            FrontQuery = "_FrontQuery",
            BackQuery = "_BackQuery",
            SERVER_TYPE = "7t_SERVER_TYPE",
            CONFIG_VERSION = "7t_CONFIG_VERSION",
            RELEASE_NOTE = "7t_RELEASE_NOTE_KEY",
            CONTACT_SUPPORT = "7_CONTACT_SUPPORT",
            OpenVPN_CERT = "7t_SINGLE_CERT",
            CONFIG_V2RAY = "7t_CONFIG_V2RAY",
            SERVER_POSITION = "7t_SERVER_POSITION",
            NETWORK_POSITION = "7t_NETWORK_POSITION",
            SERVER_TYPE_OVPN = "MTK_TYPE_OVPN",
            SERVER_TYPE_SSH = "MTK_TYPE_SSH",
            SERVER_TYPE_DNS = "MTK_TYPE_DNS",
            SERVER_TYPE_v2ray = "MTK_TYPE_v2ray",
            SERVER_TYPE_UDP_HYSTERIA_V1 = "SERVER_TYPE_UDP_HYSTERIA_V1",
            SERVER_TYPE_UDP_HYSTERIA_V2 = "SERVER_TYPE_UDP_HYSTERIA_V2";

}
