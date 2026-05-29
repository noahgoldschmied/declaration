package com.declaration.protocol

import com.declaration.domain.Event
import com.declaration.domain.PlayerId
import com.declaration.domain.PlayerView

/**
 * Messages the server sends to the browser client. Hand-mirrored from
 * protocol/messages.md. The outbound channel is typed as ServerMessage so no
 * raw GameState can ever be serialized to a client (security boundary).
 */
sealed class ServerMessage {
    /** Reply to [ClientMessage.Hello]; confirms identity. */
    data class Welcome(
        val playerId: PlayerId,
        val sessionToken: String,
        val displayName: String,
    ) : ServerMessage()

    /** Sent on lobby/roster/connection changes. */
    data class RoomState(
        val roomCode: String,
        val phase: RoomPhase,
        val hostId: PlayerId,
        val players: List<PlayerInfo>,
    ) : ServerMessage()

    /** Sent after every game state change. [view] is redacted for the receiving player. */
    data class GameUpdate(
        val view: PlayerView,
        val events: List<Event>,
    ) : ServerMessage()

    /** A submitted action/command was rejected. State unchanged. */
    data class ActionError(val reason: String) : ServerMessage()

    /** Keepalive reply. */
    data object Pong : ServerMessage()
}
