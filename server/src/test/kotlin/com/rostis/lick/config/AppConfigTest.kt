package com.rostis.lick.config

import io.ktor.server.config.*
import kotlin.test.*

class AppConfigTest {
    @Test
    fun readsDatabaseDefaultsFromApplicationYaml() {
        val config = AppConfig.load(ApplicationConfig("application.yaml"))

        assertEquals(
            DbConfig(url = "jdbc:postgresql://localhost:5433/lick", user = "lick", password = "lick"),
            config.db,
        )
    }
}
