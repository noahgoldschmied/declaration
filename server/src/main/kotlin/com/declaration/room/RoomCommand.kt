package com.declaration.room

import com.declaration.domain.Action
import com.declaration.domain.PlayerId
import com.declaration.domain.TeamId
import kotlinx.coroutines.CompletableDeferred

/** The result of a join: the new player's public id and secret session token. */
data class JoinResult(val playerId: PlayerId, val sessionToken: String)

/**
 * Everything that mutates a room goes through one of these, delivered on the room's
 * Channel and processed serially by the room's single consumer coroutine.
 */
sealed class RoomCommand {
    /** Add a new player (called by REST create/join). Completes [reply] with the new identity. */
    data class Join(
        val displayName: String,
        val reply: CompletableDeferred<JoinResult>,
    ) : RoomCommand()

    /** A WebSocket opened (or reattached on reconnect) for [sessionToken]. */
    data class Connect(val sessionToken: String, val sink: ClientSink) : RoomCommand()

    /** Lobby: pick/switch team. */
    data class ChooseTeam(val sessionToken: String, val team: TeamId) : RoomCommand()

    /** Host: begin the game. */
    data class StartGame(val sessionToken: String) : RoomCommand()

    /** In-game: submit a move. */
    data class SubmitAction(val sessionToken: String, val action: Action) : RoomCommand()

    /** Keepalive. */
    data class Ping(val sessionToken: String) : RoomCommand()

    /** A WebSocket closed for [sessionToken]. Starts the disconnect grace timer. */
    data class Disconnect(val sessionToken: String) : RoomCommand()

    /** Internal: fired after the grace period to remove a still-disconnected player. */
    data class CleanupDisconnect(val sessionToken: String, val epoch: Int) : RoomCommand()
}
