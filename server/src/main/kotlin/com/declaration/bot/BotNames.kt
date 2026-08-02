package com.declaration.bot

import kotlin.random.Random

object BotNames {
    private val NAMES = listOf(
        "Doc", "Reno", "Belle", "Tex", "Cheyenne", "Marshal", "Rosalind", "Gus",
        "Calamity", "Wyatt Jr.", "Annie", "Cactus Jack", "Sundance", "Pecos",
    )

    /** A shuffled candidate order for [Room.addBot][com.declaration.room.Room.addBot] to pick the first untaken name from. */
    fun shuffledCandidates(random: Random): List<String> = NAMES.shuffled(random)
}
