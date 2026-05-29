package com.declaration.ws

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: GameWebSocketHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // "*" matches a single path segment (the room code). Allow all origins for the
        // browser client during development (the Vite dev server is a different origin).
        registry.addHandler(handler, "/ws/room/*").setAllowedOriginPatterns("*")
    }
}
