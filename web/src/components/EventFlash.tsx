import type { GameEvent, PlayerId } from "../protocol/messages";
import { sortHand } from "../protocol/cards";
import { CARDS_BY_DECK } from "../protocol/deckCatalog";
import { describeEvent } from "../protocol/eventText";
import { Card } from "./Card";

export function EventFlash({
  event,
  nameOf,
}: {
  event: GameEvent;
  nameOf: (id: PlayerId) => string;
}) {
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-md border border-amber-800 bg-amber-950/60 px-4 py-2.5 text-sm text-amber-200">
      <div className="flex flex-wrap gap-1.5">
        {event.type === "Ask" ? (
          <Card card={event.card} />
        ) : (
          sortHand(CARDS_BY_DECK[event.deck]).map((c) => (
            <div key={c} className="flex flex-col items-center gap-1">
              <Card card={c} dim={!event.correct} />
              {/* The deck is captured either way, so the true holder is safe to reveal even on a
                  wrong declare -- it's history, not live strategic information anymore. */}
              <span className="max-w-[5rem] truncate text-xs text-amber-300">{nameOf(event.actualHolders[c])}</span>
            </div>
          ))
        )}
      </div>
      <span>{describeEvent(event, nameOf)}</span>
    </div>
  );
}
