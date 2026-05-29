package com.declaration.room

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomCodeTest {

    @Test
    fun `generated code is 4 uppercase letters`() {
        val code = RoomCodeGenerator(Random(1L)).next()
        assertEquals(4, code.value.length)
        assertTrue(code.value.all { it in 'A'..'Z' }, "code ${code.value} must be A-Z")
    }

    @Test
    fun `generated code excludes ambiguous letters O and I`() {
        val gen = RoomCodeGenerator(Random(7L))
        repeat(500) {
            val code = gen.next()
            assertTrue('O' !in code.value, "code ${code.value} must not contain O")
            assertTrue('I' !in code.value, "code ${code.value} must not contain I")
        }
    }

    @Test
    fun `seeded generator is deterministic`() {
        val a = RoomCodeGenerator(Random(42L)).next()
        val b = RoomCodeGenerator(Random(42L)).next()
        assertEquals(a, b)
    }

    @Test
    fun `successive codes differ`() {
        val gen = RoomCodeGenerator(Random(42L))
        val first = gen.next()
        val second = gen.next()
        assertTrue(first != second, "expected distinct successive codes, got $first twice")
    }
}
