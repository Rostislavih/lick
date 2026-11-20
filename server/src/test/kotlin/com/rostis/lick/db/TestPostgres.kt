package com.rostis.lick.db

import com.rostis.lick.config.DbConfig
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

/**
 * Настоящий Postgres в Docker: один контейнер на весь прогон тестов,
 * а у каждого теста своя пустая база, чтобы тесты не влияли друг на друга.
 */
object TestPostgres {
    private val container by lazy {
        PostgreSQLContainer("postgres:17").also { it.start() }
    }

    fun freshDatabase(): DbConfig {
        val name = "test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
            connection.createStatement().use { it.execute("CREATE DATABASE $name") }
        }
        return DbConfig(
            url = "jdbc:postgresql://${container.host}:${container.firstMappedPort}/$name",
            user = container.username,
            password = container.password,
        )
    }
}
