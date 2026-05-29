package com.declaration.protocol

import kotlinx.serialization.json.Json

/**
 * The single JSON configuration for the WebSocket wire. Shared by the WS handler and tests.
 * Default class discriminator is "type"; unknown keys are ignored for forward-compat;
 * defaults are encoded so empty `data object` messages still serialize their discriminator.
 */
object WireJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
