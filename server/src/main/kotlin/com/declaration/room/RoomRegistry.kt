package com.declaration.room

import com.declaration.domain.Engine
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration

/** A room and the host's identity, returned from [RoomRegistry.create]. */
data class CreatedRoom(val code: RoomCode, val host: JoinResult)

/**
 * Holds every live room, keyed by code. Plain class (no Spring annotations) so it unit-tests
 * without an application context; a @Configuration exposes it as a bean in a later milestone.
 */
class RoomRegistry(
    private val engine: Engine,
    private val random: Random,
    private val gracePeriod: Duration,
    private val scope: CoroutineScope,
) {
    private val rooms = ConcurrentHashMap<RoomCode, Room>()
    private val codeGenerator = RoomCodeGenerator(random)

    fun get(code: RoomCode): Room? = rooms[code]

    /** Create a fresh room and join [hostName] as the host. */
    suspend fun create(hostName: String): CreatedRoom {
        val code = freshCode()
        val room = Room(code, engine, random, gracePeriod, scope, onEmpty = { rooms.remove(code) })
        rooms[code] = room
        val host = room.join(hostName)
        return CreatedRoom(code, host)
    }

    /** Join an existing room, or null if no room has [code]. */
    suspend fun joinRoom(code: RoomCode, displayName: String): JoinResult? {
        val room = rooms[code] ?: return null
        return room.join(displayName)
    }

    private fun freshCode(): RoomCode {
        var code = codeGenerator.next()
        while (rooms.containsKey(code)) code = codeGenerator.next()
        return code
    }
}
