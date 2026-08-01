import { useState } from "react";
import type { Action, CardId, DeckId, PlayerId, PlayerView } from "../protocol/messages";
import { CARDS_BY_DECK, DECK_LABELS, decksHeldBy } from "../protocol/deckCatalog";
import { cardLabel, sortHand } from "../protocol/cards";
import { Card } from "./Card";
import { PlayerChip, TEAM_TEXT } from "./PlayerChip";

export function AskPanel({
  view,
  nameOf,
  onSubmit,
}: {
  view: PlayerView;
  nameOf: (id: PlayerId) => string;
  onSubmit: (action: Action) => void;
}) {
  const opponents = view.others.filter((o) => o.team !== view.you.team);
  const [target, setTarget] = useState<PlayerId | null>(opponents[0]?.id ?? null);

  const askableDecks = Array.from(decksHeldBy(view.you.hand)).sort();
  const [deck, setDeck] = useState<DeckId | null>(askableDecks[0] ?? null);
  const [card, setCard] = useState<CardId | null>(null);

  const deckCards = deck ? sortHand(CARDS_BY_DECK[deck]) : [];
  const targetPlayer = opponents.find((o) => o.id === target) ?? null;

  function selectDeck(d: DeckId) {
    setDeck(d);
    setCard(null);
  }

  function submit() {
    if (!target || !card) return;
    onSubmit({ type: "Ask", target, card });
    setCard(null);
  }

  return (
    <div className="rounded-lg border border-emerald-800 bg-emerald-950/40 p-4">
      <h3 className="mb-3 font-display text-xl text-emerald-300">Your turn — ask an opponent</h3>

      <p className="mb-2 text-xs font-medium tracking-wide text-emerald-400/70">1. PICK AN OPPONENT</p>
      <div className="mb-4 flex flex-wrap gap-2">
        {opponents.map((o) => (
          <button
            key={o.id}
            type="button"
            className="rounded-lg transition hover:opacity-90 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
            onClick={() => setTarget(o.id)}
          >
            <PlayerChip
              name={nameOf(o.id)}
              team={o.team}
              handSize={o.handSize}
              isTurn={false}
              isYou={false}
              connected
              selected={target === o.id}
            />
          </button>
        ))}
      </div>

      {askableDecks.length === 0 ? (
        <p className="mb-3 text-sm text-emerald-400/70">You hold no cards, so you cannot ask.</p>
      ) : (
        <>
          <p className="mb-2 text-xs font-medium tracking-wide text-emerald-400/70">2. PICK A DECK YOU HOLD CARDS IN</p>
          <div className="mb-4 flex flex-wrap gap-2">
            {askableDecks.map((d) => (
              <button
                key={d}
                type="button"
                onClick={() => selectDeck(d)}
                className={`rounded-md border px-3 py-1.5 text-sm font-medium transition ${
                  deck === d
                    ? "border-emerald-400 bg-emerald-900/60 text-emerald-200"
                    : "border-stone-700 bg-stone-900 text-stone-300 hover:border-stone-500"
                }`}
              >
                {DECK_LABELS[d] ?? d}
              </button>
            ))}
          </div>

          <p className="mb-2 text-xs font-medium tracking-wide text-emerald-400/70">3. PICK A CARD TO ASK FOR</p>
          <div className="mb-4 flex flex-wrap gap-2">
            {deckCards.map((c) => (
              <Card key={c} card={c} selected={card === c} onClick={() => setCard(c)} />
            ))}
          </div>
        </>
      )}

      <button
        type="button"
        disabled={!target || !card}
        className="w-full rounded-md bg-emerald-600 px-4 py-2 font-medium hover:bg-emerald-500 disabled:opacity-40 sm:w-auto"
        onClick={submit}
      >
        {targetPlayer && card ? (
          <>
            Ask <span className={TEAM_TEXT[targetPlayer.team]}>{nameOf(targetPlayer.id)}</span> for{" "}
            {cardLabel(card)}
          </>
        ) : (
          "Ask"
        )}
      </button>
    </div>
  );
}
