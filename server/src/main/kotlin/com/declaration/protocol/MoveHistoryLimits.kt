package com.declaration.protocol

/**
 * Shared bounds for [ClientMessage.SetMoveHistoryEnabled.visibleCount] / [ServerMessage.RoomState.moveHistoryVisibleCount].
 * Lives in `protocol/` (not `room/`) so both the wire types' defaults and [com.declaration.room.Room]'s
 * server-side clamp reference a single source of truth without creating a room<->protocol import cycle.
 */
object MoveHistoryLimits {
    const val MIN_VISIBLE_COUNT = 5
    const val MAX_VISIBLE_COUNT = 50
    const val DEFAULT_VISIBLE_COUNT = 10
}
