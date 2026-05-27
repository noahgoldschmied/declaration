package com.declaration.domain

object Redactor {

    fun viewFor(state: GameState, viewer: PlayerId): PlayerView {
        val viewerPlayer = state.playerById(viewer)
            ?: error("viewer $viewer not in state")

        val self = SelfView(
            id = viewerPlayer.id,
            team = viewerPlayer.team,
            seat = viewerPlayer.seat,
            hand = viewerPlayer.hand,
        )

        val others = state.players
            .filter { it.id != viewer }
            .map { p -> OpponentView(id = p.id, team = p.team, seat = p.seat, handSize = p.hand.size) }

        return PlayerView(
            you = self,
            others = others,
            turn = state.turn,
            phase = state.phase,
            winner = state.winner,
            capturedDecks = state.capturedDecks,
        )
    }
}
