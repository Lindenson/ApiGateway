package org.hormigas.ws.infrastructure.persistance.postgres.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.message.MessageType;
import org.hormigas.ws.infrastructure.persistance.postgres.dto.OutboxRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageMappersTest {

    private MessageMappers mapper;

    @BeforeEach
    void setup() {
        mapper = new MessageMappers();
        mapper.mapper = new ObjectMapper(); // Внедряем ObjectMapper вручную для теста
    }

    @Test
    void testToDomainMessage_withPayloadAndMeta() {
        Map<String, String> meta = new HashMap<>();
        meta.put("key", "value");
        String payloadJson = "{\"kind\":\"text\",\"body\":\"hello\"}";
        String metaJson = "{\"key\":\"value\"}";

        OutboxRow row = new OutboxRow(
                1L,
                "sender1",
                "recipient1",
                payloadJson,
                metaJson,
                1000L,
                "UTC",
                2000L,
                "CHAT_ACK",
                "msg-1",
                "corr-1",
                Instant.now(),
                Instant.now().plusSeconds(60)
        );

        Message msg = mapper.toDomainMessage(row);

        assertNotNull(msg);
        assertEquals("sender1", msg.getSenderId());
        assertEquals("recipient1", msg.getRecipientId());
        assertEquals(MessageType.CHAT_ACK, msg.getType());
        assertEquals("msg-1", msg.getMessageId());
        assertEquals("corr-1", msg.getCorrelationId());
        assertEquals(1000L, msg.getSenderTimestamp());
        assertEquals("UTC", msg.getSenderTimezone());
        assertEquals(2000L, msg.getServerTimestamp());
        assertNotNull(msg.getPayload());
        assertEquals("text", msg.getPayload().getKind());
        assertEquals("hello", msg.getPayload().getBody());
        assertNotNull(msg.getMeta());
        assertEquals("value", msg.getMeta().get("key"));
    }

    @Test
    void testToDomainMessage_withNullPayloadAndMeta() {
        OutboxRow row = new OutboxRow(
                2L,
                "sender2",
                "recipient2",
                null,
                null,
                1001L,
                "UTC",
                2001L,
                null,
                "msg-2",
                null,
                Instant.now(),
                Instant.now().plusSeconds(60)
        );

        Message msg = mapper.toDomainMessage(row);

        assertNotNull(msg);
        assertEquals("sender2", msg.getSenderId());
        assertEquals("recipient2", msg.getRecipientId());
        assertNull(msg.getType());
        assertEquals("msg-2", msg.getMessageId());
        assertNull(msg.getCorrelationId());
        assertEquals(1001L, msg.getSenderTimestamp());
        assertEquals("UTC", msg.getSenderTimezone());
        assertEquals(2001L, msg.getServerTimestamp());
        assertNull(msg.getPayload());
        assertNull(msg.getMeta());
    }

    @Test
    void testToOutboxMessage_andBack() throws Exception {
        Map<String, String> meta = new HashMap<>();
        meta.put("foo", "bar");

        Message.Payload payload = Message.Payload.builder().kind("text").body("hello").build();

        Message original = Message.builder()
                .senderId("s1")
                .recipientId("r1")
                .type(MessageType.CHAT_IN)
                .messageId("m1")
                .correlationId("c1")
                .senderTimestamp(123L)
                .senderTimezone("UTC")
                .serverTimestamp(456L)
                .payload(payload)
                .meta(meta)
                .build();

        var outboxMessage = mapper.toOutboxMessage(original);
        // Десериализация payload и meta обратно
        OutboxRow row = new OutboxRow(
                1L,
                outboxMessage.senderId(),
                outboxMessage.recipientId(),
                outboxMessage.payloadJson(),
                outboxMessage.metaJson(),
                outboxMessage.senderTimestamp(),
                outboxMessage.senderTimezone(),
                outboxMessage.serverTimestamp(),
                outboxMessage.type(),
                outboxMessage.messageId(),
                outboxMessage.correlationId(),
                Instant.now(),
                Instant.now().plusSeconds(60)
        );

        Message converted = mapper.toDomainMessage(row);

        assertEquals(original.getSenderId(), converted.getSenderId());
        assertEquals(original.getRecipientId(), converted.getRecipientId());
        assertEquals(original.getType(), converted.getType());
        assertEquals(original.getMessageId(), converted.getMessageId());
        assertEquals(original.getCorrelationId(), converted.getCorrelationId());
        assertEquals(original.getSenderTimestamp(), converted.getSenderTimestamp());
        assertEquals(original.getServerTimestamp(), converted.getServerTimestamp());
        assertEquals(original.getPayload().getKind(), converted.getPayload().getKind());
        assertEquals(original.getPayload().getBody(), converted.getPayload().getBody());
        assertEquals(original.getMeta().get("foo"), converted.getMeta().get("foo"));
    }
}
