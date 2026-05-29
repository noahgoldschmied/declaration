package com.declaration.ws

import com.declaration.protocol.ServerMessage
import com.declaration.protocol.WireJson
import com.declaration.room.RoomRegistry
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketIntegrationTest {

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var rooms: RoomRegistry

    private class Recorder : TextWebSocketHandler() {
        val frames = LinkedBlockingQueue<String>()
        val closed = CountDownLatch(1)
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            frames.add(message.payload)
        }
        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            closed.countDown()
        }
        inline fun <reified T : ServerMessage> await(timeoutMs: Long = 3000): T {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val payload = frames.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: continue
                val msg = WireJson.json.decodeFromString(ServerMessage.serializer(), payload)
                if (msg is T) return msg
            }
            error("did not receive a ${T::class.simpleName} within ${timeoutMs}ms")
        }
    }

    private fun connect(code: String, token: String, recorder: Recorder): WebSocketSession =
        StandardWebSocketClient()
            .execute(recorder, WebSocketHttpHeaders(), URI("ws://localhost:$port/ws/room/$code?session=$token"))
            .get(3, TimeUnit.SECONDS)

    @Test
    fun `connecting with a valid token receives Welcome then RoomState`() {
        val created = runBlocking { rooms.create("Alice") }
        val recorder = Recorder()
        val session = connect(created.code.value, created.host.sessionToken, recorder)

        val welcome = recorder.await<ServerMessage.Welcome>()
        assertEquals("Alice", welcome.displayName)
        assertEquals(created.host.playerId, welcome.playerId)
        val state = recorder.await<ServerMessage.RoomState>()
        assertEquals(1, state.players.size)

        session.close()
    }

    @Test
    fun `ping is answered with pong`() {
        val created = runBlocking { rooms.create("Alice") }
        val recorder = Recorder()
        val session = connect(created.code.value, created.host.sessionToken, recorder)
        recorder.await<ServerMessage.Welcome>()

        session.sendMessage(TextMessage("""{"type":"Ping"}"""))

        val pong = recorder.await<ServerMessage.Pong>()
        assertNotNull(pong)
        session.close()
    }

    @Test
    fun `a second player joining is broadcast to the first over the socket`() {
        val created = runBlocking { rooms.create("Alice") }
        val aliceRec = Recorder()
        val aliceSession = connect(created.code.value, created.host.sessionToken, aliceRec)
        aliceRec.await<ServerMessage.RoomState>()

        val bob = runBlocking { rooms.joinRoom(created.code, "Bob") }!!
        val bobRec = Recorder()
        val bobSession = connect(created.code.value, bob.sessionToken, bobRec)

        val deadline = System.currentTimeMillis() + 3000
        var latest = 1
        while (System.currentTimeMillis() < deadline && latest < 2) {
            latest = aliceRec.await<ServerMessage.RoomState>().players.size
        }
        assertEquals(2, latest)

        aliceSession.close()
        bobSession.close()
    }

    @Test
    fun `connecting to an unknown room closes the socket`() {
        val recorder = Recorder()
        connect("ZZZZ", "sometoken", recorder)
        assertTrue(recorder.closed.await(3, TimeUnit.SECONDS), "socket should be closed by the server")
    }
}
