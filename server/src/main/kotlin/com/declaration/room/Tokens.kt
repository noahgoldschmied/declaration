package com.declaration.room

import kotlin.random.Random

/**
 * Opaque session tokens. A token is the client's identity proof for reconnection;
 * it must be unguessable. Generated from an injected [Random] so tests are deterministic.
 * (In production the registry injects a strong Random; tests inject a seeded one.)
 */
object Tokens {

    private const val BYTES = 16  // 16 bytes -> 32 hex chars

    fun generate(random: Random): String {
        val buf = ByteArray(BYTES)
        random.nextBytes(buf)
        return buf.joinToString("") { "%02x".format(it) }
    }
}
