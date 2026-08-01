import type { ReactNode } from "react";
import { useGameStore } from "./store/gameStore";
import { Landing } from "./components/Landing";
import { RoomLobby } from "./components/RoomLobby";
import { Table } from "./components/Table";

function App() {
  const session = useGameStore((s) => s.session);
  const status = useGameStore((s) => s.status);
  const view = useGameStore((s) => s.view);
  const connect = useGameStore((s) => s.connect);

  if (!session) {
    return <Landing />;
  }

  if (status === "connecting" || status === "idle") {
    return <CenteredMessage title="Connecting…" />;
  }

  if (status === "closed") {
    return (
      <CenteredMessage title="Disconnected">
        <button type="button" className="btn-primary mt-4" onClick={() => connect(session)}>
          Reconnect
        </button>
      </CenteredMessage>
    );
  }

  // Once a game has been dealt, GameUpdate keeps arriving even after ended
  // (winner screen) — the table view stays authoritative over the lobby.
  if (view) {
    return <Table />;
  }

  return <RoomLobby />;
}

function CenteredMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2">
      <h1 className="font-display text-2xl text-amber-400">{title}</h1>
      {children}
    </div>
  );
}

export default App;
