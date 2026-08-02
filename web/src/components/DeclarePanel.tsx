import { useEffect, useState } from "react";
import type { Action, CardId, DeckId, PlayerId, PlayerView } from "../protocol/messages";
import { ALL_DECKS, CARDS_BY_DECK, DECK_LABELS } from "../protocol/deckCatalog";
import { Card } from "./Card";
import { TEAM_TEXT } from "./PlayerChip";

export function DeclarePanel({
  view,
  nameOf,
  onSubmit,
  onCancel,
}: {
  view: PlayerView;
  nameOf: (id: PlayerId) => string;
  onSubmit: (action: Action) => void;
  onCancel: () => void;
}) {
  const openDecks = ALL_DECKS.filter((d) => !(d in view.capturedDecks));
  const assignees = [
    { id: view.you.id, label: `${nameOf(view.you.id)} (you)` },
    ...view.others.filter((o) => o.team === view.you.team).map((o) => ({ id: o.id, label: nameOf(o.id) })),
  ];

  const [deck, setDeck] = useState<DeckId>(openDecks[0] ?? "");
  const cards = deck ? (CARDS_BY_DECK[deck] ?? []) : [];
  const [assignments, setAssignments] = useState<Record<CardId, PlayerId>>({});
  // Tap-to-place: select a card, then tap where it goes. Works identically with
  // mouse or touch, unlike native HTML5 drag-and-drop which most mobile
  // browsers don't fire from touch at all.
  const [selectedCard, setSelectedCard] = useState<CardId | null>(null);

  // If the selected deck just got captured (by anyone, e.g. another player's
  // declare), it's no longer a valid choice — reset to whatever's still open
  // so the picker and the drop zones stay in sync.
  useEffect(() => {
    if (deck && !openDecks.includes(deck)) {
      setDeck(openDecks[0] ?? "");
      setAssignments({});
      setSelectedCard(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view.capturedDecks]);

  function selectDeck(next: DeckId) {
    setDeck(next);
    setAssignments({});
    setSelectedCard(null);
  }

  function assign(card: CardId, playerId: PlayerId | null) {
    setAssignments((prev) => {
      const next = { ...prev };
      if (playerId) next[card] = playerId;
      else delete next[card];
      return next;
    });
  }

  function tapCard(card: CardId) {
    setSelectedCard((prev) => (prev === card ? null : card));
  }

  function placeSelected(playerId: PlayerId | null) {
    if (selectedCard) assign(selectedCard, playerId);
    setSelectedCard(null);
  }

  const unassigned = cards.filter((c) => !assignments[c]);
  const complete = cards.length > 0 && cards.every((c) => assignments[c]);

  return (
    <div className="rounded-lg border border-amber-800 bg-amber-950/30 p-4">
      <h3 className="mb-1 font-display text-xl text-amber-300">Declare a deck</h3>
      <p className="mb-3 text-xs text-amber-400/70">
        Any player can declare at any time. One wrong card gives the whole deck to the other team.
      </p>

      <p className="mb-2 text-xs font-medium tracking-wide text-amber-400/70">1. PICK A DECK</p>
      <div className="mb-4 flex flex-wrap gap-2">
        {openDecks.map((d) => (
          <button
            key={d}
            type="button"
            onClick={() => selectDeck(d)}
            className={`rounded-md border px-3 py-1.5 text-sm font-medium transition ${
              deck === d
                ? "border-amber-400 bg-amber-900/60 text-amber-200"
                : "border-stone-700 bg-stone-900 text-stone-300 hover:border-stone-500"
            }`}
          >
            {DECK_LABELS[d] ?? d}
          </button>
        ))}
      </div>

      {deck && (
        <>
          <p className="mb-2 text-xs font-medium tracking-wide text-amber-400/70">
            2. TAP A CARD, THEN TAP THE TEAMMATE WHO HOLDS IT
          </p>

          <div
            onClick={() => placeSelected(null)}
            className="mb-3 flex min-h-[7.5rem] flex-wrap items-center gap-2 rounded-lg border-2 border-dashed border-stone-700 bg-stone-950/50 p-3"
          >
            {unassigned.length === 0 ? (
              <p className="text-sm text-stone-600">All 6 cards assigned — tap a card, then tap here to unassign it.</p>
            ) : (
              unassigned.map((c) => (
                <div key={c} onClick={(e) => e.stopPropagation()}>
                  <Card card={c} selected={selectedCard === c} onClick={() => tapCard(c)} />
                </div>
              ))
            )}
          </div>

          <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-3">
            {assignees.map((a) => (
              <div
                key={a.id}
                onClick={() => placeSelected(a.id)}
                className="min-h-[6.5rem] rounded-lg border-2 border-dashed border-stone-700 bg-stone-900/40 p-2"
              >
                <p className={`mb-1.5 text-xs font-semibold ${TEAM_TEXT[view.you.team]}`}>{a.label}</p>
                <div className="flex flex-wrap gap-1.5">
                  {cards
                    .filter((c) => assignments[c] === a.id)
                    .map((c) => (
                      <div key={c} onClick={(e) => e.stopPropagation()}>
                        <Card card={c} selected={selectedCard === c} onClick={() => tapCard(c)} />
                      </div>
                    ))}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <div className="flex gap-2">
        <button
          type="button"
          disabled={!complete}
          className="btn-primary flex-1"
          onClick={() => complete && onSubmit({ type: "Declare", deck, assignments })}
        >
          Declare {DECK_LABELS[deck] ?? deck}
        </button>
        <button type="button" className="btn-secondary px-4 py-2" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
