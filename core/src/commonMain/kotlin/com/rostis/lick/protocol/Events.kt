package com.rostis.lick.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Что клиент шлёт серверу по WebSocket. */
@Serializable
sealed interface ClientEvent {
    @Serializable @SerialName("send_message")
    data class SendMessage(
        val chatId: Long,
        val clientMessageId: String,   // UUID, сгенерированный на клиенте
        val content: MessageContent,
    ) : ClientEvent

    @Serializable @SerialName("edit_message")
    data class EditMessage(val chatId: Long, val messageId: Long, val content: MessageContent) : ClientEvent

    @Serializable @SerialName("delete_message")
    data class DeleteMessage(val chatId: Long, val messageId: Long) : ClientEvent

    @Serializable @SerialName("read")
    data class MarkRead(val chatId: Long, val upToSeq: Long) : ClientEvent

    @Serializable @SerialName("typing")
    data class Typing(val chatId: Long) : ClientEvent
}

/**
 * Что сервер шлёт клиенту. Два вида событий (раздел 6 гайда):
 * - сохраняемые (ChatUpdate, MessageAck) — с eventSeq, клиент обязан их получить даже после офлайна;
 * - эфемерные (Typing) — без номера, в базу не пишутся, потеря не страшна.
 */
@Serializable
sealed interface ServerEvent {
    @Serializable @SerialName("ack")
    data class MessageAck(
        val clientMessageId: String,
        val messageId: Long,
        val seq: Long,
        val eventSeq: Long,            // номер события «новое сообщение» в журнале чата
        val createdAt: Long,
    ) : ServerEvent

    /** Любое СОХРАНЁННОЕ изменение в чате. Всегда с номером из журнала chat_events. */
    @Serializable @SerialName("chat_update")
    data class ChatUpdate(val chatId: Long, val eventSeq: Long, val change: ChatChange) : ServerEvent

    /** «Печатает…» — в базу не пишется, номера нет, потерять не страшно. */
    @Serializable @SerialName("typing")
    data class Typing(val chatId: Long, val userId: Long) : ServerEvent

    @Serializable @SerialName("error")
    data class Error(val code: String, val clientMessageId: String? = null) : ServerEvent
}

/** ЧТО именно изменилось. Эти же объекты лежат в chat_events.payload и отдаются в GET /events. */
@Serializable
sealed interface ChatChange {
    @Serializable @SerialName("message_new")
    data class MessageNew(val message: MessageDto) : ChatChange

    @Serializable @SerialName("message_edited")
    data class MessageEdited(val messageId: Long, val content: MessageContent, val editedAt: Long) : ChatChange

    @Serializable @SerialName("message_deleted")
    data class MessageDeleted(val messageId: Long) : ChatChange

    @Serializable @SerialName("reaction")          // появится вместе с миграцией V2__add_reactions
    data class Reaction(val messageId: Long, val userId: Long, val emoji: String?) : ChatChange   // null = снял

    @Serializable @SerialName("read")
    data class Read(val userId: Long, val upToSeq: Long) : ChatChange
}
