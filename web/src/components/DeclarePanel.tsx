import { useEffect, useMemo, useState } from "react";
import type { Action, PlayerId, PlayerView } from "../protocol/messages";
import { ALL_DECKS, CARDS_BY_DECK, DECK_LABELS } from "../protocol/deckCatalog";
import { cardLabel } from "../protocol/cards";

export function DeclarePanel({
  view,
  nameOf,
  onSubmit,
}: {
  view: PlayerView;
  nameOf: (id: PlayerId) => string;
  onSubmit: (action: Action) => void;
}) {
  const openDecks = ALL_DECKS.filter((d) => !(d in view.capturedDecks));
  const assignees = [
    { id: view.you.id, label: `${nameOf(view.you.id)} (you)` },
    ...view.others.filter((o) => o.team === view.you.team).map((o) => ({ id: o.id, label: nameOf(o.id) })),
  ];

  const [deck, setDeck] = useState(openDecks[0] ?? "");
  const cards = useMemo(() => CARDS_BY_DECK[deck] ?? [], [deck]);
  const [assignments, setAssignments] = useState<Record<string, PlayerId>>({});

  // If the selected deck just got captured (by anyone, e.g. another player's
  // declare), the <select> can no longer render an option for it — reset to
  // whatever's still open so the picker and the card list stay in sync.
  useEffect(() => {
    if (deck && !openDecks.includes(deck)) {
      setDeck(openDecks[0] ?? "");
      setAssignments({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view.capturedDecks]);

  function selectDeck(next: string) {
    setDeck(next);
    setAssignments({});
  }

  function assign(card: string, playerId: PlayerId) {
    setAssignments((prev) => ({ ...prev, [card]: playerId }));
  }

  const complete = cards.length > 0 && cards.every((c) => assignments[c]);

  return (
    <div className="rounded-lg border border-amber-800 bg-amber-950/30 p-4">
      <h3 className="mb-1 font-semibold text-amber-300">Declare a deck</h3>
      <p className="mb-3 text-xs text-amber-400/70">
        Any player can declare at any time. One wrong card gives the whole deck to the other team.
      </p>

      <select
        className="mb-3 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2"
        value={deck}
        onChange={(e) => selectDeck(e.target.value)}
      >
        {openDecks.map((d) => (
          <option key={d} value={d}>
            {DECK_LABELS[d] ?? d}
          </option>
        ))}
      </select>

      {deck && (
        <div className="mb-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
          {cards.map((c) => (
            <label key={c} className="flex items-center gap-2 rounded-md border border-slate-800 bg-slate-950 px-2 py-1.5">
              <span className="w-10 font-mono text-sm">{cardLabel(c)}</span>
              <select
                className="flex-1 rounded border border-slate-700 bg-slate-900 px-1 py-1 text-xs"
                value={assignments[c] ?? ""}
                onChange={(e) => assign(c, e.target.value)}
              >
                <option value="" disabled>
                  who?
                </option>
                {assignees.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.label}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
      )}

      <button
        type="button"
        disabled={!complete}
        className="w-full rounded-md bg-amber-600 px-4 py-2 font-medium hover:bg-amber-500 disabled:opacity-40"
        onClick={() => complete && onSubmit({ type: "Declare", deck, assignments })}
      >
        Declare {DECK_LABELS[deck] ?? deck}
      </button>
    </div>
  );
}
