package com.rostis.lick

import kotlinx.serialization.Serializable

/**
 * Ошибка с кодом для клиента. Один и тот же код уходит и в REST (через StatusPages),
 * и в сокет (ServerEvent.Error) — раздел 9.3 гайда.
 */
open class ApiException(val code: String) : RuntimeException(code)

class ForbiddenException(code: String = "forbidden") : ApiException(code)
class NotFoundException(code: String = "not_found") : ApiException(code)
class ValidationException(code: String = "invalid") : ApiException(code)

/** Тело ответа с ошибкой: {"code":"forbidden"}. Подробностей наружу не отдаём. */
@Serializable
data class ApiError(val code: String)
