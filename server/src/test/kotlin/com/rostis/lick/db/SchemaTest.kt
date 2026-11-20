package com.rostis.lick.db

import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

/** Требования из раздела 7.2 держатся на ограничениях базы, поэтому проверяем сами ограничения. */
class SchemaTest {
    private val db = LickDatabase(TestPostgres.freshDatabase())
    private val appliedOnStart = Migrations.run(db.dataSource)

    @AfterTest
    fun closeDatabase() = db.close()

    private fun sql(statement: String) = db.dataSource.connection.use { connection ->
        connection.createStatement().use { it.execute(statement) }
    }

    private fun assertRejected(sqlState: String, statement: String) {
        val error = assertFailsWith<SQLException> { sql(statement) }
        assertEquals(sqlState, error.sqlState, error.message)
    }

    private fun insertUser(id: Long) =
        sql("INSERT INTO users (id, username, display_name, password_hash) VALUES ($id, 'user$id', 'User', 'hash')")

    private fun insertChat(id: Long, directKey: String? = null) {
        val key = directKey?.let { "'$it'" } ?: "NULL"
        val type = if (directKey != null) "DIRECT" else "GROUP"
        sql("INSERT INTO chats (id, type, direct_key) VALUES ($id, '$type', $key)")
    }

    private fun insertMessageSql(chatId: Long, seq: Long, senderId: Long = 1, clientMessageId: UUID = UUID.randomUUID()) =
        """INSERT INTO messages (chat_id, seq, sender_id, client_message_id, content)
           VALUES ($chatId, $seq, $senderId, '$clientMessageId', '{"type":"text","text":"привет"}')"""

    private fun insertEventSql(chatId: Long, eventSeq: Long) =
        """INSERT INTO chat_events (chat_id, event_seq, type, payload)
           VALUES ($chatId, $eventSeq, 'read', '{"type":"read"}')"""

    @Test
    fun createsAllTables() {
        val tables = db.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                ).use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
        }

        assertEquals(
            setOf(
                "users", "chats", "members", "messages", "chat_events",
                "refresh_tokens", "devices", "flyway_schema_history",
            ),
            tables,
        )
    }

    @Test
    fun secondRunAppliesNothing() {
        assertTrue(appliedOnStart > 0)
        assertEquals(0, Migrations.run(db.dataSource))
    }

    @Test
    fun usernameIsUnique() {
        insertUser(1)
        assertRejected(UNIQUE, "INSERT INTO users (username, display_name, password_hash) VALUES ('user1', 'X', 'h')")
    }

    @Test
    fun secondDirectChatBetweenSameUsersIsRejected() {
        insertChat(1, directKey = "1:2")
        assertRejected(UNIQUE, "INSERT INTO chats (type, direct_key) VALUES ('DIRECT', '1:2')")
    }

    @Test
    fun groupsMayShareEmptyDirectKey() {
        insertChat(1)
        insertChat(2)
    }

    @Test
    fun unknownChatTypeIsRejected() {
        assertRejected(CHECK, "INSERT INTO chats (type) VALUES ('CHANNEL')")
    }

    @Test
    fun countersOfNewChatStartAtZero() {
        insertChat(1)
        val counters = db.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT last_seq, last_event_seq FROM chats WHERE id = 1").use { rows ->
                    rows.next()
                    rows.getLong(1) to rows.getLong(2)
                }
            }
        }
        assertEquals(0L to 0L, counters)
    }

    @Test
    fun userIsMemberOfChatOnlyOnce() {
        insertUser(1)
        insertChat(1)
        sql("INSERT INTO members (chat_id, user_id) VALUES (1, 1)")
        assertRejected(UNIQUE, "INSERT INTO members (chat_id, user_id) VALUES (1, 1)")
    }

    @Test
    fun seqIsUniqueInsideChatButNotAcrossChats() {
        insertUser(1)
        insertChat(1)
        insertChat(2)
        sql(insertMessageSql(chatId = 1, seq = 1))
        sql(insertMessageSql(chatId = 2, seq = 1))
        assertRejected(UNIQUE, insertMessageSql(chatId = 1, seq = 1))
    }

    @Test
    fun repeatedClientMessageIdFromSameSenderIsRejected() {
        insertUser(1)
        insertChat(1)
        val clientMessageId = UUID.randomUUID()
        sql(insertMessageSql(chatId = 1, seq = 1, clientMessageId = clientMessageId))
        assertRejected(UNIQUE, insertMessageSql(chatId = 1, seq = 2, clientMessageId = clientMessageId))
    }

    @Test
    fun differentSendersMayUseSameClientMessageId() {
        insertUser(1)
        insertUser(2)
        insertChat(1)
        val clientMessageId = UUID.randomUUID()
        sql(insertMessageSql(chatId = 1, seq = 1, senderId = 1, clientMessageId = clientMessageId))
        sql(insertMessageSql(chatId = 1, seq = 2, senderId = 2, clientMessageId = clientMessageId))
    }

    @Test
    fun messageCannotReferToMissingChat() {
        insertUser(1)
        assertRejected(FOREIGN_KEY, insertMessageSql(chatId = 999, seq = 1))
    }

    @Test
    fun eventSeqIsUniqueInsideChatButNotAcrossChats() {
        insertChat(1)
        insertChat(2)
        sql(insertEventSql(chatId = 1, eventSeq = 1))
        sql(insertEventSql(chatId = 2, eventSeq = 1))
        assertRejected(UNIQUE, insertEventSql(chatId = 1, eventSeq = 1))
    }

    @Test
    fun refreshTokenHashIsUnique() {
        insertUser(1)
        val insert = "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (1, 'hash', now())"
        sql(insert)
        assertRejected(UNIQUE, insert)
    }

    @Test
    fun pushTokenIsUnique() {
        insertUser(1)
        val insert = "INSERT INTO devices (user_id, platform, push_token) VALUES (1, 'android', 'token')"
        sql(insert)
        assertRejected(UNIQUE, insert)
    }

    private companion object {
        // Коды ошибок Postgres (SQLSTATE)
        const val UNIQUE = "23505"
        const val CHECK = "23514"
        const val FOREIGN_KEY = "23503"
    }
}
