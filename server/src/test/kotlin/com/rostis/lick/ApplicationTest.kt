package com.rostis.lick

import com.rostis.lick.config.DbConfig
import com.rostis.lick.db.TestPostgres
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.sql.DriverManager
import kotlin.test.*

class ApplicationTest {

    /** Тот же application.yaml, что и при настоящем запуске, но база — тестовая. */
    private fun lickTestApplication(
        db: DbConfig = TestPostgres.freshDatabase(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml").mergeWith(
                MapApplicationConfig(
                    "db.url" to db.url,
                    "db.user" to db.user,
                    "db.password" to db.password,
                ),
            )
        }
        block()
    }

    @Test
    fun healthReturnsOk() = lickTestApplication {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun startingServerAppliesMigrations() {
        val db = TestPostgres.freshDatabase()

        lickTestApplication(db) { startApplication() }

        DriverManager.getConnection(db.url, db.user, db.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM users").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun apiExceptionBecomesJsonErrorWithStatus() = lickTestApplication {
        routing {
            get("/test/forbidden") { throw ForbiddenException() }
            get("/test/not-found") { throw NotFoundException() }
            get("/test/invalid") { throw ValidationException("text_too_long") }
        }

        client.get("/test/forbidden").let {
            assertEquals(HttpStatusCode.Forbidden, it.status)
            assertEquals(ContentType.Application.Json, it.contentType()?.withoutParameters())
            assertEquals("""{"code":"forbidden"}""", it.bodyAsText())
        }
        client.get("/test/not-found").let {
            assertEquals(HttpStatusCode.NotFound, it.status)
            assertEquals("""{"code":"not_found"}""", it.bodyAsText())
        }
        client.get("/test/invalid").let {
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertEquals("""{"code":"text_too_long"}""", it.bodyAsText())
        }
    }

    @Test
    fun unexpectedExceptionBecomes500WithoutStackTrace() = lickTestApplication {
        routing {
            get("/test/boom") { error("secret internal details") }
        }

        val response = client.get("/test/boom")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("""{"code":"internal"}""", response.bodyAsText())
    }
}
