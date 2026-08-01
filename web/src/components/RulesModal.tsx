import { useState } from "react";

const DECKS = [
  ["Low Spades", "2♠ 3♠ 4♠ 5♠ 6♠ 7♠"],
  ["Low Hearts", "2♥ 3♥ 4♥ 5♥ 6♥ 7♥"],
  ["Low Diamonds", "2♦ 3♦ 4♦ 5♦ 6♦ 7♦"],
  ["Low Clubs", "2♣ 3♣ 4♣ 5♣ 6♣ 7♣"],
  ["High Spades", "9♠ 10♠ J♠ Q♠ K♠ A♠"],
  ["High Hearts", "9♥ 10♥ J♥ Q♥ K♥ A♥"],
  ["High Diamonds", "9♦ 10♦ J♦ Q♦ K♦ A♦"],
  ["High Clubs", "9♣ 10♣ J♣ Q♣ K♣ A♣"],
  ["Eights & Jokers", "8♠ 8♥ 8♦ 8♣ Joker₁ Joker₂"],
];

function RulesContent() {
  return (
    <div className="space-y-4 text-sm leading-relaxed text-stone-300">
      <p>
        Six players split into two teams of three. The 54-card deck is divided into <strong>9 decks of 6 cards
        each</strong>. The first team to capture <strong>5 decks</strong> wins.
      </p>
      <p>
        Capturing a deck requires a <strong>declaration</strong>: a player from your team correctly states which
        teammate is holding each of the 6 cards in that deck. Get any card wrong and the opposing team takes the
        deck instead.
      </p>

      <div className="divider" />

      <section>
        <h3 className="mb-1 font-display text-lg text-amber-400">The decks</h3>
        <div className="grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-2">
          {DECKS.map(([name, cards]) => (
            <div key={name} className="flex justify-between gap-2 rounded bg-stone-800/50 px-2 py-1">
              <span className="text-stone-400">{name}</span>
              <span className="font-mono">{cards}</span>
            </div>
          ))}
        </div>
      </section>

      <div className="divider" />

      <section>
        <h3 className="mb-1 font-display text-lg text-amber-400">Asking</h3>
        <p>
          On your turn, ask one opponent for one specific card. You may only ask for a card from a deck you already
          hold at least one card from — so every ask reveals something about your own hand.
        </p>
        <ul className="mt-1 list-inside list-disc space-y-1">
          <li><strong>Hit</strong> — they have it, hand it over, you keep your turn and can ask again.</li>
          <li><strong>Miss</strong> — they don't have it, your turn ends and passes to them.</li>
          <li>
            <strong>Self-overlap</strong> — you ask for a card you already hold. Legal, but it publicly reveals
            that card's location, and your turn ends and passes to them.
          </li>
        </ul>
      </section>

      <div className="divider" />

      <section>
        <h3 className="mb-1 font-display text-lg text-amber-400">Memory only</h3>
        <p>
          Every ask and its outcome is shown briefly, then disappears. There's no history log. You must remember
          what's happened — which cards moved, which players have proven they don't hold a card, and which have
          been revealed to hold one.
        </p>
      </section>

      <div className="divider" />

      <section>
        <h3 className="mb-1 font-display text-lg text-amber-400">Declaring</h3>
        <p>
          Any player can declare any deck at any time, even interrupting someone else's turn. Name all 6 cards and
          assign each to a specific teammate (including yourself). If every assignment is correct, your team
          captures the deck. If even one is wrong, the <em>other</em> team captures it instead. You don't need to
          hold any cards in a deck to declare it — declare from observation alone.
        </p>
      </section>

      <div className="divider" />

      <section>
        <h3 className="mb-1 font-display text-lg text-amber-400">Edge cases</h3>
        <ul className="list-inside list-disc space-y-1">
          <li>An empty hand permanently skips your turn — but you can still declare.</li>
          <li>If a whole team runs out of cards, the other team just keeps asking (and missing) until decks are declared out.</li>
          <li>First team to 5 decks wins immediately.</li>
        </ul>
      </section>
    </div>
  );
}

export function RulesButton() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button type="button" className="btn-secondary" onClick={() => setOpen(true)}>
        Rules
      </button>
      {open && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setOpen(false)}
        >
          <div
            className="panel max-h-[85vh] w-full max-w-2xl overflow-y-auto p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-2xl text-amber-400">Declaration — Rules</h2>
              <button type="button" className="btn-secondary" onClick={() => setOpen(false)}>
                Close
              </button>
            </div>
            <RulesContent />
          </div>
        </div>
      )}
    </>
  );
}
