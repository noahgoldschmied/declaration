# Wire Protocol

**Status:** stub — to be filled in alongside milestone 5 (WebSocket handler).

This file is the **source of truth** for all messages exchanged between the Kotlin server and the React client. The server's `ClientMessage` / `ServerMessage` sealed classes and the web client's TypeScript discriminated unions are hand-mirrored from this document. Any change to a message updates **all three** (this file + Kotlin + TS) in the same PR.

## Transport

- WebSocket at `/ws/room/{code}?session={token}`
- JSON encoded
- Sealed-class polymorphism via a `type` discriminator field

## Client → Server

| Type | Fields | When |
|---|---|---|
| `Hello` | `{}` | First message after WS open. Session token is in the URL query string, not the body. |
| `Action` | `{ action: Action }` | Player submits an in-game action. `Action` is the discriminated union from `domain/`. |
| `Ping` | `{}` | Every 20 seconds. Server replies with `Pong`. |

## Server → Client

| Type | Fields | When |
|---|---|---|
| `Welcome` | `{ playerId, sessionToken, displayName }` | Reply to `Hello`. Confirms session, echoes identity. |
| `RoomState` | `{ players: PlayerInfo[], phase: 'WAITING' \| 'PLAYING' \| 'ENDED' }` | Player joins/leaves, or phase changes. |
| `GameUpdate` | `{ view: PlayerView, events: Event[] }` | After every state change. **`view` is redacted for this specific player.** |
| `ActionError` | `{ reason: string }` | A submitted action was rejected. Server state unchanged. |
| `Pong` | `{}` | Keepalive reply. |

## Security invariant

The outbound WS channel is typed as `ServerMessage`. No code path serializes a raw `GameState` to a client. The `Redactor` is the only producer of `PlayerView`. Enforced at the type level, not by convention.

---

Concrete JSON shapes for each message type will be filled in when the corresponding Kotlin/TS code is written.
