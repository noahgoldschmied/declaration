package com.declaration.protocol

import com.declaration.domain.PlayerId
import com.declaration.domain.TeamId

/** Public, non-secret information about a player in a room. Safe to show everyone. */
data class PlayerInfo(
    val playerId: PlayerId,
    val displayName: String,
    val team: TeamId?,        // null until the player picks a team in the lobby
    val connected: Boolean,   // false while their WebSocket is detached
)

/** Lifecycle phase of a room, as seen by clients. */
enum class RoomPhase { LOBBY, PLAYING, ENDED }
