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
  const [dragCard, setDragCard] = useState<CardId | null>(null);

  // If the selected deck just got captured (by anyone, e.g. another player's
  // declare), it's no longer a valid choice — reset to whatever's still open
  // so the picker and the drop zones stay in sync.
  useEffect(() => {
    if (deck && !openDecks.includes(deck)) {
      setDeck(openDecks[0] ?? "");
      setAssignments({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view.capturedDecks]);

  function selectDeck(next: DeckId) {
    setDeck(next);
    setAssignments({});
  }

  function assign(card: CardId, playerId: PlayerId | null) {
    setAssignments((prev) => {
      const next = { ...prev };
      if (playerId) next[card] = playerId;
      else delete next[card];
      return next;
    });
  }

  function dropOn(playerId: PlayerId | null) {
    if (dragCard) assign(dragCard, playerId);
    setDragCard(null);
  }

  const unassigned = cards.filter((c) => !assignments[c]);
  const complete = cards.length > 0 && cards.every((c) => assignments[c]);

  return (
    <div className="rounded-lg border border-amber-800 bg-amber-950/30 p-4">
      <h3 className="mb-1 font-semibold text-amber-300">Declare a deck</h3>
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
                : "border-slate-700 bg-slate-900 text-slate-300 hover:border-slate-500"
            }`}
          >
            {DECK_LABELS[d] ?? d}
          </button>
        ))}
      </div>

      {deck && (
        <>
          <p className="mb-2 text-xs font-medium tracking-wide text-amber-400/70">
            2. DRAG EACH CARD ONTO THE TEAMMATE WHO HOLDS IT
          </p>

          <div
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => dropOn(null)}
            className="mb-3 flex min-h-[7.5rem] flex-wrap items-center gap-2 rounded-lg border-2 border-dashed border-slate-700 bg-slate-950/50 p-3"
          >
            {unassigned.length === 0 ? (
              <p className="text-sm text-slate-600">All 6 cards assigned — drop here to unassign one.</p>
            ) : (
              unassigned.map((c) => (
                <div
                  key={c}
                  draggable
                  onDragStart={() => setDragCard(c)}
                  onDragEnd={() => setDragCard(null)}
                  className={`cursor-grab active:cursor-grabbing ${dragCard === c ? "opacity-30" : ""}`}
                >
                  <Card card={c} />
                </div>
              ))
            )}
          </div>

          <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-3">
            {assignees.map((a) => (
              <div
                key={a.id}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => dropOn(a.id)}
                className="min-h-[6.5rem] rounded-lg border-2 border-dashed border-slate-700 bg-slate-900/40 p-2"
              >
                <p className={`mb-1.5 text-xs font-semibold ${TEAM_TEXT[view.you.team]}`}>{a.label}</p>
                <div className="flex flex-wrap gap-1.5">
                  {cards
                    .filter((c) => assignments[c] === a.id)
                    .map((c) => (
                      <div
                        key={c}
                        draggable
                        onDragStart={() => setDragCard(c)}
                        onDragEnd={() => setDragCard(null)}
                        className={`cursor-grab active:cursor-grabbing ${dragCard === c ? "opacity-30" : ""}`}
                      >
                        <Card card={c} />
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
          className="flex-1 rounded-md bg-amber-600 px-4 py-2 font-medium hover:bg-amber-500 disabled:opacity-40"
          onClick={() => complete && onSubmit({ type: "Declare", deck, assignments })}
        >
          Declare {DECK_LABELS[deck] ?? deck}
        </button>
        <button
          type="button"
          className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-400 hover:border-slate-500"
          onClick={onCancel}
        >
          Cancel
        </button>
      </div>
    </div>
  );
}
