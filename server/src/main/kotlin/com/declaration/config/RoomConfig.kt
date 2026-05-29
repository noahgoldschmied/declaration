package com.declaration.config

import com.declaration.domain.DeclarationEngine
import com.declaration.domain.Engine
import com.declaration.room.RoomRegistry
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Wires the framework-free room layer into Spring. The room/ and domain/ packages have no
 * Spring dependencies; this is the single place where they become beans.
 */
@Configuration
class RoomConfig {

    // Application-lifetime scope for all room consumer coroutines and disconnect timers.
    // SupervisorJob so one room's failure does not cancel siblings. Cancelled on shutdown.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Bean
    fun engine(): Engine = DeclarationEngine()

    /** Thread-safe, cryptographically strong source for tokens and deals (shared across rooms). */
    @Bean
    fun gameRandom(): Random = SecureRandom().asKotlinRandom()

    /** How long a disconnected player is kept before removal. */
    @Bean
    fun disconnectGracePeriod(): Duration = 120.seconds

    @Bean
    fun roomScope(): CoroutineScope = applicationScope

    @Bean
    fun roomRegistry(
        engine: Engine,
        gameRandom: Random,
        disconnectGracePeriod: Duration,
        roomScope: CoroutineScope,
    ): RoomRegistry = RoomRegistry(engine, gameRandom, disconnectGracePeriod, roomScope)

    @PreDestroy
    fun shutdown() {
        applicationScope.cancel()
    }
}
