package com.declaration.bot

import com.declaration.domain.PlayerView
import com.declaration.protocol.ServerMessage
import com.declaration.room.ClientSink
import com.declaration.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Adapts a [BotBrain] to the room's [ClientSink] interface -- the same seam a real WebSocket
 * uses, so a bot never touches a socket or JSON. [token] is set by [BotService] right after
 * [Room.addBot] returns; addBot is lobby-only, so no [ServerMessage.GameUpdate] (the only
 * message this sink reacts to) can arrive before that happens.
 */
class BotClientSink(
    private val room: Room,
    private val brain: BotBrain,
    private val scope: CoroutineScope,
    private val random: Random,
) : ClientSink {

    lateinit var token: String

    @Volatile
    private var actionScheduled = false

    @Volatile
    private var latestView: PlayerView? = null

    override suspend fun send(message: ServerMessage) {
        if (message !is ServerMessage.GameUpdate) return
        latestView = message.view
        val action = brain.observe(message.view, message.events) ?: return
        if (actionScheduled) return
        actionScheduled = true
        scope.launch {
            delay(MIN_DELAY_MS + random.nextInt(EXTRA_DELAY_RANGE_MS))
            actionScheduled = false
            // Re-derive the decision against whatever the latest observed state is by the time
            // the delay elapses, instead of blindly submitting the `action` computed when this
            // was scheduled. A Declare is a legal off-turn interrupt and can invalidate that
            // stale decision in the meantime (e.g. it captures away the very deck this bot was
            // about to ask about) -- a stale submission then gets rejected with an ActionError,
            // which this sink never reads (it only reacts to GameUpdate), so without recomputing
            // here the bot would stall forever: nothing else ever prompts it to try again.
            val current = latestView ?: return@launch
            val freshAction = brain.observe(current, emptyList()) ?: return@launch
            room.submitAction(token, freshAction)
        }
    }

    private companion object {
        // Slow enough that a human can actually track what's happening, even with
        // several bots at the table -- fast bots made multi-bot games feel unplayable.
        const val MIN_DELAY_MS = 3500L
        const val EXTRA_DELAY_RANGE_MS = 3500
    }
}
