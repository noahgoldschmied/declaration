package com.declaration.domain

sealed class ActionResult {
    data class Ok(val newState: GameState, val events: List<Event>) : ActionResult()
    data class Invalid(val reason: String) : ActionResult()
}
