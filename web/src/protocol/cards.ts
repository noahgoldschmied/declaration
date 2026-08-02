import type { CardId } from "./messages";

const SUIT_SYMBOL: Record<string, string> = { S: "♠", H: "♥", D: "♦", C: "♣" };
const SUIT_NAME: Record<string, string> = { S: "Spades", H: "Hearts", D: "Diamonds", C: "Clubs" };
const RANK_LABEL: Record<string, string> = { T: "10" };
const SUIT_ORDER = ["S", "H", "D", "C"];

export function isRed(card: CardId): boolean {
  return card[card.length - 1] === "H" || card[card.length - 1] === "D";
}

export function isJoker(card: CardId): boolean {
  return card === "JK1" || card === "JK2";
}

export function cardLabel(card: CardId): string {
  if (card === "JK1") return "B&W Joker";
  if (card === "JK2") return "Color Joker";
  const suit = card[card.length - 1];
  const rank = card.slice(0, -1);
  return `${RANK_LABEL[rank] ?? rank}${SUIT_SYMBOL[suit] ?? suit}`;
}

const RANK_ORDER = ["2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K", "A"];

function rankIndex(card: CardId): number {
  if (isJoker(card)) return RANK_ORDER.length; // jokers sort last
  return RANK_ORDER.indexOf(card.slice(0, -1));
}

function suitIndex(card: CardId): number {
  if (isJoker(card)) return SUIT_ORDER.length; // jokers sort last
  return SUIT_ORDER.indexOf(card[card.length - 1]);
}

/** Groups by suit (spades, hearts, diamonds, clubs, jokers last), rank-ordered within each suit. */
export function sortHand(cards: CardId[]): CardId[] {
  return [...cards].sort((a, b) => suitIndex(a) - suitIndex(b) || rankIndex(a) - rankIndex(b));
}

/** "SUIT" key used to group a hand for display -- distinct constant for jokers, which have no suit. */
export function suitGroupKey(card: CardId): string {
  return isJoker(card) ? "JOKER" : card[card.length - 1];
}

export function suitGroupLabel(key: string): string {
  if (key === "JOKER") return "Jokers";
  return `${SUIT_SYMBOL[key] ?? key} ${SUIT_NAME[key] ?? key}`;
}
