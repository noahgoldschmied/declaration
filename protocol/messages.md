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
| `AddBot` | `{ team: "RED" \| "BLUE", difficulty: BotDifficulty }` | Host only, lobby only. Seats a bot on `team` at `difficulty`. Rejected if the team or room is full. |
| `KickPlayer` | `{ playerId }` | Host only, lobby only. Removes a player — bot or human — freeing their seat. Rejected for the host's own `playerId`. |
| `RandomizeTeams` | `{}` | Host only, lobby only. Shuffles all seated players into a new random 3-3 (best-effort) split. |
| `SetMoveHistoryEnabled` | `{ enabled: boolean, visibleCount: number }` | Host only, lobby only. Fairness setting (same depth of recall for everyone, not a per-player preference): whether every client shows a running move-history sidebar this game, and how many recent moves it shows. Locked once `StartGame` fires. `visibleCount` is clamped server-side to 5–50 (default 10). |
| `SubmitAction` | `{ action: Action }` | In-game move. `Action` is the domain action union (`Ask` / `Declare`). |
| `SetDeclaring` | `{ declaring: boolean }` | In-game. Presence signal: the sender opened (`true`) or closed/cancelled (`false`) the declare panel. The server also auto-clears this on an actual `Declare` submission and on disconnect. |
| `Ping` | `{}` | Keepalive. Server replies `Pong`. |

`BotDifficulty` = `"EASY" | "MEDIUM" | "HARD" | "IMPOSSIBLE"` — a bot's forget rate for facts it deduces (`25% / 15% / 7.5% / 0%`; `IMPOSSIBLE` is the default when adding a bot — perfect, deterministic deduction).

## Server → Client (`ServerMessage`)

| `type` | Fields | When |
|---|---|---|
| `Welcome` | `{ playerId, sessionToken, displayName }` | Reply to `Hello`. Confirms identity. |
| `RoomState` | `{ roomCode, phase: "LOBBY" \| "PLAYING" \| "ENDED", hostId, players: PlayerInfo[], moveHistoryEnabled: boolean, moveHistoryVisibleCount: number }` | Roster / team / connection / phase changes. |
| `GameUpdate` | `{ view: PlayerView, events: Event[] }` | After every game state change. **`view` is redacted for the receiving player.** |
| `ActionError` | `{ reason: string }` | A submitted action/command was rejected. State unchanged. |
| `Kicked` | `{}` | Sent to a human player's own sink right before the host removes them. The client is expected to close its own socket and return to Landing (the server does not close it). |
| `DeclaringPlayers` | `{ playerIds: PlayerId[] }` | Broadcast whenever the set of players with the declare panel open changes. |
| `Pong` | `{}` | Keepalive reply. |

`PlayerInfo` = `{ playerId, displayName, team: "RED" | "BLUE" | null, connected: boolean, isBot: boolean, botDifficulty: BotDifficulty | null }`.

## Security invariant

The outbound channel is typed `ServerMessage`. No code path serializes a raw `GameState` to a client. The `Redactor` is the only producer of `PlayerView`. Enforced at the type level.

## Memory rule

`GameUpdate.events` is transient — broadcast once, never resent. A reconnecting player receives the current `view` (and `RoomState`) only, never event history. See `RULES.md` §5 and `ENGINE.md` §6.
