import { useGameStore } from "../store/gameStore";
import { sortHand } from "../protocol/cards";
import { ALL_DECKS } from "../protocol/deckCatalog";
import { Card } from "./Card";
import { PlayerChip } from "./PlayerChip";
import { EventFlash } from "./EventFlash";
import { AskPanel } from "./AskPanel";
import { DeclarePanel } from "./DeclarePanel";
import type { PlayerId } from "../protocol/messages";

export function Table() {
  const session = useGameStore((s) => s.session)!;
  const players = useGameStore((s) => s.players);
  const view = useGameStore((s) => s.view)!;
  const lastEvent = useGameStore((s) => s.lastEvent);
  const actionError = useGameStore((s) => s.actionError);
  const submitAction = useGameStore((s) => s.submitAction);
  const leaveRoom = useGameStore((s) => s.leaveRoom);
  const clearActionError = useGameStore((s) => s.clearActionError);
  const clearLastEvent = useGameStore((s) => s.clearLastEvent);

  const nameOf = (id: PlayerId) => players.find((p) => p.playerId === id)?.displayName ?? id;
  const connectedOf = (id: PlayerId) => players.find((p) => p.playerId === id)?.connected ?? true;

  const seats = [
    { id: view.you.id, team: view.you.team, seat: view.you.seat, handSize: view.you.hand.length },
    ...view.others.map((o) => ({ id: o.id, team: o.team, seat: o.seat, handSize: o.handSize })),
  ].sort((a, b) => a.seat - b.seat);

  const redDecks = ALL_DECKS.filter((d) => view.capturedDecks[d] === "RED").length;
  const blueDecks = ALL_DECKS.filter((d) => view.capturedDecks[d] === "BLUE").length;
  const isYourTurn = view.turn === view.you.id && view.phase === "PLAYING";

  return (
    <div className="mx-auto flex h-full max-w-4xl flex-col gap-4 px-4 py-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4 text-sm">
          <span className="font-mono text-lg tracking-widest text-slate-300">{session.roomCode}</span>
          <span className="text-rose-400">RED {redDecks}</span>
          <span className="text-slate-600">·</span>
          <span className="text-sky-400">BLUE {blueDecks}</span>
          <span className="text-slate-500">(first to 5 decks wins)</span>
        </div>
        <button
          type="button"
          className="rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-400 hover:border-slate-500"
          onClick={leaveRoom}
        >
          Leave
        </button>
      </div>

      {view.phase === "ENDED" && view.winner && (
        <div
          className={`rounded-lg border-2 px-4 py-3 text-center text-lg font-bold ${
            view.winner === "RED" ? "border-rose-500 text-rose-300" : "border-sky-500 text-sky-300"
          }`}
        >
          {view.winner} wins! ({view.winner === "RED" ? redDecks : blueDecks} decks captured)
        </div>
      )}

      {lastEvent && <EventFlash event={lastEvent} nameOf={nameOf} onDismiss={clearLastEvent} />}

      <div className="flex flex-wrap justify-center gap-3 rounded-lg border border-slate-800 bg-slate-900/50 p-4">
        {seats.map((p) => (
          <PlayerChip
            key={p.id}
            name={nameOf(p.id)}
            team={p.team}
            handSize={p.handSize}
            isTurn={view.turn === p.id}
            isYou={p.id === view.you.id}
            connected={connectedOf(p.id)}
          />
        ))}
      </div>

      <div>
        <p className="mb-2 text-sm text-slate-400">Your hand</p>
        <div className="flex flex-wrap gap-2">
          {sortHand(view.you.hand).map((c) => (
            <Card key={c} card={c} />
          ))}
          {view.you.hand.length === 0 && <p className="text-sm text-slate-600">Empty — you're out of cards.</p>}
        </div>
      </div>

      {actionError && (
        <p className="rounded-md border border-red-800 bg-red-950 px-3 py-2 text-sm text-red-300">
          {actionError}
          <button type="button" className="ml-2 underline" onClick={clearActionError}>
            dismiss
          </button>
        </p>
      )}

      {view.phase === "PLAYING" && (
        <>
          {isYourTurn ? (
            <AskPanel view={view} nameOf={nameOf} onSubmit={submitAction} />
          ) : (
            <p className="rounded-lg border border-slate-800 bg-slate-900/40 p-4 text-center text-sm text-slate-500">
              Waiting for {nameOf(view.turn)} to ask…
            </p>
          )}
          <DeclarePanel view={view} nameOf={nameOf} onSubmit={submitAction} />
        </>
      )}
    </div>
  );
}
