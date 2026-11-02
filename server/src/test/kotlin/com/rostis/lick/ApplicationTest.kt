package com.rostis.lick

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    /** Тот же application.yaml, что и при настоящем запуске: модули и дефолты настроек берутся из него. */
    private fun lickTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = ApplicationConfig("application.yaml") }
        block()
    }

    @Test
    fun healthReturnsOk() = lickTestApplication {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
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
