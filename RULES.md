# Declaration — Rules

A 6-player, team-based, hidden-information card game. Think Go Fish, played in teams, with deduction and a high-stakes "declare" mechanic that closes out each deck.

> Looking for the engine-facing version of these rules (state shape, action types, transition semantics)? See [`ENGINE.md`](./ENGINE.md).

---

## 1. Overview

Six players split into two teams of three. The 54-card deck is divided into **9 decks of 6 cards each**. The first team to capture **5 decks** wins.

You can't win a deck by accident. Capturing a deck requires a **declaration**: a player from your team correctly states which teammate is holding each of the 6 cards in that deck. Get any card wrong and the opposing team takes the deck instead.

Between declarations, players gather information by asking opponents for specific cards — Go Fish style. Every ask is public **at the moment it happens**, but only the most recent move is visible on the table. Everything before it must be held in your memory. Memory is the core skill of the game.

## 2. The 54-card Deck

The deck is a full 52-card pack plus 2 distinguishable jokers, partitioned into 9 decks of 6 cards:

| # | Deck | Cards |
|---|---|---|
| 1 | Low Spades | 2♠ 3♠ 4♠ 5♠ 6♠ 7♠ |
| 2 | Low Hearts | 2♥ 3♥ 4♥ 5♥ 6♥ 7♥ |
| 3 | Low Diamonds | 2♦ 3♦ 4♦ 5♦ 6♦ 7♦ |
| 4 | Low Clubs | 2♣ 3♣ 4♣ 5♣ 6♣ 7♣ |
| 5 | High Spades | 9♠ 10♠ J♠ Q♠ K♠ A♠ |
| 6 | High Hearts | 9♥ 10♥ J♥ Q♥ K♥ A♥ |
| 7 | High Diamonds | 9♦ 10♦ J♦ Q♦ K♦ A♦ |
| 8 | High Clubs | 9♣ 10♣ J♣ Q♣ K♣ A♣ |
| 9 | Eights & Jokers | 8♠ 8♥ 8♦ 8♣ Joker₁ Joker₂ |

The Eights & Jokers deck plays identically to every other deck. The two jokers are distinguishable (Joker₁ ≠ Joker₂) so asks always name a specific card.

## 3. Setup

1. Shuffle the 54-card deck.
2. Deal 9 cards to each of the 6 players.
3. Players are assigned to two teams of 3. (Teams are fixed for the duration of the game.)
4. Randomly choose the starting player. (There is no fixed "starting card" — a fixed start would unfairly advantage the holder.)

## 4. Turns at a glance

Two kinds of action exist:

- **Ask** — only the player whose turn it is can ask. On a hit, you keep the turn and ask again; on a miss, the turn passes (see §5).
- **Declare** — *any* player, on *any* turn, at any moment. Declarations are interrupts.

The active player must ask. The next section covers what happens.

## 5. Asking

On your turn, you ask one specific opponent for one specific card.

**The deck-membership constraint.** You can only ask for a card from a deck you currently hold at least one card from. If you have the 5♦, you can ask for any other card in Low Diamonds (2♦, 3♦, 4♦, 6♦, 7♦). You cannot ask for any card from a deck you have no cards in.

This constraint is the engine of the game's information leak: an ask publicly proves the asker holds at least one card from that deck. *"Noah asked for the 2♦"* tells the whole table that Noah holds one of {3♦, 4♦, 5♦, 6♦, 7♦}.

You can only ask **opponents**, never teammates.

### Memory only

Every ask, every hit, every miss, every self-overlap is broadcast publicly **the moment it happens**. The UI shows the most recent move and *only* the most recent move. As soon as the next move occurs, the previous one disappears from the table. Players must remember:

- Which cards have moved between which hands (each successful ask).
- Which cards a player provably *doesn't* hold (each miss).
- Which cards a player provably *does* hold (each self-overlap reveal).

No scrolling, no history pane, no notes (please). The deductive game lives entirely in players' heads.

Three outcomes are possible:

### Hit

The opponent has the card. They hand it over. You keep your turn and may ask any opponent again (same constraints).

### Miss

The opponent doesn't have the card. No card transfers. Your turn ends — the opponent you asked takes the next turn.

### Self-overlap (asking for a card you already hold)

You name a card that's actually in your own hand. This is mechanically legal — the deck-membership constraint is satisfied — but it's a self-inflicted mistake. The asked-for card is **publicly revealed as being in your hand**, and your turn ends. The opponent you asked takes the next turn.

(In effect: any unsuccessful ask passes the turn to the asked opponent. Self-overlap additionally reveals a specific card's location to everyone.)

## 6. Declaring

A declaration is how a deck gets captured. Any player may declare at any moment, including during another player's turn.

To declare a deck, you must name all 6 cards of that deck and assign each to a **specific teammate** (you may assign cards to yourself). Each of the 6 cards goes to exactly one teammate; a teammate may receive zero, one, or several assignments.

**If every assignment is correct** → your team captures the deck.

**If any assignment is wrong** (even one card on the wrong teammate, or one card actually held by an opponent) → the opposing team captures the deck.

Notes:

- You don't need to personally hold any cards in the deck to declare it. You can declare from observation alone.
- A deck whose cards are split across both teams cannot be correctly declared by either team until those cards consolidate onto one team (through asks). Until then, attempting a declaration will always go to the opposing team.
- A declaration is atomic. It happens, the deck is awarded, play resumes from whoever's turn it was before the declaration. If the declaration was an interrupt during someone's turn, that turn continues.

## 7. Edge cases

- **Empty hand.** If your hand becomes empty (every card you held has been asked away), your turn is permanently skipped for the rest of the game. You can still declare from the sidelines — and you should, since you've been watching the asks the whole time.
- **A whole team goes cardless.** If every player on a team has an empty hand, only the other team can ask (their asks will all miss, since there's no one with cards to find). The game continues via declarations until 5 decks are captured.
- **Asking for a card a deck shouldn't allow.** If you try to ask for a card from a deck you hold zero cards in, the ask is invalid — the engine rejects it. Your turn is not consumed; you simply cannot make that ask.
- **Self-overlap on a card you'd later forget about.** No special treatment. The reveal is public; everyone now knows that card's location.

## 8. Winning

The first team to capture **5 decks** wins. The game ends immediately on the 5th capture.

(With 9 decks total, a team needs only a majority. The final, 9th deck never matters once one team reaches 5.)

## 9. A worked turn

> Teams: **Red** = Alice, Charlie, Eve. **Blue** = Bob, Dan, Frank. It's Alice's turn.

1. **Alice asks Bob:** *"Do you have the 4♦?"* Alice holds the 6♦, so the Low Diamonds deck is fair game. Bob has the 4♦ — **hit**. He passes it to her.
2. **Alice asks Dan:** *"Do you have the 7♦?"* Dan doesn't have it — **miss**. Turn passes to Dan.
3. **Dan asks Eve:** *"Do you have the K♠?"* Dan holds the 10♠. Eve doesn't have the K♠ — **miss**. Turn passes to Eve.
4. **(Interrupt)** Charlie has been counting cards and announces a **declaration** for Low Diamonds: *"Alice has the 2♦, 4♦, and 6♦. I have the 3♦ and 5♦. Eve has the 7♦."* All correct → **Red captures Low Diamonds. Red: 1, Blue: 0.**
5. Play resumes with Eve's turn (the turn Charlie interrupted).

---

That's the whole game. Everything else is patience, memory, and the moment you decide a declaration is worth the risk.
