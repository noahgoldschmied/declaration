# Wire Protocol

Source of truth for all messages exchanged between the Kotlin server and the React client. The Kotlin sealed classes in `server/.../com/declaration/protocol/` and the TypeScript discriminated unions in `web/` are hand-mirrored from this document. Any change to a message updates **all** mirrors in the same PR.

## Transport

- WebSocket at `/ws/room/{code}?session={token}`
- JSON encoded; sealed-class polymorphism via a `type` discriminator field
- The session token is in the URL query string, never in a message body

## Client → Server (`ClientMessage`)

| `type` | Fields | When |
|---|---|---|
| `Hello` | `{}` | First message after the socket opens. Server replies `Welcome`. |
| `ChooseTeam` | `{ team: "RED" \| "BLUE" }` | Lobby only. Pick or switch team. Rejected if the team already has 3 players. |
| `StartGame` | `{}` | Host only, lobby only. Begins the game. Requires 6 players split 3-3. |
| `SubmitAction` | `{ action: Action }` | In-game move. `Action` is the domain action union (`Ask` / `Declare`). |
| `Ping` | `{}` | Keepalive. Server replies `Pong`. |

## Server → Client (`ServerMessage`)

| `type` | Fields | When |
|---|---|---|
| `Welcome` | `{ playerId, sessionToken, displayName }` | Reply to `Hello`. Confirms identity. |
| `RoomState` | `{ roomCode, phase: "LOBBY" \| "PLAYING" \| "ENDED", hostId, players: PlayerInfo[] }` | Roster / team / connection / phase changes. |
| `GameUpdate` | `{ view: PlayerView, events: Event[] }` | After every game state change. **`view` is redacted for the receiving player.** |
| `ActionError` | `{ reason: string }` | A submitted action/command was rejected. State unchanged. |
| `Pong` | `{}` | Keepalive reply. |

`PlayerInfo` = `{ playerId, displayName, team: "RED" | "BLUE" | null, connected: boolean }`.

## Security invariant

The outbound channel is typed `ServerMessage`. No code path serializes a raw `GameState` to a client. The `Redactor` is the only producer of `PlayerView`. Enforced at the type level.

## Memory rule

`GameUpdate.events` is transient — broadcast once, never resent. A reconnecting player receives the current `view` (and `RoomState`) only, never event history. See `RULES.md` §5 and `ENGINE.md` §6.
