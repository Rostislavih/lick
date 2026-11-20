package com.rostis.lick

import com.rostis.lick.config.AppConfig
import com.rostis.lick.db.LickDatabase
import com.rostis.lick.db.Migrations
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** Общие настройки JSON: новые поля от новых клиентов не роняют старый сервер (раздел 6). */
val LickJson = Json {
    ignoreUnknownKeys = true
}

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = AppConfig.load(environment.config)
    val db = LickDatabase(config.db)
    monitor.subscribe(ApplicationStopped) { db.close() }
    Migrations.run(db.dataSource)   // база недоступна или миграция сломана — сервер не стартует

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path() != "/health" }   // пингер мониторинга не засоряет логи
    }
    install(ContentNegotiation) { json(LickJson) }
    install(StatusPages) {
        exception<ForbiddenException> { call, e -> call.respond(HttpStatusCode.Forbidden, ApiError(e.code)) }
        exception<NotFoundException> { call, e -> call.respond(HttpStatusCode.NotFound, ApiError(e.code)) }
        exception<ValidationException> { call, e -> call.respond(HttpStatusCode.BadRequest, ApiError(e.code)) }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal"))
        }
    }

    routing {
        get("/health") { call.respondText("ok") }   // для мониторинга и docker healthcheck
    }
}
