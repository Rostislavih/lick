package com.rostis.lick.config

import io.ktor.server.config.*

/** Все настройки сервера. Приходят снаружи через application.yaml и переменные окружения (раздел 12). */
data class AppConfig(val db: DbConfig) {
    companion object {
        fun load(config: ApplicationConfig) = AppConfig(
            db = DbConfig(
                url = config.property("db.url").getString(),
                user = config.property("db.user").getString(),
                password = config.property("db.password").getString(),
            ),
        )
    }
}

data class DbConfig(val url: String, val user: String, val password: String)
