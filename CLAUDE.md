# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Status

This is the **Declaration card game monorepo** — a 6-player, turn-based, hidden-hand card game with real-time multiplayer over WebSockets.

The repo is **pre-scaffolding**: only `README.md`, `.gitignore`, and this file exist. The full design is approved and lives at:

- `~/.claude/plans/2026-05-24-card-game-design.md` (canonical spec)

Read the spec before doing any non-trivial work — the section below is a compressed pointer, not a replacement.

## Planned layout

Two top-level projects in one git repo (no Nx/Turborepo/Bazel — each builds independently):

```
server/   Kotlin + Spring Boot, Gradle    — game logic, WS, REST
web/      TypeScript + React + Vite       — browser client
protocol/ messages.md                     — source-of-truth for WS contract
```

## Architecture invariants (these are load-bearing — do not violate)

These constraints come from the spec and must be preserved across all changes:

1. **`domain/` is pure.** Zero Spring imports, zero framework imports. The rules engine must run in a unit test with no application context. `Clock` and `Random` are **injected**, never called directly (no `Instant.now()`, no `Random.Default`) so tests are deterministic.

2. **`Redactor` is the only producer of `PlayerView`.** A `GameState` contains every player's hand. A `PlayerView` shows only the viewer's hand; opponents appear as `HiddenHand(cardCount)`. **No code path serializes a raw `GameState` to a client.** This is the security boundary that prevents hand leaks via DevTools. The outbound WS channel is typed `ServerMessage` to enforce this at the type level.

3. **One coroutine + `Channel<RoomCommand>` per room.** All mutations to a room's state go through the channel and are processed serially. No locks. The `Room` class has no public mutating methods besides `submit(cmd)`.

4. **Layers depend downward only:** `ws/` and `rest/` → `room/` → `domain/`. Never the reverse.

5. **`protocol/messages.md` is the wire contract.** Kotlin `ClientMessage`/`ServerMessage` sealed classes and TS discriminated unions are **hand-mirrored** from it. Any message change updates all three (spec + Kotlin + TS) in the same PR.

## Stack decisions (locked in 2026-05-24)

- **Server:** JDK 21 (LTS) + **Spring Boot 3.5.14** + Kotlin 2.1.0, built with Gradle 8.14 (Kotlin DSL). Root package `com.declaration`.
  - Note: Boot 4.0.x was the initial pick but Initializr is currently 500-ing on Kotlin generation and Boot 4 has reshuffled test slice packages (`AutoConfigureMockMvc` etc.). 3.5.14 is the last 3.x line and works cleanly. Upgrade later if there's a reason.
- **Web:** pnpm + Vite + React 18 + TypeScript + Tailwind 3 + Zustand (not yet scaffolded).
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

Web: not scaffolded yet.

## Out of scope for v1 (don't add unprompted)

Per the spec: accounts/login, persistence, matchmaking, spectators, in-game chat, mobile-first UI, multi-instance scaling, replay storage. The architecture is designed so these are additive later — keep them out for now.

## Working in this repo

- Before scaffolding new modules, re-read the spec — the layout, package names (`com.yourgame.*`), and module boundaries are prescribed.
- The placeholder rules engine (milestone 2 in the spec) is intentional. Don't try to implement the real Declaration rules until that design cycle happens.
- When in doubt about a tradeoff already considered in the spec (room code format, action timeouts, specific rules), check the **Open questions** section at the bottom of the spec before deciding.