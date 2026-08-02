package com.declaration.bot

import com.declaration.domain.Action
import com.declaration.domain.CardId
import com.declaration.domain.DeckId
import com.declaration.domain.DeclarationEngine
import com.declaration.domain.Engine
import com.declaration.domain.GameStates
import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import com.declaration.domain.Redactor
import com.declaration.domain.TEAM_BLUE
import com.declaration.domain.TEAM_RED
import com.declaration.protocol.BotDifficulty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotBrainTest {

    private val engine: Engine = DeclarationEngine()

    @Test
    fun `declares a deck it trivially holds all 6 cards of, with no events at all`() {
        val state = GameStates.of(
            hands = listOf(
                Triple(ALICE, TEAM_RED, setOf("2S", "3S", "4S", "5S", "6S", "7S").map { CardId(it) }.toSet()),
                Triple(BOB, TEAM_BLUE, emptySet()),
                Triple(CHARLIE, TEAM_RED, emptySet()),
                Triple(DAN, TEAM_BLUE, emptySet()),
                Triple(EVE, TEAM_RED, emptySet()),
                Triple(FRANK, TEAM_BLUE, emptySet()),
            ),
            turn = ALICE,
        )
        val view = Redactor.viewFor(state, ALICE)
        val brain = BotBrain(Random(1), BotDifficulty.IMPOSSIBLE)

        val action = brain.observe(view, emptyList())

        assertEquals(
            Action.Declare(DeckId("LOW_S"), setOf("2S", "3S", "4S", "5S", "6S", "7S").associate { CardId(it) to ALICE }),
            action,
        )
    }

    @Test
    fun `elimination-by-exhaustion deduces an opponent's card from two unrelated MISSes`() {
        // HIGH_D = {9D, TD, JD, QD, KD, AD}. FRANK secretly holds QD; ALICE holds none of that
        // deck (so she's auto-excluded), and two MISSes rule out the other four non-holders,
        // leaving FRANK as the only possible holder by elimination alone.
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf(CardId("AD"))),
            Triple(BOB, TEAM_BLUE, setOf(CardId("9D"))),
            Triple(CHARLIE, TEAM_RED, emptySet()),
            Triple(DAN, TEAM_BLUE, setOf(CardId("TD"))),
            Triple(EVE, TEAM_RED, emptySet()),
            Triple(FRANK, TEAM_BLUE, setOf(CardId("QD"))),
        )
        val state0 = GameStates.of(hands, turn = BOB)

        val r1 = engine.apply(state0, BOB, Action.Ask(CHARLIE, CardId("QD"))) as ActionResultOk
        val r2 = engine.apply(r1.state, DAN, Action.Ask(EVE, CardId("QD"))) as ActionResultOk

        val brain = BotBrain(Random(1), BotDifficulty.IMPOSSIBLE)
        brain.observe(Redactor.viewFor(r1.state, ALICE), r1.events)
        val view2 = Redactor.viewFor(r2.state, ALICE)
        brain.observe(view2, r2.events)

        val action = brain.observe(view2.copy(turn = ALICE), emptyList())

        assertEquals(Action.Ask(FRANK, CardId("QD")), action)
    }

    @Test
    fun `ask-reveals-membership collapses to a known holder combined with the bot's own hand`() {
        // BOB asks for TD (which nobody holds) and MISSes -- this reveals BOB holds >=1 of
        // HIGH_D's other 5 cards. ALICE already knows 4 of those 5 are in her own hand, so the
        // constraint collapses to the 5th: BOB must hold QD, without anyone ever asking about it.
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf("9D", "JD", "KD", "AD").map { CardId(it) }.toSet()),
            Triple(BOB, TEAM_BLUE, setOf(CardId("QD"))),
            Triple(CHARLIE, TEAM_RED, emptySet()),
            Triple(DAN, TEAM_BLUE, emptySet()),
            Triple(EVE, TEAM_RED, emptySet()),
            Triple(FRANK, TEAM_BLUE, emptySet()),
        )
        val state0 = GameStates.of(hands, turn = BOB)
        val r1 = engine.apply(state0, BOB, Action.Ask(CHARLIE, CardId("TD"))) as ActionResultOk

        val brain = BotBrain(Random(1), BotDifficulty.IMPOSSIBLE)
        val view1 = Redactor.viewFor(r1.state, ALICE)
        brain.observe(view1, r1.events)

        val action = brain.observe(view1.copy(turn = ALICE), emptyList())

        assertEquals(Action.Ask(BOB, CardId("QD")), action)
    }

    @Test
    fun `a MISS clears a stale knownHolder belief instead of repeating the disproven ask forever`() {
        // Regression test for a real observed bug: two bots stuck asking each other the same
        // already-disproven card every single turn, forever. Root cause: DAN asks EVE for JD and
        // HITs, so ALICE's bot learns knownHolder[JD] = DAN. JD then moves DAN -> CHARLIE via an
        // ask ALICE's bot never observes (simulating a forgotten event, or any other gap) -- her
        // belief is now stale. Later DAN himself asks about JD again and MISSes (proof he doesn't
        // hold it). Before the fix, nothing ever cleared the stale knownHolder entry, so
        // chooseAsk's opportunistic loop would trust it forever and deterministically re-ask
        // Ask(DAN, JD) on every single turn, no matter how many times it MISSed.
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf(CardId("AD"))),
            Triple(BOB, TEAM_BLUE, setOf(CardId("TD"))),
            Triple(CHARLIE, TEAM_RED, setOf(CardId("QD"))),
            Triple(DAN, TEAM_BLUE, setOf(CardId("9D"))),
            Triple(EVE, TEAM_RED, setOf(CardId("JD"))),
            Triple(FRANK, TEAM_BLUE, setOf(CardId("KD"))),
        )
        val state0 = GameStates.of(hands, turn = DAN)

        val r1 = engine.apply(state0, DAN, Action.Ask(EVE, CardId("JD"))) as ActionResultOk // HIT: DAN gains JD
        val brain = BotBrain(Random(1), BotDifficulty.IMPOSSIBLE)
        brain.observe(Redactor.viewFor(r1.state, ALICE), r1.events) // plants knownHolder[JD] = DAN

        // Everything below happens WITHOUT ever being fed to `brain` -- simulating events it
        // missed -- until the final MISS, so ALICE's belief stays frozen at "DAN holds JD".
        val r2 = engine.apply(r1.state, DAN, Action.Ask(CHARLIE, CardId("KD"))) as ActionResultOk // MISS, passes turn to CHARLIE
        val r3 = engine.apply(r2.state, CHARLIE, Action.Ask(DAN, CardId("JD"))) as ActionResultOk // HIT: CHARLIE takes JD from DAN
        val r4 = engine.apply(r3.state, CHARLIE, Action.Ask(DAN, CardId("TD"))) as ActionResultOk // MISS, passes turn to DAN
        val r5 = engine.apply(r4.state, DAN, Action.Ask(EVE, CardId("JD"))) as ActionResultOk // MISS: DAN no longer holds JD

        brain.observe(Redactor.viewFor(r5.state, ALICE), r5.events)
        val action = brain.observe(Redactor.viewFor(r5.state, ALICE).copy(turn = ALICE), emptyList())

        assertTrue(
            action != Action.Ask(DAN, CardId("JD")),
            "belief should have self-corrected on the disproving MISS, not repeated the same disproven ask: $action",
        )
    }

    @Test
    fun `guessing prefers to stay in the deck of the bot's own last ask`() {
        // LOW_S and LOW_H are both askable (ALICE holds a card in each). ALICE's own last ask
        // (BOB, 3S) was a MISS, revealing nothing else -- chooseAsk should keep guessing within
        // LOW_S (the deck she's already "going down") rather than switching to LOW_H, even though
        // both are equally valid options.
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf("2S", "2H").map { CardId(it) }.toSet()),
            Triple(BOB, TEAM_BLUE, emptySet()),
            Triple(CHARLIE, TEAM_RED, emptySet()),
            Triple(DAN, TEAM_BLUE, emptySet()),
            Triple(EVE, TEAM_RED, emptySet()),
            Triple(FRANK, TEAM_BLUE, emptySet()),
        )
        val state0 = GameStates.of(hands, turn = ALICE)
        val r1 = engine.apply(state0, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResultOk // MISS, passes turn to BOB

        val brain = BotBrain(Random(7), BotDifficulty.IMPOSSIBLE)
        brain.observe(Redactor.viewFor(r1.state, ALICE), r1.events)

        val action = brain.observe(Redactor.viewFor(r1.state, ALICE).copy(turn = ALICE), emptyList())

        assertTrue(action is Action.Ask)
        assertEquals(DeckId("LOW_S"), com.declaration.domain.DeckCatalog.deckByCard.getValue((action as Action.Ask).card))
    }

    @Test
    fun `a teammate's SELF_OVERLAP completes a team declare, even off the bot's own turn`() {
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf("9D", "TD", "JD", "KD", "AD").map { CardId(it) }.toSet()),
            Triple(BOB, TEAM_BLUE, emptySet()),
            Triple(CHARLIE, TEAM_RED, setOf(CardId("QD"))),
            Triple(DAN, TEAM_BLUE, emptySet()),
            Triple(EVE, TEAM_RED, emptySet()),
            Triple(FRANK, TEAM_BLUE, emptySet()),
        )
        val state0 = GameStates.of(hands, turn = CHARLIE)
        val r1 = engine.apply(state0, CHARLIE, Action.Ask(BOB, CardId("QD"))) as ActionResultOk
        val view1 = Redactor.viewFor(r1.state, ALICE)
        assertTrue(view1.turn != ALICE, "the setup should leave it not-ALICE's-turn for this to prove off-turn declaring")

        val brain = BotBrain(Random(1), BotDifficulty.IMPOSSIBLE)
        val action = brain.observe(view1, r1.events)

        assertEquals(
            Action.Declare(
                DeckId("HIGH_D"),
                mapOf(
                    CardId("9D") to ALICE,
                    CardId("TD") to ALICE,
                    CardId("JD") to ALICE,
                    CardId("QD") to CHARLIE,
                    CardId("KD") to ALICE,
                    CardId("AD") to ALICE,
                ),
            ),
            action,
        )
    }

    @Test
    fun `IMPOSSIBLE never forgets -- the teammate declare fires deterministically for any seed`() {
        val (view1, events) = teammateDeclareSetup()
        repeat(20) { seed ->
            val brain = BotBrain(Random(seed.toLong()), BotDifficulty.IMPOSSIBLE)
            assertTrue(brain.observe(view1, events) is Action.Declare)
        }
    }

    @Test
    fun `EASY forgets roughly a quarter of the time over many trials`() {
        val (view1, events) = teammateDeclareSetup()
        val trials = 300
        var recorded = 0
        repeat(trials) { seed ->
            val brain = BotBrain(Random(seed.toLong()), BotDifficulty.EASY)
            if (brain.observe(view1, events) is Action.Declare) recorded++
        }
        // Expected ~225 (75% keep rate). Generous band avoids flakiness while still catching a
        // broken or inverted forget roll.
        assertTrue(recorded in 180..270, "expected roughly 75% retention, got $recorded/$trials")
    }

    @Test
    fun `a captured deck's cards are dropped from tracking`() {
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf("9D", "TD", "JD", "KD", "AD").map { CardId(it) }.toSet()),
            Triple(BOB, TEAM_BLUE, emptySet()),
            Triple(CHARLIE, TEAM_RED, setOf(CardId("QD"))),
            Triple(DAN, TEAM_BLUE, emptySet()),
            Triple(EVE, TEAM_RED, emptySet()),
            Triple(FRANK, TEAM_BLUE, emptySet()),
        )
        val state0 = GameStates.of(hands, turn = CHARLIE)
        val r1 = engine.apply(state0, CHARLIE, Action.Ask(BOB, CardId("QD"))) as ActionResultOk
        val view1 = Redactor.viewFor(r1.state, ALICE)

        val brain = BotBrain(Random(1), BotDifficulty.IMPOSSIBLE)
        assertTrue(brain.observe(view1, r1.events) is Action.Declare)

        val declared = GameStates.of(hands, turn = CHARLIE, capturedDecks = mapOf(DeckId("HIGH_D") to TEAM_RED))
        val viewAfterCapture = Redactor.viewFor(declared, ALICE)

        assertNull(brain.observe(viewAfterCapture, emptyList()))
    }

    private fun teammateDeclareSetup(): Pair<com.declaration.domain.PlayerView, List<com.declaration.domain.Event>> {
        val hands = listOf(
            Triple(ALICE, TEAM_RED, setOf("9D", "TD", "JD", "KD", "AD").map { CardId(it) }.toSet()),
            Triple(BOB, TEAM_BLUE, emptySet()),
            Triple(CHARLIE, TEAM_RED, setOf(CardId("QD"))),
            Triple(DAN, TEAM_BLUE, emptySet()),
            Triple(EVE, TEAM_RED, emptySet()),
            Triple(FRANK, TEAM_BLUE, emptySet()),
        )
        val state0 = GameStates.of(hands, turn = CHARLIE)
        val r1 = engine.apply(state0, CHARLIE, Action.Ask(BOB, CardId("QD"))) as ActionResultOk
        return Redactor.viewFor(r1.state, ALICE) to r1.events
    }
}

private typealias ActionResultOk = com.declaration.domain.ActionResult.Ok
private val ActionResultOk.state get() = newState
