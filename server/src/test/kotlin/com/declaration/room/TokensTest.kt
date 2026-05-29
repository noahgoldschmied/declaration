package com.declaration.room

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokensTest {

    @Test
    fun `token is 32 lowercase hex characters`() {
        val token = Tokens.generate(Random(1L))
        assertEquals(32, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' }, "token $token must be hex")
    }

    @Test
    fun `seeded generation is deterministic`() {
        assertEquals(Tokens.generate(Random(99L)), Tokens.generate(Random(99L)))
    }

    @Test
    fun `successive tokens differ`() {
        val random = Random(99L)
        val a = Tokens.generate(random)
        val b = Tokens.generate(random)
        assertTrue(a != b, "expected distinct tokens from successive draws")
    }
}
