import type { CardId } from "./messages";

const SUIT_SYMBOL: Record<string, string> = { S: "♠", H: "♥", D: "♦", C: "♣" };
const RANK_LABEL: Record<string, string> = { T: "10" };

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

export function sortHand(cards: CardId[]): CardId[] {
  return [...cards].sort((a, b) => rankIndex(a) - rankIndex(b) || a[a.length - 1].localeCompare(b[b.length - 1]));
}
