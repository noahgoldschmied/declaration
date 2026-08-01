import type { PlayerId, TeamId } from "../protocol/messages";

const TEAM_RING: Record<TeamId, string> = {
  RED: "ring-red-600",
  BLUE: "ring-sky-500",
};

export const TEAM_TEXT: Record<TeamId, string> = {
  RED: "text-red-400",
  BLUE: "text-sky-300",
};

export function PlayerChip({
  name,
  team,
  handSize,
  isTurn,
  isYou,
  connected,
  selected = false,
}: {
  name: string;
  team: TeamId;
  handSize: number;
  isTurn: boolean;
  isYou: boolean;
  connected: boolean;
  selected?: boolean;
  id?: PlayerId;
}) {
  return (
    <div
      className={`flex min-w-[7rem] flex-col items-center gap-1 rounded-lg border border-stone-800 bg-stone-900 px-3 py-2 ring-2 ring-offset-2 ring-offset-stone-950 transition ${
        selected ? "ring-emerald-400 bg-stone-800" : isTurn ? TEAM_RING[team] : "ring-transparent"
      }`}
    >
      <span className={`flex items-center gap-1.5 text-sm font-medium ${TEAM_TEXT[team]}`}>
        <span className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-emerald-500" : "bg-stone-600"}`} />
        {name}
        {isYou && <span className="text-xs text-stone-500">(you)</span>}
      </span>
      <span className="text-xs text-stone-500">{handSize} cards</span>
    </div>
  );
}
