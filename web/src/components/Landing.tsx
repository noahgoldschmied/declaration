import { useState, type FormEvent } from "react";
import { createRoom, joinRoom, RoomApiError } from "../api/rooms";
import { loadSavedSession, useGameStore } from "../store/gameStore";

export function Landing() {
  const connect = useGameStore((s) => s.connect);
  const staleSessionNotice = useGameStore((s) => s.staleSessionNotice);
  const [mode, setMode] = useState<"create" | "join">("create");
  const [displayName, setDisplayName] = useState("");
  const [roomCode, setRoomCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const saved = loadSavedSession();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const name = displayName.trim();
    if (!name) {
      setError("Enter a display name.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (mode === "create") {
        const res = await createRoom(name);
        connect({ roomCode: res.roomCode, sessionToken: res.sessionToken, playerId: res.playerId, displayName: name });
      } else {
        const code = roomCode.trim().toUpperCase();
        if (!code) {
          setError("Enter a room code.");
          setBusy(false);
          return;
        }
        const res = await joinRoom(code, name);
        connect({ roomCode: code, sessionToken: res.sessionToken, playerId: res.playerId, displayName: name });
      }
    } catch (err) {
      setError(err instanceof RoomApiError ? err.message : "Something went wrong.");
      setBusy(false);
    }
  }

  return (
    <div className="flex h-full flex-col items-center justify-center gap-8 px-4">
      <div className="text-center">
        <h1 className="text-6xl font-bold tracking-tight text-amber-400 drop-shadow-[0_2px_0_rgba(0,0,0,0.6)]">
          Declaration
        </h1>
        <p className="mt-2 text-sm uppercase tracking-[0.25em] text-stone-400">
          A team card game of hidden hands and deduction
        </p>
      </div>

      {staleSessionNotice && (
        <p className="max-w-sm rounded-md border border-amber-800 bg-amber-950/40 px-3 py-2 text-center text-sm text-amber-300">
          {staleSessionNotice}
        </p>
      )}

      {saved && (
        <button type="button" className="btn-secondary px-4 py-2" onClick={() => connect(saved)}>
          Rejoin room {saved.roomCode} as {saved.displayName}
        </button>
      )}

      <form onSubmit={handleSubmit} className="panel w-full max-w-sm p-6">
        <div className="mb-4 flex rounded-md border border-stone-700 bg-stone-950 p-1 text-sm">
          <button
            type="button"
            className={`flex-1 rounded px-3 py-1.5 transition ${
              mode === "create" ? "bg-amber-600 text-stone-950 font-semibold" : "text-stone-400 hover:text-stone-200"
            }`}
            onClick={() => setMode("create")}
          >
            Create room
          </button>
          <button
            type="button"
            className={`flex-1 rounded px-3 py-1.5 transition ${
              mode === "join" ? "bg-amber-600 text-stone-950 font-semibold" : "text-stone-400 hover:text-stone-200"
            }`}
            onClick={() => setMode("join")}
          >
            Join room
          </button>
        </div>

        <label className="mb-3 block text-sm text-stone-400">
          Display name
          <input
            className="mt-1 w-full rounded-md border border-stone-700 bg-stone-950 px-3 py-2 text-stone-100 focus:border-amber-600 focus:outline-none"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={24}
            autoFocus
          />
        </label>

        {mode === "join" && (
          <label className="mb-3 block text-sm text-stone-400">
            Room code
            <input
              className="mt-1 w-full rounded-md border border-stone-700 bg-stone-950 px-3 py-2 font-display uppercase tracking-widest text-amber-300 focus:border-amber-600 focus:outline-none"
              value={roomCode}
              onChange={(e) => setRoomCode(e.target.value)}
              maxLength={4}
            />
          </label>
        )}

        {error && <p className="mb-3 text-sm text-red-400">{error}</p>}

        <button type="submit" disabled={busy} className="btn-primary w-full">
          {mode === "create" ? "Create room" : "Join room"}
        </button>
      </form>
    </div>
  );
}
