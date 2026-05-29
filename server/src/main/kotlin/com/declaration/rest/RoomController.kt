package com.declaration.rest

import com.declaration.rest.dto.CreateRoomResponse
import com.declaration.rest.dto.ErrorResponse
import com.declaration.rest.dto.JoinBody
import com.declaration.rest.dto.JoinRoomResponse
import com.declaration.room.RoomCode
import com.declaration.room.RoomJoinException
import com.declaration.room.RoomRegistry
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class RoomController(
    private val rooms: RoomRegistry,
) {

    @PostMapping("/api/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: JoinBody): CreateRoomResponse {
        val name = body.displayName.trim()
        require(name.isNotEmpty()) { "displayName must not be blank" }
        val created = runBlocking { rooms.create(name) }
        return CreateRoomResponse(
            roomCode = created.code.value,
            sessionToken = created.host.sessionToken,
            playerId = created.host.playerId.value,
        )
    }

    @PostMapping("/api/rooms/{code}/join")
    fun join(@PathVariable code: String, @RequestBody body: JoinBody): ResponseEntity<Any> {
        val name = body.displayName.trim()
        require(name.isNotEmpty()) { "displayName must not be blank" }
        val result = runBlocking { rooms.joinRoom(RoomCode(code.uppercase()), name) }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("room not found"))
        return ResponseEntity.ok(JoinRoomResponse(result.sessionToken, result.playerId.value))
    }

    @ExceptionHandler(RoomJoinException::class)
    fun onJoinRejected(ex: RoomJoinException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.reason))

    @ExceptionHandler(IllegalArgumentException::class)
    fun onBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(ex.message ?: "bad request"))
}
