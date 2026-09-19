package io.legado.app.web

import fi.iki.elonen.NanoWSD
import io.legado.app.help.config.AppConfig
import io.legado.app.service.WebService
import io.legado.app.web.socket.*

class WebSocketServer(port: Int) : NanoWSD(port) {

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        WebService.serve()
        // 与 HttpServer 使用同一访问令牌；浏览器经 ?token= 种下的 Cookie 在握手时自动携带
        val token = AppConfig.webToken
        if (token.isNotBlank()) {
            val reqToken = handshake.parameters["token"]?.firstOrNull()
                ?: handshake.cookies.read("authToken")
            if (reqToken != token) {
                return null
            }
        }
        return when (handshake.uri) {
            "/bookSourceDebug" -> {
                BookSourceDebugWebSocket(handshake)
            }
            "/rssSourceDebug" -> {
                RssSourceDebugWebSocket(handshake)
            }
            "/searchBook" -> {
                BookSearchWebSocket(handshake)
            }
            else -> null
        }
    }
}
