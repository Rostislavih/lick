package com.rostis.lick.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Сообщение в том виде, в каком его видит клиент. Время — миллисекунды с 1970 года (UTC). */
@Serializable
data class MessageDto(
    val id: Long,
    val chatId: Long,
    val seq: Long,                    // место в ленте чата, выдаёт сервер (раздел 9.1)
    val senderId: Long,
    val clientMessageId: String,      // UUID с клиента, для идемпотентности
    val content: MessageContent,
    val createdAt: Long,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,      // soft delete: сообщение осталось в ленте с пометкой
)

/**
 * Содержимое сообщения. В базе лежит этим же JSON в messages.content.
 * Новые типы (картинка, файл, голосовое) добавляются сюда же отдельным классом.
 */
@Serializable
sealed interface MessageContent {
    @Serializable @SerialName("text")
    data class Text(val text: String) : MessageContent

    /**
     * Тип, которого эта версия приложения ещё не знает (сервер новее клиента).
     * Клиент показывает «обновите приложение», а не падает. Подставляется в LickJson.
     */
    @Serializable @SerialName("unsupported")
    data object Unsupported : MessageContent
}
