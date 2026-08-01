import { create } from "zustand";
import type {
  Action,
  ClientMessage,
  GameEvent,
  PlayerId,
  PlayerInfo,
  PlayerView,
  RoomPhase,
  ServerMessage,
  TeamId,
} from "../protocol/messages";

export interface SessionInfo {
  roomCode: string;
  sessionToken: string;
  playerId: PlayerId;
  displayName: string;
}

export type ConnectionStatus = "idle" | "connecting" | "open" | "closed";

interface RoomStateInfo {
  roomCode: string;
  phase: RoomPhase;
  hostId: PlayerId;
}

interface GameStore {
  session: SessionInfo | null;
  status: ConnectionStatus;
  roomState: RoomStateInfo | null;
  players: PlayerInfo[];
  view: PlayerView | null;
  lastEvent: GameEvent | null;
  actionError: string | null;
  staleSessionNotice: string | null;

  connect(session: SessionInfo): void;
  disconnect(): void;
  leaveRoom(): void;
  chooseTeam(team: TeamId): void;
  startGame(): void;
  submitAction(action: Action): void;
  clearActionError(): void;
  clearLastEvent(): void;
}

let socket: WebSocket | null = null;
let pingTimer: ReturnType<typeof setInterval> | null = null;
// True once the current connection has actually joined the room (received a
// RoomState). Distinguishes "was in the room, then dropped" (worth offering a
// Reconnect retry) from "this session was never valid to begin with" (the
// room restarted, or the grace period pruned it) — retrying the latter with
// the same token just fails identically forever.
let hasJoinedRoom = false;

const SESSION_KEY = "declaration:session";

export function loadSavedSession(): SessionInfo | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as SessionInfo) : null;
  } catch {
    return null;
  }
}

function saveSession(session: SessionInfo) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

function clearSavedSession() {
  localStorage.removeItem(SESSION_KEY);
}

function send(msg: ClientMessage) {
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(msg));
  }
}

function wsUrl(session: SessionInfo): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/room/${session.roomCode}?session=${session.sessionToken}`;
}

export const useGameStore = create<GameStore>((set, get) => ({
  session: null,
  status: "idle",
  roomState: null,
  players: [],
  view: null,
  lastEvent: null,
  actionError: null,
  staleSessionNotice: null,

  connect(session) {
    get().disconnect();
    hasJoinedRoom = false;
    saveSession(session);
    set({ session, status: "connecting", actionError: null, staleSessionNotice: null });

    const ws = new WebSocket(wsUrl(session));
    socket = ws;

    ws.addEventListener("open", () => {
      set({ status: "open" });
      pingTimer = setInterval(() => send({ type: "Ping" }), 20_000);
    });

    ws.addEventListener("message", (event) => {
      const msg = JSON.parse(event.data) as ServerMessage;
      handleServerMessage(msg, set, get);
    });

    ws.addEventListener("close", () => {
      if (socket === ws) {
        if (pingTimer) clearInterval(pingTimer);
        socket = null;
        if (!hasJoinedRoom) {
          clearSavedSession();
          set({
            session: null,
            status: "idle",
            roomState: null,
            players: [],
            view: null,
            lastEvent: null,
            actionError: null,
            staleSessionNotice: "Your saved session is no longer valid — the room may have restarted or expired. Please rejoin.",
          });
        } else {
          set({ status: "closed" });
        }
      }
    });

    ws.addEventListener("error", () => {
      ws.close();
    });
  },

  disconnect() {
    if (pingTimer) {
      clearInterval(pingTimer);
      pingTimer = null;
    }
    socket?.close();
    socket = null;
  },

  leaveRoom() {
    get().disconnect();
    clearSavedSession();
    set({
      session: null,
      status: "idle",
      roomState: null,
      players: [],
      view: null,
      lastEvent: null,
      actionError: null,
    });
  },

  chooseTeam(team) {
    send({ type: "ChooseTeam", team });
  },

  startGame() {
    send({ type: "StartGame" });
  },

  submitAction(action) {
    send({ type: "SubmitAction", action });
  },

  clearActionError() {
    set({ actionError: null });
  },

  clearLastEvent() {
    set({ lastEvent: null });
  },
}));

function handleServerMessage(
  msg: ServerMessage,
  set: (partial: Partial<GameStore>) => void,
  get: () => GameStore,
) {
  switch (msg.type) {
    case "Welcome":
      break;
    case "RoomState":
      hasJoinedRoom = true;
      set({
        roomState: { roomCode: msg.roomCode, phase: msg.phase, hostId: msg.hostId },
        players: msg.players,
      });
      break;
    case "GameUpdate": {
      const events = msg.events;
      set({
        view: msg.view,
        lastEvent: events.length > 0 ? events[events.length - 1] : get().lastEvent,
      });
      break;
    }
    case "ActionError":
      if (!hasJoinedRoom) {
        // The server rejected the connect attempt itself (unknown/expired
        // session token) — no legitimate in-game action error can arrive
        // before we've ever joined, so treat this as "the session is dead".
        // Close the socket (not get().disconnect(), which would null out
        // `socket` immediately and defeat the close handler's `socket === ws`
        // check) — the close handler's !hasJoinedRoom branch does the
        // actual state cleanup once the browser fires the close event.
        socket?.close();
        break;
      }
      set({ actionError: msg.reason });
      break;
    case "Pong":
      break;
  }
}
