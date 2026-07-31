import { useState } from "react";
import type { Action, OpponentView, PlayerId, PlayerView } from "../protocol/messages";
import { CARDS_BY_DECK, decksHeldBy } from "../protocol/deckCatalog";
import { cardLabel, sortHand } from "../protocol/cards";

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
  const [target, setTarget] = useState<OpponentView | null>(opponents[0] ?? null);

  const askableDecks = decksHeldBy(view.you.hand);
  const askableCards = sortHand(Array.from(askableDecks).flatMap((deck) => CARDS_BY_DECK[deck]));
  const [card, setCard] = useState(askableCards[0] ?? "");

  const currentTarget = opponents.find((o) => o.id === target?.id) ?? opponents[0] ?? null;

  return (
    <div className="rounded-lg border border-emerald-800 bg-emerald-950/40 p-4">
      <h3 className="mb-3 font-semibold text-emerald-300">Your turn — ask an opponent</h3>
      <div className="flex flex-col gap-3 sm:flex-row">
        <select
          className="flex-1 rounded-md border border-slate-700 bg-slate-950 px-3 py-2"
          value={currentTarget?.id ?? ""}
          onChange={(e) => setTarget(opponents.find((o) => o.id === e.target.value) ?? null)}
        >
          {opponents.map((o) => (
            <option key={o.id} value={o.id}>
              {nameOf(o.id)} ({o.handSize} cards)
            </option>
          ))}
        </select>
        <select
          className="flex-1 rounded-md border border-slate-700 bg-slate-950 px-3 py-2 font-mono"
          value={card}
          onChange={(e) => setCard(e.target.value)}
        >
          {askableCards.map((c) => (
            <option key={c} value={c}>
              {cardLabel(c)}
            </option>
          ))}
        </select>
        <button
          type="button"
          disabled={!currentTarget || !card}
          className="rounded-md bg-emerald-600 px-4 py-2 font-medium hover:bg-emerald-500 disabled:opacity-40"
          onClick={() => currentTarget && card && onSubmit({ type: "Ask", target: currentTarget.id, card })}
        >
          Ask
        </button>
      </div>
      {askableCards.length === 0 && (
        <p className="mt-2 text-sm text-emerald-400/70">You hold no cards, so you cannot ask.</p>
      )}
    </div>
  );
}
