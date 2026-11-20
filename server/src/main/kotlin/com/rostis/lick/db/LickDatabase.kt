package com.rostis.lick.db

import com.rostis.lick.config.DbConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

/** Пул соединений к Postgres и единственный вход в транзакции (разделы 7.4, 7.5). */
class LickDatabase(config: DbConfig) : AutoCloseable {
    private val pool = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 10
        },
    )
    private val exposed = Database.connect(pool)

    val dataSource: DataSource get() = pool

    /**
     * Весь блок — одна транзакция: COMMIT в конце, любое исключение — ROLLBACK.
     * JDBC блокирующий, поэтому работаем на Dispatchers.IO, а не на потоках Ktor.
     * Блок намеренно не suspend: нельзя держать транзакцию и блокировку строки, ожидая сеть.
     */
    suspend fun <T> dbQuery(block: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { transaction(exposed) { block() } }

    override fun close() = pool.close()
}
