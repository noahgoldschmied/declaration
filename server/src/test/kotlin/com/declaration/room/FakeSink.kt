package com.declaration.room

import com.declaration.protocol.ServerMessage

/** A ClientSink that records every message it receives, for assertions in tests. */
class FakeSink : ClientSink {
    val messages = mutableListOf<ServerMessage>()

    override suspend fun send(message: ServerMessage) {
        messages.add(message)
    }

    /** Most recent message of type [T], or null. */
    inline fun <reified T : ServerMessage> last(): T? = messages.filterIsInstance<T>().lastOrNull()

    /** All messages of type [T], in order. */
    inline fun <reified T : ServerMessage> all(): List<T> = messages.filterIsInstance<T>()

    fun clear() = messages.clear()
}
