package com.declaration.bot

import com.declaration.domain.TeamId
import com.declaration.protocol.BotDifficulty
import com.declaration.room.Room
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component
import kotlin.random.Random

/** Bridges [ClientMessage.AddBot][com.declaration.protocol.ClientMessage.AddBot] to a live bot player. */
@Component
class BotService(
    private val random: Random,
    private val scope: CoroutineScope,
) {
    suspend fun addBot(room: Room, hostSessionToken: String, team: TeamId, difficulty: BotDifficulty) {
        val brain = BotBrain(random, difficulty)
        val sink = BotClientSink(room, brain, scope, random)
        val candidateNames = BotNames.shuffledCandidates(random)
        val result = room.addBot(hostSessionToken, candidateNames, team, difficulty, sink) ?: return
        sink.token = result.sessionToken
    }
}
