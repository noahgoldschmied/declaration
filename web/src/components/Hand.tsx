import { useEffect, useState } from "react";
import { sortHand, suitGroupKey, suitGroupLabel } from "../protocol/cards";
import type { CardId } from "../protocol/messages";
import { Card } from "./Card";

export function Hand({ cards }: { cards: CardId[] }) {
  const [order, setOrder] = useState<CardId[]>(() => sortHand(cards));
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);

  useEffect(() => {
    setOrder((prev) => {
      const held = new Set(cards);
      const kept = prev.filter((c) => held.has(c));
      const keptSet = new Set(kept);
      const added = sortHand(cards.filter((c) => !keptSet.has(c)));
      return [...kept, ...added];
    });
    setSelectedIndex(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cards.join(",")]);

  if (order.length === 0) {
    return <p className="text-sm text-stone-600">Empty — you're out of cards.</p>;
  }

  // Tap-to-move: works identically with mouse or touch, unlike native HTML5
  // drag-and-drop which most mobile browsers don't fire from touch at all.
  function tapCard(index: number) {
    if (selectedIndex === null) {
      setSelectedIndex(index);
      return;
    }
    if (selectedIndex === index) {
      setSelectedIndex(null);
      return;
    }
    setOrder((prev) => {
      const next = [...prev];
      const [moved] = next.splice(selectedIndex, 1);
      next.splice(index, 0, moved);
      return next;
    });
    setSelectedIndex(null);
  }

  // Suit-grouped for display: consecutive same-suit runs in `order` become one
  // labeled column, each card keeping its real index into `order` so tapCard
  // (which splices `order`) still targets the right card after a manual move.
  const groups: { key: string; entries: { card: CardId; index: number }[] }[] = [];
  order.forEach((card, index) => {
    const key = suitGroupKey(card);
    const lastGroup = groups[groups.length - 1];
    if (lastGroup && lastGroup.key === key) {
      lastGroup.entries.push({ card, index });
    } else {
      groups.push({ key, entries: [{ card, index }] });
    }
  });

  const labelColor: Record<string, string> = {
    H: "text-red-400",
    D: "text-red-400",
    S: "text-stone-400",
    C: "text-stone-400",
    JOKER: "text-stone-500",
  };

  return (
    <div className="flex flex-wrap items-start gap-4">
      {groups.map((group, gi) => (
        <div key={gi} className="flex flex-col gap-1">
          <span className={`text-xs font-medium tracking-wide ${labelColor[group.key] ?? "text-stone-400"}`}>
            {suitGroupLabel(group.key)}
          </span>
          <div className="flex gap-2">
            {group.entries.map(({ card, index }) => (
              <Card key={card} card={card} selected={selectedIndex === index} onClick={() => tapCard(index)} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
