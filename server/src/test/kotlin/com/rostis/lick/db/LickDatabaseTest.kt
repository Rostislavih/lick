package com.rostis.lick.db

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LickDatabaseTest {
    private val db = LickDatabase(TestPostgres.freshDatabase())

    @AfterTest
    fun closeDatabase() = db.close()

    private fun rowCount(): Int = db.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM items").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    private fun createItemsTable() = db.dataSource.connection.use { connection ->
        connection.createStatement().use { it.execute("CREATE TABLE items (id INT)") }
    }

    @Test
    fun dbQueryReturnsBlockResultAndCommits() = runBlocking {
        createItemsTable()

        val result = db.dbQuery {
            exec("INSERT INTO items VALUES (1)")
            "done"
        }

        assertEquals("done", result)
        assertEquals(1, rowCount())
    }

    @Test
    fun exceptionInDbQueryRollsBackEverything() = runBlocking {
        createItemsTable()

        assertFailsWith<IllegalStateException> {
            db.dbQuery {
                exec("INSERT INTO items VALUES (1)")
                error("упали посреди транзакции")
            }
        }

        assertEquals(0, rowCount())
    }
}
