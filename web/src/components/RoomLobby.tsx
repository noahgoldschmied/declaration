import { useGameStore } from "../store/gameStore";
import { RulesButton } from "./RulesModal";
import { TEAM_TEXT } from "./PlayerChip";
import type { TeamId } from "../protocol/messages";

const TEAM_STYLES: Record<TeamId, string> = {
  RED: "border-rose-500 text-rose-300",
  BLUE: "border-sky-500 text-sky-300",
};

export function RoomLobby() {
  const session = useGameStore((s) => s.session)!;
  const roomState = useGameStore((s) => s.roomState);
  const players = useGameStore((s) => s.players);
  const actionError = useGameStore((s) => s.actionError);
  const chooseTeam = useGameStore((s) => s.chooseTeam);
  const startGame = useGameStore((s) => s.startGame);
  const leaveRoom = useGameStore((s) => s.leaveRoom);
  const clearActionError = useGameStore((s) => s.clearActionError);

  const isHost = roomState?.hostId === session.playerId;
  const redCount = players.filter((p) => p.team === "RED").length;
  const blueCount = players.filter((p) => p.team === "BLUE").length;
  const canStart = players.length === 6 && redCount === 3 && blueCount === 3;
  const me = players.find((p) => p.playerId === session.playerId);

  return (
    <div className="mx-auto flex h-full max-w-2xl flex-col gap-6 px-4 py-10">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-slate-400">Room code</p>
          <p className="text-4xl font-bold tracking-[0.3em]">{roomState?.roomCode ?? session.roomCode}</p>
        </div>
        <div className="flex items-center gap-2">
          <RulesButton />
          <button
            type="button"
            className="rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-400 hover:border-slate-500"
            onClick={leaveRoom}
          >
            Leave
          </button>
        </div>
      </div>

      <div className="flex gap-3">
        {(["RED", "BLUE"] as const).map((team) => (
          <button
            key={team}
            type="button"
            onClick={() => chooseTeam(team)}
            className={`flex-1 rounded-lg border-2 py-3 font-semibold transition ${TEAM_STYLES[team]} ${
              me?.team === team ? "bg-slate-800" : "bg-slate-900 opacity-70 hover:opacity-100"
            }`}
          >
            {team} ({team === "RED" ? redCount : blueCount}/3)
          </button>
        ))}
      </div>

      <div className="rounded-lg border border-slate-800 bg-slate-900">
        <ul className="divide-y divide-slate-800">
          {players.map((p) => (
            <li key={p.playerId} className="flex items-center justify-between px-4 py-2.5">
              <span className="flex items-center gap-2">
                <span className={`h-2 w-2 rounded-full ${p.connected ? "bg-emerald-500" : "bg-slate-600"}`} />
                <span className={p.team ? TEAM_TEXT[p.team] : ""}>{p.displayName}</span>
                {p.playerId === roomState?.hostId && (
                  <span className="rounded bg-slate-800 px-1.5 py-0.5 text-xs text-slate-400">host</span>
                )}
                {p.playerId === session.playerId && <span className="text-xs text-slate-500">(you)</span>}
              </span>
              <span className={`text-sm ${p.team ? TEAM_STYLES[p.team] : "text-slate-600"}`}>
                {p.team ?? "no team"}
              </span>
            </li>
          ))}
          {Array.from({ length: Math.max(0, 6 - players.length) }).map((_, i) => (
            <li key={`empty-${i}`} className="px-4 py-2.5 text-slate-600">
              Waiting for player…
            </li>
          ))}
        </ul>
      </div>

      {actionError && (
        <p className="rounded-md border border-red-800 bg-red-950 px-3 py-2 text-sm text-red-300">
          {actionError}
          <button type="button" className="ml-2 underline" onClick={clearActionError}>
            dismiss
          </button>
        </p>
      )}

      {isHost && (
        <button
          type="button"
          disabled={!canStart}
          onClick={startGame}
          className="rounded-md bg-indigo-600 px-4 py-3 font-semibold hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {canStart ? "Start game" : "Need 6 players, 3 per team"}
        </button>
      )}
      {!isHost && <p className="text-center text-sm text-slate-500">Waiting for the host to start the game…</p>}
    </div>
  );
}
