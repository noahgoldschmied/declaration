import type { PlayerId, TeamId } from "../protocol/messages";

const TEAM_RING: Record<TeamId, string> = {
  RED: "ring-rose-500",
  BLUE: "ring-sky-500",
};

export function PlayerChip({
  name,
  team,
  handSize,
  isTurn,
  isYou,
  connected,
}: {
  name: string;
  team: TeamId;
  handSize: number;
  isTurn: boolean;
  isYou: boolean;
  connected: boolean;
  id?: PlayerId;
}) {
  return (
    <div
      className={`flex min-w-[7rem] flex-col items-center gap-1 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 ring-2 ring-offset-2 ring-offset-slate-950 ${
        isTurn ? TEAM_RING[team] : "ring-transparent"
      }`}
    >
      <span className="flex items-center gap-1.5 text-sm font-medium">
        <span className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-emerald-500" : "bg-slate-600"}`} />
        {name}
        {isYou && <span className="text-xs text-slate-500">(you)</span>}
      </span>
      <span className="text-xs text-slate-500">{handSize} cards</span>
    </div>
  );
}
