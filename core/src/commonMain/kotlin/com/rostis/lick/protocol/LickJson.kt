package com.rostis.lick.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Единые настройки JSON для клиента и сервера (раздел 6 гайда, «Версионирование»).
 * Старые версии приложения живут годами, поэтому:
 * - лишние поля от более новой версии пропускаются, а не роняют разбор;
 * - незнакомый тип содержимого сообщения превращается в MessageContent.Unsupported.
 */
val LickJson = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphicDefaultDeserializer(MessageContent::class) { MessageContent.Unsupported.serializer() }
    }
}
