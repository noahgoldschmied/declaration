package com.declaration.protocol

import com.declaration.domain.Event
import com.declaration.domain.PlayerId
import com.declaration.domain.PlayerView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Messages the server sends to the browser client. Hand-mirrored from
 * protocol/messages.md. The outbound channel is typed as ServerMessage so no
 * raw GameState can ever be serialized to a client (security boundary).
 */
@Serializable
sealed class ServerMessage {
    /** Reply to [ClientMessage.Hello]; confirms identity. */
    @Serializable @SerialName("Welcome")
    data class Welcome(
        val playerId: PlayerId,
        val sessionToken: String,
        val displayName: String,
    ) : ServerMessage()

    /** Sent on lobby/roster/connection changes. */
    @Serializable @SerialName("RoomState")
    data class RoomState(
        val roomCode: String,
        val phase: RoomPhase,
        val hostId: PlayerId,
        val players: List<PlayerInfo>,
    ) : ServerMessage()

    /** Sent after every game state change. [view] is redacted for the receiving player. */
    @Serializable @SerialName("GameUpdate")
    data class GameUpdate(
        val view: PlayerView,
        val events: List<Event>,
    ) : ServerMessage()

    /** A submitted action/command was rejected. State unchanged. */
    @Serializable @SerialName("ActionError")
    data class ActionError(val reason: String) : ServerMessage()

    /** Keepalive reply. */
    @Serializable @SerialName("Pong") data object Pong : ServerMessage()
}
