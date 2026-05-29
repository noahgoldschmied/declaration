package com.declaration.rest

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun body(vararg pairs: Pair<String, String>): String =
        objectMapper.writeValueAsString(pairs.toMap())

    private fun createRoom(displayName: String = "Alice"): String {
        val response = mockMvc.post("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
            content = body("displayName" to displayName)
        }.andReturn().response.contentAsString
        return objectMapper.readTree(response).get("roomCode").asText()
    }

    @Test
    fun `create room returns 201 with code token and playerId`() {
        mockMvc.post("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
            content = body("displayName" to "Alice")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.roomCode") { isNotEmpty() }
            jsonPath("$.sessionToken") { isNotEmpty() }
            jsonPath("$.playerId") { value("p0") }
        }
    }

    @Test
    fun `join existing room returns 200 with token and playerId`() {
        val code = createRoom()
        mockMvc.post("/api/rooms/$code/join") {
            contentType = MediaType.APPLICATION_JSON
            content = body("displayName" to "Bob")
        }.andExpect {
            status { isOk() }
            jsonPath("$.sessionToken") { isNotEmpty() }
            jsonPath("$.playerId") { value("p1") }
        }
    }

    @Test
    fun `join unknown room returns 404`() {
        mockMvc.post("/api/rooms/ZZZZ/join") {
            contentType = MediaType.APPLICATION_JSON
            content = body("displayName" to "Bob")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `join a full room returns 409`() {
        val code = createRoom()
        repeat(5) { i ->
            mockMvc.post("/api/rooms/$code/join") {
                contentType = MediaType.APPLICATION_JSON
                content = body("displayName" to "p${i + 1}")
            }.andExpect { status { isOk() } }
        }
        mockMvc.post("/api/rooms/$code/join") {
            contentType = MediaType.APPLICATION_JSON
            content = body("displayName" to "latecomer")
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `create with blank displayName returns 400`() {
        mockMvc.post("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
            content = body("displayName" to "   ")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
