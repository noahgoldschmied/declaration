package com.declaration.room

/** Thrown (via the join reply deferred) when a player cannot join: room full or already started. */
class RoomJoinException(val reason: String) : Exception(reason)
