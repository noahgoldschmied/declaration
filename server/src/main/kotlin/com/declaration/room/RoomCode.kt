package com.declaration.room

import kotlin.random.Random

@JvmInline
value class RoomCode(val value: String)

/**
 * Generates 4-letter uppercase room codes from an injected [Random].
 * Excludes the visually ambiguous letters O and I (and, since codes are letters
 * only, there are no 0/1 digits to confuse them with). 24 usable letters.
 */
class RoomCodeGenerator(private val random: Random) {

    fun next(): RoomCode =
        RoomCode((0 until CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.size)] }.joinToString(""))

    companion object {
        const val CODE_LENGTH = 4
        val ALPHABET: List<Char> = ('A'..'Z').filter { it != 'O' && it != 'I' }
    }
}
