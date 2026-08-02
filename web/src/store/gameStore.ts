import { create } from "zustand";
import type {
  Action,
  BotDifficulty,
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
  // Host-chosen, lobby-only fairness setting (same depth of recall for
  // everyone, not a per-player preference) -- locked once the game starts.
  moveHistoryEnabled: boolean;
  moveHistoryVisibleCount: number;
}

interface GameStore {
  session: SessionInfo | null;
  status: ConnectionStatus;
  roomState: RoomStateInfo | null;
  players: PlayerInfo[];
  view: PlayerView | null;
  lastEvent: GameEvent | null;
  // Rolling log of the last roomState.moveHistoryVisibleCount events, oldest
  // first (like a chat). Purely a local client-side cache -- the server never
  // resends event history (see protocol/messages.md "Memory rule"), so a
  // fresh reconnect starts empty. Only rendered when roomState.moveHistoryEnabled is true.
  eventLog: GameEvent[];
  actionError: string | null;
  staleSessionNotice: string | null;
  declaringPlayers: PlayerId[];

  connect(session: SessionInfo): void;
  disconnect(): void;
  leaveRoom(): void;
  chooseTeam(team: TeamId): void;
  startGame(): void;
  addBot(team: TeamId, difficulty: BotDifficulty): void;
  kickPlayer(playerId: PlayerId): void;
  randomizeTeams(): void;
  setMoveHistoryEnabled(enabled: boolean, visibleCount: number): void;
  submitAction(action: Action): void;
  setDeclaring(declaring: boolean): void;
  clearActionError(): void;
}

// Fallback only -- once RoomState has arrived (always before any GameUpdate),
// the synced roomState.moveHistoryVisibleCount is used instead.
const DEFAULT_EVENT_LOG_LIMIT = 10;

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
  eventLog: [],
  actionError: null,
  staleSessionNotice: null,
  declaringPlayers: [],

  connect(session) {
    get().disconnect();
    hasJoinedRoom = false;
    saveSession(session);
    set({ session, status: "connecting", actionError: null, staleSessionNotice: null, declaringPlayers: [], eventLog: [] });

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
            eventLog: [],
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
      eventLog: [],
      actionError: null,
      declaringPlayers: [],
    });
  },

  chooseTeam(team) {
    send({ type: "ChooseTeam", team });
  },

  startGame() {
    send({ type: "StartGame" });
  },

  addBot(team, difficulty) {
    send({ type: "AddBot", team, difficulty });
  },

  kickPlayer(playerId) {
    send({ type: "KickPlayer", playerId });
  },

  randomizeTeams() {
    send({ type: "RandomizeTeams" });
  },

  setMoveHistoryEnabled(enabled, visibleCount) {
    send({ type: "SetMoveHistoryEnabled", enabled, visibleCount });
  },

  submitAction(action) {
    send({ type: "SubmitAction", action });
  },

  setDeclaring(declaring) {
    send({ type: "SetDeclaring", declaring });
  },

  clearActionError() {
    set({ actionError: null });
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
        roomState: {
          roomCode: msg.roomCode,
          phase: msg.phase,
          hostId: msg.hostId,
          moveHistoryEnabled: msg.moveHistoryEnabled,
          moveHistoryVisibleCount: msg.moveHistoryVisibleCount,
        },
        players: msg.players,
      });
      break;
    case "GameUpdate": {
      const events = msg.events;
      const limit = get().roomState?.moveHistoryVisibleCount ?? DEFAULT_EVENT_LOG_LIMIT;
      set({
        view: msg.view,
        lastEvent: events.length > 0 ? events[events.length - 1] : get().lastEvent,
        eventLog: events.length > 0 ? [...get().eventLog, ...events].slice(-limit) : get().eventLog,
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
    case "Kicked":
      // Client-driven disconnect, same pattern as the stale-session case: the
      // server doesn't close the socket itself, it just tells us we're out.
      socket?.close();
      clearSavedSession();
      set({
        session: null,
        status: "idle",
        roomState: null,
        players: [],
        view: null,
        lastEvent: null,
        eventLog: [],
        actionError: null,
        declaringPlayers: [],
        staleSessionNotice: "You were removed from the room by the host.",
      });
      break;
    case "DeclaringPlayers":
      set({ declaringPlayers: msg.playerIds });
      break;
    case "Pong":
      break;
  }
}
