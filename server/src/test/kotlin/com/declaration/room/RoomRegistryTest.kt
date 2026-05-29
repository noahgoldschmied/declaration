package com.declaration.room

import com.declaration.domain.DeclarationEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRegistryTest {

    private fun registry(scope: kotlinx.coroutines.CoroutineScope) =
        RoomRegistry(
            engine = DeclarationEngine(),
            random = Random(1L),
            gracePeriod = 120.seconds,
            scope = scope,
        )

    @Test
    fun `create makes a room and joins the host`() = runTest {
        val registry = registry(backgroundScope)
        val created = registry.create("Alice")
        advanceUntilIdle()

        assertEquals("p0", created.host.playerId.value)
        assertNotNull(registry.get(created.code))
    }

    @Test
    fun `get returns null for an unknown code`() = runTest {
        val registry = registry(backgroundScope)
        assertNull(registry.get(RoomCode("ZZZZ")))
    }

    @Test
    fun `joinRoom adds a player to an existing room`() = runTest {
        val registry = registry(backgroundScope)
        val created = registry.create("Alice")
        advanceUntilIdle()

        val bob = registry.joinRoom(created.code, "Bob")
        advanceUntilIdle()

        assertNotNull(bob)
        assertEquals("p1", bob.playerId.value)
    }

    @Test
    fun `joinRoom returns null for an unknown code`() = runTest {
        val registry = registry(backgroundScope)
        val result = registry.joinRoom(RoomCode("ZZZZ"), "Bob")
        assertNull(result)
    }

    @Test
    fun `created rooms have distinct codes`() = runTest {
        val registry = registry(backgroundScope)
        val codes = (0 until 50).map { registry.create("p$it").code }
        advanceUntilIdle()
        assertEquals(codes.size, codes.toSet().size, "all room codes must be unique")
    }
}
