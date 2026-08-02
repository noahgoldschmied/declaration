import type { CardId, GameEvent, PlayerId } from "./messages";
import { cardLabel, sortHand } from "./cards";
import { DECK_LABELS } from "./deckCatalog";

export function describeEvent(event: GameEvent, nameOf: (id: PlayerId) => string): string {
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
  if (event.correct) {
    return `${declarer} correctly declared ${deck} — ${event.awardedTo} captures the deck!`;
  }
  const truth = sortHand(Object.keys(event.actualHolders) as CardId[])
    .map((card) => `${cardLabel(card)} ${nameOf(event.actualHolders[card])}`)
    .join(", ");
  return `${declarer} declared ${deck} incorrectly — ${event.awardedTo} captures the deck instead. Actually: ${truth}.`;
}
