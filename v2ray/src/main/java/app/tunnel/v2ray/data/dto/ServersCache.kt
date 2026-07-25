package app.tunnel.v2ray.data.dto

import app.tunnel.v2ray.data.model.ServerConfig

data class ServersCache(
    val guid: String,
    val config: ServerConfig
)