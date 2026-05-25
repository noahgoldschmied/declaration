package com.declaration.domain

interface Engine {
    fun apply(state: GameState, actor: PlayerId, action: Action): ActionResult
}
