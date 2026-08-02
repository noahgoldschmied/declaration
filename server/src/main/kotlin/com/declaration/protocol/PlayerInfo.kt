package com.declaration.protocol

import com.declaration.domain.PlayerId
import com.declaration.domain.TeamId
import kotlinx.serialization.Serializable

/** Public, non-secret information about a player in a room. Safe to show everyone. */
@Serializable
data class PlayerInfo(
    val playerId: PlayerId,
    val displayName: String,
    val team: TeamId?,        // null until the player picks a team in the lobby
    val connected: Boolean,   // false while their WebSocket is detached
    val isBot: Boolean = false,
    val botDifficulty: BotDifficulty? = null,   // null for humans
)

/** Lifecycle phase of a room, as seen by clients. */
@Serializable
enum class RoomPhase { LOBBY, PLAYING, ENDED }
