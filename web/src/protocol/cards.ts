import type { CardId } from "./messages";

const SUIT_SYMBOL: Record<string, string> = { S: "♠", H: "♥", D: "♦", C: "♣" };
const RANK_LABEL: Record<string, string> = { T: "10" };

export function isRed(card: CardId): boolean {
  return card[card.length - 1] === "H" || card[card.length - 1] === "D";
}

export function cardLabel(card: CardId): string {
  if (card === "JK1") return "Jkr 1";
  if (card === "JK2") return "Jkr 2";
  const suit = card[card.length - 1];
  const rank = card.slice(0, -1);
  return `${RANK_LABEL[rank] ?? rank}${SUIT_SYMBOL[suit] ?? suit}`;
}

export function sortHand(cards: CardId[]): CardId[] {
  return [...cards].sort((a, b) => a.localeCompare(b));
}
