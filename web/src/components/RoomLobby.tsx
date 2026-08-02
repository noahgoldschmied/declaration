import { useState } from "react";
import { useGameStore } from "../store/gameStore";
import { RulesButton } from "./RulesModal";
import { TEAM_TEXT } from "./PlayerChip";
import type { BotDifficulty, TeamId } from "../protocol/messages";

const TEAM_STYLES: Record<TeamId, string> = {
  RED: "border-red-600 text-red-400",
  BLUE: "border-sky-600 text-sky-300",
};

const DIFFICULTIES: BotDifficulty[] = ["IMPOSSIBLE", "HARD", "MEDIUM", "EASY"];

const MOVE_HISTORY_COUNT_OPTIONS = [5, 10, 15, 20, 25, 40];

export function RoomLobby() {
  const session = useGameStore((s) => s.session)!;
  const roomState = useGameStore((s) => s.roomState);
  const players = useGameStore((s) => s.players);
  const actionError = useGameStore((s) => s.actionError);
  const chooseTeam = useGameStore((s) => s.chooseTeam);
  const startGame = useGameStore((s) => s.startGame);
  const addBot = useGameStore((s) => s.addBot);
  const kickPlayer = useGameStore((s) => s.kickPlayer);
  const randomizeTeams = useGameStore((s) => s.randomizeTeams);
  const setMoveHistoryEnabled = useGameStore((s) => s.setMoveHistoryEnabled);
  const leaveRoom = useGameStore((s) => s.leaveRoom);
  const clearActionError = useGameStore((s) => s.clearActionError);

  const [difficulty, setDifficulty] = useState<BotDifficulty>("IMPOSSIBLE");

  const isHost = roomState?.hostId === session.playerId;
  const redCount = players.filter((p) => p.team === "RED").length;
  const blueCount = players.filter((p) => p.team === "BLUE").length;
  const canStart = players.length === 6 && redCount === 3 && blueCount === 3;
  const me = players.find((p) => p.playerId === session.playerId);
  const roomFull = players.length >= 6;
  const moveHistoryVisibleCount = roomState?.moveHistoryVisibleCount ?? 10;

  return (
    <div className="mx-auto flex h-full max-w-2xl flex-col gap-6 px-4 py-10">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-stone-400">Room code</p>
          <p className="font-display text-4xl tracking-[0.3em] text-amber-400">
            {roomState?.roomCode ?? session.roomCode}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <RulesButton />
          <button type="button" className="btn-secondary" onClick={leaveRoom}>
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
            className={`flex-1 rounded-lg border-2 py-3 font-display text-lg tracking-wide transition ${TEAM_STYLES[team]} ${
              me?.team === team ? "bg-stone-800" : "bg-stone-900 opacity-70 hover:opacity-100"
            }`}
          >
            {team} ({team === "RED" ? redCount : blueCount}/3)
          </button>
        ))}
      </div>

      <div className="panel">
        <ul className="divide-y divide-stone-800">
          {players.map((p) => (
            <li key={p.playerId} className="flex items-center justify-between px-4 py-2.5">
              <span className="flex items-center gap-2">
                <span className={`h-2 w-2 rounded-full ${p.connected ? "bg-emerald-500" : "bg-stone-600"}`} />
                <span className={p.team ? TEAM_TEXT[p.team] : ""}>{p.displayName}</span>
                {p.playerId === roomState?.hostId && (
                  <span className="rounded border border-amber-800 bg-amber-950/50 px-1.5 py-0.5 text-xs text-amber-400">
                    host
                  </span>
                )}
                {p.isBot && (
                  <span className="rounded border border-stone-600 bg-stone-800 px-1.5 py-0.5 text-xs text-stone-300">
                    bot · {p.botDifficulty?.toLowerCase()}
                  </span>
                )}
                {p.playerId === session.playerId && <span className="text-xs text-stone-500">(you)</span>}
              </span>
              <span className="flex items-center gap-3">
                <span className={`text-sm ${p.team ? TEAM_STYLES[p.team] : "text-stone-600"}`}>
                  {p.team ?? "no team"}
                </span>
                {isHost && p.playerId !== session.playerId && (
                  <button
                    type="button"
                    className="text-stone-500 hover:text-red-400"
                    title={p.isBot ? "Remove bot" : "Kick player"}
                    onClick={() => kickPlayer(p.playerId)}
                  >
                    ✕
                  </button>
                )}
              </span>
            </li>
          ))}
          {Array.from({ length: Math.max(0, 6 - players.length) }).map((_, i) => (
            <li key={`empty-${i}`} className="px-4 py-2.5 text-stone-600">
              Waiting for player…
            </li>
          ))}
        </ul>
      </div>

      {isHost && (
        <div className="panel flex flex-wrap items-center gap-3 p-3">
          <span className="text-sm text-stone-400">Add a bot:</span>
          <select
            className="rounded-md border border-stone-700 bg-stone-950 px-2 py-1.5 text-sm text-stone-200 focus:border-amber-600 focus:outline-none"
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value as BotDifficulty)}
          >
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d.charAt(0) + d.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={roomFull || redCount >= 3}
            className="btn-secondary disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => addBot("RED", difficulty)}
          >
            + to RED
          </button>
          <button
            type="button"
            disabled={roomFull || blueCount >= 3}
            className="btn-secondary disabled:cursor-not-allowed disabled:opacity-40"
            onClick={() => addBot("BLUE", difficulty)}
          >
            + to BLUE
          </button>
          <button type="button" className="btn-secondary ml-auto" onClick={randomizeTeams}>
            Randomize teams
          </button>
        </div>
      )}

      <div
        className={`panel flex flex-wrap items-center gap-3 p-3 text-sm ${isHost ? "" : "opacity-70"}`}
      >
        <label className={`flex items-center gap-3 ${isHost ? "cursor-pointer" : "cursor-default"}`}>
          <input
            type="checkbox"
            checked={roomState?.moveHistoryEnabled ?? false}
            disabled={!isHost}
            onChange={(e) => setMoveHistoryEnabled(e.target.checked, moveHistoryVisibleCount)}
            className="h-4 w-4 accent-amber-600"
          />
          <span className="text-stone-300">Show a recent-moves log during the game</span>
        </label>
        {(roomState?.moveHistoryEnabled ?? false) && (
          <label className="flex items-center gap-2">
            <span className="text-stone-500">showing last</span>
            <select
              className="rounded-md border border-stone-700 bg-stone-950 px-2 py-1 text-sm text-stone-200 focus:border-amber-600 focus:outline-none disabled:cursor-not-allowed disabled:opacity-60"
              value={moveHistoryVisibleCount}
              disabled={!isHost}
              onChange={(e) => setMoveHistoryEnabled(true, Number(e.target.value))}
            >
              {MOVE_HISTORY_COUNT_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
            <span className="text-stone-500">moves</span>
          </label>
        )}
        <span className="text-stone-600">
          — {isHost ? "your call, locked in once you start" : "set by the host, same for everyone"}
        </span>
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
        <button type="button" disabled={!canStart} onClick={startGame} className="btn-primary py-3 text-lg">
          {canStart ? "Start game" : "Need 6 players, 3 per team"}
        </button>
      )}
      {!isHost && <p className="text-center text-sm text-stone-500">Waiting for the host to start the game…</p>}
    </div>
  );
}
