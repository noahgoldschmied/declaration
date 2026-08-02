// Hand-mirrored from protocol/messages.md and the Kotlin types in
// server/src/main/kotlin/com/declaration/{domain,protocol}/. Any wire message
// change must update protocol/messages.md + the Kotlin sealed classes + this
// file together.
//
// Kotlin value classes (PlayerId, TeamId, CardId, DeckId) encode as bare JSON
// strings, so they're plain `string` aliases here. Map<K, V> where K is a
// value class encodes as a JSON object keyed by the wrapped string.

export type PlayerId = string;
export type CardId = string;
export type DeckId = string;
export type TeamId = "RED" | "BLUE";

export type AskOutcome = "HIT" | "MISS" | "SELF_OVERLAP";
export type Phase = "PLAYING" | "ENDED";
export type RoomPhase = "LOBBY" | "PLAYING" | "ENDED";
export type BotDifficulty = "EASY" | "MEDIUM" | "HARD" | "IMPOSSIBLE";

export type Action =
  | { type: "Ask"; target: PlayerId; card: CardId }
  | { type: "Declare"; deck: DeckId; assignments: Record<CardId, PlayerId> };

export type GameEvent =
  | { type: "Ask"; asker: PlayerId; asked: PlayerId; card: CardId; outcome: AskOutcome }
  | {
      type: "Declaration";
      declarer: PlayerId;
      deck: DeckId;
      assignments: Record<CardId, PlayerId>;
      correct: boolean;
      awardedTo: TeamId;
      // The deck's true holder for each card, as it stood right before this declare captured it.
      // Equal to `assignments` when `correct`. Safe to reveal unconditionally -- the deck leaves
      // play in this same event either way.
      actualHolders: Record<CardId, PlayerId>;
    };

export interface SelfView {
  id: PlayerId;
  team: TeamId;
  seat: number;
  hand: CardId[];
}

export interface OpponentView {
  id: PlayerId;
  team: TeamId;
  seat: number;
  handSize: number;
}

export interface PlayerView {
  you: SelfView;
  others: OpponentView[];
  turn: PlayerId;
  phase: Phase;
  winner: TeamId | null;
  capturedDecks: Record<DeckId, TeamId>;
}

export interface PlayerInfo {
  playerId: PlayerId;
  displayName: string;
  team: TeamId | null;
  connected: boolean;
  isBot: boolean;
  botDifficulty: BotDifficulty | null;
}

export type ClientMessage =
  | { type: "Hello" }
  | { type: "ChooseTeam"; team: TeamId }
  | { type: "StartGame" }
  | { type: "AddBot"; team: TeamId; difficulty: BotDifficulty }
  | { type: "KickPlayer"; playerId: PlayerId }
  | { type: "RandomizeTeams" }
  | { type: "SetMoveHistoryEnabled"; enabled: boolean; visibleCount: number }
  | { type: "SubmitAction"; action: Action }
  | { type: "SetDeclaring"; declaring: boolean }
  | { type: "Ping" };

export type ServerMessage =
  | { type: "Welcome"; playerId: PlayerId; sessionToken: string; displayName: string }
  | {
      type: "RoomState";
      roomCode: string;
      phase: RoomPhase;
      hostId: PlayerId;
      players: PlayerInfo[];
      moveHistoryEnabled: boolean;
      moveHistoryVisibleCount: number;
    }
  | { type: "GameUpdate"; view: PlayerView; events: GameEvent[] }
  | { type: "ActionError"; reason: string }
  | { type: "Kicked" }
  | { type: "DeclaringPlayers"; playerIds: PlayerId[] }
  | { type: "Pong" };
