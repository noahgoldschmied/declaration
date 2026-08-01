import { cardLabel, isJoker, isRed } from "../protocol/cards";
import type { CardId } from "../protocol/messages";
import jokerBw from "../assets/joker-bw.svg";
import jokerColor from "../assets/joker-color.svg";

export function Card({
  card,
  dim = false,
  selected = false,
  onClick,
}: {
  card: CardId;
  dim?: boolean;
  selected?: boolean;
  onClick?: () => void;
}) {
  const Tag = onClick ? "button" : "span";
  const joker = isJoker(card);
  const colorClasses = dim
    ? "border-stone-700 bg-stone-800 text-stone-500"
    : joker
      ? "border-stone-700 bg-white"
      : isRed(card)
        ? "border-red-800 bg-white text-red-700"
        : "border-stone-700 bg-white text-stone-900";

  return (
    <Tag
      type={onClick ? "button" : undefined}
      onClick={onClick}
      className={`inline-flex h-28 w-20 flex-none items-center justify-center rounded-xl border-2 font-mono font-bold shadow-md transition ${
        onClick ? "cursor-pointer hover:-translate-y-1" : ""
      } ${selected ? "-translate-y-1 ring-4 ring-emerald-400 ring-offset-2 ring-offset-stone-950" : ""} ${colorClasses}`}
    >
      {joker ? (
        <img
          src={card === "JK2" ? jokerColor : jokerBw}
          alt={cardLabel(card)}
          className={dim ? "h-14 w-14 object-contain opacity-40 grayscale" : "h-14 w-14 object-contain"}
        />
      ) : (
        <span className="text-2xl">{cardLabel(card)}</span>
      )}
    </Tag>
  );
}
