package com.rostis.lick.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolJsonTest {

    @Test
    fun sendMessageGoesOverTheWireWithTypeDiscriminators() {
        val event: ClientEvent = ClientEvent.SendMessage(
            chatId = 42,
            clientMessageId = "c0ffee00-0000-4000-8000-000000000001",
            content = MessageContent.Text("привет"),
        )

        assertEquals(
            """{"type":"send_message","chatId":42,"clientMessageId":"c0ffee00-0000-4000-8000-000000000001",""" +
                """"content":{"type":"text","text":"привет"}}""",
            LickJson.encodeToString(event),
        )
    }

    @Test
    fun chatUpdateWithNewMessageSurvivesRoundTrip() {
        val event: ServerEvent = ServerEvent.ChatUpdate(
            chatId = 42,
            eventSeq = 127,
            change = ChatChange.MessageNew(
                MessageDto(
                    id = 9001,
                    chatId = 42,
                    seq = 118,
                    senderId = 7,
                    clientMessageId = "c0ffee00-0000-4000-8000-000000000001",
                    content = MessageContent.Text("привет"),
                    createdAt = 1_700_000_000_000,
                ),
            ),
        )

        val json = LickJson.encodeToString(event)

        assertEquals(event, LickJson.decodeFromString<ServerEvent>(json))
    }

    @Test
    fun clientReadsErrorWithoutClientMessageId() {
        assertEquals(
            ServerEvent.Error(code = "forbidden"),
            LickJson.decodeFromString<ServerEvent>("""{"type":"error","code":"forbidden"}"""),
        )
    }

    @Test
    fun fieldAddedByNewerVersionIsIgnored() {
        val json = """{"type":"read","chatId":42,"upToSeq":118,"fromFutureVersion":true}"""

        assertEquals(
            ClientEvent.MarkRead(chatId = 42, upToSeq = 118),
            LickJson.decodeFromString<ClientEvent>(json),
        )
    }

    @Test
    fun contentTypeFromNewerServerBecomesUnsupportedInsteadOfCrash() {
        val json = """{"type":"message_new","message":{"id":1,"chatId":42,"seq":1,"senderId":7,""" +
            """"clientMessageId":"c0ffee00-0000-4000-8000-000000000001",""" +
            """"content":{"type":"sticker","stickerId":"cat"},"createdAt":1}}"""

        val change = LickJson.decodeFromString<ChatChange>(json) as ChatChange.MessageNew

        assertEquals(MessageContent.Unsupported, change.message.content)
    }
}
