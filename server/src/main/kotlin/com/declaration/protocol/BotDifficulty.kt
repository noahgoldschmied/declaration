package com.declaration.protocol

import kotlinx.serialization.Serializable

/** How often a bot fails to record a fact it just observed — see [com.declaration.bot.BotBrain]. */
@Serializable
enum class BotDifficulty(val forgetRate: Double) {
    EASY(0.25),
    MEDIUM(0.15),
    HARD(0.075),
    IMPOSSIBLE(0.0),
}
