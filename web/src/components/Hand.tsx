import { useEffect, useState } from "react";
import { sortHand } from "../protocol/cards";
import type { CardId } from "../protocol/messages";
import { Card } from "./Card";

export function Hand({ cards }: { cards: CardId[] }) {
  const [order, setOrder] = useState<CardId[]>(() => sortHand(cards));
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  useEffect(() => {
    setOrder((prev) => {
      const held = new Set(cards);
      const kept = prev.filter((c) => held.has(c));
      const keptSet = new Set(kept);
      const added = sortHand(cards.filter((c) => !keptSet.has(c)));
      return [...kept, ...added];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cards.join(",")]);

  if (order.length === 0) {
    return <p className="text-sm text-stone-600">Empty — you're out of cards.</p>;
  }

  function moveTo(index: number) {
    setOrder((prev) => {
      if (dragIndex === null || dragIndex === index) return prev;
      const next = [...prev];
      const [moved] = next.splice(dragIndex, 1);
      next.splice(index, 0, moved);
      return next;
    });
    setDragIndex(null);
  }

  return (
    <div className="flex flex-wrap gap-2">
      {order.map((c, i) => (
        <div
          key={c}
          draggable
          onDragStart={() => setDragIndex(i)}
          onDragOver={(e) => e.preventDefault()}
          onDrop={() => moveTo(i)}
          onDragEnd={() => setDragIndex(null)}
          className={`cursor-grab touch-none transition-opacity active:cursor-grabbing ${
            dragIndex === i ? "opacity-30" : ""
          }`}
        >
          <Card card={c} />
        </div>
      ))}
    </div>
  );
}
