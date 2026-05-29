package com.declaration.config

import com.declaration.room.RoomRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@SpringBootTest
class RoomConfigTest {

    @Autowired
    lateinit var roomRegistry: RoomRegistry

    @Test
    fun `RoomRegistry bean is wired into the application context`() {
        assertNotNull(roomRegistry)
    }
}
