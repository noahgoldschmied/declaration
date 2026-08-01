import { useEffect } from "react";
import type { GameEvent, PlayerId } from "../protocol/messages";
import { cardLabel } from "../protocol/cards";
import { DECK_LABELS } from "../protocol/deckCatalog";

export function EventFlash({
  event,
  nameOf,
  onDismiss,
}: {
  event: GameEvent;
  nameOf: (id: PlayerId) => string;
  onDismiss: () => void;
}) {
  useEffect(() => {
    const t = setTimeout(onDismiss, 5000);
    return () => clearTimeout(t);
  }, [event, onDismiss]);

  return (
    <div className="rounded-md border border-amber-800 bg-amber-950/60 px-4 py-2.5 text-sm text-amber-200">
      {describe(event, nameOf)}
    </div>
  );
}

function describe(event: GameEvent, nameOf: (id: PlayerId) => string): string {
  if (event.type === "Ask") {
    const asker = nameOf(event.asker);
    const asked = nameOf(event.asked);
    const card = cardLabel(event.card);
    switch (event.outcome) {
      case "HIT":
        return `${asker} asked ${asked} for ${card} — hit! Card moves to ${asker}.`;
      case "MISS":
        return `${asker} asked ${asked} for ${card} — miss. Turn passes to ${asked}.`;
      case "SELF_OVERLAP":
        return `${asker} asked ${asked} for ${card}, but ${asker} already holds it — revealed. Turn passes to ${asked}.`;
    }
  }
  const declarer = nameOf(event.declarer);
  const deck = DECK_LABELS[event.deck] ?? event.deck;
  return event.correct
    ? `${declarer} correctly declared ${deck} — ${event.awardedTo} captures the deck!`
    : `${declarer} declared ${deck} incorrectly — ${event.awardedTo} captures the deck instead.`;
}
