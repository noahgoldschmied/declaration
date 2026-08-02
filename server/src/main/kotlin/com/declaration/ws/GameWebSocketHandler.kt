package com.declaration.ws

import com.declaration.bot.BotService
import com.declaration.protocol.ClientMessage
import com.declaration.protocol.WireJson
import com.declaration.room.Room
import com.declaration.room.RoomCode
import com.declaration.room.RoomRegistry
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * Bridges a raw WebSocket to a [Room]. URL: /ws/room/{code}?session={token}.
 * - open  -> resolve room+token, attach a sink, room.connect (room replies Welcome + RoomState)
 * - frame -> decode ClientMessage, route to the room
 * - close -> room.disconnect (starts the grace timer)
 * The handler holds no game logic; everything goes through room.submit on the room's coroutine.
 */
@Component
class GameWebSocketHandler(
    private val rooms: RoomRegistry,
    private val botService: BotService,
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val code = session.uri?.path?.substringAfterLast('/')
        val token = tokenFrom(session)
        val room = code?.let { rooms.get(RoomCode(it.uppercase())) }
        if (room == null || token == null) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        session.attributes[ATTR_ROOM] = room
        session.attributes[ATTR_TOKEN] = token
        runBlocking { room.connect(token, WebSocketClientSink(session)) }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val room = session.attributes[ATTR_ROOM] as? Room ?: return
        val token = session.attributes[ATTR_TOKEN] as? String ?: return
        val msg = try {
            WireJson.json.decodeFromString(ClientMessage.serializer(), message.payload)
        } catch (_: Exception) {
            return // ignore malformed frames
        }
        runBlocking {
            when (msg) {
                is ClientMessage.Hello -> Unit // connect already happened on socket open
                is ClientMessage.ChooseTeam -> room.chooseTeam(token, msg.team)
                is ClientMessage.StartGame -> room.startGame(token)
                is ClientMessage.AddBot -> botService.addBot(room, token, msg.team, msg.difficulty)
                is ClientMessage.KickPlayer -> room.kickPlayer(token, msg.playerId)
                is ClientMessage.RandomizeTeams -> room.randomizeTeams(token)
                is ClientMessage.SetMoveHistoryEnabled -> room.setMoveHistoryEnabled(token, msg.enabled, msg.visibleCount)
                is ClientMessage.SubmitAction -> room.submitAction(token, msg.action)
                is ClientMessage.SetDeclaring -> room.setDeclaring(token, msg.declaring)
                is ClientMessage.Ping -> room.ping(token)
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val room = session.attributes[ATTR_ROOM] as? Room ?: return
        val token = session.attributes[ATTR_TOKEN] as? String ?: return
        runBlocking { room.disconnect(token) }
    }

    private fun tokenFrom(session: WebSocketSession): String? =
        session.uri?.query
            ?.split("&")
            ?.firstOrNull { it.startsWith("session=") }
            ?.substringAfter("session=")
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val ATTR_ROOM = "room"
        const val ATTR_TOKEN = "token"
    }
}
