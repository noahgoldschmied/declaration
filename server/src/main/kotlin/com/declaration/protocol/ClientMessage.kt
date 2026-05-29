package com.declaration.protocol

import com.declaration.domain.Action
import com.declaration.domain.TeamId

/**
 * Messages the browser client sends to the server. Hand-mirrored from
 * protocol/messages.md; any change updates that file in the same PR.
 *
 * The session token is carried in the WebSocket URL query string, never in a body.
 */
sealed class ClientMessage {
    /** First message after the socket opens. Server replies with [ServerMessage.Welcome]. */
    data object Hello : ClientMessage()

    /** Lobby only: pick or switch team. Rejected if the team already has 3 players. */
    data class ChooseTeam(val team: TeamId) : ClientMessage()

    /** Host only, lobby only: begin the game. Requires exactly 6 players, 3 per team. */
    data object StartGame : ClientMessage()

    /** In-game: submit a move. [action] is the domain action union. */
    data class SubmitAction(val action: Action) : ClientMessage()

    /** Keepalive. Server replies with [ServerMessage.Pong]. */
    data object Ping : ClientMessage()
}
