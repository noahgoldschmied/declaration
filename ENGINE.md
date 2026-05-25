# Declaration — Engine Semantics

Implementer-facing companion to [`RULES.md`](./RULES.md). This document specifies the game state, the action surface, validity, transitions, invariants, and the public/hidden event boundary. It is intended to map directly onto the `domain/` layer described in the architecture spec (`~/.claude/plans/2026-05-24-card-game-design.md`).

Whenever this document and `RULES.md` disagree, treat it as a bug in this document — `RULES.md` is the game's source of truth. File an issue and fix here.

---

## 1. State shape

The `GameState` is everything the engine needs to evaluate any action. It is fully server-side; clients only ever see a `PlayerView` produced by the `Redactor`.

```kotlin
// Identifiers
@JvmInline value class PlayerId(val value: String)         // opaque, stable per-game
@JvmInline value class TeamId(val value: String)           // "RED" | "BLUE"
@JvmInline value class CardId(val value: String)           // canonical: "2D", "TC", "JH", "AS", "8C", "JK1", "JK2"
@JvmInline value class DeckId(val value: String)           // "LOW_D", "HIGH_S", "EIGHTS_JOKERS", ...

// A single card knows which deck it belongs to.
data class Card(val id: CardId, val deck: DeckId)

// Static config: deck composition. Computed once at game start, never mutated.
data class DeckCatalog(val decksByCard: Map<CardId, DeckId>, val cardsByDeck: Map<DeckId, Set<CardId>>)

// Per-player state.
data class Player(
    val id: PlayerId,
    val team: TeamId,
    val seat: Int,                // 0..5; defines turn order
    val hand: Set<CardId>,        // hidden from other players; possibly empty
)

// Events emitted by a transition. These are TRANSIENT — they are not retained
// in GameState. Each is broadcast to all clients once, when the move happens,
// then forgotten by the server. Memory is the player's responsibility.
sealed class Event {
    data class Ask(val asker: PlayerId, val asked: PlayerId, val card: CardId, val outcome: AskOutcome) : Event()
    data class Declaration(
        val declarer: PlayerId,
        val deck: DeckId,
        val assignments: Map<CardId, PlayerId>,
        val correct: Boolean,
        val awardedTo: TeamId
    ) : Event()
}

enum class AskOutcome { HIT, MISS, SELF_OVERLAP }

// The world.
data class GameState(
    val players: List<Player>,                    // size 6, by seat order
    val turn: PlayerId,                           // whose turn it is
    val capturedDecks: Map<DeckId, TeamId>,       // decks that have been declared, with the team that captured them
    val phase: Phase,                             // PLAYING | ENDED
    val winner: TeamId?,                          // null until phase = ENDED
    val catalog: DeckCatalog,                     // immutable for the game
)

enum class Phase { PLAYING, ENDED }
```

**Why each field exists:**

- `players[].hand` is the only hidden field. The `Redactor` masks every other player's hand as a count (`HiddenHand(cardCount: Int)`).
- `capturedDecks` is the score state. `capturedDecks.count { it.value == TeamId("RED") }` is Red's deck total.
- `catalog` is included in `GameState` for serialization simplicity; in practice it could be a constant. It is not mutated.

**No `log` field.** The game's memory rule (see [`RULES.md`](./RULES.md) §5 "Memory only") means history is not part of state. `Event`s are produced by `Engine.apply` as part of `ActionResult.Ok(newState, events)` and broadcast immediately. The server retains them only long enough to send the WebSocket frame; the engine itself is amnesiac. A reconnecting player sees the current `GameState` only — no event history.

## 2. `Action` variants

```kotlin
sealed class Action {
    data class Ask(val target: PlayerId, val card: CardId) : Action()
    data class Declare(val deck: DeckId, val assignments: Map<CardId, PlayerId>) : Action()
}
```

`Ask` is restricted to the active player. `Declare` may be submitted by any of the 6 players at any time, regardless of whose turn it is or whether the declarer holds any cards.

There is no "pass" action — turns advance as a deterministic consequence of `Ask` outcomes.

## 3. Validity rules per action

The engine's `apply` function returns `ActionResult.Invalid(reason)` if any of these are violated. State is unchanged on rejection.

### `Ask(target, card)`

| Check | Reject reason |
|---|---|
| `actor == state.turn` | `"not your turn"` |
| `target` exists in `state.players` | `"unknown player"` |
| `target.team != actor.team` | `"cannot ask a teammate"` |
| `target.id != actor` | `"cannot ask yourself"` |
| `card` exists in `state.catalog` | `"unknown card"` |
| `state.players[actor].hand` contains at least one card from `catalog.decksByCard[card]` | `"you don't hold any card from that deck"` |
| `state.phase == PLAYING` | `"game has ended"` |
| `actor.hand.isNotEmpty()` | `"you have no cards"` |

Note: asking for a card you *do* hold (self-overlap) is **valid**. It just resolves to a specific outcome (see §4).

### `Declare(deck, assignments)`

| Check | Reject reason |
|---|---|
| `deck` exists in `state.catalog.cardsByDeck` | `"unknown deck"` |
| `assignments.keys == catalog.cardsByDeck[deck]` | `"assignments must name exactly the 6 cards of the deck"` |
| Every `playerId` in `assignments.values` is on the declarer's team | `"can only assign cards to teammates"` |
| `deck` is not already in `state.capturedDecks` | `"deck already captured"` |
| `state.phase == PLAYING` | `"game has ended"` |

The declarer does **not** need to be the active player. The declarer's `hand.isEmpty()` does **not** disqualify them (cardless players can still declare).

## 4. Transition rules per action

Transitions assume validity has passed. Each produces a new `GameState` and a list of `Event`s for broadcast to clients (transient — see §6).

### `Ask(target, card)`

Let `actor = state.turn`, `actualHolder = state.players.find { card in it.hand }`.

Three cases by outcome:

| Outcome | Condition | State changes | Next turn |
|---|---|---|---|
| `HIT` | `actualHolder == target` | Remove `card` from `target.hand`; add to `actor.hand`. | `actor` (keeps turn) |
| `MISS` | `actualHolder != target` AND `actualHolder != actor` | No card movement. | `target` |
| `SELF_OVERLAP` | `actualHolder == actor` | No card movement. The card's location is broadcast in the emitted `Event` for players to commit to memory. | `target` |

In all three cases, the transition emits one `Event.Ask` (with the outcome) in `ActionResult.events`. It is broadcast once and not retained.

If the new active player has `hand.isEmpty()`, advance the turn forward (by `seat` order) until a player with a non-empty hand is found. If no such player exists, no `Ask` will ever be issued again — the game proceeds only via `Declare` interrupts.

### `Declare(deck, assignments)`

Let `actor = the player who submitted the declaration`. Compute:

```
correct = assignments.all { (card, claimed) -> claimed == actualHolderOf(card) }
awardedTo = if (correct) actor.team else opposingTeam(actor.team)
```

State changes:

- `capturedDecks[deck] = awardedTo`
- Emit one `Event.Declaration(...)` in `ActionResult.events` with full assignments, correctness, and award. (Transient — broadcast once, not retained.)
- If `capturedDecks.count { it.value == awardedTo } >= 5`, set `phase = ENDED`, `winner = awardedTo`.

**Turn is not consumed by a declaration.** Whoever's turn it was before the declaration retains it (subject to the empty-hand skipping rule).

## 5. Invariants the engine must preserve

These hold at every moment and should be asserted in tests (and ideally in a runtime debug check):

1. **Card conservation.** `players.flatMap { it.hand }.toSet().size == 54 - cards_in_captured_decks`. No card is duplicated; no card disappears outside of being captured.
2. **Card uniqueness.** Every card is held by at most one player at any time.
3. **Deck completeness on capture.** Each entry in `capturedDecks` represents exactly 6 cards being moved out of player hands and into the captured pile. (Implementation note: captured cards are removed from `players[].hand` — they are no longer "in play".)
4. **Team membership stability.** A player's `team` never changes after deal.
5. **Turn legality.** `state.turn` always points to either a player with a non-empty hand, OR (if all players on both teams are cardless) to anyone — turn becomes a no-op in that state.
6. **Phase monotonicity.** Once `phase = ENDED`, no further action is valid.
7. **No history in state.** `GameState` contains no event log. Two states are observationally indistinguishable if their fields match, regardless of how they were reached. (Necessary for the memory rule — see [`RULES.md`](./RULES.md) §5 "Memory only".)

## 6. Public vs hidden — the `Redactor` boundary and the transient event channel

Two channels reach the client:

1. **`PlayerView`** — a redacted snapshot of `GameState`, sent on every transition and on reconnect. Reflects "what the world looks like right now."
2. **`events`** — the just-emitted `List<Event>` from `ActionResult.Ok`, sent once with the `PlayerView` and **never resent**. Reflects "what just happened."

The memory rule lives in the gap between these two channels: the view shows the present, the events flash by once, and nothing else is server-retained.

### Per-event broadcast contents (transient, broadcast once)

Every `Event` is sent verbatim to all 6 players. There is no per-player redaction of events — the information leak is the whole point.

| Event | What everyone learns (once) |
|---|---|
| `Event.Ask` (any outcome) | Asker, target, card asked, outcome |
| `Event.Ask` with `HIT` | Card moved from `target` to `asker` |
| `Event.Ask` with `MISS` | `target` does **not** hold that card. (Implicit deduction available to players: `asker` must hold ≥1 *other* card from that deck — this follows from the ask-validity rule, not a separately broadcast fact.) |
| `Event.Ask` with `SELF_OVERLAP` | `asker` holds the named card (publicly revealed) |
| `Event.Declaration` | Declarer, deck, full assignment map, correctness, awarded team |

Clients are expected to display each event briefly and then drop it from the UI when the next event arrives. No history pane.

### `PlayerView` shape (snapshot, sent on every update and on reconnect)

```kotlin
data class PlayerView(
    val you: SelfView,                  // your hand (visible to you)
    val opponents: List<OpponentView>,  // each: id, displayName, team, handSize
    val teammates: List<OpponentView>,  // same shape; teammates' specific cards are hidden too
    val turn: PlayerId,
    val phase: Phase,
    val winner: TeamId?,
    val capturedDecks: Map<DeckId, TeamId>,
    val catalog: DeckCatalog,
)
```

Notes:

- Teammates and opponents both expose only `handSize`, not specific cards. There is no information advantage given to teammates by the engine — they cannot see each other's hands.
- `capturedDecks` is fully public.
- **No event history** is ever included in a `PlayerView`. Reconnecting players see the current state only.

### What the `Redactor` does

```kotlin
object Redactor {
    fun viewFor(state: GameState, viewer: PlayerId): PlayerView
}
```

Implementation:

1. Locate `viewer` in `state.players`.
2. Build `SelfView` from the viewer's `hand` (visible).
3. For every other player, produce an `OpponentView` containing `handSize` only.
4. Copy `turn`, `phase`, `winner`, `capturedDecks`, `catalog` verbatim.

The `Redactor` does not touch events — events flow through `ActionResult.events`, not through `GameState`. This is the type-level enforcement of the memory rule: there is no path from `GameState` to event history because event history is not in `GameState`.

---

## Appendix: deterministic helpers for tests

- `Clock` and `Random` must be injected (see architecture spec §"Layer 1"). The dealer in setup uses the injected `Random`; tests pass seeded RNGs to assert against known deals.
- A useful test helper: `GameState.from(hands: Map<PlayerId, Set<CardId>>, turn: PlayerId)` — construct an arbitrary state directly, bypassing setup, so action tests don't have to play out a full deal.
