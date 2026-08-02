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
        /**
         * Host-chosen, lobby-only setting: whether the web client shows a running move-history
         * sidebar, and how many recent moves it shows. Locked once the game starts (see
         * [com.declaration.protocol.ClientMessage.SetMoveHistoryEnabled]) so every player has the
         * same depth of recall for the whole game -- it's a fairness setting, not a per-player
         * preference.
         */
        val moveHistoryEnabled: Boolean = false,
        val moveHistoryVisibleCount: Int = MoveHistoryLimits.DEFAULT_VISIBLE_COUNT,
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

    /** Sent to a human player's own sink right before the host removes them from the room. */
    @Serializable @SerialName("Kicked") data object Kicked : ServerMessage()

    /** Broadcast whenever the set of players with the declare panel open changes. */
    @Serializable @SerialName("DeclaringPlayers")
    data class DeclaringPlayers(val playerIds: Set<PlayerId>) : ServerMessage()

    /** Keepalive reply. */
    @Serializable @SerialName("Pong") data object Pong : ServerMessage()
}
