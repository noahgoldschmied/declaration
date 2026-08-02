import { useEffect, useRef, useState } from "react";
import { useGameStore } from "../store/gameStore";
import { ALL_DECKS } from "../protocol/deckCatalog";
import { PlayerChip, TEAM_TEXT } from "./PlayerChip";
import { EventFlash } from "./EventFlash";
import { describeEvent } from "../protocol/eventText";
import { AskPanel } from "./AskPanel";
import { DeclarePanel } from "./DeclarePanel";
import { Hand } from "./Hand";
import { RulesButton } from "./RulesModal";
import type { GameEvent, PlayerId } from "../protocol/messages";

export function Table() {
  const session = useGameStore((s) => s.session)!;
  const roomState = useGameStore((s) => s.roomState);
  const players = useGameStore((s) => s.players);
  const view = useGameStore((s) => s.view)!;
  const lastEvent = useGameStore((s) => s.lastEvent);
  const actionError = useGameStore((s) => s.actionError);
  const submitAction = useGameStore((s) => s.submitAction);
  const leaveRoom = useGameStore((s) => s.leaveRoom);
  const clearActionError = useGameStore((s) => s.clearActionError);
  const declaringPlayers = useGameStore((s) => s.declaringPlayers);
  const setDeclaring = useGameStore((s) => s.setDeclaring);
  const eventLog = useGameStore((s) => s.eventLog);

  const [declareOpen, setDeclareOpen] = useState(false);
  const [confirmingDeclare, setConfirmingDeclare] = useState(false);

  function openDeclare() {
    setDeclareOpen(true);
    setDeclaring(true);
  }

  function closeDeclare() {
    setDeclareOpen(false);
    setDeclaring(false);
  }

  const nameOf = (id: PlayerId) => players.find((p) => p.playerId === id)?.displayName ?? id;
  const connectedOf = (id: PlayerId) => players.find((p) => p.playerId === id)?.connected ?? true;

  const seats = [
    { id: view.you.id, team: view.you.team, seat: view.you.seat, handSize: view.you.hand.length },
    ...view.others.map((o) => ({ id: o.id, team: o.team, seat: o.seat, handSize: o.handSize })),
  ].sort((a, b) => a.seat - b.seat);

  const redDecks = ALL_DECKS.filter((d) => view.capturedDecks[d] === "RED").length;
  const blueDecks = ALL_DECKS.filter((d) => view.capturedDecks[d] === "BLUE").length;
  const isYourTurn = view.turn === view.you.id && view.phase === "PLAYING";
  const otherDeclarers = declaringPlayers.filter((id) => id !== view.you.id);
  const showMoveHistory = roomState?.moveHistoryEnabled ?? false;

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-6 lg:flex-row lg:items-start">
      <div className="flex w-full flex-1 flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4 text-sm">
            <span className="font-display text-lg tracking-widest text-amber-400">{session.roomCode}</span>
            <span className="text-red-400">RED {redDecks}</span>
            <span className="text-stone-600">·</span>
            <span className="text-sky-400">BLUE {blueDecks}</span>
            <span className="text-stone-500">(first to 5 decks wins)</span>
          </div>
          <div className="flex items-center gap-2">
            <RulesButton />
            <button type="button" className="btn-secondary" onClick={leaveRoom}>
              Leave
            </button>
          </div>
        </div>

        {view.phase === "ENDED" && view.winner && (
          <div
            className={`rounded-lg border-2 px-4 py-3 text-center font-display text-2xl ${
              view.winner === "RED" ? "border-red-600 text-red-400" : "border-sky-500 text-sky-300"
            }`}
          >
            {view.winner} wins! ({view.winner === "RED" ? redDecks : blueDecks} decks captured)
          </div>
        )}

        {lastEvent && <EventFlash event={lastEvent} nameOf={nameOf} />}

        {otherDeclarers.length > 0 && (
          <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 bg-black/85 px-4 text-center">
            <p className="font-display text-4xl text-amber-300 sm:text-5xl">
              {otherDeclarers.map((id) => nameOf(id)).join(", ")}
            </p>
            <p className="font-display text-2xl tracking-wide text-amber-200">
              {otherDeclarers.length === 1 ? "is" : "are"} declaring a deck…
            </p>
            <p className="mt-2 text-sm text-stone-400">The game is paused until they finish.</p>
          </div>
        )}

        <div className="flex flex-wrap justify-center gap-3 rounded-lg border border-stone-800 bg-stone-900/50 p-4">
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
          <p className="mb-2 text-sm text-stone-400">
            Your hand <span className="text-stone-600">(tap a card, then tap where it goes, to reorder)</span>
          </p>
          <Hand cards={view.you.hand} />
        </div>

        {actionError && (
          <p className="rounded-md border border-red-800 bg-red-950 px-3 py-2 text-sm text-red-300">
            {actionError}
            <button type="button" className="ml-2 underline" onClick={clearActionError}>
              dismiss
            </button>
          </p>
        )}

        {view.phase === "PLAYING" && otherDeclarers.length > 0 && (
          <p className="rounded-lg border border-amber-800 bg-amber-950/30 p-4 text-center text-sm text-amber-400/70">
            Game paused — {otherDeclarers.map((id) => nameOf(id)).join(", ")}{" "}
            {otherDeclarers.length === 1 ? "is" : "are"} declaring.
          </p>
        )}

        {view.phase === "PLAYING" && otherDeclarers.length === 0 && (
          <>
            {isYourTurn ? (
              <AskPanel view={view} nameOf={nameOf} onSubmit={submitAction} />
            ) : (
              <p className="rounded-lg border border-stone-800 bg-stone-900/40 p-4 text-center text-sm text-stone-500">
                Waiting for{" "}
                <span className={TEAM_TEXT[seats.find((s) => s.id === view.turn)?.team ?? "RED"]}>
                  {nameOf(view.turn)}
                </span>{" "}
                to ask…
              </p>
            )}
            {declareOpen ? (
              <DeclarePanel
                view={view}
                nameOf={nameOf}
                onSubmit={(action) => {
                  submitAction(action);
                  closeDeclare();
                }}
                onCancel={closeDeclare}
              />
            ) : confirmingDeclare ? (
              <div className="rounded-lg border-2 border-amber-500 bg-amber-950/60 p-4 text-center">
                <p className="mb-3 font-display text-lg text-amber-200">
                  Declare a deck? One wrong card gives it to the other team.
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="btn-primary flex-1"
                    onClick={() => {
                      setConfirmingDeclare(false);
                      openDeclare();
                    }}
                  >
                    Yes, I'm sure
                  </button>
                  <button type="button" className="btn-secondary px-4 py-2" onClick={() => setConfirmingDeclare(false)}>
                    No, go back
                  </button>
                </div>
              </div>
            ) : (
              <button
                type="button"
                className="w-full rounded-lg border border-amber-800 bg-amber-950/30 px-4 py-2 font-display text-lg tracking-wide text-amber-300 hover:bg-amber-950/50"
                onClick={() => setConfirmingDeclare(true)}
              >
                Declare a deck…
              </button>
            )}
          </>
        )}
      </div>

      {showMoveHistory && <MoveHistory eventLog={eventLog} nameOf={nameOf} />}
    </div>
  );
}

function MoveHistory({
  eventLog,
  nameOf,
}: {
  eventLog: GameEvent[];
  nameOf: (id: PlayerId) => string;
}) {
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [eventLog]);

  return (
    <aside className="panel flex w-full flex-col gap-2 p-3 lg:sticky lg:top-6 lg:w-72 lg:shrink-0">
      <p className="text-xs font-medium tracking-wide text-stone-500">RECENT MOVES</p>
      <ul ref={listRef} className="flex max-h-64 flex-col gap-1.5 overflow-y-auto text-sm text-stone-300 lg:max-h-[70vh]">
        {eventLog.length === 0 ? (
          <li className="text-stone-600">Nothing yet.</li>
        ) : (
          eventLog.map((e, i) => (
            <li key={i} className="border-b border-stone-800 pb-1.5 last:border-0">
              {describeEvent(e, nameOf)}
            </li>
          ))
        )}
      </ul>
    </aside>
  );
}
