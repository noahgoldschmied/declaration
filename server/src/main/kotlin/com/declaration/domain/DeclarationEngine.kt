package com.declaration.domain

class DeclarationEngine : Engine {
    override fun apply(state: GameState, actor: PlayerId, action: Action): ActionResult =
        ActionResult.Invalid("not implemented")
}
