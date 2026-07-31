// Hand-mirrored from server/src/main/kotlin/com/declaration/domain/DeckCatalog.kt.
// The wire protocol never sends the catalog (it's static, known to both sides),
// so the client keeps its own copy for grouping cards into decks (the ask
// deck-membership constraint, and the declare UI).
import type { CardId, DeckId } from "./messages";

const LOW_RANKS = ["2", "3", "4", "5", "6", "7"];
const HIGH_RANKS = ["9", "T", "J", "Q", "K", "A"];
const SUITS = ["S", "H", "D", "C"] as const;

export const CARDS_BY_DECK: Record<DeckId, CardId[]> = {};
for (const suit of SUITS) {
  CARDS_BY_DECK[`LOW_${suit}`] = LOW_RANKS.map((rank) => `${rank}${suit}`);
  CARDS_BY_DECK[`HIGH_${suit}`] = HIGH_RANKS.map((rank) => `${rank}${suit}`);
}
CARDS_BY_DECK["EIGHTS_JOKERS"] = ["8S", "8H", "8D", "8C", "JK1", "JK2"];

export const DECK_BY_CARD: Record<CardId, DeckId> = {};
for (const [deck, cards] of Object.entries(CARDS_BY_DECK)) {
  for (const card of cards) DECK_BY_CARD[card] = deck;
}

export const ALL_DECKS: DeckId[] = Object.keys(CARDS_BY_DECK);

export const DECK_LABELS: Record<DeckId, string> = {
  LOW_S: "Low Spades",
  LOW_H: "Low Hearts",
  LOW_D: "Low Diamonds",
  LOW_C: "Low Clubs",
  HIGH_S: "High Spades",
  HIGH_H: "High Hearts",
  HIGH_D: "High Diamonds",
  HIGH_C: "High Clubs",
  EIGHTS_JOKERS: "Eights & Jokers",
};

export function decksHeldBy(hand: CardId[]): Set<DeckId> {
  return new Set(hand.map((card) => DECK_BY_CARD[card]));
}
