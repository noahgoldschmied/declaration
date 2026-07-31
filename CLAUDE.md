# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Status

This is the **Declaration card game monorepo** — a 6-player, team-based, hidden-hand card game with real-time multiplayer over WebSockets.

Canonical references (read before non-trivial work — the sections below are compressed pointers, not replacements):
- `~/.claude/plans/2026-05-24-card-game-design.md` — infrastructure/architecture spec
- `RULES.md` — the game rules (player-facing)
- `ENGINE.md` — engine semantics (state, actions, transitions, redaction boundary)
- `protocol/messages.md` — wire contract (source of truth for WS messages)

### Progress (milestones from the spec)

- ✅ **M1 — Bootstrap server.** Spring Boot app + `GET /healthz`.
- ✅ **M2 — Domain layer (`com.declaration.domain`).** Full pure rules engine: `GameState`, `Action` (`Ask`/`Declare`), `DeclarationEngine`, `Redactor`, `Setup`, `DeckCatalog`. ~70 unit tests, zero framework deps.
- ✅ **M3 — Room layer (`com.declaration.room`) + protocol (`com.declaration.protocol`).** One coroutine + `Channel<RoomCommand>` per `Room`, `RoomRegistry`, lobby→game lifecycle (host-start, team-pick), reconnect + disconnect-grace cleanup, `ClientMessage`/`ServerMessage` wire types. ~33 tests. Still framework-free (no Spring annotations yet).
- ✅ **M4 — REST + Spring wiring.** `POST /api/rooms` (201) + `POST /api/rooms/{code}/join` (200/404/409), bodies `{displayName}`. `config/RoomConfig` exposes `RoomRegistry`/`Engine`/`SecureRandom`-backed `Random`/grace `Duration`/app `CoroutineScope` (cancelled on shutdown) as beans. Controller bridges blocking MVC → suspend registry via `runBlocking`. `room/` + `domain/` stay Spring-free; only `config/` and `rest/` touch Spring.
- ✅ **M5 — WebSocket.** `ws/GameWebSocketHandler` at `/ws/room/{code}?session={token}` adapts `WebSocketSession`→`ClientSink`, decodes `ClientMessage`, routes to the room, broadcasts `ServerMessage`. Wire format is kotlinx.serialization JSON with a `type` discriminator (`protocol/WireJson`); domain + protocol types are `@Serializable`, `GameState` deliberately is not. The game is **playable end-to-end** (server side). Connect e.g. with `websocat 'ws://localhost:8080/ws/room/{CODE}?session={TOKEN}'`.
- ✅ **M6–8 — Web client (`web/`).** Vite + React 19 + TS + Tailwind 3 + Zustand. `protocol/messages.ts` + `protocol/deckCatalog.ts` hand-mirror the Kotlin wire types and `DeckCatalog`. `store/gameStore.ts` owns the WebSocket connection + all server-message handling. Landing (create/join, localStorage session for rejoin) → RoomLobby (roster, team-pick, host-gated start) → Table (hands, turn indicator, ask/declare panels, captured-deck scoreboard, transient event flash per the "memory only" rule, winner banner). Dev-time Vite proxy to the server (`BACKEND_PORT` env var to override the default 8080) so no server-side CORS config is needed. Verified end-to-end with a real 6-player game driven through the actual UI.
- ⬜ **M9 — Polish.** Reconnection UX, action timeouts, error toasts.

Each milestone is implemented on a `milestone-N-*` branch via TDD + subagent review, then merged `--no-ff` to `main` locally. Plans live in `docs/superpowers/plans/`.

## Layout

One git repo, independent subprojects (no Nx/Turborepo/Bazel):

```
server/   Kotlin + Spring Boot, Gradle    — domain/, room/, protocol/, config/, rest/, ws/ all done
web/      TypeScript + React + Vite       — not yet scaffolded
protocol/ messages.md                     — source-of-truth for WS contract
```

## Architecture invariants (these are load-bearing — do not violate)

These constraints come from the spec and must be preserved across all changes:

1. **`domain/` is pure.** Zero Spring imports, no application-context framework. The rules engine must run in a unit test with no application context. `Clock` and `Random` are **injected**, never called directly (no `Instant.now()`, no `Random.Default`) so tests are deterministic. **Exception (approved M5):** wire-facing domain types carry `@Serializable` (kotlinx.serialization) so they encode for the WS layer — this is a compile-time annotation, adds no context dependency, and domain still unit-tests with no Spring. `GameState` itself is deliberately **not** `@Serializable` (see invariant #2).

2. **`Redactor` is the only producer of `PlayerView`.** A `GameState` contains every player's hand. A `PlayerView` shows only the viewer's hand (`SelfView`); every other player (teammates included) appears as `OpponentView` exposing only `handSize`, never their cards. **No code path serializes a raw `GameState` to a client.** This is the security boundary that prevents hand leaks via DevTools. The outbound WS channel is typed `ServerMessage` to enforce this at the type level.

3. **One coroutine + `Channel<RoomCommand>` per room.** All mutations to a room's state go through the channel and are processed serially. No locks. The `Room` class has no public mutating methods besides `submit(cmd)`.

4. **Layers depend downward only:** `ws/` and `rest/` → `room/` → `domain/`. Never the reverse.

5. **`protocol/messages.md` is the wire contract.** Kotlin `ClientMessage`/`ServerMessage` sealed classes and TS discriminated unions are **hand-mirrored** from it. Any message change updates all three (spec + Kotlin + TS) in the same PR.

## Stack decisions (locked in 2026-05-24)

- **Server:** JDK 21 (LTS) + **Spring Boot 3.5.14** + Kotlin 2.1.0, built with Gradle 8.14 (Kotlin DSL). Root package `com.declaration`.
  - Note: Boot 4.0.x was the initial pick but Initializr is currently 500-ing on Kotlin generation and Boot 4 has reshuffled test slice packages (`AutoConfigureMockMvc` etc.). 3.5.14 is the last 3.x line and works cleanly. Upgrade later if there's a reason.
- **Web:** pnpm + Vite + React + TypeScript + Tailwind 3 + Zustand. Scaffolded with React 19 (the current Vite template default) rather than React 18 — nothing in the app depends on the 18-vs-19 distinction, and pinning back would fight the tooling for no benefit.
- **No root build orchestrator.** Each subproject builds independently; run two terminals during dev.

## Dev commands

Server (`cd server` first):

| Task | Command |
|---|---|
| Run the server | `./gradlew bootRun` (binds `:8080`) |
| Health check | `curl http://localhost:8080/healthz` → `ok` |
| Run all tests | `./gradlew test` |
| Run one test class | `./gradlew test --tests com.declaration.rest.HealthControllerTest` |
| Run one test method | `./gradlew test --tests 'com.declaration.rest.HealthControllerTest.healthz returns 200 ok'` |
| Clean build | `./gradlew clean build` |

Web (`cd web` first):

| Task | Command |
|---|---|
| Install deps | `pnpm install` |
| Run the dev server | `pnpm dev` (binds `:5173`, proxies `/api` and `/ws` to the server on `:8080`) |
| Run against a non-default server port | `BACKEND_PORT=8081 pnpm dev` |
| Typecheck | `npx tsc -b` |
| Production build | `pnpm build` |

## Out of scope for v1 (don't add unprompted)

Per the spec: accounts/login, persistence, matchmaking, spectators, in-game chat, mobile-first UI, multi-instance scaling, replay storage. The architecture is designed so these are additive later — keep them out for now.

## Working in this repo

- Before scaffolding new modules, re-read the spec — the layout, package names (`com.yourgame.*`), and module boundaries are prescribed.
- The placeholder rules engine (milestone 2 in the spec) is intentional. Don't try to implement the real Declaration rules until that design cycle happens.
- When in doubt about a tradeoff already considered in the spec (room code format, action timeouts, specific rules), check the **Open questions** section at the bottom of the spec before deciding.