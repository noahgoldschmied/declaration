package com.declaration.protocol

import com.declaration.domain.Action
import com.declaration.domain.PlayerId
import com.declaration.domain.TeamId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Messages the browser client sends to the server. Hand-mirrored from
 * protocol/messages.md; any change updates that file in the same PR.
 *
 * The session token is carried in the WebSocket URL query string, never in a body.
 */
@Serializable
sealed class ClientMessage {
    /** First message after the socket opens. Server replies with [ServerMessage.Welcome]. */
    @Serializable @SerialName("Hello") data object Hello : ClientMessage()

    /** Lobby only: pick or switch team. Rejected if the team already has 3 players. */
    @Serializable @SerialName("ChooseTeam")
    data class ChooseTeam(val team: TeamId) : ClientMessage()

    /** Host only, lobby only: begin the game. Requires exactly 6 players, 3 per team. */
    @Serializable @SerialName("StartGame") data object StartGame : ClientMessage()

    /** Host only, lobby only: seat a bot on [team] at the given [difficulty]. */
    @Serializable @SerialName("AddBot")
    data class AddBot(val team: TeamId, val difficulty: BotDifficulty) : ClientMessage()

    /** Host only, lobby only: remove a player — bot or human — freeing their seat. */
    @Serializable @SerialName("KickPlayer")
    data class KickPlayer(val playerId: PlayerId) : ClientMessage()

    /** Host only, lobby only: shuffle all seated players into a new random team split. */
    @Serializable @SerialName("RandomizeTeams") data object RandomizeTeams : ClientMessage()

    /**
     * Host only, lobby only: choose whether every player's client shows a running move-history
     * sidebar, and how many recent moves it shows. A fairness setting (same depth of recall for
     * everyone), not a per-player preference -- locked once [StartGame] fires, same as team
     * choice. [visibleCount] is clamped server-side to the room's allowed range.
     */
    @Serializable @SerialName("SetMoveHistoryEnabled")
    data class SetMoveHistoryEnabled(val enabled: Boolean, val visibleCount: Int) : ClientMessage()

    /** In-game: submit a move. [action] is the domain action union. */
    @Serializable @SerialName("SubmitAction")
    data class SubmitAction(val action: Action) : ClientMessage()

    /**
     * In-game: the sender has opened (true) or closed/cancelled (false) the declare panel.
     * Purely a presence signal for other players -- the server also auto-clears this on an
     * actual Declare submission and on disconnect, so the client only needs to send it for
     * open and cancel.
     */
    @Serializable @SerialName("SetDeclaring")
    data class SetDeclaring(val declaring: Boolean) : ClientMessage()

    /** Keepalive. Server replies with [ServerMessage.Pong]. */
    @Serializable @SerialName("Ping") data object Ping : ClientMessage()
}
