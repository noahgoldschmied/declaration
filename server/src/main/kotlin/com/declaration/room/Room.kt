package com.declaration.room

import com.declaration.domain.Action
import com.declaration.domain.ActionResult
import com.declaration.domain.Engine
import com.declaration.domain.Event
import com.declaration.domain.Phase
import com.declaration.domain.GameState
import com.declaration.domain.PlayerId
import com.declaration.domain.Redactor
import com.declaration.domain.Setup
import com.declaration.domain.TeamId
import com.declaration.protocol.PlayerInfo
import com.declaration.protocol.RoomPhase
import com.declaration.protocol.ServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration

/**
 * One game session. All state lives here and is mutated only by the single consumer
 * coroutine draining [inbox]. Public methods enqueue commands; they never touch state
 * directly, so there are no locks and no data races.
 */
class Room(
    val code: RoomCode,
    private val engine: Engine,
    private val random: Random,
    private val gracePeriod: Duration,
    private val scope: CoroutineScope,
) {
    private val inbox = Channel<RoomCommand>(Channel.BUFFERED)

    // --- mutable state, touched ONLY inside handle() ---
    private val sessions = LinkedHashMap<String, Session>() // token -> session, insertion-ordered
    private var hostToken: String? = null
    private var phase: RoomPhase = RoomPhase.LOBBY
    private var game: GameState? = null
    private var joinCount = 0

    private class Session(
        val playerId: PlayerId,
        val token: String,
        val displayName: String,
        var team: TeamId? = null,
        var sink: ClientSink? = null,
        var disconnectEpoch: Int = 0,
    )

    init {
        scope.launch {
            for (cmd in inbox) handle(cmd)
        }
    }

    // --- public API: enqueue only ---

    suspend fun submit(cmd: RoomCommand) {
        inbox.send(cmd)
        // Hand control to the consumer coroutine so the command is processed promptly.
        // Harmless in production (cooperative yield); required for the test scheduler to
        // drain the background consumer within a single advanceUntilIdle().
        kotlinx.coroutines.yield()
    }

    suspend fun join(displayName: String): JoinResult {
        val reply = CompletableDeferred<JoinResult>()
        submit(RoomCommand.Join(displayName, reply))
        return reply.await()
    }

    suspend fun connect(sessionToken: String, sink: ClientSink) =
        submit(RoomCommand.Connect(sessionToken, sink))

    suspend fun chooseTeam(sessionToken: String, team: TeamId) =
        submit(RoomCommand.ChooseTeam(sessionToken, team))

    suspend fun startGame(sessionToken: String) =
        submit(RoomCommand.StartGame(sessionToken))

    suspend fun submitAction(sessionToken: String, action: Action) =
        submit(RoomCommand.SubmitAction(sessionToken, action))

    suspend fun ping(sessionToken: String) = submit(RoomCommand.Ping(sessionToken))

    suspend fun disconnect(sessionToken: String) = submit(RoomCommand.Disconnect(sessionToken))

    // --- serial command handling ---

    private suspend fun handle(cmd: RoomCommand) {
        when (cmd) {
            is RoomCommand.Join -> handleJoin(cmd)
            is RoomCommand.Connect -> handleConnect(cmd)
            is RoomCommand.ChooseTeam -> handleChooseTeam(cmd)
            is RoomCommand.StartGame -> handleStartGame(cmd)
            is RoomCommand.SubmitAction -> handleSubmitAction(cmd)
            is RoomCommand.Ping -> {
                sessions[cmd.sessionToken]?.let { sendTo(it, ServerMessage.Pong) }
            }
            is RoomCommand.Disconnect -> handleDisconnect(cmd)
            is RoomCommand.CleanupDisconnect -> { /* implemented in Task 10 */ }
        }
    }

    private suspend fun handleJoin(cmd: RoomCommand.Join) {
        val index = joinCount++
        val token = Tokens.generate(random)
        val session = Session(
            playerId = PlayerId("p$index"),
            token = token,
            displayName = cmd.displayName,
        )
        sessions[token] = session
        if (hostToken == null) hostToken = token
        cmd.reply.complete(JoinResult(session.playerId, token))
        broadcastRoomState()
    }

    private suspend fun handleConnect(cmd: RoomCommand.Connect) {
        val session = sessions[cmd.sessionToken] ?: return
        session.sink = cmd.sink
        session.disconnectEpoch++ // invalidate any pending cleanup
        sendTo(session, ServerMessage.Welcome(session.playerId, session.token, session.displayName))
        sendTo(session, currentRoomState())
        val current = game
        if (current != null) {
            sendTo(session, ServerMessage.GameUpdate(Redactor.viewFor(current, session.playerId), emptyList()))
        }
        broadcastRoomState()
    }

    private suspend fun handleChooseTeam(cmd: RoomCommand.ChooseTeam) {
        val session = sessions[cmd.sessionToken] ?: return
        if (phase != RoomPhase.LOBBY) {
            sendTo(session, ServerMessage.ActionError("cannot change team after the game has started"))
            return
        }
        val onTeam = sessions.values.count { it !== session && it.team == cmd.team }
        if (onTeam >= 3) {
            sendTo(session, ServerMessage.ActionError("team ${cmd.team.value} is full"))
            return
        }
        session.team = cmd.team
        broadcastRoomState()
    }

    private suspend fun handleStartGame(cmd: RoomCommand.StartGame) {
        val session = sessions[cmd.sessionToken] ?: return
        if (cmd.sessionToken != hostToken) {
            sendTo(session, ServerMessage.ActionError("only the host can start the game"))
            return
        }
        if (phase != RoomPhase.LOBBY) {
            sendTo(session, ServerMessage.ActionError("game already started"))
            return
        }
        val seated = sessions.values.toList()
        val redCount = seated.count { it.team == com.declaration.domain.TEAM_RED }
        val blueCount = seated.count { it.team == com.declaration.domain.TEAM_BLUE }
        if (seated.size != 6 || redCount != 3 || blueCount != 3) {
            sendTo(session, ServerMessage.ActionError("need 6 players split 3-3 to start"))
            return
        }
        val seats = seated.map { it.playerId to it.team!! }
        game = Setup.newGame(seats, random)
        phase = RoomPhase.PLAYING
        broadcastGameUpdate(emptyList())
    }

    private suspend fun handleSubmitAction(cmd: RoomCommand.SubmitAction) {
        val session = sessions[cmd.sessionToken] ?: return
        val current = game
        if (phase != RoomPhase.PLAYING || current == null) {
            sendTo(session, ServerMessage.ActionError("game is not in progress"))
            return
        }
        when (val result = engine.apply(current, session.playerId, cmd.action)) {
            is ActionResult.Ok -> {
                game = result.newState
                if (result.newState.phase == Phase.ENDED) phase = RoomPhase.ENDED
                broadcastGameUpdate(result.events)
            }
            is ActionResult.Invalid -> {
                sendTo(session, ServerMessage.ActionError(result.reason))
            }
        }
    }

    private suspend fun handleDisconnect(cmd: RoomCommand.Disconnect) {
        val session = sessions[cmd.sessionToken] ?: return
        session.sink = null
        session.disconnectEpoch++
        val epoch = session.disconnectEpoch
        val token = session.token
        scope.launch {
            delay(gracePeriod)
            submit(RoomCommand.CleanupDisconnect(token, epoch))
        }
        broadcastRoomState()
    }

    // --- broadcast helpers ---

    private fun currentRoomState(): ServerMessage.RoomState =
        ServerMessage.RoomState(
            roomCode = code.value,
            phase = phase,
            hostId = sessions[hostToken]?.playerId ?: PlayerId("p0"),
            players = sessions.values.map {
                PlayerInfo(
                    playerId = it.playerId,
                    displayName = it.displayName,
                    team = it.team,
                    connected = it.sink != null,
                )
            },
        )

    private suspend fun broadcastRoomState() {
        val state = currentRoomState()
        sessions.values.forEach { it.sink?.send(state) }
    }

    private suspend fun broadcastGameUpdate(events: List<Event>) {
        val current = game ?: return
        sessions.values.forEach { session ->
            session.sink?.send(
                ServerMessage.GameUpdate(
                    view = Redactor.viewFor(current, session.playerId),
                    events = events,
                ),
            )
        }
    }

    private suspend fun sendTo(session: Session, message: ServerMessage) {
        session.sink?.send(message)
    }
}
