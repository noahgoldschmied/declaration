import { cardLabel, isRed } from "../protocol/cards";
import type { CardId } from "../protocol/messages";

export function Card({ card, dim = false }: { card: CardId; dim?: boolean }) {
  return (
    <span
      className={`inline-flex h-14 w-10 flex-none items-center justify-center rounded-md border font-mono text-sm font-semibold shadow-sm ${
        dim
          ? "border-slate-700 bg-slate-800 text-slate-500"
          : isRed(card)
            ? "border-rose-800 bg-white text-rose-600"
            : "border-slate-700 bg-white text-slate-900"
      }`}
    >
      {cardLabel(card)}
    </span>
  );
}
