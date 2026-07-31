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

  connect(session) {
    get().disconnect();
    saveSession(session);
    set({ session, status: "connecting", actionError: null });

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
        set({ status: "closed" });
        if (pingTimer) clearInterval(pingTimer);
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
      set({ actionError: msg.reason });
      break;
    case "Pong":
      break;
  }
}
