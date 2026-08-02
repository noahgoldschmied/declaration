package com.declaration.room

import com.declaration.domain.Action
import com.declaration.domain.PlayerId
import com.declaration.domain.TeamId
import com.declaration.protocol.BotDifficulty
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

    /**
     * Host, lobby only: seat a bot. [candidateNames] is tried in order and the first name not
     * already held by a seated player is used (falls back to a numbered suffix on the first
     * candidate if every candidate collides) -- resolved inside the room's serial handler so two
     * bots can never land on the same name. Completes [reply] with the new identity, or null on
     * rejection.
     */
    data class AddBot(
        val hostSessionToken: String,
        val candidateNames: List<String>,
        val team: TeamId,
        val difficulty: BotDifficulty,
        val sink: ClientSink,
        val reply: CompletableDeferred<JoinResult?>,
    ) : RoomCommand()

    /** Host, lobby only: remove a player (bot or human), freeing their seat. */
    data class KickPlayer(val sessionToken: String, val targetPlayerId: PlayerId) : RoomCommand()

    /** Host, lobby only: shuffle all seated players into a new random team split. */
    data class RandomizeTeams(val sessionToken: String) : RoomCommand()

    /**
     * Host, lobby only: choose whether the move-history sidebar is on for everyone this game,
     * and how many recent moves it shows. [visibleCount] is clamped server-side.
     */
    data class SetMoveHistoryEnabled(val sessionToken: String, val enabled: Boolean, val visibleCount: Int) : RoomCommand()

    /** In-game: submit a move. */
    data class SubmitAction(val sessionToken: String, val action: Action) : RoomCommand()

    /** In-game: the sender opened/closed the declare panel -- a presence signal, see [com.declaration.protocol.ClientMessage.SetDeclaring]. */
    data class SetDeclaring(val sessionToken: String, val declaring: Boolean) : RoomCommand()

    /** Keepalive. */
    data class Ping(val sessionToken: String) : RoomCommand()

    /** A WebSocket closed for [sessionToken]. Starts the disconnect grace timer. */
    data class Disconnect(val sessionToken: String) : RoomCommand()

    /** Internal: fired after the grace period to remove a still-disconnected player. */
    data class CleanupDisconnect(val sessionToken: String, val epoch: Int) : RoomCommand()
}
