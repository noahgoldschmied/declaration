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
import com.declaration.protocol.BotDifficulty
import com.declaration.protocol.MoveHistoryLimits
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
    private val onEmpty: () -> Unit = {},
) {
    private val inbox = Channel<RoomCommand>(Channel.BUFFERED)

    // --- mutable state, touched ONLY inside handle() ---
    private val sessions = LinkedHashMap<String, Session>() // token -> session, insertion-ordered
    private var hostToken: String? = null
    private var phase: RoomPhase = RoomPhase.LOBBY
    private var game: GameState? = null
    private var joinCount = 0
    private var moveHistoryEnabled: Boolean = false
    private var moveHistoryVisibleCount: Int = MoveHistoryLimits.DEFAULT_VISIBLE_COUNT

    private class Session(
        val playerId: PlayerId,
        val token: String,
        val displayName: String,
        var team: TeamId? = null,
        var sink: ClientSink? = null,
        var disconnectEpoch: Int = 0,
        val isBot: Boolean = false,
        val botDifficulty: BotDifficulty? = null,
        var isDeclaring: Boolean = false,
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

    /**
     * Seats a bot. Returns null (and sends the host an [ServerMessage.ActionError]) if the
     * host/phase/capacity/team checks fail; the caller is expected to have already built
     * [sink] and to wire the returned token into it afterward (the token doesn't exist until
     * this call assigns one). See [RoomCommand.AddBot] for how [candidateNames] resolves to a
     * name that doesn't collide with any currently seated player.
     */
    suspend fun addBot(
        hostSessionToken: String,
        candidateNames: List<String>,
        team: TeamId,
        difficulty: BotDifficulty,
        sink: ClientSink,
    ): JoinResult? {
        val reply = CompletableDeferred<JoinResult?>()
        submit(RoomCommand.AddBot(hostSessionToken, candidateNames, team, difficulty, sink, reply))
        return reply.await()
    }

    suspend fun kickPlayer(hostSessionToken: String, targetPlayerId: PlayerId) =
        submit(RoomCommand.KickPlayer(hostSessionToken, targetPlayerId))

    suspend fun randomizeTeams(hostSessionToken: String) =
        submit(RoomCommand.RandomizeTeams(hostSessionToken))

    suspend fun setMoveHistoryEnabled(hostSessionToken: String, enabled: Boolean, visibleCount: Int) =
        submit(RoomCommand.SetMoveHistoryEnabled(hostSessionToken, enabled, visibleCount))

    suspend fun submitAction(sessionToken: String, action: Action) =
        submit(RoomCommand.SubmitAction(sessionToken, action))

    suspend fun setDeclaring(sessionToken: String, declaring: Boolean) =
        submit(RoomCommand.SetDeclaring(sessionToken, declaring))

    suspend fun ping(sessionToken: String) = submit(RoomCommand.Ping(sessionToken))

    suspend fun disconnect(sessionToken: String) = submit(RoomCommand.Disconnect(sessionToken))

    // --- serial command handling ---

    private suspend fun handle(cmd: RoomCommand) {
        when (cmd) {
            is RoomCommand.Join -> handleJoin(cmd)
            is RoomCommand.Connect -> handleConnect(cmd)
            is RoomCommand.ChooseTeam -> handleChooseTeam(cmd)
            is RoomCommand.StartGame -> handleStartGame(cmd)
            is RoomCommand.AddBot -> handleAddBot(cmd)
            is RoomCommand.KickPlayer -> handleKickPlayer(cmd)
            is RoomCommand.RandomizeTeams -> handleRandomizeTeams(cmd)
            is RoomCommand.SetMoveHistoryEnabled -> handleSetMoveHistoryEnabled(cmd)
            is RoomCommand.SubmitAction -> handleSubmitAction(cmd)
            is RoomCommand.SetDeclaring -> handleSetDeclaring(cmd)
            is RoomCommand.Ping -> {
                sessions[cmd.sessionToken]?.let { sendTo(it, ServerMessage.Pong) }
            }
            is RoomCommand.Disconnect -> handleDisconnect(cmd)
            is RoomCommand.CleanupDisconnect -> handleCleanupDisconnect(cmd)
        }
    }

    private suspend fun handleJoin(cmd: RoomCommand.Join) {
        if (phase != RoomPhase.LOBBY) {
            // No *new* players once the game has started — but if this display name
            // belongs to a currently-disconnected existing player (they lost their
            // session token: cleared storage, new device, different browser), let
            // them straight back in as that same player rather than locking them
            // out for the rest of the game. Only matches disconnected sessions, so
            // it can't hijack someone who's still actively connected.
            val existing = sessions.values.firstOrNull { it.displayName == cmd.displayName && it.sink == null }
            if (existing != null) {
                cmd.reply.complete(JoinResult(existing.playerId, existing.token))
                return
            }
            cmd.reply.completeExceptionally(RoomJoinException("game already started"))
            return
        }
        if (sessions.size >= 6) {
            cmd.reply.completeExceptionally(RoomJoinException("room is full"))
            return
        }
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
        // Start the same grace clock a disconnect would — a REST join only issues
        // a token, it doesn't itself prove a client ever showed up to use it (the
        // WS connect is a separate follow-up request that might never happen: an
        // abandoned tab, a network failure, a stray test script). handleConnect()
        // bumps disconnectEpoch on a real connect, invalidating this exactly like
        // a genuine reconnect would — so a session that *does* connect is
        // unaffected, and one that never does gets swept like any other.
        scheduleCleanup(session)
        broadcastRoomState()
    }

    private suspend fun handleConnect(cmd: RoomCommand.Connect) {
        val session = sessions[cmd.sessionToken]
        if (session == null) {
            // Unknown token: either the grace-period cleanup already pruned this
            // session, or (after a server restart) the room itself is a fresh,
            // empty in-memory instance that never saw this token. Say so
            // explicitly rather than leaving the socket open with nothing ever
            // arriving — the client can't otherwise tell "stale session" apart
            // from "still connecting".
            cmd.sink.send(ServerMessage.ActionError("session not found — it may have expired or the room restarted"))
            return
        }
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

    private suspend fun handleAddBot(cmd: RoomCommand.AddBot) {
        val hostSession = sessions[cmd.hostSessionToken]
        if (hostSession == null || cmd.hostSessionToken != hostToken) {
            hostSession?.let { sendTo(it, ServerMessage.ActionError("only the host can add a bot")) }
            cmd.reply.complete(null)
            return
        }
        if (phase != RoomPhase.LOBBY) {
            sendTo(hostSession, ServerMessage.ActionError("cannot add a bot after the game has started"))
            cmd.reply.complete(null)
            return
        }
        if (sessions.size >= 6) {
            sendTo(hostSession, ServerMessage.ActionError("room is full"))
            cmd.reply.complete(null)
            return
        }
        val onTeam = sessions.values.count { it.team == cmd.team }
        if (onTeam >= 3) {
            sendTo(hostSession, ServerMessage.ActionError("team ${cmd.team.value} is full"))
            cmd.reply.complete(null)
            return
        }
        val takenNames = sessions.values.mapTo(HashSet()) { it.displayName }
        val name = cmd.candidateNames.firstOrNull { it !in takenNames }
            ?: "${cmd.candidateNames.first()} ${sessions.size + 1}"
        val index = joinCount++
        val token = Tokens.generate(random)
        val session = Session(
            playerId = PlayerId("p$index"),
            token = token,
            displayName = name,
            team = cmd.team,
            sink = cmd.sink,
            isBot = true,
            botDifficulty = cmd.difficulty,
        )
        sessions[token] = session
        cmd.reply.complete(JoinResult(session.playerId, token))
        broadcastRoomState()
    }

    private suspend fun handleKickPlayer(cmd: RoomCommand.KickPlayer) {
        val hostSession = sessions[cmd.sessionToken] ?: return
        if (cmd.sessionToken != hostToken) {
            sendTo(hostSession, ServerMessage.ActionError("only the host can remove a player"))
            return
        }
        if (phase != RoomPhase.LOBBY) {
            sendTo(hostSession, ServerMessage.ActionError("cannot remove a player after the game has started"))
            return
        }
        if (cmd.targetPlayerId == hostSession.playerId) {
            sendTo(hostSession, ServerMessage.ActionError("cannot remove yourself"))
            return
        }
        val target = sessions.values.firstOrNull { it.playerId == cmd.targetPlayerId }
        if (target == null) {
            sendTo(hostSession, ServerMessage.ActionError("player not found"))
            return
        }
        sendTo(target, ServerMessage.Kicked)
        sessions.remove(target.token)
        broadcastRoomState()
    }

    private suspend fun handleRandomizeTeams(cmd: RoomCommand.RandomizeTeams) {
        val hostSession = sessions[cmd.sessionToken] ?: return
        if (cmd.sessionToken != hostToken) {
            sendTo(hostSession, ServerMessage.ActionError("only the host can randomize teams"))
            return
        }
        if (phase != RoomPhase.LOBBY) {
            sendTo(hostSession, ServerMessage.ActionError("cannot randomize teams after the game has started"))
            return
        }
        val shuffled = sessions.values.shuffled(random)
        shuffled.forEachIndexed { i, session ->
            session.team = if (i % 2 == 0) com.declaration.domain.TEAM_RED else com.declaration.domain.TEAM_BLUE
        }
        broadcastRoomState()
    }

    private suspend fun handleSetMoveHistoryEnabled(cmd: RoomCommand.SetMoveHistoryEnabled) {
        val hostSession = sessions[cmd.sessionToken] ?: return
        if (cmd.sessionToken != hostToken) {
            sendTo(hostSession, ServerMessage.ActionError("only the host can change the move history setting"))
            return
        }
        if (phase != RoomPhase.LOBBY) {
            sendTo(hostSession, ServerMessage.ActionError("move history is locked once the game has started"))
            return
        }
        moveHistoryEnabled = cmd.enabled
        moveHistoryVisibleCount = cmd.visibleCount.coerceIn(MoveHistoryLimits.MIN_VISIBLE_COUNT, MoveHistoryLimits.MAX_VISIBLE_COUNT)
        broadcastRoomState()
    }

    private suspend fun handleSubmitAction(cmd: RoomCommand.SubmitAction) {
        val session = sessions[cmd.sessionToken] ?: return
        val current = game
        if (phase != RoomPhase.PLAYING || current == null) {
            sendTo(session, ServerMessage.ActionError("game is not in progress"))
            return
        }
        val declarer = sessions.values.firstOrNull { it.isDeclaring && it !== session }
        if (declarer != null) {
            sendTo(session, ServerMessage.ActionError("${declarer.displayName} is declaring — the game is paused"))
            return
        }
        when (val result = engine.apply(current, session.playerId, cmd.action)) {
            is ActionResult.Ok -> {
                game = result.newState
                if (result.newState.phase == Phase.ENDED) phase = RoomPhase.ENDED
                // A submitted declaration ends that player's "declaring" episode either way
                // (correct or not) -- clear it here rather than relying on the client to also
                // send SetDeclaring(false), so a dropped message can't leave a stale banner.
                if (cmd.action is Action.Declare && session.isDeclaring) {
                    session.isDeclaring = false
                    broadcastDeclaringPlayers()
                }
                broadcastGameUpdate(result.events)
            }
            is ActionResult.Invalid -> {
                sendTo(session, ServerMessage.ActionError(result.reason))
            }
        }
    }

    private suspend fun handleSetDeclaring(cmd: RoomCommand.SetDeclaring) {
        val session = sessions[cmd.sessionToken] ?: return
        if (session.isDeclaring == cmd.declaring) return
        if (cmd.declaring) {
            val declarer = sessions.values.firstOrNull { it.isDeclaring && it !== session }
            if (declarer != null) {
                sendTo(session, ServerMessage.ActionError("${declarer.displayName} is already declaring — wait for them to finish"))
                return
            }
        }
        session.isDeclaring = cmd.declaring
        broadcastDeclaringPlayers()
    }

    private suspend fun broadcastDeclaringPlayers() {
        val declaring = sessions.values.filter { it.isDeclaring }.map { it.playerId }.toSet()
        val message = ServerMessage.DeclaringPlayers(declaring)
        sessions.values.forEach { it.sink?.send(message) }
    }

    private suspend fun handleDisconnect(cmd: RoomCommand.Disconnect) {
        val session = sessions[cmd.sessionToken] ?: return
        session.sink = null
        val wasDeclaring = session.isDeclaring
        session.isDeclaring = false
        scheduleCleanup(session)
        broadcastRoomState()
        if (wasDeclaring) broadcastDeclaringPlayers()
    }

    private fun scheduleCleanup(session: Session) {
        session.disconnectEpoch++
        val epoch = session.disconnectEpoch
        val token = session.token
        scope.launch {
            delay(gracePeriod)
            submit(RoomCommand.CleanupDisconnect(token, epoch))
        }
    }

    private suspend fun handleCleanupDisconnect(cmd: RoomCommand.CleanupDisconnect) {
        val session = sessions[cmd.sessionToken] ?: return
        // Only remove if still disconnected AND no reconnect happened since this timer was set.
        if (session.sink == null && session.disconnectEpoch == cmd.epoch) {
            sessions.remove(cmd.sessionToken)
            if (cmd.sessionToken == hostToken) {
                // Host left for good: hand off to the next remaining human (insertion order) —
                // a bot can't usefully be host — falling back to a bot only if no humans remain
                // (which triggers the all-bot cleanup below anyway).
                hostToken = sessions.keys.firstOrNull { !sessions.getValue(it).isBot } ?: sessions.keys.firstOrNull()
            }
            // Bots never disconnect on their own (no real socket), so "sessions.isEmpty()" alone
            // would never be true again once a bot-holding room's last human leaves — a silent
            // leak. Treat "no humans left" as empty instead, and drop any leftover bot sessions
            // too so nothing lingers referencing a dead room. We deliberately don't try to tear
            // down this room's own coroutine/channel here; it's cheap to leave idling and doing
            // so safely (without racing a stray in-flight submit()) isn't worth the complexity
            // at this project's scale.
            if (sessions.values.all { it.isBot }) {
                sessions.clear()
                onEmpty()
            }
            broadcastRoomState()
        }
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
                    isBot = it.isBot,
                    botDifficulty = it.botDifficulty,
                )
            },
            moveHistoryEnabled = moveHistoryEnabled,
            moveHistoryVisibleCount = moveHistoryVisibleCount,
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
