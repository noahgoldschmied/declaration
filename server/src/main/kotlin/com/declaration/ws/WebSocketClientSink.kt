package com.declaration.ws

import com.declaration.protocol.ServerMessage
import com.declaration.protocol.WireJson
import com.declaration.room.ClientSink
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

/**
 * Adapts a Spring [WebSocketSession] to the room's [ClientSink]. Encodes each outbound
 * [ServerMessage] to JSON and writes it as a text frame. Sends are serialized per session
 * (each session is fed only by its room's single consumer coroutine); the `synchronized`
 * guard is belt-and-suspenders against a concurrent send. A closed session throws on send,
 * which we swallow — the close handler will have already told the room to disconnect.
 */
class WebSocketClientSink(
    private val session: WebSocketSession,
) : ClientSink {
    override suspend fun send(message: ServerMessage) {
        val text = WireJson.json.encodeToString(ServerMessage.serializer(), message)
        try {
            synchronized(session) {
                if (session.isOpen) session.sendMessage(TextMessage(text))
            }
        } catch (_: Exception) {
            // session closing/closed; ignore — disconnect is handled by afterConnectionClosed
        }
    }
}
