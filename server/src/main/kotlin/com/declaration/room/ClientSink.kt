package com.declaration.room

import com.declaration.protocol.ServerMessage

/**
 * The room's outbound channel to one connected client. The WebSocket layer adapts a
 * real session to this interface; tests use a fake. Keeping this abstraction here means
 * room/ never depends on Spring's WebSocket types.
 */
fun interface ClientSink {
    suspend fun send(message: ServerMessage)
}
