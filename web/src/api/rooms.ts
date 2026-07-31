export interface CreateRoomResponse {
  roomCode: string;
  sessionToken: string;
  playerId: string;
}

export interface JoinRoomResponse {
  sessionToken: string;
  playerId: string;
}

export class RoomApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new RoomApiError(res.status, body.error ?? res.statusText);
  }
  return res.json() as Promise<T>;
}

export async function createRoom(displayName: string): Promise<CreateRoomResponse> {
  const res = await fetch("/api/rooms", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName }),
  });
  return unwrap<CreateRoomResponse>(res);
}

export async function joinRoom(roomCode: string, displayName: string): Promise<JoinRoomResponse> {
  const res = await fetch(`/api/rooms/${encodeURIComponent(roomCode)}/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName }),
  });
  return unwrap<JoinRoomResponse>(res);
}
