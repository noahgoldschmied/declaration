package com.declaration.rest.dto

/** Body for POST /api/rooms and POST /api/rooms/{code}/join. */
data class JoinBody(val displayName: String = "")

/** 201 response from POST /api/rooms. */
data class CreateRoomResponse(
    val roomCode: String,
    val sessionToken: String,
    val playerId: String,
)

/** 200 response from POST /api/rooms/{code}/join. */
data class JoinRoomResponse(
    val sessionToken: String,
    val playerId: String,
)

/** Error body for 4xx responses. */
data class ErrorResponse(val error: String)
