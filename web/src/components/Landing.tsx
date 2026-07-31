import { useState, type FormEvent } from "react";
import { createRoom, joinRoom, RoomApiError } from "../api/rooms";
import { loadSavedSession, useGameStore } from "../store/gameStore";

export function Landing() {
  const connect = useGameStore((s) => s.connect);
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
        <h1 className="text-4xl font-bold tracking-tight">Declaration</h1>
        <p className="mt-1 text-slate-400">A 6-player team card game of memory and deduction.</p>
      </div>

      {saved && (
        <button
          type="button"
          className="rounded-md border border-slate-700 bg-slate-900 px-4 py-2 text-sm text-slate-300 hover:border-slate-500"
          onClick={() => connect(saved)}
        >
          Rejoin room {saved.roomCode} as {saved.displayName}
        </button>
      )}

      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-lg border border-slate-800 bg-slate-900 p-6">
        <div className="mb-4 flex rounded-md bg-slate-800 p-1 text-sm">
          <button
            type="button"
            className={`flex-1 rounded px-3 py-1.5 ${mode === "create" ? "bg-indigo-600 text-white" : "text-slate-400"}`}
            onClick={() => setMode("create")}
          >
            Create room
          </button>
          <button
            type="button"
            className={`flex-1 rounded px-3 py-1.5 ${mode === "join" ? "bg-indigo-600 text-white" : "text-slate-400"}`}
            onClick={() => setMode("join")}
          >
            Join room
          </button>
        </div>

        <label className="mb-3 block text-sm text-slate-400">
          Display name
          <input
            className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100 focus:border-indigo-500 focus:outline-none"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={24}
            autoFocus
          />
        </label>

        {mode === "join" && (
          <label className="mb-3 block text-sm text-slate-400">
            Room code
            <input
              className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 uppercase tracking-widest text-slate-100 focus:border-indigo-500 focus:outline-none"
              value={roomCode}
              onChange={(e) => setRoomCode(e.target.value)}
              maxLength={4}
            />
          </label>
        )}

        {error && <p className="mb-3 text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-md bg-indigo-600 px-4 py-2 font-medium hover:bg-indigo-500 disabled:opacity-50"
        >
          {mode === "create" ? "Create room" : "Join room"}
        </button>
      </form>
    </div>
  );
}
